package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.AesBlockEncrypt
import com.dsikic.cluecose.core.crypto.bundled6388f0LowSeedEntrySource
import com.dsikic.cluecose.core.crypto.builderProcess2P5PublicKey65FromEntropy
import com.dsikic.cluecose.core.crypto.phase5BlockEncryptor
import com.dsikic.cluecose.core.tables.TablesSource
import com.dsikic.cluecose.core.util.ByteUtil
import com.dsikic.cluecose.core.util.Hex
import java.security.SecureRandom

/**
 * Drives the Libre 3 BLE pairing handshake over a [PairingTransport]. 1:1 port
 * of the orchestration parts of LibreCRKit `Pairing/PairingFlow.swift`: the
 * *cached/direct reconnect* (the pristine post-pairing reconnect path a
 * paired sensor uses) plus the command-gated first-pair / authorization
 * preamble and handshake through Phase 6.
 *
 * The cached path is self-contained:
 *
 *   `0x11 StartAuthorization`
 *   → command notify prefix `0x08`
 *   → challenge notify `R1_16 || nonce7`
 *   → Phase 5 response `R1 || R2 || tail4` (54B) under the cached Phase-5 key
 *   → command `0x08` / response prefix `0x08`
 *   → challenge notify Phase 6 (67B) → session material `R2 || R1 || kEnc || ivEnc8`
 *
 * The transport blocks on the GATT callbacks (Android adapter) or returns
 * scripted bytes (tests), so this flow runs synchronously on a background
 * thread.
 */
class PairingFlow {

    private val transport: CommandPairingTransport
    private val phoneCert: PhoneCert?
    private val phoneEph: EphemeralKeyPair
    private val sensorCertSigningKeys: List<ByteArray>
    private val eventLogger: (String) -> Unit

    /**
     * Create a flow for cached/direct reconnects that do not send a phone
     * certificate or ephemeral public key. Calling full cert/ephemeral
     * authorization methods on this instance throws
     * [PairingFlowError.PhoneCertRequired].
     *
     * 1:1 port of the Swift primary `PairingFlow.init(transport:
     * sensorCertSigningKeys:eventLogger:)`.
     */
    constructor(
        transport: CommandPairingTransport,
        sensorCertSigningKeys: List<ByteArray> = Libre3PatchSigningKey.known,
        eventLogger: (String) -> Unit = { },
    ) {
        this.transport = transport
        this.phoneCert = null
        this.phoneEph = EphemeralKeyPair.generate()
        this.sensorCertSigningKeys = sensorCertSigningKeys
        this.eventLogger = eventLogger
    }

    /**
     * Create a full first-pair / authorization flow: the [phoneCert] is sent
     * in Phase 1 and the [phoneEph] ephemeral keypair in Phase 3.
     *
     * 1:1 port of the Swift secondary `PairingFlow.init(transport:phoneCert:
     * phoneEph:sensorCertSigningKeys:eventLogger:)`.
     */
    constructor(
        transport: CommandPairingTransport,
        phoneCert: PhoneCert,
        phoneEph: EphemeralKeyPair = EphemeralKeyPair.generate(),
        sensorCertSigningKeys: List<ByteArray> = Libre3PatchSigningKey.known,
        eventLogger: (String) -> Unit = { },
    ) {
        this.transport = transport
        this.phoneCert = phoneCert
        this.phoneEph = phoneEph
        this.sensorCertSigningKeys = sensorCertSigningKeys
        this.eventLogger = eventLogger
    }

    // MARK: - Phases 1..4 (full cert/ephemeral exchange)

