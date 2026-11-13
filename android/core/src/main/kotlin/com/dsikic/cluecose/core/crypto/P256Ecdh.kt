package com.dsikic.cluecose.core.crypto

/**
 * P-256 ECDH on top of [P256ScalarMultiplier] — the Android stand-in for
 * CryptoKit `P256.KeyAgreement` (used by LibreCRKit `Pairing/EphemeralExchange.swift`).
 *
 * `sharedSecret(privateScalar32BE, peerPub65X963)` = the 32-byte
 * **big-endian X coordinate** of `[private] · peerPub` — exactly what CryptoKit's
 * `privateKey.sharedSecretFromKeyAgreement(with:)` returns for P-256.
 */
object P256Ecdh {

    /**
     * ECDH shared secret: the 32-byte big-endian X coordinate of the product
     * point.
     *
     * @param privateScalar32BE the 32-byte scalar, **big-endian** — the raw
     *   private-key representation (`x963Representation` /
     *   `rawRepresentation` form). Internally converted to the little-endian
     *   form [P256ScalarMultiplier.multiply] expects.
     * @param peerPub65 the peer's 65-byte X9.63 uncompressed point
     *   (`0x04 ‖ X(32) ‖ Y(32)`).
     * @throws P256ScalarMultiplier.P256ScalarMultiplierError.InvalidPoint when
     *   the scalar is zero or the product lands at infinity.
     */
    fun sharedSecret(privateScalar32BE: ByteArray, peerPub65: ByteArray): ByteArray {
        val x = peerPub65.copyOfRange(1, 33)
        val y = peerPub65.copyOfRange(33, 65)
        val point = P256ScalarMultiplier.AffinePoint.fromBigEndian(x, y)
        val scalarLE = privateScalar32BE.copyOf().also { it.reverse() }
        val product = P256ScalarMultiplier.multiply(scalarLE = scalarLE, point = point)
        return toBigEndian32(product.x)
    }

    /** 32-byte big-endian encoding of a field element (CryptoKit coordinate bytes). */
    fun toBigEndian32(field: P256ScalarMultiplier.Field): ByteArray {
        val value = field.value
        val out = ByteArray(32)
        if (value.signum() > 0) {
            val be = value.toByteArray()
            val start = (be.size - 32).coerceAtLeast(0)
            System.arraycopy(be, start, out, 32 - (be.size - start), be.size - start)
        }
        return out
    }

    /**
     * 65-byte X9.63 uncompressed encoding of a scalar's public point
     * (`0x04 ‖ X ‖ Y`) — Swift `P256.KeyAgreement.PrivateKey.publicKey.x963Representation`.
     */
    fun publicKey65FromScalar(privateScalar32BE: ByteArray): ByteArray {
        val basePoint = P256ScalarMultiplier.AffinePoint.fromBigEndian(BASE_GX, BASE_GY)
        val scalarLE = privateScalar32BE.copyOf().also { it.reverse() }
        val publicPoint = P256ScalarMultiplier.multiply(scalarLE = scalarLE, point = basePoint)
        val out = ByteArray(65)
        out[0] = 0x04
        System.arraycopy(toBigEndian32(publicPoint.x), 0, out, 1, 32)
        System.arraycopy(toBigEndian32(publicPoint.y), 0, out, 33, 32)
        return out
    }
}

// P-256 generator coordinates (hex verbatim from the NIST/SECG P-256 definition;
// the Swift reference relies on CryptoKit's internal G, so they are copied here
// — same values as the modulus/`b` constants already in P256ScalarMultiplier.kt).
private val BASE_GX: ByteArray = byteArrayOf(
    0x6b.toByte(), 0x17.toByte(), 0xd1.toByte(), 0xf2.toByte(), 0xe1.toByte(), 0x2c.toByte(), 0x42.toByte(), 0x47.toByte(),
    0xf8.toByte(), 0xbc.toByte(), 0xe6.toByte(), 0xe5.toByte(), 0x63.toByte(), 0xa4.toByte(), 0x40.toByte(), 0xf2.toByte(),
    0x77.toByte(), 0x03.toByte(), 0x7d.toByte(), 0x81.toByte(), 0x2d.toByte(), 0xeb.toByte(), 0x33.toByte(), 0xa0.toByte(),
    0xf4.toByte(), 0xa1.toByte(), 0x39.toByte(), 0x45.toByte(), 0xd8.toByte(), 0x98.toByte(), 0xc2.toByte(), 0x96.toByte(),
)

private val BASE_GY: ByteArray = byteArrayOf(
    0x4f.toByte(), 0xe3.toByte(), 0x42.toByte(), 0xe2.toByte(), 0xfe.toByte(), 0x1a.toByte(), 0x7f.toByte(), 0x9b.toByte(),
    0x8e.toByte(), 0xe7.toByte(), 0xeb.toByte(), 0x4a.toByte(), 0x7c.toByte(), 0x0f.toByte(), 0x9e.toByte(), 0x16.toByte(),
    0x2b.toByte(), 0xce.toByte(), 0x33.toByte(), 0x57.toByte(), 0x6b.toByte(), 0x31.toByte(), 0x5e.toByte(), 0xce.toByte(),
    0xcb.toByte(), 0xb6.toByte(), 0x40.toByte(), 0x68.toByte(), 0x37.toByte(), 0xbf.toByte(), 0x51.toByte(), 0xf5.toByte(),
)
