package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.Builder633fa8NullScalarResult
import com.dsikic.cluecose.core.crypto.Builder6388f0FirstPairStreamSeeds
import com.dsikic.cluecose.core.crypto.bundled6388f0LowSeedEntrySource
import com.dsikic.cluecose.core.crypto.builder633fa8NullScalarWindowFromEntropy
import com.dsikic.cluecose.core.crypto.builder633fa8NullScalarWindowFromEntropySource
import com.dsikic.cluecose.core.crypto.builder633fa8StaticScalarWindowFromEntrySource
import com.dsikic.cluecose.core.crypto.builder6388f0FirstPairStreamSeedsFromEntropySourceAndSensorPoints
import com.dsikic.cluecose.core.crypto.builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints
import com.dsikic.cluecose.core.crypto.builderProcess2P5PublicKey65FromEntropy
import com.dsikic.cluecose.core.crypto.deriveFrom6388f0FirstPairStreamSeeds

/**
 * First-pair Phase 5 key surfaces.
 * 1:1 port of LibreCRKit `Pairing/SessionKey.swift`.
 *
 * Ground truth (2026-05-06):
 *
 * - [Phase5KeySchedule.deriveRawKey] maps a 66-byte, 3-bit-per-byte source
 *   into the raw Phase 5 wire key.
 *
 * - The current clean-room first-pair source path composes the accepted null
 *   entropy, the candidate invariant entry source, the Phase 4 sensor
 *   ephemeral point, and the sensor certificate's static point.
 *
 * `derive` remains only as an older ECDH-only injection point used by tests
 * and capture-era scaffolding. It deliberately still throws
 * [SessionKeyError.NotYetSpecified] rather than implying that ECDH alone
 * derives the Phase 5 raw key.
 *
 * **Documented deviation from Swift:** the iOS type is an `enum` with static
 * functions; the Android [Phase5KeySchedule] is an *instance* class (it loads
 * its two runtime tables lazily from an injected [TablesSource]). So this port
 * is a `class SessionKey(private val phase5: Phase5KeySchedule)` and every
 * Swift static function is an instance function taking that same parameter.
 * Call sites build it as `SessionKey(Phase5KeySchedule(tablesSource))` once
 * per handshake — `deriveRawKey` is the only table-backed call and its tables
 * are cached lazily inside the schedule.
 *
 * Swift `entropySource: (Int) throws -> Data` maps to a plain Kotlin
 * `(Int) -> ByteArray` (no checked throws on this platform).
 */

/**
 * ECDH-only injection-point inputs. 1:1 port of Swift `SessionKeyInputs`.
 *
 * @property sharedEphStatic ECDH(phone_eph_priv, sensor_static_pub).
 * @property sharedEphEph ECDH(phone_eph_priv, sensor_eph_pub).
 * @property k1 Static keys from the lib (present in every kAuth blob).
 * @property k2 Static keys from the lib (present in every kAuth blob).
 * @property extra Optional protocol-side material (per-session nonce, sensor R1
 *   challenge, etc.) — caller may pack any extra material here.
 */
data class SessionKeyInputs(
    val sharedEphStatic: ByteArray,
    val sharedEphEph: ByteArray,
    val k1: ByteArray,
    val k2: ByteArray,
    val extra: ByteArray = byteArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SessionKeyInputs) return false
        return sharedEphStatic.contentEquals(other.sharedEphStatic) &&
            sharedEphEph.contentEquals(other.sharedEphEph) &&
            k1.contentEquals(other.k1) &&
            k2.contentEquals(other.k2) &&
            extra.contentEquals(other.extra)
    }

    override fun hashCode(): Int {
        var h = sharedEphStatic.contentHashCode()
        h = 31 * h + sharedEphEph.contentHashCode()
        h = 31 * h + k1.contentHashCode()
        h = 31 * h + k2.contentHashCode()
        h = 31 * h + extra.contentHashCode()
        return h
    }
}