    /**
     * Run Phases 1..4: send the phone cert (162B), receive + parse the sensor
     * cert (140B notify) and verify its signature, send the phone ephemeral
     * pubkey (65B padded to 72B), receive the sensor ephemeral pubkey (65B
     * notify) and compute both ECDH secrets.
     *
     * Phase 5+ requires the session-key derivation to be pinned down; see
     * [SessionKey] and [runCommandGatedAuthorizationHandshake].
     *
     * 1:1 port of Swift `PairingFlow.runPhases1To4`.
     */
    fun runPhases1To4(): PairingHandshakeResult {
        val cert = phoneCert ?: throw PairingFlowError.PhoneCertRequired
        // Phase 1 — send phone cert (162B; framed into 9 x (2B offset + 18B chunk))
        transport.write(cert.raw, BleCharRef.CERT_HANDSHAKE)

        // Phase 2 — receive 140B sensor cert + parse
        val sensorCertRaw = transport.awaitNotify(BleCharRef.CERT_HANDSHAKE, SensorCert.TOTAL_SIZE)
        val sensorCert = SensorCert(sensorCertRaw)
        val sensorCertSigningKeyIndex = verifySensorCertificate(sensorCert)

        // Phase 3 — send phone ephemeral pubkey (65B). The captured fresh-pair
        // padded the message to 72B = 4 fragments x 18B; we replicate that so
        // the wire bytes round-trip.
        val phase3Wire = padTo(phoneEph.publicKey65, length = 72)
        transport.write(phase3Wire, BleCharRef.CERT_HANDSHAKE)

        // Phase 4 — receive 65B sensor ephemeral pubkey + ECDH math
        val sensorEphRaw = transport.awaitNotify(BleCharRef.CERT_HANDSHAKE, 65)
        val sensorEphPub = EphemeralExchange.parsePeerPubkey(sensorEphRaw)
        val sensorStaticPub = EphemeralExchange.parsePeerPubkey(sensorCert.staticPub)

        val sharedEphStatic = EphemeralExchange.sharedSecret(
            privateKey = phoneEph.privateKey,
            peer = sensorStaticPub,
        )
        val sharedEphEph = EphemeralExchange.sharedSecret(
            privateKey = phoneEph.privateKey,
            peer = sensorEphPub,
        )

        return PairingHandshakeResult(
            phoneCert = cert,
            sensorCert = sensorCert,
            sensorCertSigningKeyIndex = sensorCertSigningKeyIndex,
            phoneEph = phoneEph,
            sensorEphPub = sensorEphRaw,
            sharedEphStatic = sharedEphStatic,
            sharedEphEph = sharedEphEph,
        )
    }

    // MARK: - Command-gated first-pair / authorization (03 03)

