package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.util.Hex
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Correctness gate for the pure-Kotlin P-256 scalar-multiplication port.
 *
 * The golden vector is ported 1:1 from LibreCRKit
 * `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift`
 * (the `builder5bcf98P256Outputs` section): the same scalar window, the same
 * generator point, and the same 70-byte little-endian output coordinates and
 * SHA-256 digests. The ladder vectors (2 - G, 3 - G) and the random-scalar
 * vectors pin the [P256ScalarMultiplier.multiply] surface directly (the Swift
 * suite only exercises it through `builder5bcf98P256Outputs`); their
 * coordinates come from an independent standard-P-256 reference that
 * reproduces the Swift golden vector bit-for-bit.
 *
 * Byte orders: `scalarWindow` and the `*Output70` values are little-endian;
 * `generatorPoint` and the random `pointXY` are uncompressed big-endian
 * `x || y`.
 */
class P256ScalarMultiplierTest {

    // --- Swift golden vector (1:1 from FirstPairSourceSliceTests.swift) ---
    // scalarWindow: 70 bytes, little-endian scalar
    private val scalarWindow = Hex.decode(
        "3b588dd68f20da5f883993332cabcda6576645712cdd039d0a8195f4b1c0b52e" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000",
    )
    // sensorPointXYBE: uncompressed generator point (x || y, big-endian)
    private val generatorPoint = Hex.decode(
        "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
        "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",
    )
    private val expectedXOutput70 = Hex.decode(
        "a1e69a746868223565f55b036dcb352ac7ad64457d8304d2a015b5ee90942023" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000"
    )
    private val expectedYOutput70 = Hex.decode(
        "3ac85ab9f4754fade9fb79588ec4d48ef3af4d916151ad0477d595de947261ea" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000"
    )
    private val expectedXSha256 = "fb123cffe9d4e8e9e27f9c5251cdcd24a9f513c43d130a07925b8ceee0fe75d0"
    private val expectedYSha256 = "5f6979616cb8bbeb57dac5b362653508b597e8292b6bc0a3defc0787cc4737ca"

    // --- Ladder vectors: 2 - G = double(G), 3 - G = double(G) + G ---
    private val expected2GxOutput70 = Hex.decode(
        "78996647fc480ba6351bf277e26989c0c31ab5040338528a7e4f038d187bf27c" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000"
    )
    private val expected2GYOutput70 = Hex.decode(
        "d17378229db7049e2982e93ce6ad7dbadb30749fc69a3d2940d08edb10557707" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000"
    )
    private val expected3GxOutput70 = Hex.decode(
        "6cfde7c61b6641fb85a9adef21b7c6e665f14b1d95eff7c8440a33a6d1e4cb5e" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000"
    )
    private val expected3GYOutput70 = Hex.decode(
        "32507da227b1799a3db84f3836b02ad8eca2641ace064b377eff98490c643487" +
        "0000000000000000000000000000000000000000000000000000000000000000000000000000"
    )

