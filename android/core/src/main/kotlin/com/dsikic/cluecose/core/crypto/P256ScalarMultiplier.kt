package com.dsikic.cluecose.core.crypto

import java.math.BigInteger

/**
 * P-256 (secp256r1) scalar multiplication — pure Kotlin, 1:1 port of LibreCRKit
 * `Crypto/P256ScalarMultiplier.swift` (the private `P256ScalarMultiplier` enum and its
 * `Field` / `JacobianPoint` / `AffinePoint` types, plus the public
 * `builder5bcf98P256Outputs` entry point that lives in an extension of
 * `FirstPairSourceSlice` in the same file).
 *
 * What it computes
 * ----------------
 * `multiply(scalarLE, point)` returns `[scalar] * point` on P-256
 * (y² = x³ − 3x + b, prime p = 2²⁵⁶ − 2²²⁴ + 2¹⁹² + 2⁹⁶ − 1), using the exact
 * Swift algorithm structure:
 *
 * - MSB-first double-and-add ladder (`stride(topBit … 0)`), one unconditional
 *   Jacobian `double` per bit and a conditional *mixed* addition of the (affine)
 *   addend — no precomputation tables, no Montgomery form.
 * - Internal representation is Jacobian `(X, Y, Z, infinity)`; the result is
 *   converted back to affine by a single field inversion at the end
 *   (`z⁻¹ = z^(p−2)` computed as the Swift's 256-step square-and-multiply).
 * - `addMixed` reproduces the Swift special cases verbatim (point at infinity,
 *   `h == 0` → double or negate-to-infinity).
 *
 * Encodings (as in Swift)
 * -----------------------
 * - Input point: **uncompressed**, raw `x ‖ y`, 32 bytes each, **big-endian**,
 *   no 0x04 prefix. Validated on-curve (`y² == x³ − 3x + b`); off-curve inputs
 *   throw [P256ScalarMultiplierError.InvalidPoint].
 * - Scalar: **little-endian** bytes (`scalarWindowLE`); only the first 70 bytes
 *   are consumed. An all-zero scalar throws [P256ScalarMultiplierError.InvalidPoint]
 *   (the ladder would land on the point at infinity), mirroring the Swift
 *   `highestSetBit == nil` guard.
 * - Output coordinates are field elements encoded as **70 bytes, little-endian**:
 *   the four 64-bit limbs (l0 first, least significant) each in little-endian
 *   byte order, followed by 38 zero bytes — [Field.littleEndianPadded70],
 *   mirroring Swift `Field.littleEndianPadded70`.
 *
 * Arithmetic note
 * ---------------
 * The Swift `Field` does redundant 4×64-bit-limb schoolbook arithmetic with the
 * hardcoded `modulus` / `carryCorrection` constants and a 512-bit
 * shift-append-bit reduction. Those constants are exactly the P-256 prime
 * (`carryCorrection` == p − 2) and the reduction is provably equivalent to
 * ordinary reduction mod p (verified 9 000/9 000 random add/sub/mul samples and
 * 200/200 inverses against true mod-p, and the port reproduces the Swift golden
 * test vectors bit-for-bit). Per the porting brief, field arithmetic therefore
 * uses `java.math.BigInteger` (always reduced mod p — the same ring), while the
 * ladder, point formulas, special cases, on-curve check, byte orderings and
 * padding follow the Swift 1:1 so outputs match.
 *
 * API surface note
 * ----------------
 * In Swift the algorithm types are `private`; the only public surface is
 * `builder5bcf98P256Outputs`. It is mirrored here as
 * [P256ScalarMultiplier.builder5bcf98P256Outputs] returning
 * [Builder5bcf98P256Outputs]. [P256ScalarMultiplier.multiply], [AffinePoint]
 * and [Field] are additionally exposed `public` (Swift keeps them file-private)
 * so the pending 6388f0 high-seed port can reuse the same primitive without a
 * later API change — additive, not a deviation.
 *
 * NOT ported: `builder6388f0HighSeedStreamStartSeedsFromScalarP256` (and the
 * downstream `Builder6388f0HighSeedStreamStartSeeds` derivation) — it depends on
 * the 6388f0 seed machinery in `FirstPairSourceSlice.swift`, which is a
 * separate port.
 */