    /**
     * Run the command-gated fresh-pair security preamble decoded from
     * `captures/fresh_pair_2026_04_26/btsnoop_hci.log` and
     * `findings/PROTOCOL_STATE_MACHINE_2026_05_02.md`.
     *
     * The sequence reaches the point where the sensor has emitted its 23B
     * `R1 || nonce7` challenge. It does not send the Phase 5 phone response.
     *
     * Wire order (1:1 with Swift `runCommandGatedFirstPairPreamble`):
     * `0x01 StartAuthentication`, `0x02 LoadCertificate`, phone cert write,
     * `0x03 SendCertificateLoadDone` → prefix `0x04`, `0x09 GetCertificate` →
     * prefix `0x0a`, sensor cert notify (140B), `0x0d ValidateCertificate`,
     * phone ephemeral write (72B), `0x0e SendEphemeralDone` → prefix `0x0f`,
     * sensor ephemeral notify (65B), `0x11 StartAuthorization` → prefix `0x08`,
     * then the 23B challenge notify.
     */
    fun runCommandGatedFirstPairPreamble(commandTimeout: Int = 2): FirstPairPreambleResult {
        val cert = phoneCert ?: throw PairingFlowError.PhoneCertRequired
        // Android transports are always [CommandPairingTransport], so the
        // Swift `transport as? CommandPairingTransport` check is a no-op here.
        val commandTransport: CommandPairingTransport = transport

        fun waitForCommand(expectedPrefix: ByteArray, label: String) {
            log("wait $label expectedPrefix=${Hex.encode(expectedPrefix)}")
            val actual = commandTransport.awaitCommandResponse(commandTimeout)
            log("got $label response=${Hex.encode(actual)}")
            if (!actual.contentStartsWith(expectedPrefix)) {
                throw PairingFlowError.UnexpectedCommandResponse(label, expectedPrefix, actual)
            }
        }

        val writeTimeout = 2

        log("preamble start")
        log("send StartAuthentication 0x01")
        withWriteTimeout(writeTimeout, label = "StartAuthentication") {
            commandTransport.writeCommand(0x01)
        }
        log("send LoadCertificate 0x02")
        withWriteTimeout(writeTimeout, label = "LoadCertificate") {
            commandTransport.writeCommand(0x02)
        }

        log("send phone cert len=${cert.raw.size}")
        withWriteTimeout(writeTimeout, label = "phoneCertWrite") {
            transport.write(cert.raw, BleCharRef.CERT_HANDSHAKE)
        }

        log("send SendCertificateLoadDone 0x03")
        withWriteTimeout(writeTimeout, label = "SendCertificateLoadDone") {
            commandTransport.writeCommand(0x03)
        }
        waitForCommand(byteArrayOf(0x04), label = "CertificateAccepted")

        log("send GetCertificate 0x09")
        withWriteTimeout(writeTimeout, label = "GetCertificate") {
            commandTransport.writeCommand(0x09)
        }
        waitForCommand(byteArrayOf(0x0a), label = "CertificateReady")

        log("await sensor cert")
        val sensorCertRaw = transport.awaitNotify(BleCharRef.CERT_HANDSHAKE, SensorCert.TOTAL_SIZE)
        val sensorCert = SensorCert(sensorCertRaw)
        val sensorCertSigningKeyIndex = verifySensorCertificate(sensorCert)
        log("parsed sensor cert len=${sensorCertRaw.size}")
        log(
            "sensor static pub=${Hex.encode(sensorCert.staticPub)} " +
                "certKeyIndex=${sensorCertSigningKeyIndex?.toString() ?: "skipped"}",
        )

        log("send ValidateCertificate 0x0d")
        withWriteTimeout(writeTimeout, label = "ValidateCertificate") {
            commandTransport.writeCommand(0x0d)
        }

        val phase3Wire = padTo(phoneEph.publicKey65, length = 72)
        log("send phone ephemeral len=${phase3Wire.size}")
        withWriteTimeout(writeTimeout, label = "phoneEphemeralWrite") {
            transport.write(phase3Wire, BleCharRef.CERT_HANDSHAKE)
        }

        log("send SendEphemeralDone 0x0e")
        withWriteTimeout(writeTimeout, label = "SendEphemeralDone") {
            commandTransport.writeCommand(0x0e)
        }
        waitForCommand(byteArrayOf(0x0f), label = "EphemeralReady")

        log("await sensor ephemeral")
        val sensorEphRaw = transport.awaitNotify(BleCharRef.CERT_HANDSHAKE, 65)
        val sensorEphPub = EphemeralExchange.parsePeerPubkey(sensorEphRaw)
        log("sensor ephemeral pub=${Hex.encode(sensorEphRaw)}")
        val sensorStaticPub = EphemeralExchange.parsePeerPubkey(sensorCert.staticPub)
        val sharedEphStatic = EphemeralExchange.sharedSecret(
            privateKey = phoneEph.privateKey,
            peer = sensorStaticPub,
        )
        val sharedEphEph = EphemeralExchange.sharedSecret(
            privateKey = phoneEph.privateKey,
            peer = sensorEphPub,
        )
        val p4 = PairingHandshakeResult(
            phoneCert = cert,
            sensorCert = sensorCert,
            sensorCertSigningKeyIndex = sensorCertSigningKeyIndex,
            phoneEph = phoneEph,
            sensorEphPub = sensorEphRaw,
            sharedEphStatic = sharedEphStatic,
            sharedEphEph = sharedEphEph,
        )
        log("computed ECDH static=${sharedEphStatic.size}B eph=${sharedEphEph.size}B")

        log("send StartAuthorization 0x11")
        commandTransport.writeCommand(0x11) // StartAuthorization
        waitForCommand(byteArrayOf(0x08), label = "ChallengeLoadDone")

        log("await R1 challenge")
        val r1Wire = transport.awaitNotify(BleCharRef.CHALLENGE, 23)
        if (r1Wire.size != 23) {
            throw PairingFlowError.SensorR1WrongSize(r1Wire.size)
        }
        val sensorR1 = ByteUtil.slice(r1Wire, 0, 16)
        val nonce7 = ByteUtil.slice(r1Wire, 16, 23)
        log("preamble complete R1=${Hex.encode(sensorR1)} nonce7=${Hex.encode(nonce7)}")
        return FirstPairPreambleResult(
            phaseHandshake = p4,
            sensorR1Wire = r1Wire,
            sensorR1 = sensorR1,
            nonce7 = nonce7,
        )
    }

