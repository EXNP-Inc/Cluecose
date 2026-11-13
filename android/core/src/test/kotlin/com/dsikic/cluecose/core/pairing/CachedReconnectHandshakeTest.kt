package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.AesCcm
import com.dsikic.cluecose.core.crypto.phase5BlockEncryptor
import com.dsikic.cluecose.core.tables.ClasspathTablesSource
import com.dsikic.cluecose.core.util.ByteUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Offline golden tests for the cached/direct (0x11) reconnect handshake.
 * 1:1 port of LibreCRKit `PublicSmokeTests` cases that drive the *real*
 * LibAES + AesCcm + Phase5Challenge + Phase6Response through a scripted
 * transport (the exact wire operation a paired sensor performs on reconnect).
 */
class CachedReconnectHandshakeTest {

    private val tables = ClasspathTablesSource()

    /**
     * Port of `testCachedReconnectHandshakeSkipsCertAndEphemeralExchange`.
     * The transport sees exactly `writeCommand(0x11)`, `writeCommand(0x08)`,
     * one 54B challenge write (Phase 5), and returns the scripted R1 challenge
     * and Phase 6 notify. The flow must skip the cert/ephemeral exchange,
     * derive the session material from the Phase 6 CCM, and verify the R2/R1
     * echoes.
     */
    @Test
    fun `cached reconnect handshake skips cert and ephemeral exchange`() {
        val sensorR1 = (0x10..0x1f).map { it.toByte() }.toByteArray()
        val challengeNonce = byteArrayOf(0x21, 0x04, 0x00, 0x00, 0x8f.toByte(), 0x8c.toByte(), 0x4b)
        val phase6Nonce = byteArrayOf(0x22, 0x04, 0x00, 0x00, 0x7f.toByte(), 0x43, 0x8e.toByte())
        val phoneR2 = (0x30..0x3f).map { it.toByte() }.toByteArray()
        val tail4 = byteArrayOf(0x32, 0x25, 0xec.toByte(), 0x72)
        val phase5RawKey = (0x40..0x4f).map { it.toByte() }.toByteArray()
        val kEnc = (0x50..0x5f).map { it.toByte() }.toByteArray()
        val ivEnc = (0x60..0x67).map { it.toByte() }.toByteArray()

        val aes = phase5BlockEncryptor(phase5RawKey, tables)
        val phase6Plaintext = ByteUtil.concat(phoneR2, sensorR1, kEnc, ivEnc)
        val phase6CtTag = AesCcm.encrypt(
            nonce = phase6Nonce,
            plaintext = phase6Plaintext,
            tagLength = Phase6Response.TAG_SIZE,
            aes = aes,
        )
        val transport = ScriptedCommandPairingTransport(
            commandResponses = listOf(
                byteArrayOf(0x08, 0x17),
                byteArrayOf(0x08, 0x43),
            ),
            challengeNotifies = listOf(
                ByteUtil.concat(sensorR1, challengeNonce),
                ByteUtil.concat(phase6CtTag, phase6Nonce),
            ),
        )
        val flow = PairingFlow(transport)

        val result = flow.runCachedReconnectHandshake(
            tail4 = tail4,
            phase5RawKey = phase5RawKey,
            tables = tables,
            r2Provider = { phoneR2 },
        )

        assertEquals(listOf(0x11, 0x08), transport.commandWrites)
        assertEquals(listOf(BleCharRef.CHALLENGE), transport.messageWrites.map { it.first })
        assertEquals(Phase5Challenge.WIRE_SIZE, transport.messageWrites.first().second.size)
        assertContentEquals(ByteUtil.concat(sensorR1, challengeNonce), result.preamble.sensorR1Wire)
        assertContentEquals(phoneR2, result.sessionMaterial.phoneR2)
        assertContentEquals(sensorR1, result.sessionMaterial.sensorR1)
        assertContentEquals(kEnc, result.sessionMaterial.kEnc)
        assertContentEquals(ivEnc, result.sessionMaterial.ivEnc)
        assertContentEquals(
            ByteUtil.concat(sensorR1, phoneR2, tail4),
            result.phase5Sent.decrypt(aes = aes, nonce = challengeNonce),
        )
    }