/**
 * Clean-room first-pair Phase 5 source-builder inputs. 1:1 port of Swift
 * `FirstPairPhase5KeyInputs`.
 *
 * @property entrySource Candidate invariant 532-byte entry source consumed by
 *   the row-0 low-seed path.
 * @property nullEntropy11A Accepted 282-byte entropy block for the null scalar
 *   branch.
 * @property sensorEphemeralPub65 Sensor ephemeral P-256 point from Phase 4,
 *   encoded as `04 || X || Y`.
 * @property sensorStaticPub65 Sensor static P-256 point from the certificate,
 *   encoded as `04 || X || Y`.
 * @property staticScalarWindow Optional native 70-byte static-scalar window
 *   for cert families whose Phase 5 static branch is not the
 *   bundled entry-source-derived default.
 */
data class FirstPairPhase5KeyInputs(
    val entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
    val nullEntropy11A: ByteArray,
    val sensorEphemeralPub65: ByteArray,
    val sensorStaticPub65: ByteArray,
    val staticScalarWindow: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstPairPhase5KeyInputs) return false
        return entrySource.contentEquals(other.entrySource) &&
            nullEntropy11A.contentEquals(other.nullEntropy11A) &&
            sensorEphemeralPub65.contentEquals(other.sensorEphemeralPub65) &&
            sensorStaticPub65.contentEquals(other.sensorStaticPub65) &&
            (if (staticScalarWindow == null) other.staticScalarWindow == null
             else other.staticScalarWindow != null && staticScalarWindow.contentEquals(other.staticScalarWindow))
    }

    override fun hashCode(): Int {
        var h = entrySource.contentHashCode()
        h = 31 * h + nullEntropy11A.contentHashCode()
        h = 31 * h + sensorEphemeralPub65.contentHashCode()
        h = 31 * h + sensorStaticPub65.contentHashCode()
        h = 31 * h + (staticScalarWindow?.contentHashCode() ?: 0)
        return h
    }
}

/**
 * The derived Phase 5 material. 1:1 port of Swift `FirstPairPhase5KeyMaterial`.
 *
 * @property source66 The 66-byte 3-bit-per-byte Phase 5 source.
 * @property rawKey The 16-byte raw Phase 5 wire key.
 * @property nullEntropy11A The accepted null-branch entropy block.
 * @property nullScalarWindow The native 70-byte null scalar window.
 * @property nullAttempts Entropy-sampling attempts consumed.
 */
data class FirstPairPhase5KeyMaterial(
    val source66: ByteArray,
    val rawKey: ByteArray,
    val nullEntropy11A: ByteArray,
    val nullScalarWindow: ByteArray,
    val nullAttempts: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstPairPhase5KeyMaterial) return false
        return source66.contentEquals(other.source66) &&
            rawKey.contentEquals(other.rawKey) &&
            nullEntropy11A.contentEquals(other.nullEntropy11A) &&
            nullScalarWindow.contentEquals(other.nullScalarWindow) &&
            nullAttempts == other.nullAttempts
    }

    override fun hashCode(): Int {
        var h = source66.contentHashCode()
        h = 31 * h + rawKey.contentHashCode()
        h = 31 * h + nullEntropy11A.contentHashCode()
        h = 31 * h + nullScalarWindow.contentHashCode()
        h = 31 * h + nullAttempts
        return h
    }
}

/**
 * Phase 3 phone material in the native first-pair style. 1:1 port of Swift
 * `FirstPairNativeEphemeralMaterial`.
 *
 * @property keyPair The native ephemeral keypair (null-branch scalar window +
 *   `process2(5)` public point).
 * @property nullEntropy11A The accepted null-branch entropy block.
 * @property nullScalarWindow The native 70-byte null scalar window.
 * @property attempts Entropy-sampling attempts consumed.
 */
data class FirstPairNativeEphemeralMaterial(
    val keyPair: EphemeralKeyPair,
    val nullEntropy11A: ByteArray,
    val nullScalarWindow: ByteArray,
    val attempts: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstPairNativeEphemeralMaterial) return false
        return keyPair == other.keyPair &&
            nullEntropy11A.contentEquals(other.nullEntropy11A) &&
            nullScalarWindow.contentEquals(other.nullScalarWindow) &&
            attempts == other.attempts
    }

    override fun hashCode(): Int {
        var h = keyPair.hashCode()
        h = 31 * h + nullEntropy11A.contentHashCode()
        h = 31 * h + nullScalarWindow.contentHashCode()
        h = 31 * h + attempts
        return h
    }
}