    /**
     * Semantic alias for the command-gated cert/ephemeral/challenge preamble
     * used by both first-pair candidates and saved-state reconnect/recovery.
     * 1:1 port of Swift `runCommandGatedAuthorizationPreamble`.
     */
    fun runCommandGatedAuthorizationPreamble(commandTimeout: Int = 2): CommandGatedAuthorizationPreambleResult =
        runCommandGatedFirstPairPreamble(commandTimeout)

    // MARK: - Cached/direct reconnect (0x11)

    /**
     * Run pristine-style cached/direct reconnect through Phase 6. Skips the
     * `StartAuthentication`/certificate/ephemeral exchange: it only succeeds
     * when the sensor already has cached Phase 5 material accepted for the
     * saved receiver/session state (i.e. we are the original pairing phone,
     * or the sensor rebinds to a phone it has seen). Callers fall back to the
     * full 03 03 path when this fails.
     *
     * @param tail4 the persisted 4-byte BLE PIN (Phase-5 tail from the NFC read).
     * @param phase5RawKey the cached 16-byte Phase-5 raw key learned on the first
     *   full handshake (persisted with the paired sensor state).
     * @param tables runtime tables backing the Phase-5 white-box AES block.
     * @param r2Provider supplies the fresh 16-byte phone R2 random.
     */
    fun runCachedReconnectHandshake(
        tail4: ByteArray,
        phase5RawKey: ByteArray,
        tables: TablesSource,
        r2Provider: () -> ByteArray,
    ): CachedReconnectHandshakeResult {
        require(tail4.size == 4) { "tail4 must be 4 bytes, got ${tail4.size}" }
        require(phase5RawKey.size == 16) { "phase5RawKey must be 16 bytes, got ${phase5RawKey.size}" }

        log("cached reconnect handshake start tail4=${Hex.encode(tail4)}")
        val preamble = runCachedReconnectPreamble()
        val r2 = r2Provider()
        require(r2.size == 16) { "r2 must be 16 bytes, got ${r2.size}" }
        log("generated R2 len=${r2.size}")

        val phase5Block: AesBlockEncrypt = phase5BlockEncryptor(phase5RawKey, tables)
        val phase5Plaintext = ByteUtil.concat(preamble.sensorR1, r2, tail4)
        val phase5 = Phase5Challenge.encrypt(plaintext = phase5Plaintext, aes = phase5Block, nonce = preamble.nonce7)
        log("send cached reconnect Phase 5 len=${phase5.wireBytes.size}")
        transport.write(phase5.wireBytes, BleCharRef.CHALLENGE)
        sendChallengeLoadDoneIfAvailable()

        log("await cached reconnect Phase 6")
        val phase6Raw = transport.awaitNotify(BleCharRef.CHALLENGE, Phase6Response.WIRE_SIZE)
        val phase6 = Phase6Response.decode(phase6Raw)
        val sessionMaterial = phase6.decrypt(aes = phase5Block)
        if (!sessionMaterial.phoneR2.contentEquals(r2)) {
            throw PairingFlowError.Phase6VerificationFailed("Phase 6 R2 echo mismatch")
        }
        if (!sessionMaterial.sensorR1.contentEquals(preamble.sensorR1)) {
            throw PairingFlowError.Phase6VerificationFailed("Phase 6 R1 echo mismatch")
        }
        log("cached reconnect Phase 6 OK kEnc=${Hex.encode(sessionMaterial.kEnc)}")

        return CachedReconnectHandshakeResult(
            preamble = preamble,
            phase5Sent = phase5,
            phase6Raw = phase6Raw,
            phase6 = phase6,
            sessionMaterial = sessionMaterial,
        )
    }

