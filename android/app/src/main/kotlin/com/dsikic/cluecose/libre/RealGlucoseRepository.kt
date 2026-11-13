package com.dsikic.cluecose.libre

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.dsikic.cluecose.LinkKeeperService
import com.dsikic.cluecose.app.tables.AssetsTablesSource
import com.dsikic.cluecose.ble.DataCharRef
import com.dsikic.cluecose.ble.DataPlaneListener
import com.dsikic.cluecose.ble.GattSession
import com.dsikic.cluecose.ble.SensorScanner
import com.dsikic.cluecose.ble.SensorScanError
import com.dsikic.cluecose.core.dataplane.DataPlaneCrypto
import com.dsikic.cluecose.core.dataplane.DataPlanePacketKind
import com.dsikic.cluecose.core.dataplane.DataPlaneDecodedPacket
import com.dsikic.cluecose.core.dataplane.DataPlaneDecodedPayload
import com.dsikic.cluecose.core.dataplane.PatchControlCommand
import com.dsikic.cluecose.core.nfc.Libre3ReceiverID
import com.dsikic.cluecose.core.pairing.PairingFlow
import com.dsikic.cluecose.core.pairing.Phase5KeySchedule
import com.dsikic.cluecose.core.pairing.PhoneCert
import com.dsikic.cluecose.core.pairing.SessionKey
import com.dsikic.cluecose.core.pairing.secureRandomData
import com.dsikic.cluecose.core.tables.TablesSource
import com.dsikic.cluecose.nfc.NfcActivationReader
import com.dsikic.cluecose.ui.GlucoseRepository
import com.dsikic.cluecose.ui.GlucoseRepository.Event
import com.dsikic.cluecose.ui.GlucoseSample
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The real BLE-backed glucose source (Android mirror of iOS
 * `LibreDirectReader`):
 *
 *   NFC tap (NfcV) → sensor identity + BLE PIN → scan (75 s) → GATT connect →
 *   security handshake (cached 0x11 reconnect first when the stored data is
 *   complete, full 03 03 first-pair otherwise) → encrypted data plane →
 *   live glucose stream + 24 h backfill.
 *
 * Threading: every blocking step (scan, handshake, backfill waits, reconnect
 * sleeps) runs on the single "cluecose-repo" worker thread. GATT callbacks
 * arrive on GattSession's executor and are dispatched to the data-plane
 * listener from there; UI events flow out through [GlucoseRepository.Listener]
 * on whichever thread produced them (the ViewModel's StateFlow is
 * thread-safe).
 *
 * Persistence (SharedPreferences, base64-of-JSON — mirroring iOS
 * `UserDefaults` data):
 *   `cluecose.libre.directState`   — the paired-sensor state (incl. the
 *   16-byte Phase-5 raw key that enables the fast cached reconnect),
 *   `cluecose.libre.directSamples` — lifeCount → mg/dL rows (dates are
 *   recomputed from the activation time at load),
 *   `cluecose.libre.accountlessID` — this install's stable receiver UUID.
 */