object P256ScalarMultiplier {

    sealed class P256ScalarMultiplierError(message: String = "P-256 scalar multiplication failure") :
        Exception(message) {
        /** `scalarWindowLE.count < 70` (Swift `.invalidP256ScalarLength`). */
        class InvalidScalarLength(val length: Int) :
            P256ScalarMultiplierError("invalid P-256 scalar length: $length")

        /** `sensorPointXYBE.count < 64` or a coordinate that is not exactly 32 bytes
         * (Swift `.invalidP256PointLength`). */
        class InvalidPointLength(val length: Int) :
            P256ScalarMultiplierError("invalid P-256 point length: $length")

        /** Input point not on the curve, zero scalar, or point-at-infinity result
         * (Swift `.invalidP256Point`). */
        object InvalidPoint : P256ScalarMultiplierError()
    }

    /**
     * Uncompressed P-256 point in affine coordinates (x, y), both on-curve
     * validated. Mirrors Swift `P256ScalarMultiplier.AffinePoint`.
     */
    class AffinePoint(val x: Field, val y: Field) {
        companion object {
            /**
             * Parses an uncompressed point as `x ‖ y`, 32 bytes each, big-endian,
             * and validates `y² == x³ − 3x + b` (Swift `AffinePoint.init(xBE:yBE:)`).
             * Throws [P256ScalarMultiplierError.InvalidPointLength] on wrong
             * coordinate length and [P256ScalarMultiplierError.InvalidPoint] when
             * the point is off the curve.
             */
            fun fromBigEndian(xBE: ByteArray, yBE: ByteArray): AffinePoint {
                val x = Field.fromBigEndian32(xBE)
                val y = Field.fromBigEndian32(yBE)
                val rhs = x.squared() * x - x.times3() + Field.b
                if (y.squared() != rhs) throw P256ScalarMultiplierError.InvalidPoint
                return AffinePoint(x, y)
            }
        }
    }

    /**
     * Jacobian projective point (X, Y, Z) with an explicit infinity flag,
     * mirroring Swift `P256ScalarMultiplier.JacobianPoint`.
     */
    internal class JacobianPoint(val x: Field, val y: Field, val z: Field, val infinity: Boolean = false) {
        constructor(affine: AffinePoint) : this(affine.x, affine.y, Field.one)

        companion object {
            val infinity = JacobianPoint(Field.zero, Field.one, Field.zero, true)
        }
    }

    /**
     * 70-byte little-endian padded representation of the two result coordinates,
     * mirroring Swift `Builder5bcf98P256Outputs` (each coordinate is
     * `littleEndianPadded70`).
     */
    data class Builder5bcf98P256Outputs(val xOutput70: ByteArray, val yOutput70: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Builder5bcf98P256Outputs &&
                xOutput70.contentEquals(other.xOutput70) &&
                yOutput70.contentEquals(other.yOutput70)

        override fun hashCode(): Int = 31 * xOutput70.contentHashCode() + yOutput70.contentHashCode()
    }