    /**
     * The 0x11 preamble: `StartAuthorization` → `0x08` → `R1_16 || nonce7` on
     * the challenge characteristic.
     */
    fun runCachedReconnectPreamble(): CachedReconnectPreambleResult {
        log("cached reconnect preamble start")
        log("send StartAuthorization 0x11")
        transport.writeCommand(0x11)
        waitFor(prefix = byteArrayOf(0x08), label = "ChallengeLoadDone")

        log("await cached reconnect R1 challenge")
        val r1Wire = transport.awaitNotify(BleCharRef.CHALLENGE, R1_WIRE_SIZE)
        require(r1Wire.size == R1_WIRE_SIZE) { "R1 wire must be $R1_WIRE_SIZE bytes, got ${r1Wire.size}" }
        val sensorR1 = ByteUtil.slice(r1Wire, 0, 16)
        val nonce7 = ByteUtil.slice(r1Wire, 16, 23)
        log("cached reconnect preamble complete R1=${Hex.encode(sensorR1)} nonce7=${Hex.encode(nonce7)}")
        return CachedReconnectPreambleResult(sensorR1Wire = r1Wire, sensorR1 = sensorR1, nonce7 = nonce7)
    }

    // MARK: - Command-gated authorization handshake through Phase 6

    /**
     * Run the command-gated authorization handshake through Phase 6 using an
     * injected Phase 5 raw key.
     *
     * This is the reusable full authorization wire driver for initial pairing
     * candidates and saved-state reconnect/recovery fallback. For the shorter
     * pristine-style saved-state reconnect attempt, use
     * [runCachedReconnectHandshake].
     *
     * The key provider is called after the cert/ephemeral preamble and the
     * sensor's `R1 || nonce7` notify, so source derivation can use the sensor
     * cert, both ECDH secrets, R1, nonce, and the accepted 4-byte Phase 5 tail
     * without changing this wire driver.
     *
     * 1:1 port of Swift `runCommandGatedAuthorizationHandshake`.
     */
    fun runCommandGatedAuthorizationHandshake(
        tail4: ByteArray,
        phase5RawKeyProvider: (CommandGatedAuthorizationPreambleResult) -> ByteArray,
        r2Provider: () -> ByteArray = ::defaultPhase5R2,
        commandTimeout: Int = 2,
        tables: TablesSource,
    ): CommandGatedAuthorizationHandshakeResult {
        if (tail4.size != 4) {
            throw PairingFlowError.Tail4WrongSize(tail4.size)
        }

        log("authorization handshake start tail4=${Hex.encode(tail4)}")
        val preamble = runCommandGatedFirstPairPreamble(commandTimeout)
        val phase5R2 = r2Provider()
        log("generated R2 len=${phase5R2.size} data=${Hex.encode(phase5R2)}")
        if (phase5R2.size != 16) {
            throw ChallengeError.WrongPlaintextSize(preamble.sensorR1.size + phase5R2.size + tail4.size)
        }

        val phase5RawKey = phase5RawKeyProvider(preamble)
        log("derived Phase 5 raw key len=${phase5RawKey.size} data=${Hex.encode(phase5RawKey)}")
        if (phase5RawKey.size != 16) {
            throw ChallengeError.WrongKeySize(phase5RawKey.size)
        }
        val phase5Block = phase5BlockEncryptor(phase5RawKey, tables)
        val phase5Plaintext = ByteUtil.concat(preamble.sensorR1, phase5R2, tail4)
        val phase5 = Phase5Challenge.encrypt(
            plaintext = phase5Plaintext,
            aes = phase5Block,
            nonce = preamble.nonce7,
        )
        log("send Phase 5 len=${phase5.wireBytes.size} data=${Hex.encode(phase5.wireBytes)}")
        withWriteTimeout(2, label = "authorizationPhase5Write") {
            transport.write(phase5.wireBytes, BleCharRef.CHALLENGE)
        }
        sendChallengeLoadDoneIfAvailable()

        log("await Phase 6")
        val phase6Raw = transport.awaitNotify(BleCharRef.CHALLENGE, Phase6Response.WIRE_SIZE)
        val phase6 = Phase6Response.decode(phase6Raw)
        val sessionMaterial = phase6.decrypt(aes = phase5Block)
        if (!sessionMaterial.phoneR2.contentEquals(phase5R2)) {
            throw PairingFlowError.Phase6VerificationFailed("Phase 6 R2 echo mismatch")
        }
        if (!sessionMaterial.sensorR1.contentEquals(preamble.sensorR1)) {
            throw PairingFlowError.Phase6VerificationFailed("Phase 6 R1 echo mismatch")
        }

        return FirstPairHandshakeResult(
            preamble = preamble,
            phase5Sent = phase5,
            phase6Raw = phase6Raw,
            phase6 = phase6,
            sessionMaterial = sessionMaterial,
        )
    }

