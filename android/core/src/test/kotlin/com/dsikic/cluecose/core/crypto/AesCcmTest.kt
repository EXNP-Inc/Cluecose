package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

/**
 * Correctness gate for the pure-Kotlin AES-CCM port: RFC 3610 Packet Vector #1
 * (the same vector the Swift suite uses in AESCCMTests), plus tamper detection
 * and a data-plane-style M=4 round trip.
 */
class AesCcmTest {

    // RFC 3610 Packet Vector #1
    //   Key:    C0 C1 C2 C3 C4 C5 C6 C7 C8 C9 CA CB CC CD CE CF
    //   Nonce:  00 00 00 03 02 01 00 A0 A1 A2 A3 A4 A5     (13 bytes)
    //   AAD:    00 01 02 03 04 05 06 07                     (8 bytes)
    //   PT:     08..20 21 22 23                              (23 bytes)
    //   CT:     58 8C 97 9A 61 C6 63 D2 F0 66 D0 C2 C0 F9 89 80 6D 5F 6B 61 DA C3 84
    //   Tag:    17 E8 D1 2C FD F9 26 E0                     (M=8)

    private val key = Hex.decode("c0c1c2c3c4c5c6c7c8c9cacbcccdcecf")
    private val nonce = Hex.decode("00000003020100a0a1a2a3a4a5")
    private val aad = Hex.decode("0001020304050607")
    private val pt = Hex.decode("08090a0b0c0d0e0f101112131415161718191a1b1c1d1e")
    private val expectedCt = Hex.decode("588c979a61c663d2f066d0c2c0f989806d5f6b61dac384")
    private val expectedTag = Hex.decode("17e8d12cfdf926e0")

    @Test
    fun `RFC 3610 vector 1 encrypts to known ciphertext and tag`() {
        val out = AesCcm.encrypt(
            nonce = nonce,
            plaintext = pt,
            aad = aad,
            tagLength = 8,
            aes = aesBlock128(key),
        )
        val ct = out.copyOfRange(0, out.size - 8)
        val tag = out.copyOfRange(out.size - 8, out.size)
        assertContentEquals(expectedCt, ct)
        assertContentEquals(expectedTag, tag)
    }

    @Test
    fun `RFC 3610 vector 1 round trips through decrypt`() {
        val out = AesCcm.encrypt(nonce, pt, 8, aad, aesBlock128(key))
        val recovered = AesCcm.decrypt(nonce, out, 8, aad, aesBlock128(key))
        assertContentEquals(pt, recovered)
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val out = AesCcm.encrypt(nonce, pt, 8, aad, aesBlock128(key))
        val tampered = out.copyOf()
        tampered[0] = (tampered[0].toInt() xor 1).toByte()
        assertFailsWith<AesCcm.AesCcmError.MacMismatch> {
            AesCcm.decrypt(nonce, tampered, 8, aad, aesBlock128(key))
        }
    }

    @Test
    fun `tampered tag fails authentication`() {
        val out = AesCcm.encrypt(nonce, pt, 8, aad, aesBlock128(key))
        val tampered = out.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<AesCcm.AesCcmError.MacMismatch> {
            AesCcm.decrypt(nonce, tampered, 8, aad, aesBlock128(key))
        }
    }

    @Test
    fun `data plane shape M=4 round trip with empty AAD`() {
        // Mirrors the data-plane usage: 13-byte nonce, 4-byte tag, no AAD.
        val kEnc = Hex.decode("00112233445566778899aabbccddeeff")
        val ivEnc = Hex.decode("0102030405060708")
        val seq = 0x0201 // seq16 LE: 01 02
        val descriptor = byteArrayOf(0x00, 0x0f, 0x00) // kind3
        val nonce13 = byteArrayOf(
            (seq and 0xFF).toByte(), ((seq ushr 8) and 0xFF).toByte()
        ) + descriptor + ivEnc
        assertEquals(13, nonce13.size)

        val plaintext = ByteArray(29) { (it * 7 + 3).toByte() }
        val aes = aesBlock128(kEnc)
        val frame = AesCcm.encrypt(nonce13, plaintext, tagLength = 4, aes = aes)
        assertEquals(29 + 4, frame.size)

        val recovered = AesCcm.decrypt(nonce13, frame, tagLength = 4, aes = aes)
        assertContentEquals(plaintext, recovered)

        // Wrong descriptor in the nonce = different nonce = tag mismatch.
        // kind3 descriptor sits at [2,3,4] = 00 0f 00; flip the 0x0f to 0x00 (→ kind0).
        val wrongNonce = nonce13.copyOf()
        wrongNonce[3] = 0x00
        assertFailsWith<AesCcm.AesCcmError.MacMismatch> {
            AesCcm.decrypt(wrongNonce, frame, tagLength = 4, aes = aes)
        }
    }

    @Test
    fun `empty plaintext round trip`() {
        val aes = aesBlock128(key)
        val out = AesCcm.encrypt(nonce, ByteArray(0), 8, aad, aes)
        assertEquals(8, out.size) // only the tag
        assertContentEquals(ByteArray(0), AesCcm.decrypt(nonce, out, 8, aad, aes))
    }

    @Test
    fun `invalid nonce length rejected`() {
        // L = 15 - nonceLen must be in [2, 8]  =>  nonceLen in [7, 13].
        assertFailsWith<AesCcm.AesCcmError.InvalidParameters> {
            AesCcm.encrypt(ByteArray(6), ByteArray(16), 8, aad, aesBlock128(key)) // L=9
        }
        assertFailsWith<AesCcm.AesCcmError.InvalidParameters> {
            AesCcm.encrypt(ByteArray(14), ByteArray(16), 8, aad, aesBlock128(key)) // L=1
        }
    }

    @Test
    fun `wrong tag length rejected`() {
        assertFailsWith<AesCcm.AesCcmError.InvalidParameters> {
            AesCcm.encrypt(nonce, ByteArray(16), 5, aad, aesBlock128(key))
        }
    }
}
