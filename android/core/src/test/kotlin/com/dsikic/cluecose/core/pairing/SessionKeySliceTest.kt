package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.tables.RuntimeTable
import com.dsikic.cluecose.core.util.ByteUtil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Slice tests for the first-pair key surfaces ([SessionKey] + the result
 * types in [FirstPairPreambleResult.kt]). These are the VM-free invariants:
 * value equality, the `04`/65-byte sensor-point guard, and the error shapes.
 * The VM-backed derivation paths are covered by the golden suite.
 */
class SessionKeySliceTest {

    /** Tables source that fails loudly — the tests must not reach it. */
    private val failingTables = TablesFailLoud()

    /** A [SessionKey] whose [Phase5KeySchedule] tables are never loaded. */
    private val sessionKey = SessionKey(Phase5KeySchedule(failingTables))

    // MARK: - SessionKeyInputs

    @Test
    fun `session key inputs equality is structural and extra defaults to empty`() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        val c = byteArrayOf(7, 8, 9)
        val d = byteArrayOf(10, 11, 12)
        val left = SessionKeyInputs(a, b, c, d)
        val right = SessionKeyInputs(a.copyOf(), b.copyOf(), c.copyOf(), d.copyOf())
        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
        // Swift `extra: Data = Data()`
        assertContentEquals(byteArrayOf(), left.extra)
    }

    @Test
    fun `session key inputs differ on any field`() {
        val a = byteArrayOf(1, 2, 3)
        val base = SessionKeyInputs(a, a, a, a)
        val differentExtra = SessionKeyInputs(a, a, a, a, extra = byteArrayOf(0x01))
        val differentK1 = SessionKeyInputs(a, a, a, a).copy(k1 = byteArrayOf(0xFF.toByte()))
        assertEquals(base, base)
        assertEquals(false, base == differentExtra)
        assertEquals(false, base == differentK1)
    }

    // MARK: - derive (injection point)

    @Test
    fun `derive throws not yet specified`() {
        val inputs = SessionKeyInputs(ByteArray(32), ByteArray(32), ByteArray(16), ByteArray(16))
        assertFailsWith<SessionKeyError> { sessionKey.derive(inputs) }
            .let { error ->
                assertEquals(SessionKeyError.NotYetSpecified, error)
            }
    }

    // MARK: - uncompressedPointXYBE guards (via deriveFirstPairPhase5Material)

    /** 65B `04 || ...` point built from a seed byte so X/Y are deterministic. */
    private fun point65(seed: Int = 0x11): ByteArray {
        val out = ByteArray(65)
        out[0] = 0x04
        for (i in 1 until 65) out[i] = (i + seed).toByte()
        return out
    }

    @Test
    fun `invalid sensor point size is rejected with count`() {
        val inputs = FirstPairPhase5KeyInputs(
            nullEntropy11A = ByteArray(282),
            sensorEphemeralPub65 = point65().copyOfRange(0, 64), // 64B — too short
            sensorStaticPub65 = point65(),
        )
        val e = assertFailsWith<SessionKeyError.InvalidSensorPointEncoding> {
            sessionKey.deriveFirstPairPhase5Material(inputs)
        }
        assertEquals("sensor ephemeral", e.label)
        assertEquals(64, e.count)
        assertEquals(0x04, e.prefix)
    }

    @Test
    fun `wrong sensor point prefix is rejected with prefix byte`() {
        val bad = point65()
        bad[0] = 0x06
        val inputs = FirstPairPhase5KeyInputs(
            nullEntropy11A = ByteArray(282),
            sensorEphemeralPub65 = point65(),
            sensorStaticPub65 = bad,
        )
        val e = assertFailsWith<SessionKeyError.InvalidSensorPointEncoding> {
            sessionKey.deriveFirstPairPhase5Material(inputs)
        }
        assertEquals("sensor static", e.label)
        assertEquals(65, e.count)
        assertEquals(0x06, e.prefix)
    }

    @Test
    fun `empty sensor point is rejected with null prefix`() {
        val inputs = FirstPairPhase5KeyInputs(
            nullEntropy11A = ByteArray(282),
            sensorEphemeralPub65 = byteArrayOf(),
            sensorStaticPub65 = point65(),
        )
        val e = assertFailsWith<SessionKeyError.InvalidSensorPointEncoding> {
            sessionKey.deriveFirstPairPhase5Material(inputs)
        }
        assertEquals("sensor ephemeral", e.label)
        assertEquals(0, e.count)
        assertNull(e.prefix)
    }

    // MARK: - Result types

    @Test
    fun `pairing handshake result equality is structural`() {
        val phoneCertRaw = ByteArray(PhoneCert.TOTAL_SIZE).also { it[33] = 0x04 }
        val sensorCertRaw = ByteArray(SensorCert.TOTAL_SIZE).also { it[11] = 0x04 }
        val phoneCert = PhoneCert(phoneCertRaw)
        val sensorCert = SensorCert(sensorCertRaw)
        val phoneEph = EphemeralKeyPair(P256PrivateKey(ByteArray(32)), point65())
        val r1 = PairingHandshakeResult(
            phoneCert = phoneCert,
            sensorCert = sensorCert,
            sensorCertSigningKeyIndex = 1,
            phoneEph = phoneEph,
            sensorEphPub = point65(0x22),
            sharedEphStatic = ByteArray(32) { 0x0A },
            sharedEphEph = ByteArray(32) { 0x0B },
        )
        val r2 = PairingHandshakeResult(
            phoneCert = PhoneCert(phoneCertRaw.copyOf()),
            sensorCert = SensorCert(sensorCertRaw.copyOf()),
            sensorCertSigningKeyIndex = 1,
            phoneEph = EphemeralKeyPair(P256PrivateKey(ByteArray(32)), point65()),
            sensorEphPub = point65(0x22).copyOf(),
            sharedEphStatic = ByteArray(32) { 0x0A },
            sharedEphEph = ByteArray(32) { 0x0B },
        )
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun `preamble result slices r1 wire into r1 and nonce`() {
        val phoneCertRaw = ByteArray(PhoneCert.TOTAL_SIZE).also { it[33] = 0x04 }
        val sensorCertRaw = ByteArray(SensorCert.TOTAL_SIZE).also { it[11] = 0x04 }
        val handshake = PairingHandshakeResult(
            phoneCert = PhoneCert(phoneCertRaw),
            sensorCert = SensorCert(sensorCertRaw),
            sensorCertSigningKeyIndex = null,
            phoneEph = EphemeralKeyPair(P256PrivateKey(ByteArray(32)), point65()),
            sensorEphPub = point65(),
            sharedEphStatic = ByteArray(32),
            sharedEphEph = ByteArray(32),
        )
        val wire = (0..22).map { it.toByte() }.toByteArray()
        val preamble = FirstPairPreambleResult(
            phaseHandshake = handshake,
            sensorR1Wire = wire,
            sensorR1 = ByteUtil.slice(wire, 0, 16),
            nonce7 = ByteUtil.slice(wire, 16, 23),
        )
        assertContentEquals(wire.copyOfRange(0, 16), preamble.sensorR1)
        assertContentEquals(wire.copyOfRange(16, 23), preamble.nonce7)
        assertEquals(16, preamble.sensorR1.size)
        assertEquals(7, preamble.nonce7.size)
        // The Swift typealiases: CommandGatedAuthorizationPreambleResult is
        // FirstPairPreambleResult itself.
        val aliased: CommandGatedAuthorizationPreambleResult = preamble
        assertEquals(preamble, aliased)
    }

    @Test
    fun `handshake result carries phase 5 6 and material`() {
        val phoneCertRaw = ByteArray(PhoneCert.TOTAL_SIZE).also { it[33] = 0x04 }
        val sensorCertRaw = ByteArray(SensorCert.TOTAL_SIZE).also { it[11] = 0x04 }
        val handshake = PairingHandshakeResult(
            phoneCert = PhoneCert(phoneCertRaw),
            sensorCert = SensorCert(sensorCertRaw),
            sensorCertSigningKeyIndex = 0,
            phoneEph = EphemeralKeyPair(P256PrivateKey(ByteArray(32)), point65()),
            sensorEphPub = point65(),
            sharedEphStatic = ByteArray(32),
            sharedEphEph = ByteArray(32),
        )
        val r1Wire = (0..22).map { it.toByte() }.toByteArray()
        val preamble = FirstPairPreambleResult(
            phaseHandshake = handshake,
            sensorR1Wire = r1Wire,
            sensorR1 = ByteUtil.slice(r1Wire, 0, 16),
            nonce7 = ByteUtil.slice(r1Wire, 16, 23),
        )
        val phase5Sent = Phase5Challenge(ByteArray(36), ByteArray(4))
        val phase6Raw = ByteArray(Phase6Response.WIRE_SIZE)
        val phase6 = Phase6Response.decode(phase6Raw)
        val material = Phase6SessionMaterial(
            phoneR2 = ByteArray(16),
            sensorR1 = ByteArray(16),
            kEnc = ByteArray(16),
            ivEnc = ByteArray(8),
        )
        val result = FirstPairHandshakeResult(
            preamble = preamble,
            phase5Sent = phase5Sent,
            phase6Raw = phase6Raw,
            phase6 = phase6,
            sessionMaterial = material,
        )
        assertContentEquals(phase6Raw, result.phase6Raw)
        assertEquals(material, result.sessionMaterial)
        assertNotNull(result.phase5Sent.wireBytes)
        assertEquals(Phase5Challenge.WIRE_SIZE, result.phase5Sent.wireBytes.size)
        // Typealias identity with the authorization spelling.
        val aliased: CommandGatedAuthorizationHandshakeResult = result
        assertEquals(result, aliased)
    }
}

/** Tables source that throws — proves the slice tests never touch the VM tables. */
private class TablesFailLoud : com.dsikic.cluecose.core.tables.TablesSource {
    override fun load(table: RuntimeTable): ByteArray =
        error("tables must not be loaded in SessionKeySliceTest: ${table.fileName}")
}