    /**
     * Backward-compatible first-pair spelling for callers that supply the NFC
     * BLE PIN as the 4-byte Phase 5 tail.
     * 1:1 port of Swift `runCommandGatedFirstPairHandshake(blePIN:
     * phase5RawKeyProvider:r2Provider:commandTimeout:)`.
     */
    fun runCommandGatedFirstPairHandshake(
        blePIN: ByteArray,
        phase5RawKeyProvider: (FirstPairPreambleResult) -> ByteArray,
        r2Provider: () -> ByteArray = ::defaultPhase5R2,
        commandTimeout: Int = 2,
        tables: TablesSource,
    ): FirstPairHandshakeResult {
        if (blePIN.size != 4) {
            throw PairingFlowError.BlePINWrongSize(blePIN.size)
        }
        log("first-pair handshake start blePIN=${Hex.encode(blePIN)}")
        return runCommandGatedAuthorizationHandshake(
            tail4 = blePIN,
            phase5RawKeyProvider = phase5RawKeyProvider,
            r2Provider = r2Provider,
            commandTimeout = commandTimeout,
            tables = tables,
        )
    }

    /**
     * Run the command-gated fresh-pair handshake through Phase 6, deriving
     * the Phase 5 source and raw key from the clean-room first-pair source
     * builder after the sensor cert, sensor ephemeral point, and R1 challenge
     * arrive.
     *
     * 1:1 port of Swift `runCommandGatedFirstPairHandshake(blePIN:entrySource:
     * maxEntropyAttempts:entropySource:r2Provider:commandTimeout:)`.
     */
    fun runCommandGatedFirstPairHandshake(
        blePIN: ByteArray,
        entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
        maxEntropyAttempts: Int = 64,
        entropySource: (Int) -> ByteArray,
        r2Provider: () -> ByteArray = ::defaultPhase5R2,
        commandTimeout: Int = 2,
        tables: TablesSource,
        sessionKey: SessionKey,
    ): FirstPairDerivedHandshakeResult {
        var material: FirstPairPhase5KeyMaterial? = null
        val handshake = runCommandGatedFirstPairHandshake(
            blePIN = blePIN,
            phase5RawKeyProvider = { preamble ->
                val staticScalarWindow = preamble.phaseHandshake.phoneCert.phase5StaticScalarWindowOverride
                val derived = sessionKey.deriveFirstPairPhase5Material(
                    preamble = preamble,
                    entrySource = entrySource,
                    staticScalarWindow = staticScalarWindow,
                    maxAttempts = maxEntropyAttempts,
                    entropySource = entropySource,
                )
                eventLogger(
                    "PairingFlow: derived Phase 5 material attempts=${derived.nullAttempts} " +
                        "staticScalarOverride=${staticScalarWindow?.size ?: 0}B " +
                        "nullScalarWindow=${Hex.encode(derived.nullScalarWindow)} " +
                        "nullEntropy11A=${Hex.encode(derived.nullEntropy11A)} " +
                        "source66=${Hex.encode(derived.source66)} rawKey=${Hex.encode(derived.rawKey)}",
                )
                val expectedPhoneEph = builderProcess2P5PublicKey65FromEntropy(derived.nullEntropy11A)
                if (!preamble.phaseHandshake.phoneEph.publicKey65.contentEquals(expectedPhoneEph)) {
                    throw PairingFlowError.Phase5EphemeralPublicKeyMismatch(
                        expected = expectedPhoneEph,
                        actual = preamble.phaseHandshake.phoneEph.publicKey65,
                    )
                }
                material = derived
                derived.rawKey
            },
            r2Provider = r2Provider,
            commandTimeout = commandTimeout,
            tables = tables,
        )
        val derivedMaterial = material ?: throw PairingFlowError.Phase5MaterialUnavailable
        return FirstPairDerivedHandshakeResult(handshake = handshake, phase5Material = derivedMaterial)
    }