class RealGlucoseRepository(
    private val app: Application,
    private val tablesSource: TablesSource = AssetsTablesSource(app.assets),
) : GlucoseRepository {

    // MARK: Events

    @Volatile
    override var listener: GlucoseRepository.Listener = GlucoseRepository.Listener {}

    /** Set by MainActivity (reader mode needs the Activity). Null = NFC unavailable. */
    @Volatile
    var nfcReader: NfcActivationReader? = null

    // MARK: State

    // Eager (not `by lazy`): [state]'s initializer calls [loadState], which
    // reads [prefs] — a lazy delegate assigned later in the constructor body
    // is still null at that point (NPE on first launch).
    private val prefs: android.content.SharedPreferences =
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var state: DirectSensorState? = loadState()
        private set(value) {
            field = value
            if (value != null) saveState()
        }

    /**
     * lifeCount → (mg/dL, dateMs). Life count is unique per sensor, so
     * realtime, clinical and historical sources merge + dedupe by it. Touched
     * from the GATT executor (packet ingestion) and the worker (gap checks,
     * forget) — guarded by [storeLock].
     */
    private val sampleStore = HashMap<Int, Pair<Int, Long>>()
    private val storeLock = Any()

    private val sensorScanner = SensorScanner(app)
    private val sessionKeyInstance = SessionKey(Phase5KeySchedule(tablesSource))

    // MARK: Worker / lifecycle flags

    /**
     * The single repository thread. Serializes connect attempts, NFC pair
     * connects, teardown, and (scheduled) reconnect attempts; the backoff
     * delay is a SCHEDULED task, so it never blocks the thread.
     */
    private val worker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "cluecose-repo").apply { isDaemon = true }
    }

    /** True while stopped/forget — the reconnect loop stops honoring drops. */
    @Volatile private var cancelled = true

    /** Bumped on stop/forget/nfc-pair so a sleeping reconnect aborts. */
    @Volatile private var generation = 0

    @Volatile private var gatt: GattSession? = null

    @Volatile private var linkActive = false
    @Volatile private var isWorking = false
    @Volatile private var nfcPairInFlight = false
    /** A reconnect attempt is pending or running (or the link is up). Guards against a second loop. */
    @Volatile private var reconnecting = false
    @Volatile private var samplesEmitted = false

    /**
     * The receiver ID the sensor's CURRENT owner used to activate it,
     * little-endian hex (e.g. the Cluecose iOS install's stable accountless
     * receiver ID — a different Android install generates its own UUID, so its
     * accountless ID is rejected with 0xB1 until the sensor is re-bound).
     * Presented FIRST by the [nfcPair] candidate ladder; the install's own
     * accountless ID is the fallback. Mirrors iOS's account-ID field, but for
     * a Cluecose install rather than an Abbott app.
     */
    @Volatile
    var nfcOwnerReceiverIdLEHex: String? = prefs.getString(OWNER_RECEIVER_ID_KEY, null)
        set(value) {
            field = value
            prefs.edit().apply {
                if (value == null) remove(OWNER_RECEIVER_ID_KEY) else putString(OWNER_RECEIVER_ID_KEY, value)
                apply()
            }
        }

    /** The candidate ladder for the in-flight pair (owner ID first). */
    @Volatile private var receiverCandidates: List<Libre3ReceiverID> = emptyList()
    @Volatile private var receiverCandidateIndex = 0

    @Volatile private var reconnectAttempt = 0 // worker + reconnect threads (backoff counter only)

    init {
        loadSamples()
    }

    // MARK: GlucoseRepository

    override fun startListening() {
        cancelled = false
        val saved = state
        if (saved == null) {
            // The reader is NOT armed yet — `enableReaderMode` only runs in
            // [nfcPair] (the "Pair with NFC tap" tap). Claiming "armed" here
            // showed a stale "reader is armed" line next to an "NFC is off"
            // rejection on the Sony.
            emit(Event.NfcAwaiting(false))
            emit(
                Event.Status(
                    "No sensor paired yet — tap “Pair with NFC tap”, then hold the top of the phone to the sensor.",
                ),
            )
            return
        }
        worker.execute {
            runCatching {
                if (cancelled || linkActive || reconnecting || isWorking) return@runCatching
                emitStoredSamplesOnce()
                reconnecting = true
                try {
                    connectAndAuthorize(saved.blePIN, saved.bleAddress, saved, allowCached = true)
                } catch (e: Exception) {
                    Log.w(TAG, "reconnect failed: ${e.message}")
                    teardownSession() // discard the half-open GATT session, if any
                    emit(Event.Status("Reconnect failed: ${shortError(e)}"))
                    scheduleReconnect()
                }
            }
        }
    }

    override fun stopListening() {
        cancelled = true
        generation += 1
        isWorking = false
        nfcPairInFlight = false
        reconnecting = false
        worker.execute {
            nfcReader?.stop()
            teardownSession()
        }
    }

    override fun nfcPair() {
        val reader = nfcReader
        // A rejected tap must clear any prior "reader armed" state — otherwise
        // the NFC box keeps claiming the reader is armed while the status line
        // says the tap was rejected (observed on the Sony).
        emit(Event.NfcAwaiting(false))
        if (reader == null) {
            emit(Event.Status("NFC is not available on this device."))
            return
        }
        if (!reader.isAvailable()) {
            emit(Event.Status("NFC is off — enable it in Settings, then tap again."))
            return
        }
        if (isWorking) return
        isWorking = true
        nfcPairInFlight = true
        emit(Event.NfcAwaiting(true))
        emit(Event.Status("Hold the top of the phone to the sensor — pairing over NFC…"))
        // Candidate ladder (mirrors iOS GlucoseModel.pair()): the receiver ID
        // the sensor's current owner used FIRST — a switch-receiver from any
        // other ID is rejected with 0xB1 — then this install's own accountless
        // ID (covers sensors activated by a different Android install).
        receiverCandidates = buildList {
            nfcOwnerReceiverIdLEHex?.let { hex ->
                runCatching { Libre3ReceiverID.fromLittleEndianHex(hex) }.getOrNull()?.let { add(it) }
            }
            val own = accountlessReceiverID()
            if (none { it == own }) add(own)
        }
        receiverCandidateIndex = 0
        Log.i(TAG, "NFC pair ladder: ${receiverCandidates.joinToString { it.displayString }}")
        reader.onComplete = { result, failure -> onNfcComplete(reader, result, failure) }
        worker.execute {
            // Release any existing link before the sensor switches to us.
            teardownSession()
            reconnecting = false
            if (!nfcPairInFlight) return@execute
            startReaderWithCurrentCandidate(reader)
        }
    }

    /** Arm the reader with the ladder's current receiver-ID candidate. */
    private fun startReaderWithCurrentCandidate(reader: NfcActivationReader) {
        val idx = receiverCandidateIndex
        val candidate = receiverCandidates.getOrNull(idx)
            ?: accountlessReceiverID()
        try {
            reader.start(candidate.value)
        } catch (e: NfcActivationReader.Failure) {
            onNfcComplete(reader, null, e)
        } catch (e: Exception) {
            onNfcComplete(
                reader,
                null,
                NfcActivationReader.Failure.TransceiveFailed(e.message ?: "reader start failed"),
            )
        }
    }

    override fun forgetSensor() {
        cancelled = true
        generation += 1
        isWorking = false
        nfcPairInFlight = false
        worker.execute {
            runCatching {
                nfcReader?.stop()
                teardownSession()
                reconnecting = false
                state = null
                synchronized(storeLock) { sampleStore.clear() }
                prefs.edit().remove(STATE_KEY).remove(SAMPLES_KEY).apply()
                samplesEmitted = false
                emit(Event.Samples(emptyList()))
                emit(Event.SensorMeta(null, false, null))
                emit(Event.Status("Sensor forgotten — hold the top of the phone to the sensor to pair again."))
                emit(Event.NfcAwaiting(true))
            }
        }
    }

    // MARK: NFC completion (main thread, via the reader)

    private fun onNfcComplete(
        reader: NfcActivationReader,
        result: NfcActivationReader.PairingResult?,
        failure: NfcActivationReader.Failure?,
    ) {
        if (!nfcPairInFlight) return
        nfcPairInFlight = false

        // Receiver mismatch (0xB1): the sensor is bound to another receiver ID
        // (e.g. the Cluecose iOS install). Advance the candidate ladder and
        // re-arm — mirroring iOS GlucoseModel.pair() ("tap it again to try the
        // next one"). The disable→enable cycle re-discovers a tag still held
        // in the field, so no extra hold is usually needed.
        if (result == null && failure is NfcActivationReader.Failure.ReceiverMismatch) {
            val next = receiverCandidateIndex + 1
            if (next < receiverCandidates.size) {
                receiverCandidateIndex = next
                emit(Event.NfcAwaiting(true))
                emit(
                    Event.Status(
                        "That receiver ID was rejected — hold the top of the phone to the sensor to try the next…",
                    ),
                )
                worker.execute {
                    reader.stop()
                    startReaderWithCurrentCandidate(reader)
                }
                return
            }
            emit(Event.NfcAwaiting(false))
            reader.stop()
            isWorking = false
            emit(
                Event.Status(
                    "The sensor is bound to a receiver ID this install doesn't have. " +
                        "Settings → Re-pair can set the other install's receiver ID; " +
                        "otherwise pair the sensor fresh on this phone.",
                ),
            )
            return
        }

        emit(Event.NfcAwaiting(false))
        reader.stop()
        if (result == null) {
            isWorking = false
            emit(Event.Status(failure?.message ?: "NFC pairing failed — try again."))
            return
        }
        // [isWorking] stays true through the BLE connect so a second
        // "Pair with NFC tap" can't tear down the in-progress handshake.
        emit(Event.Status("NFC OK — connecting over Bluetooth…"))
        worker.execute {
            try {
                nfcPairConnect(result)
            } finally {
                isWorking = false
            }
        }
    }

    private fun nfcPairConnect(result: NfcActivationReader.PairingResult) {
        // Record the receiver ID the sensor actually accepted (the ladder's
        // current candidate), not blindly this install's accountless one — the
        // persisted value must match what activated the sensor.
        val usedIndex = receiverCandidateIndex
        val receiverId = receiverCandidates.getOrNull(usedIndex) ?: accountlessReceiverID()
        val receiverSource = if (usedIndex == 0 && nfcOwnerReceiverIdLEHex != null) "ownerId" else "accountless"
        val newState = DirectSensorState(
            serialNumber = result.patchInfo.serialNumber,
            bleAddress = result.activation.bleAddressDisplay,
            blePIN = result.activation.blePIN.copyOf(),
            receiverIDLEHex = receiverId.littleEndianHex,
            receiverIDSource = receiverSource,
            activationTimeSeconds = activationEpochSeconds(result.activation.activationTimeRaw),
            warmupDurationMinutes = result.patchInfo.warmupMinutes,
            wearDurationMinutes = result.patchInfo.wearDurationMinutes,
            lastGlucoseLifeCount = null,
            lastGlucoseMgDL = null,
            pairedAtMs = System.currentTimeMillis(),
            phase5RawKey = ByteArray(0),
        )
        try {
            connectAndAuthorize(newState.blePIN, newState.bleAddress, newState, allowCached = false)
        } catch (e: Exception) {
            Log.w(TAG, "NFC-pair connect failed: ${e.message}", e)
            emit(Event.Status("Bluetooth connection failed: ${shortError(e)}"))
            emit(Event.Status("Hold the top of the phone to the sensor to pair again."))
        }
    }

    // MARK: BLE connect + authorization (worker thread)

    private fun connectAndAuthorize(
        blePIN: ByteArray,
        bleAddress: String?,
        newSessionState: DirectSensorState,
        allowCached: Boolean,
    ) {
        var s = newSessionState

        val target = sensorScanner.normalizedAddress(bleAddress)

        // The sensor only accepts connections during its short advertising
        // burst (about once a minute). The scanner now ends as soon as the
        // target advertises, but a connect can still miss the window (GATT
        // 133); rescan + retry a few times before giving up.
        val maxAttempts = 3
        var gatt: GattSession? = null
        var lastError: Throwable? = null
        for (attempt in 1..maxAttempts) {
            emit(Event.Status(if (attempt == 1) {
                "Looking for the sensor… (it opens a connection window about every minute)"
            } else {
                "Still waiting for the sensor's connection window… (attempt $attempt/$maxAttempts)"
            }))
            val discovered = sensorScanner.scanOnce(target, DISCOVERY_TIMEOUT_MS)
                ?: throw RealGlucoseError.BleNotFound
            val device = bluetoothDevice(discovered.address)

            val session = GattSession(app)
            session.onDisconnected = { code -> onGattDropped(code, session) }
            this.gatt = session
            val attemptResult = runCatching { session.connect(device, discovered.name) }
            if (attemptResult.isSuccess) {
                gatt = session
                lastError = null
                emit(Event.Status("Connected — running the security handshake…"))
                break
            } else {
                val err = attemptResult.exceptionOrNull()
                lastError = err
                Log.w(TAG, "connect attempt $attempt/$maxAttempts failed: ${err?.message}")
                session.disconnect()
            }
        }
        if (gatt == null) {
            lastError?.let { throw it }
            throw RealGlucoseError.BleNotFound
        }

        // ── Handshake choice: cached 0x11 or full 03 03 ─────────────────────
        // The fast cached path reuses the saved Phase-5 key and resumes the
        // realtime stream immediately, but its data plane is a *continuation*
        // of the sensor's existing session: encrypted patchControl command
        // writes (the backfill requests) are rejected (0xfd), so backfill is
        // first-pair only. If the stored data is complete and current (the
        // link just dropped briefly), take the fast 0x11; if there is a hole
        // the realtime stream cannot fill, run the full 03 03.
        val backfillReason = backfillNeededReason()
        val useCached = allowCached && s.phase5RawKey.size == 16 && backfillReason == null
        Log.i(TAG, "handshake: ${if (useCached) "CACHED (0x11)" else "FULL first-pair (03 03)"} (backfill: ${backfillReason ?: "no"})")

        var crypto: DataPlaneCrypto
        var noncePrefix: Int?
        if (useCached) {
            val cached = runCatching { runCachedReconnect(gatt, blePIN, s.phase5RawKey) }
            if (cached.isSuccess) {
                val (c, prefix) = cached.getOrThrow()
                crypto = c
                noncePrefix = prefix
            } else {
                Log.w(TAG, "cached reconnect failed (${cached.exceptionOrNull()?.message}) — full first-pair fallback")
                val (c, material, prefix) = runFirstPairHandshake(gatt, blePIN)
                crypto = c
                s = s.copy(phase5RawKey = material.rawKey)
                noncePrefix = prefix
            }
        } else {
            val (c, material, prefix) = runFirstPairHandshake(gatt, blePIN)
            crypto = c
            s = s.copy(phase5RawKey = material.rawKey)
            noncePrefix = prefix
        }

        // ── Data plane ──────────────────────────────────────────────────────
        val listener = DataPlaneListener(crypto)
        gatt.onDataNotify = { ref, fragment ->
            listener.feed(ref, fragment, sink = { packet, receivedAt -> handlePacket(packet, receivedAt) })
        }

        emit(Event.Status("Authorized — arming the data stream…"))
        state = s
        setLink(true)
        s.lastGlucoseLifeCount?.let { lc -> emit(Event.SensorMeta(lc, false, s.serialNumber)) }

        runBackfillPass(gatt, listener, crypto, noncePrefix, backfillNeeded = backfillReason != null)
    }

    /**
     * The proven first-pair/candidate handshake: `03 03` phone cert, native
     * phone ephemeral generated up-front, and its null-entropy replayed as
     * the FIXED Phase-5 entropy source (maxEntropyAttempts = 1) so the sent
     * ephemeral point matches the on-device derived Phase-5 key. Returns
     * (crypto, phase5 material, phase6 nonce prefix).
     */
    private fun runFirstPairHandshake(
        gatt: GattSession,
        blePIN: ByteArray,
    ): Triple<DataPlaneCrypto, com.dsikic.cluecose.core.pairing.FirstPairPhase5KeyMaterial, Int?> {
        val phoneCert = PhoneCert.bundled162b()
        val nativeEphemeral = sessionKeyInstance.makeFirstPairNativeEphemeral { n -> secureRandomData(n) }
        val flow = PairingFlow(gatt, phoneCert, phoneEph = nativeEphemeral.keyPair)
        val result = flow.runCommandGatedFirstPairHandshake(
            blePIN = blePIN,
            maxEntropyAttempts = 1,
            entropySource = { n ->
                if (n != nativeEphemeral.nullEntropy11A.size) {
                    throw RealGlucoseError.EntropySizeMismatch(nativeEphemeral.nullEntropy11A.size, n)
                }
                nativeEphemeral.nullEntropy11A
            },
            r2Provider = { secureRandomData(16) },
            tables = tablesSource,
            sessionKey = sessionKeyInstance,
        )
        val material = result.handshake.sessionMaterial
        val crypto = DataPlaneCrypto(material.kEnc, material.ivEnc)
        val nonce = result.handshake.phase6.nonce
        val prefix = if (nonce.size >= 2) {
            (nonce[0].toInt() and 0xFF) or ((nonce[1].toInt() and 0xFF) shl 8)
        } else {
            null
        }
        return Triple(crypto, result.phase5Material, prefix)
    }

    /** Fast reconnect: StartAuthorization 0x11 + cached Phase-5 raw key. */
    private fun runCachedReconnect(
        gatt: GattSession,
        blePIN: ByteArray,
        rawKey: ByteArray,
    ): Pair<DataPlaneCrypto, Int> {
        val flow = PairingFlow(gatt)
        val result = flow.runCachedReconnectHandshake(
            tail4 = blePIN,
            phase5RawKey = rawKey,
            tables = tablesSource,
            r2Provider = { secureRandomData(16) },
        )
        return DataPlaneCrypto(result.sessionMaterial.kEnc, result.sessionMaterial.ivEnc) to result.phase6NoncePrefix
    }

    // MARK: Backfill (worker thread)

    /**
     * Sequential data-plane re-arm (reference bootstrap order), then — only
     * when a gap exists — one bounded historical attempt and the clinical
     * re-subscribe burst wait. Mirrors iOS `startBackfill` + `runBackfill`.
     */
    private fun runBackfillPass(
        gatt: GattSession,
        listener: DataPlaneListener,
        crypto: DataPlaneCrypto,
        noncePrefix: Int?,
        backfillNeeded: Boolean,
    ) {
        // Wait a bit for the first patchStatus/realtime to learn the current life count.
        var currentLC = state?.lastGlucoseLifeCount
        if (currentLC == null) {
            for (i in 0 until 45) {
                currentLC = state?.lastGlucoseLifeCount
                if (currentLC != null) break
                Thread.sleep(2_000)
            }
        }
        val start = when {
            currentLC != null -> maxOf(0, currentLC - 1440)
            noncePrefix != null -> if (noncePrefix > 180) noncePrefix - 180 else 0
            else -> 0
        }

        // Post-Phase-6 the sensor stays silent until each data-plane CCCD gets
        // a fresh write (off→on, 90 ms settles, patchControl first).
        gatt.rearmDataNotifications(enable = true)

        if (!backfillNeeded) {
            // Fast-path reconnect: no hole to fill. The reference never issues
            // backfill commands on a cached reconnect — just stream.
            Log.i(TAG, "backfill: skipped (no gap — data complete)")
            emit(Event.Status("Live — readings update about once a minute."))
            return
        }

        emit(Event.Status("Filling in missing history…"))
        val quietMs = 5_000L
        var sequence = 0x0001

        // ── Historical: one bounded attempt (10-min overlap) ──────────────
        try {
            gatt.setDataNotification(DataCharRef.HISTORIC_DATA, false)
            Thread.sleep(90)
            gatt.setDataNotification(DataCharRef.HISTORIC_DATA, true)
            Thread.sleep(90)
            val fromLC = maxOf(0, start - 10)
            try {
                val frame = crypto.encrypt(
                    plaintext = PatchControlCommand.historicalBackfillGreaterEqual(fromLC).plaintext,
                    sequence = sequence,
                    kind = DataPlanePacketKind.PATCH_CONTROL_WRITE,
                )
                gatt.writeData(frame.raw, DataCharRef.PATCH_CONTROL)
                sequence += 1
                Log.i(TAG, "backfill: historical request sent (10-min overlap, from lc $fromLC)")
            } catch (e: Exception) {
                Log.w(TAG, "backfill: historical request rejected: ${e.message}")
            }
            val pagesBefore = listener.historicalPageCountValue
            val waitStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - waitStart < 15_000L) {
                val gotPages = listener.historicalPageCountValue > pagesBefore
                if (gotPages && System.currentTimeMillis() - listener.lastHistoricalPageAtValue >= quietMs) break
                Thread.sleep(250)
            }
            Log.i(TAG, "backfill: historical wait over — ${listener.historicalPageCountValue} pages total (+${listener.historicalPageCountValue - pagesBefore})")
        } catch (e: Exception) {
            Log.w(TAG, "backfill: historical FAILED: ${e.message}")
        }
        runCatching { gatt.setDataNotification(DataCharRef.HISTORIC_DATA, false) }

        // ── Clinical stream (per-minute records) ──────────────────────────
        // The sensor buffers per-minute clinical records while disconnected
        // and replays the buffered window in a burst on re-subscribe (the
        // designed minute-resolution gap-fill). The burst is triggered by the
        // CCCD off→on — NOT by a patchControl command — so the wait runs
        // regardless.
        try {
            gatt.setDataNotification(DataCharRef.CLINICAL_DATA, false)
            Thread.sleep(90)
            gatt.setDataNotification(DataCharRef.CLINICAL_DATA, true)
            Thread.sleep(90)
            val recordsBefore = listener.clinicalRecordCountValue
            val waitStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - waitStart < 15_000L) {
                val got = listener.clinicalRecordCountValue > recordsBefore
                if (got && System.currentTimeMillis() - listener.lastClinicalRecordAtValue >= quietMs) break
                Thread.sleep(250)
            }
            Log.i(TAG, "backfill: clinical wait over — ${listener.clinicalRecordCountValue} records total (+${listener.clinicalRecordCountValue - recordsBefore})")
        } catch (e: Exception) {
            Log.w(TAG, "backfill: clinical CCCD FAILED: ${e.message}")
        }
        runCatching { gatt.setDataNotification(DataCharRef.CLINICAL_DATA, false) }

        emit(Event.Status("Live — readings update about once a minute."))
    }

    // MARK: Packet handling (GATT executor thread)

    private fun handlePacket(packet: DataPlaneDecodedPacket, receivedAt: Long) {
        when (val payload = packet.payload) {
            is DataPlaneDecodedPayload.RealtimeGlucose -> {
                val reading = payload.reading
                val mg = reading.currentGlucoseMgDL
                if (mg != null && mg > 0) {
                    record(reading.lifeCount, mg, dateFor(reading.lifeCount, receivedAt))
                    updateState { it.copy(lastGlucoseLifeCount = reading.lifeCount, lastGlucoseMgDL = mg) }
                }
            }

            is DataPlaneDecodedPayload.ClinicalRecordPayload -> {
                val record = payload.record
                val mg = record.currentGlucoseMgDL
                if (mg != null && mg > 0) {
                    this.record(record.lifeCount, mg, dateFor(record.lifeCount, receivedAt))
                }
            }

            is DataPlaneDecodedPayload.HistoricalPagePayload -> {
                for (sample in payload.page.samples) {
                    val mg = sample.glucoseMgDL
                    if (mg != null && mg > 0) {
                        this.record(sample.lifeCount, mg, dateFor(sample.lifeCount, receivedAt))
                    }
                }
            }

            is DataPlaneDecodedPayload.PatchStatusPayload -> {
                val st = payload.status
                val ageMinutes = st.currentLifeCount
                val expired = !st.isPatchStateActive && st.isPatchStateExpiredOrError
                if (ageMinutes > 0) {
                    val asCapped = ageMinutes.coerceIn(0, 65535)
                    updateState { it.copy(lastGlucoseLifeCount = maxOf(it.lastGlucoseLifeCount ?: 0, asCapped)) }
                    emit(Event.SensorMeta(ageMinutes, expired, state?.serialNumber))
                } else {
                    emit(Event.SensorMeta(state?.lastGlucoseLifeCount, false, state?.serialNumber))
                }
            }

            is DataPlaneDecodedPayload.Raw -> Unit
        }
    }

    private fun record(lifeCount: Int, mg: Int, dateMs: Long) {
        val rows: List<GlucoseSample>
        synchronized(storeLock) {
            sampleStore[lifeCount] = mg to dateMs
            val now = System.currentTimeMillis()
            val cutoff = now - WINDOW_MS
            val toDrop = sampleStore.entries.filter { it.value.second < cutoff }
            if (toDrop.isNotEmpty()) {
                toDrop.forEach { sampleStore.remove(it.key) }
            }
            persistSamples(sampleStore.toMap())
            rows = sampleStore.entries
                .sortedBy { it.value.second }
                .map { GlucoseSample(it.value.second, it.value.first.toDouble()) }
        }
        emit(Event.Samples(rows))
    }

    /** lifeCount → absolute date from the activation epoch (receivedAt fallback). */
    private fun dateFor(lifeCount: Int, receivedAt: Long): Long {
        val activation = state?.activationTimeSeconds ?: return receivedAt
        return activation.toLong() * 1000 + lifeCount.toLong() * 60_000
    }

    private fun updateState(transform: (DirectSensorState) -> DirectSensorState) {
        val current = state ?: return
        state = transform(current)
    }

    // MARK: Link + reconnect

    /**
     * Called from the GATT executor thread when the link drops. [session] is
     * the GattSession instance that reported it (captured at registration)
     * so a drop from a superseded session can never tear down the live one.
     */
    private fun onGattDropped(code: Int, session: GattSession) {
        Log.w(TAG, "GATT link dropped status=$code")
        if (gatt !== session) return // a newer session already took over
        worker.execute {
            runCatching {
                if (cancelled) return@runCatching
                if (gatt !== session) return@runCatching
                gatt = null
                setLink(false)
                scheduleReconnect()
            }
        }
    }

    /**
     * Persistent reconnect with backoff: a paired sensor should be
     * re-acquired on its own. Quick for the first few tries (typical
     * app-suspension drops), then a steady 30 s — the sensor only answers
     * when its connection window is open, so a scan can miss one round and
     * hit the next. The delay is a SCHEDULED worker task (the single repo
     * thread is never parked sleeping), and [generation] supersedes pending
     * attempts on stop / forget / NFC re-pair.
     */
    private fun scheduleReconnect() {
        if (cancelled || state == null) {
            reconnecting = false
            return
        }
        val gen = generation
        reconnecting = true
        reconnectAttempt += 1
        val delaySec = when (reconnectAttempt) {
            1 -> 2
            2 -> 4
            3 -> 8
            4 -> 16
            else -> 30
        }
        if (reconnectAttempt <= 4) {
            emit(Event.Status("Link lost — reconnecting in $delaySec s…"))
        } else if (reconnectAttempt == 5) {
            emit(Event.Status("Still searching for the sensor — trying every 30 s…"))
        }
        Log.i(TAG, "scheduleReconnect #$reconnectAttempt in $delaySec s")
        worker.schedule({
            if (cancelled || gen != generation || linkActive) return@schedule
            val saved = state
            if (saved == null) {
                reconnecting = false
                return@schedule
            }
            runCatching {
                connectAndAuthorize(saved.blePIN, saved.bleAddress, saved, allowCached = true)
            }.onFailure { e ->
                Log.w(TAG, "reconnect failed: ${e.message}")
                teardownSession()
                emit(Event.Status("Reconnect failed: ${shortError(e)}"))
                scheduleReconnect()
            }
        }, delaySec.toLong(), TimeUnit.SECONDS)
    }

    private fun setLink(active: Boolean) {
        if (linkActive == active) return
        linkActive = active
        if (active) {
            reconnectAttempt = 0
            LinkKeeperService.start(app)
        } else {
            LinkKeeperService.stop(app)
        }
        emit(Event.Link(active))
    }

    private fun teardownSession() {
        val g = gatt
        gatt = null
        if (g != null) runCatching { g.disconnect() }
        setLink(false)
    }

    // MARK: Backfill decision (worker thread)

    /**
     * A human-readable reason the stored data needs a backfill, or nil when
     * the data is complete and current. A non-nil reason forces the full
     * 03 03 handshake (only its freshly initialized data plane accepts the
     * patchControl backfill writes).
     */
    private fun backfillNeededReason(): String? {
        val s = state ?: return "no saved state"
        val activation = s.activationTimeSeconds ?: return "no activation time"
        val lastLC = s.lastGlucoseLifeCount ?: return "no stored samples"
        val nowEpoch = System.currentTimeMillis() / 1000
        val sampleEpoch = activation.toLong() + lastLC.toLong() * 60
        val tailAgeMinutes = ((nowEpoch - sampleEpoch) / 60).coerceAtLeast(0)
        if (tailAgeMinutes > 90) {
            return "tail ${tailAgeMinutes.toInt()} min old"
        }
        // Scan the last 24 h of stored lifeCounts for a gap the realtime
        // stream cannot fill (anything over 2 h is an outage, not 5-min
        // paging latency).
        val floor = if (lastLC >= 1440) lastLC - 1440 else 0
        val lcs = synchronized(storeLock) { sampleStore.keys.filter { it >= floor }.sorted() }
        if (lcs.size <= 1) return "no stored samples in window"
        var maxGap = 0
        for (i in 1 until lcs.size) {
            if (lcs[i] - lcs[i - 1] > maxGap) maxGap = lcs[i] - lcs[i - 1]
        }
        if (maxGap > 120) return "$maxGap-min hole in last 24 h"
        return null
    }

    // MARK: Persistence

    private fun loadState(): DirectSensorState? {
        val b64 = prefs.getString(STATE_KEY, null) ?: return null
        return try {
            val json = String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
            val o = JSONObject(json)
            DirectSensorState(
                serialNumber = optStringOrNull(o, "serialNumber"),
                bleAddress = optStringOrNull(o, "bleAddress"),
                blePIN = hexDecode(o.optString("blePIN", "")),
                receiverIDLEHex = o.getString("receiverIDLEHex"),
                receiverIDSource = o.optString("receiverIDSource", "accountless"),
                activationTimeSeconds = o.optIntOrNull("activationTimeSeconds"),
                warmupDurationMinutes = o.optIntOrNull("warmupDurationMinutes"),
                wearDurationMinutes = o.optIntOrNull("wearDurationMinutes"),
                lastGlucoseLifeCount = o.optIntOrNull("lastGlucoseLifeCount"),
                lastGlucoseMgDL = o.optIntOrNull("lastGlucoseMgDL"),
                pairedAtMs = o.optLong("pairedAt", 0),
                phase5RawKey = hexDecode(o.optString("phase5RawKey", "")),
            )
        } catch (e: Exception) {
            Log.w(TAG, "state load failed: ${e.message}")
            null
        }
    }

    private fun saveState() {
        val s = state ?: return
        try {
            val o = JSONObject()
            o.put("serialNumber", s.serialNumber)
            o.put("bleAddress", s.bleAddress)
            o.put("blePIN", hexEncode(s.blePIN))
            o.put("receiverIDLEHex", s.receiverIDLEHex)
            o.put("receiverIDSource", s.receiverIDSource)
            if (s.activationTimeSeconds != null) o.put("activationTimeSeconds", s.activationTimeSeconds)
            if (s.warmupDurationMinutes != null) o.put("warmupDurationMinutes", s.warmupDurationMinutes)
            if (s.wearDurationMinutes != null) o.put("wearDurationMinutes", s.wearDurationMinutes)
            if (s.lastGlucoseLifeCount != null) o.put("lastGlucoseLifeCount", s.lastGlucoseLifeCount)
            if (s.lastGlucoseMgDL != null) o.put("lastGlucoseMgDL", s.lastGlucoseMgDL)
            o.put("pairedAt", s.pairedAtMs)
            o.put("phase5RawKey", hexEncode(s.phase5RawKey))
            prefs.edit()
                .putString(STATE_KEY, Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                .apply()
        } catch (e: JSONException) {
            Log.w(TAG, "state save failed: ${e.message}")
        }
    }

    private fun loadSamples() {
        val activation = state?.activationTimeSeconds ?: return
        val b64 = prefs.getString(SAMPLES_KEY, null) ?: return
        try {
            val json = String(Base64.decode(b64, Base64.NO_WRAP), Charsets.UTF_8)
            val arr = JSONArray(json)
            val now = System.currentTimeMillis()
            synchronized(storeLock) {
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    val lifeCount = row.getInt("lifeCount")
                    val mg = row.getInt("mgdl")
                    val dateMs = activation.toLong() * 1000 + lifeCount.toLong() * 60_000
                    if (now - dateMs <= WINDOW_MS) {
                        sampleStore[lifeCount] = mg to dateMs
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "samples load failed: ${e.message}")
        }
    }

    private fun persistSamples(all: Map<Int, Pair<Int, Long>>) {
        try {
            val arr = JSONArray()
            for ((lifeCount, value) in all) {
                val row = JSONObject()
                row.put("lifeCount", lifeCount)
                row.put("mgdl", value.first)
                arr.put(row)
            }
            prefs.edit()
                .putString(SAMPLES_KEY, Base64.encodeToString(arr.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                .apply()
        } catch (e: JSONException) {
            Log.w(TAG, "samples save failed: ${e.message}")
        }
    }

    private fun emitStoredSamplesOnce() {
        if (samplesEmitted) return
        samplesEmitted = true
        val rows = synchronized(storeLock) {
            sampleStore.entries
                .sortedBy { it.value.second }
                .map { GlucoseSample(it.value.second, it.value.first.toDouble()) }
        }
        if (rows.isNotEmpty()) {
            emit(Event.Samples(rows))
            emit(Event.SensorMeta(state?.lastGlucoseLifeCount, false, state?.serialNumber))
        }
    }

    /** Stable per-install accountless receiver identity (no Abbott material). */
    private fun accountlessReceiverID(): Libre3ReceiverID {
        var id = prefs.getString(ACCOUNTLESS_KEY, null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString().lowercase()
            prefs.edit().putString(ACCOUNTLESS_KEY, id).apply()
        }
        return Libre3ReceiverID.fromAccountlessUniqueId(id)
    }

    /** Activation-time epoch seconds (LE u32), when in a plausible range. */
    private fun activationEpochSeconds(raw: ByteArray): Int? {
        if (raw.size != 4) return null
        val v = ((raw[3].toInt() and 0xFF) shl 24) or
            ((raw[2].toInt() and 0xFF) shl 16) or
            ((raw[1].toInt() and 0xFF) shl 8) or
            (raw[0].toInt() and 0xFF)
        // Plausible sensor activation epochs: ~2019…2035.
        return if (v in 1_550_000_000..2_060_000_000) v else null
    }

    // MARK: Small utilities

    @SuppressLint("MissingPermission")
    private fun bluetoothDevice(address: String): BluetoothDevice {
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: throw SensorScanError.BleNotAvailable
        @Suppress("DEPRECATION")
        return adapter.getRemoteDevice(address)
    }

    private fun emit(event: GlucoseRepository.Event) {
        listener.onEvent(event)
    }

    private fun shortError(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() }?.take(160) ?: (e::class.simpleName ?: "unknown error")

    private fun hexEncode(data: ByteArray): String =
        data.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun hexDecode(s: String): ByteArray {
        val clean = s.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        if (clean.isEmpty()) return ByteArray(0)
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return out
    }

    private fun optStringOrNull(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.getString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else getInt(key)

    companion object {
        private const val TAG = "CluecoseLibre"
        private const val PREFS_NAME = "cluecose"

        /** iOS `DirectSensorState.stateKey` / `.samplesKey` / `.accountlessIDKey`. */
        const val STATE_KEY = "cluecose.libre.directState"
        const val SAMPLES_KEY = "cluecose.libre.directSamples"
        const val ACCOUNTLESS_KEY = "cluecose.libre.accountlessID"
        const val OWNER_RECEIVER_ID_KEY = "cluecose.libre.ownerReceiverID"

        /** 48 h sample window (iOS `Self.window`). */
        private const val WINDOW_MS = 48L * 60 * 60 * 1000

        /** The sensor opens a connection window about once a minute. */
        private const val DISCOVERY_TIMEOUT_MS = 75_000L
    }
}

/**
 * Everything needed to reconnect and keep streaming without another NFC tap
 * (1:1 of iOS `DirectSensorState`; bytes hex-encoded in the JSON payload).
 */
private data class DirectSensorState(
    val serialNumber: String?,
    val bleAddress: String?,
    val blePIN: ByteArray,
    val receiverIDLEHex: String,
    val receiverIDSource: String,
    val activationTimeSeconds: Int?,
    val warmupDurationMinutes: Int?,
    val wearDurationMinutes: Int?,
    val lastGlucoseLifeCount: Int?,
    val lastGlucoseMgDL: Int?,
    val pairedAtMs: Long,
    val phase5RawKey: ByteArray,
)

sealed class RealGlucoseError(message: String) : Exception(message) {
    object BleNotFound : RealGlucoseError("Couldn't find the sensor on Bluetooth. Keep it on and try the tap again.")
    data class EntropySizeMismatch(val expected: Int, val actual: Int) :
        RealGlucoseError("entropy size mismatch (expected $expected, got $actual)")
}
