package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.P256Ecdsa

/**
 * Sensor certificate - 140 bytes, received in Phase 2 of the pairing
 * handshake (notify on handle 0x002d, reassembled from sensor → phone
 * notify fragments).
 * 1:1 port of LibreCRKit `Pairing/SensorCert.swift`.
 *
 * Format (decoded from captures/fresh_pair_2026_04_26):
 *
 *   [0]       msg_type  (0x01 observed)
 *   [1..11)   10B header
 *   [11]      0x04 (P-256 uncompressed-point prefix)
 *   [12..76)  X(32) || Y(32)      ← sensor STATIC pubkey
 *   [76..140) ECDSA signature     (64B raw r || s)
 *
 * The signature verifies as ECDSA-P256(SHA-256) over `raw[0..<76]` using the
 * Abbott patch-signing public key for the cert family. CryptoKit's
 * `P256.Signing` APIs accept this raw r||s signature representation directly;
 * on Android the verification is [P256Ecdsa.verify] over
 * [P256ScalarMultiplier].
 */

/**
 * Abbott patch-signing public keys (public verifier material, not private
 * keys). Mirrors Swift `Libre3PatchSigningKey`.
 */
object Libre3PatchSigningKey {

    /** Abbott patch-signing public key family 0, 65-byte X9.63/uncompressed P-256 point. */
    val level0: ByteArray = byteArrayOf(
        0x04.toByte(), 0xb6.toByte(), 0x9d.toByte(), 0x17.toByte(), 0x34.toByte(), 0xf5.toByte(), 0xe4.toByte(), 0x25.toByte(),
        0xbc.toByte(), 0xc0.toByte(), 0x57.toByte(), 0x6a.toByte(), 0xd1.toByte(), 0xf7.toByte(), 0x27.toByte(), 0xc1.toByte(),
        0x31.toByte(), 0x1c.toByte(), 0x90.toByte(), 0xb6.toByte(), 0xea.toByte(), 0x98.toByte(), 0x6f.toByte(), 0x00.toByte(),
        0x6e.toByte(), 0x7e.toByte(), 0x9f.toByte(), 0x90.toByte(), 0x96.toByte(), 0xf6.toByte(), 0xa8.toByte(), 0x28.toByte(),
        0x4f.toByte(), 0x12.toByte(), 0xbf.toByte(), 0x7d.toByte(), 0xdf.toByte(), 0xe1.toByte(), 0x54.toByte(), 0xa3.toByte(),
        0xf1.toByte(), 0xd4.toByte(), 0x5a.toByte(), 0x0f.toByte(), 0x27.toByte(), 0x34.toByte(), 0xec.toByte(), 0xab.toByte(),
        0xca.toByte(), 0x6b.toByte(), 0x9e.toByte(), 0xb5.toByte(), 0x6e.toByte(), 0xe4.toByte(), 0xec.toByte(), 0xca.toByte(),
        0x87.toByte(), 0x85.toByte(), 0x3a.toByte(), 0xd8.toByte(), 0x53.toByte(), 0xb6.toByte(), 0xa6.toByte(), 0x41.toByte(),
        0x80.toByte(),
    )

    /** Abbott patch-signing public key family 1, 65-byte X9.63/uncompressed P-256 point. Observed fresh-pair cert fixtures verify with this key. */
    val level1: ByteArray = byteArrayOf(
        0x04.toByte(), 0xa2.toByte(), 0xd8.toByte(), 0x47.toByte(), 0x89.toByte(), 0x90.toByte(), 0x94.toByte(), 0x5f.toByte(),
        0x70.toByte(), 0xa9.toByte(), 0x57.toByte(), 0x0a.toByte(), 0xde.toByte(), 0x07.toByte(), 0xb1.toByte(), 0x55.toByte(),
        0xbc.toByte(), 0x90.toByte(), 0x4d.toByte(), 0x2d.toByte(), 0x38.toByte(), 0x06.toByte(), 0x47.toByte(), 0x58.toByte(),
        0x7b.toByte(), 0x12.toByte(), 0x39.toByte(), 0x17.toByte(), 0x01.toByte(), 0x30.toByte(), 0x9b.toByte(), 0xd1.toByte(),
        0x0b.toByte(), 0x59.toByte(), 0x90.toByte(), 0xc4.toByte(), 0xc4.toByte(), 0x7c.toByte(), 0x47.toByte(), 0xf1.toByte(),
        0xf0.toByte(), 0x80.toByte(), 0x46.toByte(), 0xcb.toByte(), 0x6f.toByte(), 0x2d.toByte(), 0xe0.toByte(), 0x74.toByte(),
        0x8d.toByte(), 0x1f.toByte(), 0xa7.toByte(), 0xf7.toByte(), 0x37.toByte(), 0x90.toByte(), 0xec.toByte(), 0x9d.toByte(),
        0x8d.toByte(), 0xd6.toByte(), 0x37.toByte(), 0x21.toByte(), 0x27.toByte(), 0x78.toByte(), 0x52.toByte(), 0x88.toByte(),
        0x38.toByte(),
    )

