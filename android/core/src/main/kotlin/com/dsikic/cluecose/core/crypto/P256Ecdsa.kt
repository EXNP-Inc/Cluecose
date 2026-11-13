package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.crypto.P256ScalarMultiplier.AffinePoint
import java.math.BigInteger
import java.security.MessageDigest

/**
 * ECDSA-P256(SHA-256) **verification** on top of [P256ScalarMultiplier] — the
 * Android stand-in for CryptoKit `P256.Signing` (used by
 * LibreCRKit `Pairing/SensorCert.swift`).
 *
 * Standard verify (mirrors CryptoKit `isValidSignature(_:for:)`):
 *
 *   hash = SHA-256(message);  z = hash as big-endian integer (P-256: the
 *   leftmost 256 bits of the 256-bit digest, i.e. the whole digest).
 *   sInv = s⁻¹ mod n;
 *   u1 = (z·sInv) mod n,  u2 = (r·sInv) mod n;
 *   P  = u1·G + u2·Q   (u1·G and u2·Q via [P256ScalarMultiplier.multiply],
 *       then an affine addition implemented here in `BigInteger` mod p).
 *   valid iff P is not the point at infinity and (P.x mod n) == r.
 *
 * The raw signature is `r ‖ s`, 64 bytes, each a 32-byte big-endian integer
 * (CryptoKit's `ECDSASignature(rawRepresentation:)` format).
 */
object P256Ecdsa {

    /** P-256 group order n. */
    private val ORDER: BigInteger = BigInteger(
        "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16,
    )

    /** P-256 generator G (NIST/SECG P-256). */
    private val G: AffinePoint = AffinePoint.fromBigEndian(BASE_GX, BASE_GY)

    /** P-256 base field prime p (== [P256ScalarMultiplier.Field.modulus]). */
    private val P: BigInteger = P256ScalarMultiplier.Field.modulus.value

    /**
     * Verify an ECDSA-P256(SHA-256) signature over [message] under the 65B
     * X9.63 uncompressed [publicKey65].
     *
     * @param message the signed bytes (e.g. the sensor cert `raw[0..<76]`).
     * @param signature raw `r ‖ s` (64 bytes).
     * @param publicKey65 65-byte uncompressed P-256 point `0x04 ‖ X ‖ Y`.
     * @return true when the signature is valid (CryptoKit `isValidSignature`).
     *   Returns false for out-of-range r/s; throws [P256ScalarMultiplierError]
     *   only if the public key is off the curve (CryptoKit's `PublicKey`
     *   constructor rejects those too).
     */
    fun verify(message: ByteArray, signature: ByteArray, publicKey65: ByteArray): Boolean {
        if (signature.size != 64) return false
        val r = BigInteger(1, signature.copyOfRange(0, 32))
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        if (r.signum() <= 0 || r >= ORDER) return false
        if (s.signum() <= 0 || s >= ORDER) return false

        val publicKey = AffinePoint.fromBigEndian(
            xBE = publicKey65.copyOfRange(1, 33),
            yBE = publicKey65.copyOfRange(33, 65),
        )

        val digest = MessageDigest.getInstance("SHA-256").digest(message)
        val z = BigInteger(1, digest)

        val sInv = s.modInverse(ORDER)
        val u1 = z.multiply(sInv).mod(ORDER)
        val u2 = r.multiply(sInv).mod(ORDER)

        val p1 = if (u1.signum() != 0) toPt(scalarMultiply(u1, G)) else null
        val p2 = if (u2.signum() != 0) toPt(scalarMultiply(u2, publicKey)) else null
        val point = addPts(p1, p2) ?: return false

        return point.x.mod(ORDER) == r
    }

    // MARK: - Internals

    private fun scalarMultiply(scalar: BigInteger, point: AffinePoint): AffinePoint =
        P256ScalarMultiplier.multiply(scalarLE = toLittleEndian32(scalar), point = point)

    /** Affine point as raw `BigInteger` field values (for mod-p addition). */
    private class Pt(val x: BigInteger, val y: BigInteger)

    private fun toPt(p: AffinePoint): Pt = Pt(p.x.value, p.y.value)

    /**
     * Affine point addition mod p. Returns null for the point at infinity.
     * Handles the x1 == x2 cases (doubling or negate-to-infinity) verbatim.
     */
    private fun addPts(a: Pt?, b: Pt?): Pt? {
        if (a == null) return b
        if (b == null) return a
        if (a.x == b.x) {
            if (a.y.add(b.y).mod(P).signum() == 0) return null // y2 == -y1
            return doublePt(a)
        }
        val lambda = b.y.subtract(a.y)
            .multiply(b.x.subtract(a.x).modInverse(P))
            .mod(P)
        val x3 = lambda.multiply(lambda).subtract(a.x).subtract(b.x).mod(P)
        val y3 = lambda.multiply(a.x.subtract(x3)).subtract(a.y).mod(P)
        return Pt(x3, y3)
    }

    /** Affine point doubling mod p (a = −3). */
    private fun doublePt(a: Pt): Pt {
        val num = a.x.multiply(a.x).multiply(BigInteger.valueOf(3))
            .subtract(BigInteger.valueOf(3))
            .mod(P)
        val lambda = num.multiply(a.y.add(a.y).modInverse(P)).mod(P)
        val x3 = lambda.multiply(lambda).subtract(a.x.add(a.x)).mod(P)
        val y3 = lambda.multiply(a.x.subtract(x3)).subtract(a.y).mod(P)
        return Pt(x3, y3)
    }

    private fun toLittleEndian32(v: BigInteger): ByteArray = toBigEndian32(v).also { it.reverse() }

    private fun toBigEndian32(v: BigInteger): ByteArray {
        val out = ByteArray(32)
        if (v.signum() > 0) {
            val be = v.toByteArray()
            val start = (be.size - 32).coerceAtLeast(0)
            System.arraycopy(be, start, out, 32 - (be.size - start), be.size - start)
        }
        return out
    }
}

// P-256 generator coordinates (NIST/SECG P-256; the Swift reference relies on
// CryptoKit's internal G, so the values are copied here — consistent with the
// modulus/`b` constants already in P256ScalarMultiplier.kt).
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