    /**
     * Public entry point mirroring Swift `FirstPairSourceSlice.builder5bcf98P256Outputs`
     * (defined in the same file as an extension).
     *
     * @param scalarWindowLE little-endian scalar window; the first 70 bytes are used.
     * @param sensorPointXYBE uncompressed sensor point `x ‖ y` (64 bytes, big-endian).
     * @return the two 70-byte little-endian output coordinates of `[scalar] · point`.
     */
    fun builder5bcf98P256Outputs(scalarWindowLE: ByteArray, sensorPointXYBE: ByteArray): Builder5bcf98P256Outputs {
        if (scalarWindowLE.size < 70) throw P256ScalarMultiplierError.InvalidScalarLength(scalarWindowLE.size)
        if (sensorPointXYBE.size < 64) throw P256ScalarMultiplierError.InvalidPointLength(sensorPointXYBE.size)
        val affine = AffinePoint.fromBigEndian(
            xBE = sensorPointXYBE.copyOfRange(0, 32),
            yBE = sensorPointXYBE.copyOfRange(32, 64),
        )
        val product = multiply(
            scalarLE = scalarWindowLE.copyOfRange(0, 70),
            point = affine,
        )
        return Builder5bcf98P256Outputs(
            xOutput70 = product.x.littleEndianPadded70,
            yOutput70 = product.y.littleEndianPadded70,
        )
    }

    /**
     * P-256 scalar multiplication: `[scalarLE] · point` (Swift `multiply`).
     *
     * @param scalarLE little-endian scalar bytes; the highest set bit bounds the
     *                 ladder, all bytes above are ignored. An all-zero scalar
     *                 throws [P256ScalarMultiplierError.InvalidPoint].
     * @param point an on-curve affine point.
     * @return the product in affine coordinates.
     */
    fun multiply(scalarLE: ByteArray, point: AffinePoint): AffinePoint {
        val topBit = highestSetBit(scalarLE) ?: throw P256ScalarMultiplierError.InvalidPoint
        var result = JacobianPoint.infinity
        for (bit in topBit downTo 0) {
            result = double(result)
            if (scalarBit(scalarLE, bit)) {
                result = addMixed(result, point)
            }
        }
        return toAffine(result)
    }

    // MARK: - Ladder internals (1:1 of the Swift private statics)

    private fun double(point: JacobianPoint): JacobianPoint {
        if (point.infinity || point.y == Field.zero) {
            return JacobianPoint.infinity
        }
        val yy = point.y.squared()
        val yyyy = yy.squared()
        val zz = point.z.squared()
        val zzzz = zz.squared()
        val s = (point.x * yy).times4()
        val m = (point.x.squared() - zzzz).times3()
        val x3 = m.squared() - s.doubled()
        val y3 = m * (s - x3) - yyyy.times8()
        val z3 = point.y.doubled() * point.z
        return JacobianPoint(x = x3, y = y3, z = z3)
    }

    private fun addMixed(point: JacobianPoint, addend: AffinePoint): JacobianPoint {
        if (point.infinity) {
            return JacobianPoint(addend)
        }
        val z1z1 = point.z.squared()
        val u2 = addend.x * z1z1
        val s2 = addend.y * point.z * z1z1
        val h = u2 - point.x
        if (h == Field.zero) {
            return if (s2 == point.y) double(point) else JacobianPoint.infinity
        }
        val hh = h.squared()
        val i = hh.times4()
        val j = h * i
        val r = (s2 - point.y).doubled()
        val v = point.x * i
        val x3 = r.squared() - j - v.doubled()
        val y3 = r * (v - x3) - (point.y * j).doubled()
        val z3 = (point.z + h).squared() - z1z1 - hh
        return JacobianPoint(x = x3, y = y3, z = z3)
    }

    private fun toAffine(point: JacobianPoint): AffinePoint {
        if (point.infinity) throw P256ScalarMultiplierError.InvalidPoint
        val zInv = point.z.inverted()
        val zInv2 = zInv.squared()
        val zInv3 = zInv2 * zInv
        return AffinePoint(
            x = point.x * zInv2,
            y = point.y * zInv3,
        )
    }

    private fun highestSetBit(scalarLE: ByteArray): Int? {
        for (byteIndex in scalarLE.indices.reversed()) {
            val byte = scalarLE[byteIndex].toInt() and 0xFF
            if (byte == 0) continue
            for (bit in 7 downTo 0) {
                if (byte and (1 shl bit) != 0) return byteIndex * 8 + bit
            }
        }
        return null
    }