    val known: List<ByteArray> = listOf(level0, level1)
}

/**
 * 140-byte sensor certificate. Mirrors Swift `SensorCert`.
 */
data class SensorCert(val raw: ByteArray, val staticPub: ByteArray, val signature: ByteArray) {

    /**
     * 1:1 port of Swift `SensorCert.init(raw:)` — size check, `raw[11..<76]`,
     * 0x04 prefix check, `raw[76..<140]` signature.
     */
    constructor(raw: ByteArray) : this(
        raw = raw,
        staticPub = extractStaticPub(raw),
        signature = extractSignature(raw),
    )

    init {
        if (raw.size != TOTAL_SIZE) throw SensorCertError.WrongSize(raw.size)
        if ((staticPub[0].toInt() and 0xFF) != 0x04) throw SensorCertError.NotUncompressedPoint
    }

    /** The 10-byte header region `raw[0..<11]` (Swift `header`). */
    val header: ByteArray
        get() = raw.copyOfRange(0, HEADER_RANGE_END)

    /** The signed payload `raw[0..<76]` (Swift `signedPayload`). */
    val signedPayload: ByteArray
        get() = raw.copyOfRange(0, SIGNED_PAYLOAD_RANGE_END)

    /**
     * Verify the cert's raw r||s signature over `raw[0..<76]` as
     * ECDSA-P256(SHA-256) under [signingPublicKey] (65B X9.63).
     * Swift `SensorCert.verifyECDSA(with:)`.
     *
     * @throws SensorCertError.InvalidSigningPublicKey when [signingPublicKey]
     *   is not a 65-byte 0x04-prefixed point.
     */
    fun verifyECDSA(signingPublicKey: ByteArray): Boolean {
        if (signingPublicKey.size != 65 || (signingPublicKey[0].toInt() and 0xFF) != 0x04) {
            throw SensorCertError.InvalidSigningPublicKey
        }
        return P256Ecdsa.verify(
            message = signedPayload,
            signature = signature,
            publicKey65 = signingPublicKey,
        )
    }

    /**
     * Find the first [signingKeys] entry that verifies this cert.
     * Swift `SensorCert.verifiedSigningKeyIndex(in:)`.
     */
    fun verifiedSigningKeyIndex(signingKeys: List<ByteArray> = Libre3PatchSigningKey.known): Int? {
        for ((index, signingKey) in signingKeys.withIndex()) {
            if (verifyECDSA(signingKey)) return index
        }
        return null
    }

    companion object {
        const val TOTAL_SIZE: Int = 140
        const val SIGNED_PAYLOAD_RANGE_END: Int = 76 // Swift `signedPayloadRange = 0..<76`
        const val HEADER_RANGE_END: Int = 11 // Swift `headerRange = 0..<11`
        const val PUBKEY_RANGE_START: Int = 11
        const val PUBKEY_RANGE_END: Int = 76 // Swift `pubkeyRange = 11..<76`
        const val SIGNATURE_RANGE_START: Int = 76
        const val SIGNATURE_RANGE_END: Int = 140 // Swift `signatureRange = 76..<140`
        const val SIGNATURE_SIZE: Int = 64
        const val STATIC_PUB_SIZE: Int = 65

        // Kept out of the constructor delegate so the size guard (and thus the
        // Swift error order) runs before the slices — same order as
        // `guard raw.count == Self.totalSize` → `raw.subdata(in: ...)`.
        private fun extractStaticPub(raw: ByteArray): ByteArray {
            if (raw.size != TOTAL_SIZE) throw SensorCertError.WrongSize(raw.size)
            return raw.copyOfRange(PUBKEY_RANGE_START, PUBKEY_RANGE_END)
        }

        private fun extractSignature(raw: ByteArray): ByteArray {
            if (raw.size != TOTAL_SIZE) throw SensorCertError.WrongSize(raw.size)
            return raw.copyOfRange(SIGNATURE_RANGE_START, SIGNATURE_RANGE_END)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorCert) return false
        return raw.contentEquals(other.raw) &&
            staticPub.contentEquals(other.staticPub) &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var h = raw.contentHashCode()
        h = 31 * h + staticPub.contentHashCode()
        h = 31 * h + signature.contentHashCode()
        return h
    }
}

/**
 * 1:1 port of Swift `SensorCertError` (same cases, so error equality matches).
 */
sealed class SensorCertError(message: String = "sensor certificate failure") : Exception(message) {

    /** `raw.count != 140` (Swift `.wrongSize`). */
    data class WrongSize(val size: Int) : SensorCertError("wrong size: $size")

    /** `raw[11] != 0x04` (Swift `.notUncompressedPoint`). */
    object NotUncompressedPoint : SensorCertError("static pubkey is not an uncompressed point")

    /** Signing key not a 65-byte 0x04-prefixed point (Swift `.invalidSigningPublicKey`). */
    object InvalidSigningPublicKey : SensorCertError("invalid signing public key")
}