/**
 * 1:1 port of Swift `SessionKeyError` (same cases, so error equality matches).
 */
sealed class SessionKeyError(message: String = "session key failure") : Exception(message) {

    /**
     * Returned by [SessionKey.derive] until the empirical construction is
     * pinned down (Swift `.notYetSpecified`).
     */
    object NotYetSpecified : SessionKeyError("session key derivation not yet specified")

    /**
     * A sensor point was not a 65-byte `04 || X || Y` encoding (Swift
     * `.invalidSensorPointEncoding`).
     */
    data class InvalidSensorPointEncoding(
        val label: String,
        val count: Int,
        /** The first byte of [point65], or null when the input was empty. */
        val prefix: Int?,
    ) : SessionKeyError(
            "invalid sensor point encoding for $label: count=$count prefix=${prefix?.let { "%02x".format(it) }}",
        ) {
        override fun toString() =
            "invalidSensorPointEncoding(label=$label, count=$count, prefix=$prefix)"
    }
}

/**
 * Phase 5 key derivation for first-pair (see the class-level deviation note:
 * the iOS static `enum SessionKey` is an instance class here because the
 * Android [Phase5KeySchedule] is table-backed).
 */
class SessionKey(private val phase5: Phase5KeySchedule) {

    /**
     * Generate the Phase 3 phone material in the same way native first-pair
     * does: sample the accepted null-branch entropy, derive the native null
     * scalar for Phase 5, and build the `process2(5)` fixed-point public
     * point derived from that same entropy.
     *
     * 1:1 port of Swift `SessionKey.makeFirstPairNativeEphemeral`.
     */
    fun makeFirstPairNativeEphemeral(
        maxAttempts: Int = 64,
        entropySource: (Int) -> ByteArray,
    ): FirstPairNativeEphemeralMaterial {
        val result: Builder633fa8NullScalarResult = builder633fa8NullScalarWindowFromEntropySource(
            maxAttempts = maxAttempts,
            entropySource = entropySource,
        )
        val publicKey65 = builderProcess2P5PublicKey65FromEntropy(result.entropy11A)
        val keyPair = EphemeralKeyPair.fromNativeScalarWindow(
            nativeScalarWindowLE = result.scalarWindow,
            publicKey65Override = publicKey65,
        )
        return FirstPairNativeEphemeralMaterial(
            keyPair = keyPair,
            nullEntropy11A = result.entropy11A,
            nullScalarWindow = result.scalarWindow,
            attempts = result.attempts,
        )
    }

    /**
     * Derive the 16-byte session key from the Phase 1-4 ECDH outputs + any
     * protocol-side material. Currently throws [SessionKeyError.NotYetSpecified]
     * pending empirical confirmation of the construction.
     *
     * Callers should treat this as an injection point: tests can pass a
     * pre-computed key around [derive] until the real derivation lands.
     *
     * 1:1 port of Swift `SessionKey.derive(_:)`.
     */
    fun derive(inputs: SessionKeyInputs): ByteArray {
        throw SessionKeyError.NotYetSpecified
    }

    /**
     * Derive the 66-byte Phase 5 source for first-pair from the clean-room
     * source builder. Row 0 uses the Phase 4 sensor ephemeral point; row 59
     * uses the sensor certificate's static public point.
     *
     * 1:1 port of Swift `SessionKey.deriveFirstPairPhase5Source(_:)`.
     */
    fun deriveFirstPairPhase5Source(inputs: FirstPairPhase5KeyInputs): ByteArray =
        deriveFirstPairPhase5Material(inputs).source66