    /**
     * Port of `testAuthorizationHandshakeRejectsNonFourByteTailBeforeTransportUse`
     * (validation half): a 3-byte tail is rejected before any transport use.
     */
    @Test
    fun `rejects non four-byte tail before transport use`() {
        val transport = FailingCommandPairingTransport()
        val flow = PairingFlow(transport)
        val e = assertFailsWith<IllegalArgumentException> {
            flow.runCachedReconnectHandshake(
                tail4 = byteArrayOf(0x01, 0x02, 0x03),
                phase5RawKey = ByteArray(16),
                tables = tables,
                r2Provider = { ByteArray(16) },
            )
        }
        assertEquals("tail4 must be 4 bytes, got 3", e.message)
        assertEquals(0, transport.commandWrites.size)
    }

    /** A wrong cached raw key makes the Phase-6 CCM tag fail. */
    @Test
    fun `wrong cached raw key fails phase 6 tag`() {
        val sensorR1 = (0x10..0x1f).map { it.toByte() }.toByteArray()
        val challengeNonce = byteArrayOf(0x21, 0x04, 0x00, 0x00, 0x8f.toByte(), 0x8c.toByte(), 0x4b)
        val phase6Nonce = byteArrayOf(0x22, 0x04, 0x00, 0x00, 0x7f.toByte(), 0x43, 0x8e.toByte())
        val phoneR2 = (0x30..0x3f).map { it.toByte() }.toByteArray()
        val tail4 = byteArrayOf(0x32, 0x25, 0xec.toByte(), 0x72)
        val rightKey = (0x40..0x4f).map { it.toByte() }.toByteArray()
        val wrongKey = (0x41..0x50).map { it.toByte() }.toByteArray()

        val rightAes = phase5BlockEncryptor(rightKey, tables)
        val phase6CtTag = AesCcm.encrypt(
            nonce = phase6Nonce,
            plaintext = ByteUtil.concat(phoneR2, sensorR1, ByteArray(16), ByteArray(8)),
            tagLength = Phase6Response.TAG_SIZE,
            aes = rightAes,
        )
        val transport = ScriptedCommandPairingTransport(
            commandResponses = listOf(byteArrayOf(0x08, 0x17), byteArrayOf(0x08, 0x43)),
            challengeNotifies = listOf(
                ByteUtil.concat(sensorR1, challengeNonce),
                ByteUtil.concat(phase6CtTag, phase6Nonce),
            ),
        )
        val flow = PairingFlow(transport)
        assertFailsWith<Phase6ResponseError.TagMismatch> {
            flow.runCachedReconnectHandshake(
                tail4 = tail4,
                phase5RawKey = wrongKey,
                tables = tables,
                r2Provider = { phoneR2 },
            )
        }
    }
}

/** Scripted transport mirroring LibreCRKit `ScriptedCommandPairingTransport`. */
private class ScriptedCommandPairingTransport(
    commandResponses: List<ByteArray>,
    challengeNotifies: List<ByteArray>,
) : CommandPairingTransport {
    private val pendingCommandResponses = ArrayDeque(commandResponses)
    private val pendingChallengeNotifies = ArrayDeque(challengeNotifies)
    val commandWrites = mutableListOf<Int>()
    val messageWrites = mutableListOf<Pair<BleCharRef, ByteArray>>()

    override fun write(message: ByteArray, to: BleCharRef) {
        messageWrites.add(to to message)
    }

    override fun awaitNotify(on: BleCharRef, exactly: Int): ByteArray {
        check(on == BleCharRef.CHALLENGE && pendingChallengeNotifies.isNotEmpty()) { "unexpected awaitNotify" }
        val next = pendingChallengeNotifies.removeFirst()
        check(next.size == exactly) { "unexpected notify size ${next.size}, wanted $exactly" }
        return next
    }

    override fun writeCommand(command: Int) {
        commandWrites.add(command)
    }

    override fun awaitCommandResponse(timeoutSeconds: Int): ByteArray {
        check(pendingCommandResponses.isNotEmpty()) { "no command response scripted" }
        return pendingCommandResponses.removeFirst()
    }
}

/** Transport that fails any use — proves validation happens before I/O. */
private class FailingCommandPairingTransport : CommandPairingTransport {
    val commandWrites = mutableListOf<Int>()
    override fun write(message: ByteArray, to: BleCharRef) = error("transport used before validation")
    override fun awaitNotify(on: BleCharRef, exactly: Int): ByteArray = error("transport used before validation")
    override fun writeCommand(command: Int) {
        commandWrites.add(command)
        error("transport used before validation")
    }

    override fun awaitCommandResponse(timeoutSeconds: Int): ByteArray = error("transport used before validation")
}