    // MARK: - Helpers

    private fun verifySensorCertificate(sensorCert: SensorCert): Int? {
        if (sensorCertSigningKeys.isEmpty()) {
            log("sensor cert verification skipped")
            return null
        }
        val index = sensorCert.verifiedSigningKeyIndex(sensorCertSigningKeys)
            ?: throw PairingFlowError.SensorCertificateVerificationFailed
        log("sensor cert verified signingKeyIndex=$index")
        return index
    }

    /**
     * Bound a BLE write against a wall-clock deadline. The Android transport
     * blocks on the GATT callbacks on a background thread; a half-dead link
     * means the callback never fires and the call would park indefinitely.
     *
     * **Async→sync conversion note:** the Swift original races the work on a
     * `Task.detached` against `DispatchQueue.asyncAfter` + a continuation
     * (`WriteTimeoutBox`). This port keeps the identical observable contract
     * — a [PairingFlowError.WriteTimeout] when the work outlives [seconds] —
     * using a daemon watchdog timer thread instead of GCD: the timer flips an
     * `AtomicBoolean` that the caller checks after the blocking call returns
     * (the Swift side additionally cancels the still-running work task, which
     * has no equivalent on a synchronously blocking call).
     */
    private fun <T> withWriteTimeout(seconds: Int, label: String, work: () -> T): T {
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
        val timer = java.util.Timer(true)
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                timedOut.set(true)
            }
        }, seconds * 1000L)
        try {
            val result = work()
            if (timedOut.get()) {
                throw PairingFlowError.WriteTimeout(label, seconds)
            }
            return result
        } finally {
            timer.cancel()
        }
    }

    private fun sendChallengeLoadDoneIfAvailable() {
        log("send SendChallengeLoadDone 0x08")
        transport.writeCommand(0x08)
        val actual = transport.awaitCommandResponse(COMMAND_TIMEOUT_SECONDS)
        log("got PatchChallengeLoadDone response=${Hex.encode(actual)}")
        if (!actual.contentStartsWith(byteArrayOf(0x08))) {
            throw PairingFlowError.UnexpectedCommandResponse("PatchChallengeLoadDone", byteArrayOf(0x08), actual)
        }
    }

    private fun waitFor(prefix: ByteArray, label: String) {
        log("wait $label")
        val actual = transport.awaitCommandResponse(COMMAND_TIMEOUT_SECONDS)
        log("got $label response=${Hex.encode(actual)}")
        if (!actual.contentStartsWith(prefix)) {
            throw PairingFlowError.UnexpectedCommandResponse(label, prefix, actual)
        }
    }

    private fun log(message: String) {
        eventLogger("PairingFlow: $message")
    }

    companion object {
        const val R1_WIRE_SIZE = 23
        const val COMMAND_TIMEOUT_SECONDS = 2
    }
}

/** Result of the pristine-style cached/direct reconnect preamble. */
data class CachedReconnectPreambleResult(
    val sensorR1Wire: ByteArray,
    val sensorR1: ByteArray,
    val nonce7: ByteArray,
)