    /**
     * 1:1 port of Swift `SessionKey.deriveFirstPairPhase5Material(_:)`.
     */
    fun deriveFirstPairPhase5Material(inputs: FirstPairPhase5KeyInputs): FirstPairPhase5KeyMaterial {
        val row0Point = uncompressedPointXYBE(inputs.sensorEphemeralPub65, label = "sensor ephemeral")
        val row59Point = uncompressedPointXYBE(inputs.sensorStaticPub65, label = "sensor static")
        val nullScalar = builder633fa8NullScalarWindowFromEntropy(inputs.nullEntropy11A)
        val staticScalar: ByteArray =
            inputs.staticScalarWindow ?: builder633fa8StaticScalarWindowFromEntrySource(inputs.entrySource)
        val seeds = builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
            entrySource = inputs.entrySource,
            nullScalarWindow = nullScalar,
            staticScalarWindow = staticScalar,
            row0SensorPointXYBE = row0Point,
            row59SensorPointXYBE = row59Point,
            nullEntropy11A = inputs.nullEntropy11A,
            nullAttempts = 1,
        )
        return materialFrom(seeds)
    }

    /**
     * Preamble overload. 1:1 port of Swift
     * `SessionKey.deriveFirstPairPhase5Source(preamble:nullEntropy11A:entrySource:staticScalarWindow:)`.
     */
    fun deriveFirstPairPhase5Source(
        preamble: FirstPairPreambleResult,
        nullEntropy11A: ByteArray,
        entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
        staticScalarWindow: ByteArray? = null,
    ): ByteArray {
        return deriveFirstPairPhase5Source(
            FirstPairPhase5KeyInputs(
                entrySource = entrySource,
                nullEntropy11A = nullEntropy11A,
                sensorEphemeralPub65 = preamble.phaseHandshake.sensorEphPub,
                sensorStaticPub65 = preamble.phaseHandshake.sensorCert.staticPub,
                staticScalarWindow = staticScalarWindow
                    ?: preamble.phaseHandshake.phoneCert.phase5StaticScalarWindowOverride,
            ),
        )
    }

    /**
     * 1:1 port of Swift `SessionKey.deriveFirstPairPhase5RawKey(_:)`.
     */
    fun deriveFirstPairPhase5RawKey(inputs: FirstPairPhase5KeyInputs): ByteArray =
        deriveFirstPairPhase5Material(inputs).rawKey

    /**
     * Preamble overload. 1:1 port of Swift
     * `SessionKey.deriveFirstPairPhase5RawKey(preamble:nullEntropy11A:entrySource:staticScalarWindow:)`.
     */
    fun deriveFirstPairPhase5RawKey(
        preamble: FirstPairPreambleResult,
        nullEntropy11A: ByteArray,
        entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
        staticScalarWindow: ByteArray? = null,
    ): ByteArray {
        val source = deriveFirstPairPhase5Source(
            preamble = preamble,
            nullEntropy11A = nullEntropy11A,
            entrySource = entrySource,
            staticScalarWindow = staticScalarWindow,
        )
        return phase5.deriveRawKey(source)
    }

    /**
     * Preamble overload. 1:1 port of Swift
     * `SessionKey.deriveFirstPairPhase5Material(preamble:nullEntropy11A:entrySource:staticScalarWindow:)`.
     */
    fun deriveFirstPairPhase5Material(
        preamble: FirstPairPreambleResult,
        nullEntropy11A: ByteArray,
        entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
        staticScalarWindow: ByteArray? = null,
    ): FirstPairPhase5KeyMaterial {
        return deriveFirstPairPhase5Material(
            FirstPairPhase5KeyInputs(
                entrySource = entrySource,
                nullEntropy11A = nullEntropy11A,
                sensorEphemeralPub65 = preamble.phaseHandshake.sensorEphPub,
                sensorStaticPub65 = preamble.phaseHandshake.sensorCert.staticPub,
                staticScalarWindow = staticScalarWindow
                    ?: preamble.phaseHandshake.phoneCert.phase5StaticScalarWindowOverride,
            ),
        )
    }

    /**
     * Fresh-entropy overload: samples the null-branch entropy through
     * [entropySource] (or reuses [staticScalarWindow] when supplied). 1:1 port
     * of Swift `SessionKey.deriveFirstPairPhase5Material(entrySource:
     * sensorEphemeralPub65:sensorStaticPub65:staticScalarWindow:maxAttempts:
     * entropySource:)`.
     */
    fun deriveFirstPairPhase5Material(
        entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
        sensorEphemeralPub65: ByteArray,
        sensorStaticPub65: ByteArray,
        staticScalarWindow: ByteArray? = null,
        maxAttempts: Int = 64,
        entropySource: (Int) -> ByteArray,
    ): FirstPairPhase5KeyMaterial {
        val row0Point = uncompressedPointXYBE(sensorEphemeralPub65, label = "sensor ephemeral")
        val row59Point = uncompressedPointXYBE(sensorStaticPub65, label = "sensor static")
        val seeds: Builder6388f0FirstPairStreamSeeds
        if (staticScalarWindow != null) {
            val nullResult = builder633fa8NullScalarWindowFromEntropySource(
                maxAttempts = maxAttempts,
                entropySource = entropySource,
            )
            seeds = builder6388f0FirstPairStreamSeedsFromScalarsAndSensorPoints(
                entrySource = entrySource,
                nullScalarWindow = nullResult.scalarWindow,
                staticScalarWindow = staticScalarWindow,
                row0SensorPointXYBE = row0Point,
                row59SensorPointXYBE = row59Point,
                nullEntropy11A = nullResult.entropy11A,
                nullAttempts = nullResult.attempts,
            )
        } else {
            seeds = builder6388f0FirstPairStreamSeedsFromEntropySourceAndSensorPoints(
                entrySource = entrySource,
                row0SensorPointXYBE = row0Point,
                row59SensorPointXYBE = row59Point,
                maxAttempts = maxAttempts,
                entropySource = entropySource,
            )
        }
        return materialFrom(seeds)
    }

    /**
     * Preamble + fresh-entropy overload. 1:1 port of Swift
     * `SessionKey.deriveFirstPairPhase5Material(preamble:entrySource:
     * staticScalarWindow:maxAttempts:entropySource:)`.
     */
    fun deriveFirstPairPhase5Material(
        preamble: FirstPairPreambleResult,
        entrySource: ByteArray = bundled6388f0LowSeedEntrySource,
        staticScalarWindow: ByteArray? = null,
        maxAttempts: Int = 64,
        entropySource: (Int) -> ByteArray,
    ): FirstPairPhase5KeyMaterial {
        return deriveFirstPairPhase5Material(
            entrySource = entrySource,
            sensorEphemeralPub65 = preamble.phaseHandshake.sensorEphPub,
            sensorStaticPub65 = preamble.phaseHandshake.sensorCert.staticPub,
            staticScalarWindow = staticScalarWindow
                ?: preamble.phaseHandshake.phoneCert.phase5StaticScalarWindowOverride,
            maxAttempts = maxAttempts,
            entropySource = entropySource,
        )
    }

    // MARK: - Private helpers

    /**
     * Strip the `04` prefix from a 65-byte uncompressed point, validating the
     * encoding. 1:1 port of Swift `SessionKey.uncompressedPointXYBE(_:label:)`.
     */
    private fun uncompressedPointXYBE(point65: ByteArray, label: String): ByteArray {
        if (point65.size != 65 || (point65.firstOrNull()?.toInt() ?: 0) != 0x04) {
            throw SessionKeyError.InvalidSensorPointEncoding(
                label = label,
                count = point65.size,
                prefix = point65.firstOrNull()?.toInt()?.and(0xFF),
            )
        }
        return point65.copyOfRange(1, 65)
    }

    /**
     * Compose the 66-byte source and raw key from stream seeds. 1:1 port of
     * Swift `SessionKey.material(from:)`.
     */
    private fun materialFrom(seeds: Builder6388f0FirstPairStreamSeeds): FirstPairPhase5KeyMaterial {
        val source = deriveFrom6388f0FirstPairStreamSeeds(seeds)
        val rawKey = phase5.deriveRawKey(source)
        return FirstPairPhase5KeyMaterial(
            source66 = source,
            rawKey = rawKey,
            nullEntropy11A = seeds.nullEntropy11A,
            nullScalarWindow = seeds.nullScalarWindow,
            nullAttempts = seeds.nullAttempts,
        )
    }
}
