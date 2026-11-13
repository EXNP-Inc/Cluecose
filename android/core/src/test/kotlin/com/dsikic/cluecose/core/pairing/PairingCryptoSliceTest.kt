package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.P256Ecdh
import com.dsikic.cluecose.core.crypto.aesBlock128
import com.dsikic.cluecose.core.util.ByteUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trivial smoke tests for the PhoneCert / EphemeralExchange (ECDH) /
 * SensorCert / ChallengeError slice — the structural guarantees a 1:1 port
 * must hold. The golden vector suite (another agent) is the correctness gate;
 * no vectors are invented here.
 */
class PairingCryptoSliceTest {

    @BeforeTest
    fun installClasspathCertSource() {
        PhoneCertConfig.source = ClasspathPhoneCertSource()
    }

    @AfterTest
    fun clearCertSource() {
        PhoneCertConfig.source = null
    }

    // MARK: - PhoneCert

    @Test
    fun `phone cert rejects wrong size`() {
        val e = assertFailsWith<PhoneCertError.WrongSize> { PhoneCert(ByteArray(161)) }
        assertEquals(161, e.size)
        val e2 = assertFailsWith<PhoneCertError.WrongSize> { PhoneCert(ByteArray(163)) }
        assertEquals(163, e2.size)
    }

    @Test
    fun `phone cert rejects non-uncompressed static pubkey`() {
        val raw = ByteArray(PhoneCert.TOTAL_SIZE)
        raw[33] = 0x02 // not the 0x04 uncompressed prefix
        assertFailsWith<PhoneCertError.NotUncompressedPoint> { PhoneCert(raw) }
    }

    @Test
    fun `phone cert parses bundled 162b cert and selects index-1 window`() {
        val cert = PhoneCert.bundled162b()
        assertEquals(PhoneCert.TOTAL_SIZE, cert.raw.size)
        assertEquals(PhoneCert.STATIC_PUB_SIZE, cert.staticPub.size)
        assertEquals(0x04, cert.staticPub[0].toInt() and 0xFF)
        assertContentEquals(
            cert.raw.copyOfRange(PhoneCert.PUBKEY_RANGE_START, PhoneCert.PUBKEY_RANGE_END),
            cert.staticPub,
        )
        assertContentEquals(FirstPairStaticScalarWindow.firstPairIndex1, cert.phase5StaticScalarWindowOverride)
        assertEquals(70, FirstPairStaticScalarWindow.firstPairIndex1.size)
    }

    @Test
    fun `phone cert firstpair bundle has null window override`() {
        val cert = PhoneCert.bundledFirstPair()
        assertEquals(0x00, cert.raw[1].toInt() and 0xFF)
        assertNull(cert.phase5StaticScalarWindowOverride)
    }

    @Test
    fun `bundled cert missing when source does not provide it`() {
        assertFailsWith<PhoneCertError.BundledResourceMissing> { PhoneCert.bundled("phone_cert_missing") }
        PhoneCertConfig.source = null
        assertFailsWith<PhoneCertError.BundledResourceMissing> { PhoneCert.bundled162b() }
    }

    // MARK: - EphemeralExchange (ECDH)

    @Test
    fun `ecdh round trip and bilinearity`() {
        val alice = EphemeralKeyPair.generate()
        val bob = EphemeralKeyPair.generate()
        val ab = EphemeralExchange.sharedSecret(alice.privateKey, EphemeralExchange.parsePeerPubkey(bob.publicKey65))
        val ba = EphemeralExchange.sharedSecret(bob.privateKey, EphemeralExchange.parsePeerPubkey(alice.publicKey65))
        assertContentEquals(ab, ba)
        assertEquals(32, ab.size)

        // publicKey65(scalar) must equal the point [scalar]·G.
        val scalar = ByteArray(32) { it.toByte() }
        assertContentEquals(P256Ecdh.publicKey65FromScalar(scalar), EphemeralKeyPair.fromPrivateKey(P256PrivateKey(scalar)).publicKey65)
    }