/** Result of the cached/direct reconnect handshake through Phase 6. */
data class CachedReconnectHandshakeResult(
    val preamble: CachedReconnectPreambleResult,
    val phase5Sent: Phase5Challenge,
    val phase6Raw: ByteArray,
    val phase6: Phase6Response,
    val sessionMaterial: Phase6SessionMaterial,
) {
    /** Low 16 bits of the Phase-6 sensor nonce (LE) = the sensor's current life count. */
    val phase6NoncePrefix: Int
        get() {
            val n = phase6.nonce
            if (n.size < 2) return 0
            return (n[0].toInt() and 0xFF) or ((n[1].toInt() and 0xFF) shl 8)
        }
}

sealed class PairingFlowError(message: String) : Exception(message) {
    class UnexpectedCommandResponse(
        val label: String,
        val expectedPrefix: ByteArray,
        val actual: ByteArray,
    ) : PairingFlowError("unexpected command response for $label: got ${Hex.encode(actual)}") {
        override fun toString() =
            "unexpectedCommandResponse($label, expectedPrefix=${Hex.encode(expectedPrefix)}, actual=${Hex.encode(actual)})"
    }

    class Phase6VerificationFailed(detail: String) : PairingFlowError("Phase 6 verification failed: $detail")

    class WriteTimeout(val label: String, val seconds: Int) :
        PairingFlowError("GATT write timed out during $label after ${seconds}s (link fault — retry)")

    /** `runPhases1To4`/preamble called on a reconnect-only flow (Swift `.phoneCertRequired`). */
    object PhoneCertRequired : PairingFlowError("phone cert required for this handshake")

    /** Transport does not support the command state machine (Swift `.commandTransportRequired`).
     * Retained for error-shape parity; the Android transport is always a
     * [CommandPairingTransport], so no live code path throws this. */
    object CommandTransportRequired : PairingFlowError("command transport required")

    /** `r1Wire.count != 23` (Swift `.sensorR1WrongSize`). */
    data class SensorR1WrongSize(val size: Int) : PairingFlowError("sensor R1 wrong size: $size")

    /** `blePIN.count != 4` (Swift `.blePINWrongSize`). */
    data class BlePINWrongSize(val size: Int) : PairingFlowError("BLE PIN wrong size: $size")

    /** `tail4.count != 4` (Swift `.tail4WrongSize`). */
    data class Tail4WrongSize(val size: Int) : PairingFlowError("tail4 wrong size: $size")

    /** The Phase 5 material could not be captured (Swift `.phase5MaterialUnavailable`). */
    object Phase5MaterialUnavailable : PairingFlowError("Phase 5 material unavailable")

    /** Credential-shaped: cached ephemeral public key mismatch (Swift `.phase5EphemeralPublicKeyMismatch`). */
    data class Phase5EphemeralPublicKeyMismatch(
        val expected: ByteArray,
        val actual: ByteArray,
    ) : PairingFlowError("Phase 5 ephemeral public key mismatch") {
        override fun toString() =
            "phase5EphemeralPublicKeyMismatch(expected=${Hex.encode(expected)}, actual=${Hex.encode(actual)})"
    }

    /** Credential-shaped: the sensor certificate failed verification (Swift `.sensorCertificateVerificationFailed`). */
    object SensorCertificateVerificationFailed : PairingFlowError("sensor certificate verification failed")
}

/**
 * Swift `defaultPhase5R2()`: a fresh 16-byte phone R2 random.
 */
fun defaultPhase5R2(): ByteArray = secureRandomData(16)

/**
 * Swift `secureRandomData(count:)` on top of `SecureRandom`.
 */
fun secureRandomData(count: Int): ByteArray {
    if (count < 0) {
        throw IllegalArgumentException("count must be >= 0, got $count")
    }
    val bytes = ByteArray(count)
    SecureRandom().nextBytes(bytes)
    return bytes
}

/**
 * Swift top-level `padTo(_:length:)` — left-pad [data] with zeros to [length]
 * (no-op when it is already at least [length]).
 */
fun padTo(data: ByteArray, length: Int): ByteArray {
    if (data.size >= length) return data
    return ByteUtil.concat(data, ByteArray(length - data.size))
}

private fun ByteArray.contentStartsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) if (this[i] != prefix[i]) return false
    return true
}