    // --- Random vectors: scalar k (little-endian 32 bytes), point m - G,
    //     expect (k - m) - G; generated from the same reference computation ---
    private val randomVectors: List<Triple<ByteArray, ByteArray, Pair<ByteArray, ByteArray>>> = listOf(
        Triple(
            Hex.decode("ba7f69f75edf35c71fdad37066eae91d14f6ea01456b341770b835e942f549f1"),
            Hex.decode(
                "aa4265f083a9fa07c532ad8b6efb5269b489591468e12bf872edf47896277070" +
                "ef7b726fff81fe186a15b07d175255c0c44ae67b6c70e69a887c5251a6722b77",
            ),
            Pair(
                Hex.decode(
                    "e0df46e9cdae8157e0aa0449c17c340b29bd0f9f22e79fac32558b8a9143912b" +
                    "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                ),
                Hex.decode(
                    "6507bf36f5ef7e60831e7b95763b2094e58952a142938733dc31fc970d5396b4" +
                    "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                ),
            ),
        ),
        Triple(
            Hex.decode("371838c42866c95ac9909f3c222a7604606de307a79997fc4802edc9301e0e04"),
            Hex.decode(
                "5fdd437641c75c02eef99988b6706ca79dff169693aef8b5d77c56d6960b4728" +
                "4c0b1cc1ee7b33f47f9057a09f1c4422cfab7935d43d206ad0cc1d2c565b9079",
            ),
            Pair(
                Hex.decode(
                    "296e5f3005c454a06451857745b64ba0780d7850ba38f790697b7e54b632bc74" +
                    "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                ),
                Hex.decode(
                    "8b76f53669f4bcc36cbfa9381e4c7da5ce6b4cdd6513fefafce039d4403e3858" +
                    "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                ),
            ),
        ),
        Triple(
            Hex.decode("4407df2eb00ba2b6122b4a1dcfc268d8660ebe03b717ca80aebf9b7c718266e1"),
            Hex.decode(
                "1480e4eef7baf21fe2500542bf1bc8ae912f043ce629e2aeaab08eba6722ea26" +
                "3f642569ab5652f5f952c9fc16357b5b6650321b1e102fde547b8279fce0e668",
            ),
            Pair(
                Hex.decode(
                    "6d00316b9b89e93efabae766d572a79b385f1bdf473450176816b6cae2987702" +
                    "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                ),
                Hex.decode(
                    "097cc7d9c81a804347267c1b7a07127723ac08b0a4540b6894aba759b85c9280" +
                    "0000000000000000000000000000000000000000000000000000000000000000000000000000"
                ),
            ),
        )
    )

    @Test
    fun `builder5bcf98P256Outputs reproduces the Swift golden vector`() {
        val outputs = P256ScalarMultiplier.builder5bcf98P256Outputs(scalarWindow, generatorPoint)
        assertEquals(70, outputs.xOutput70.size)
        assertEquals(70, outputs.yOutput70.size)
        assertContentEquals(expectedXOutput70, outputs.xOutput70)
        assertContentEquals(expectedYOutput70, outputs.yOutput70)
        assertEquals(expectedXSha256, sha256Hex(outputs.xOutput70))
        assertEquals(expectedYSha256, sha256Hex(outputs.yOutput70))
    }

    @Test
    fun `multiply reproduces the golden 5bcf98 product coordinates`() {
        // Direct multiply() surface with the same inputs (70 LE scalar bytes,
        // generator point parsed uncompressed big-endian).
        val point = P256ScalarMultiplier.AffinePoint.fromBigEndian(
            xBE = generatorPoint.copyOfRange(0, 32),
            yBE = generatorPoint.copyOfRange(32, 64),
        )
        val product = P256ScalarMultiplier.multiply(scalarLE = scalarWindow, point = point)
        assertContentEquals(expectedXOutput70, product.x.littleEndianPadded70)
        assertContentEquals(expectedYOutput70, product.y.littleEndianPadded70)
    }

    @Test
    fun `multiply 2 times G equals 2G and 3 times G equals 3G`() {
        val point = P256ScalarMultiplier.AffinePoint.fromBigEndian(
            xBE = generatorPoint.copyOfRange(0, 32),
            yBE = generatorPoint.copyOfRange(32, 64),
        )
        val scalar2 = ByteArray(70)
        scalar2[0] = 0x02 // little-endian scalar 2
        val doubled = P256ScalarMultiplier.multiply(scalarLE = scalar2, point = point)
        assertContentEquals(expected2GxOutput70, doubled.x.littleEndianPadded70)
        assertContentEquals(expected2GYOutput70, doubled.y.littleEndianPadded70)

        val scalar3 = ByteArray(70)
        scalar3[0] = 0x03 // little-endian scalar 3
        val tripled = P256ScalarMultiplier.multiply(scalarLE = scalar3, point = point)
        assertContentEquals(expected3GxOutput70, tripled.x.littleEndianPadded70)
        assertContentEquals(expected3GYOutput70, tripled.y.littleEndianPadded70)
    }

    @Test
    fun `random scalars against curve points match reference vectors`() {
        randomVectors.forEachIndexed { index, vector ->
            val scalar = vector.first
            val pointXY = vector.second
            val expected = vector.third
            val point = P256ScalarMultiplier.AffinePoint.fromBigEndian(
                xBE = pointXY.copyOfRange(0, 32),
                yBE = pointXY.copyOfRange(32, 64),
            )
            val product = P256ScalarMultiplier.multiply(
                scalarLE = scalar.copyOf(70),
                point = point,
            )
            assertContentEquals(expected.first, product.x.littleEndianPadded70, "vector $index x")
            assertContentEquals(expected.second, product.y.littleEndianPadded70, "vector $index y")
        }
    }

    @Test
    fun `off-curve input point is rejected`() {
        // (1, 1) is not on y^2 = x^3 - 3x + b over P-256.
        val offCurve = ByteArray(64) { 0x01 }
        assertFailsWith<P256ScalarMultiplier.P256ScalarMultiplierError.InvalidPoint> {
            P256ScalarMultiplier.builder5bcf98P256Outputs(scalarWindow, offCurve)
        }
    }

    @Test
    fun `scalar window shorter than 70 bytes is rejected`() {
        assertFailsWith<P256ScalarMultiplier.P256ScalarMultiplierError.InvalidScalarLength> {
            P256ScalarMultiplier.builder5bcf98P256Outputs(ByteArray(69), generatorPoint)
        }
    }

    @Test
    fun `point buffer shorter than 64 bytes is rejected`() {
        assertFailsWith<P256ScalarMultiplier.P256ScalarMultiplierError.InvalidPointLength> {
            P256ScalarMultiplier.builder5bcf98P256Outputs(scalarWindow, ByteArray(63))
        }
    }

    @Test
    fun `all-zero scalar is rejected`() {
        // Mirrors Swift highestSetBit == nil -> .invalidP256Point.
        assertFailsWith<P256ScalarMultiplier.P256ScalarMultiplierError.InvalidPoint> {
            P256ScalarMultiplier.builder5bcf98P256Outputs(ByteArray(70), generatorPoint)
        }
    }

    @Test
    fun `coordinate longer than 32 bytes is rejected`() {
        val tooLong = ByteArray(33)
        assertFailsWith<P256ScalarMultiplier.P256ScalarMultiplierError.InvalidPointLength> {
            P256ScalarMultiplier.AffinePoint.fromBigEndian(xBE = tooLong, yBE = generatorPoint.copyOfRange(32, 64))
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