    @Test
    fun `native scalar window round trip`() {
        val raw32 = ByteArray(32) { ((it * 7 + 3) and 0xFF).toByte() }
        val window70 = ByteArray(70)
        System.arraycopy(raw32, 0, window70, 0, 32) // LE scalar, high 38 bytes zero
        val keyPair = EphemeralKeyPair.fromNativeScalarWindow(window70)
        assertContentEquals(raw32.reversedArray(), keyPair.privateKey.raw) // stored BE
        assertContentEquals(
            EphemeralKeyPair.publicKey65(window70),
            keyPair.publicKey65,
        )
        // The override constructor keeps the caller-supplied wire point.
        val override = ByteArray(65)
        override[0] = 0x04
        val overridden = EphemeralKeyPair.fromNativeScalarWindow(window70, override)
        assertContentEquals(override, overridden.publicKey65)
        assertFailsWith<EphemeralExchangeError.InvalidEncoding> {
            EphemeralKeyPair.fromNativeScalarWindow(window70, ByteArray(64))
        }
        assertFailsWith<EphemeralExchangeError.InvalidScalarWindowLength> {
            EphemeralKeyPair.fromNativeScalarWindow(ByteArray(31))
        }
    }

    // MARK: - SensorCert

    @Test
    fun `sensor cert rejects wrong size`() {
        val e = assertFailsWith<SensorCertError.WrongSize> { SensorCert(ByteArray(139)) }
        assertEquals(139, e.size)
    }

    @Test
    fun `sensor cert rejects non-uncompressed static pubkey`() {
        val raw = ByteArray(SensorCert.TOTAL_SIZE)
        raw[11] = 0x02
        assertFailsWith<SensorCertError.NotUncompressedPoint> { SensorCert(raw) }
    }

    @Test
    fun `sensor cert verify rejects invalid signing key`() {
        val raw = ByteArray(SensorCert.TOTAL_SIZE)
        raw[11] = 0x04
        val cert = SensorCert(raw)
        assertEquals(65, cert.staticPub.size)
        assertEquals(64, cert.signature.size)
        assertEquals(11, cert.header.size)
        assertEquals(76, cert.signedPayload.size)
        assertFailsWith<SensorCertError.InvalidSigningPublicKey> { cert.verifyECDSA(ByteArray(64)) }
        assertNull(cert.verifiedSigningKeyIndex(listOf()))
    }

    // MARK: - Challenge (AES-CCM session-key path)

    @Test
    fun `phase 5 challenge round trips under a standard aes key`() {
        val key = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(7) { (it + 1).toByte() }
        val plaintext = ByteUtil.concat(
            ByteArray(16) { (0x10 + it).toByte() },
            ByteArray(16) { (0x20 + it).toByte() },
            byteArrayOf(0x32, 0x25, 0xec.toByte(), 0x72),
        )
        val challenge = Phase5Challenge.encrypt(
            plaintext = plaintext,
            aes = aesBlock128(key),
            nonce = nonce,
        )
        assertEquals(Phase5Challenge.WIRE_SIZE, challenge.wireBytes.size)
        assertEquals(Phase5Challenge.LOGICAL_SIZE, challenge.logicalBytes.size)
        val decoded = Phase5Challenge.decode(challenge.wireBytes)
        assertContentEquals(challenge.ciphertext, decoded.ciphertext)
        assertContentEquals(challenge.tag, decoded.tag)
        assertContentEquals(plaintext, decoded.decrypt(aes = aesBlock128(key), nonce = nonce))
    }

    @Test
    fun `phase 5 challenge session-key overloads round trip`() {
        val key = ByteArray(16) { (0xA0 + it).toByte() }
        val nonce = ByteArray(7) { (0x60 + it).toByte() }
        val plaintext = ByteArray(36) { it.toByte() }
        val challenge = Phase5Challenge.encryptSessionKey(plaintext = plaintext, sessionKey = key, nonce = nonce)
        assertContentEquals(plaintext, challenge.decryptSessionKey(sessionKey = key, nonce = nonce))
        assertFailsWith<ChallengeError.WrongKeySize> {
            Phase5Challenge.encryptSessionKey(
                plaintext = ByteArray(36),
                sessionKey = ByteArray(15),
                nonce = nonce,
            )
        }
        assertFailsWith<ChallengeError.WrongKeySize> {
            challenge.decryptSessionKey(sessionKey = ByteArray(17), nonce = nonce)
        }
        // Tampered tag fails (AesCcm.MacMismatch, same as the Swift macMismatch path).
        val tampered = Phase5Challenge(
            ciphertext = challenge.ciphertext.copyOf(),
            tag = challenge.tag.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
        )
        assertFailsWith<com.dsikic.cluecose.core.crypto.AesCcm.AesCcmError.MacMismatch> {
            tampered.decrypt(aes = aesBlock128(key), nonce = nonce)
        }
    }
}