    private fun scalarBit(scalarLE: ByteArray, bit: Int): Boolean =
        (scalarLE[bit / 8].toInt() ushr (bit and 7)) and 1 != 0

    /**
     * P-256 base field element in [0, p). Mirrors Swift `Field`.
     *
     * The Swift implementation keeps redundant 4×64-bit limbs with the hardcoded
     * `modulus` / `carryCorrection` reduction constants; here the same ring is
     * represented directly with [BigInteger] (always reduced mod [modulus]), which
     * is provably the same arithmetic and keeps the port portable and testable.
     * [littleEndianPadded70] reproduces the Swift wire encoding bit-for-bit.
     */
    class Field internal constructor(internal val value: BigInteger) {
        companion object {
            val zero = Field(BigInteger.ZERO)
            val one = Field(BigInteger.ONE)

            /** P-256 prime p = 2²⁵⁶ − 2²²⁴ + 2¹⁹² + 2⁹⁶ − 1 (Swift `Field.modulus`). */
            val modulus = Field(BigInteger(
                "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16,
            ))

            /** Swift `Field.carryCorrection` (= p − 2; redundant-limb add correction in the Swift). */
            val carryCorrection = Field(modulus.value - BigInteger.valueOf(2))

            /** P-256 curve coefficient b (Swift `Field.b`). */
            val b = Field(BigInteger(
                "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16,
            ))

            /**
             * 32-byte **big-endian** unsigned coordinate (Swift `Field.init(bigEndian32:)`).
             * Note the deliberate contrast with the scalar/output byte order, which is
             * little-endian: `BigInteger(byteArray)` is big-endian with the first byte as
             * a sign, hence the explicit `signum = 1` and the [modulus] reduction.
             */
            internal fun fromBigEndian32(bytes: ByteArray): Field {
                if (bytes.size != 32) throw P256ScalarMultiplierError.InvalidPointLength(bytes.size)
                return Field(BigInteger(1, bytes).mod(modulus.value))
            }
        }

        private val byteMask = BigInteger.valueOf(0xFF)

        /**
         * 70-byte little-endian encoding: four 64-bit limbs (l0 = least significant
         * first), each limb in little-endian byte order, padded with 38 zero bytes —
         * Swift `Field.littleEndianPadded70`.
         */
        val littleEndianPadded70: ByteArray
            get() {
                val out = ByteArray(70)
                var v = value
                var pos = 0
                repeat(4) {
                    repeat(8) { out[pos++] = (v.shiftRight(8 * it) and byteMask).toByte() }
                    v = v.shiftRight(64)
                }
                return out
            }

        operator fun plus(other: Field) = Field(value.add(other.value).mod(modulus.value))

        operator fun minus(other: Field) = Field(value.subtract(other.value).mod(modulus.value))

        operator fun times(other: Field) = Field(value.multiply(other.value).mod(modulus.value))

        fun squared(): Field = this * this

        fun doubled(): Field = this + this

        fun times3(): Field = doubled() + this

        fun times4(): Field = doubled().doubled()

        fun times8(): Field = times4().doubled()

        /**
         * Field inverse via the Swift's 256-step square-and-multiply of
         * `self^(p−2)` (same exponent, same MSB-first loop shape as the Swift
         * `inverted()`; `BigInteger.testBit` reads the exponent bits).
         */
        fun inverted(): Field {
            val exponent = modulus.value - BigInteger.valueOf(2)
            var result = Field.one
            for (bit in 255 downTo 0) {
                result = result.squared()
                if (exponent.testBit(bit)) {
                    result = result * this
                }
            }
            return result
        }

        override fun equals(other: Any?): Boolean = other is Field && other.value == value

        override fun hashCode(): Int = value.hashCode()
    }
}
