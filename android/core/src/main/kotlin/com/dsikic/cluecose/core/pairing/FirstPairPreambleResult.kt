package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.P256Ecdh
import com.dsikic.cluecose.core.crypto.P256ScalarMultiplier

/**
 * Result types for the first-pair / command-gated authorization preamble and
 * handshake through Phase 6. 1:1 port of the first-pair-related result
 * structs in LibreCRKit `Pairing/PairingFlow.swift` (lines 23-109).
 *
 * Takeover-mode results (`TakeoverHandshakeResult`, the `UnwrappedKAuth`
 * dependency) are intentionally NOT ported — takeover is out of scope for
 * this slice.
 *
 * **Documented deviation from Swift:** [PairingHandshakeResult.sensorEphPub]
 * holds the raw 65-byte X9.63 encoding (`04 || X || Y`) of the sensor's
 * ephemeral public key, NOT a parsed `P256.KeyAgreement.PublicKey` as in
 * Swift. The raw bytes are what the wire carries, they keep the data class a
 * plain value type, and callers that need the parsed point use
 * [EphemeralExchange.parsePeerPubkey] (the same parse the handshake already
 * performs).
 */
data class PairingHandshakeResult(
    val phoneCert: PhoneCert,
    val sensorCert: SensorCert,
    val sensorCertSigningKeyIndex: Int?,
    val phoneEph: EphemeralKeyPair,
    /** 65-byte X9.63 (`04 || X || Y`) sensor ephemeral pubkey (see class KDoc for the Swift deviation). */
    val sensorEphPub: ByteArray,
    /** ECDH(phone_eph_priv, sensor_static_pub) — 32-byte BE X coordinate. */
    val sharedEphStatic: ByteArray,
    /** ECDH(phone_eph_priv, sensor_eph_pub) — 32-byte BE X coordinate. */
    val sharedEphEph: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PairingHandshakeResult) return false
        return phoneCert == other.phoneCert &&
            sensorCert == other.sensorCert &&
            sensorCertSigningKeyIndex == other.sensorCertSigningKeyIndex &&
            phoneEph == other.phoneEph &&
            sensorEphPub.contentEquals(other.sensorEphPub) &&
            sharedEphStatic.contentEquals(other.sharedEphStatic) &&
            sharedEphEph.contentEquals(other.sharedEphEph)
    }

    override fun hashCode(): Int {
        var h = phoneCert.hashCode()
        h = 31 * h + sensorCert.hashCode()
        h = 31 * h + (sensorCertSigningKeyIndex?.hashCode() ?: 0)
        h = 31 * h + phoneEph.hashCode()
        h = 31 * h + sensorEphPub.contentHashCode()
        h = 31 * h + sharedEphStatic.contentHashCode()
        h = 31 * h + sharedEphEph.contentHashCode()
        return h
    }
}

/**
 * Result of the command-gated fresh-pair preamble through the sensor's
 * Phase 10 R1 challenge. Stops before the Phase 5 phone response.
 * 1:1 port of Swift `FirstPairPreambleResult`.
 */
data class FirstPairPreambleResult(
    val phaseHandshake: PairingHandshakeResult,
    /** The 23-byte `R1_16 || nonce7` challenge notify as received. */
    val sensorR1Wire: ByteArray,
    /** The 16-byte R1 challenge: [sensorR1Wire][0..<16]. */
    val sensorR1: ByteArray,
    /** The 7-byte CCM nonce: [sensorR1Wire][16..<23]. */
    val nonce7: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstPairPreambleResult) return false
        return phaseHandshake == other.phaseHandshake &&
            sensorR1Wire.contentEquals(other.sensorR1Wire) &&
            sensorR1.contentEquals(other.sensorR1) &&
            nonce7.contentEquals(other.nonce7)
    }

    override fun hashCode(): Int {
        var h = phaseHandshake.hashCode()
        h = 31 * h + sensorR1Wire.contentHashCode()
        h = 31 * h + sensorR1.contentHashCode()
        h = 31 * h + nonce7.contentHashCode()
        return h
    }
}

/**
 * The command-gated authorization preamble is the same wire operation used by
 * first-pair and reconnect after a sensor is already known. The older
 * [FirstPairPreambleResult] name is kept for source compatibility.
 * 1:1 port of the Swift `typealias CommandGatedAuthorizationPreambleResult`.
 */
typealias CommandGatedAuthorizationPreambleResult = FirstPairPreambleResult

/**
 * Result of the command-gated fresh-pair handshake through Phase 6. The
 * Phase 5 raw key is supplied by the caller so the BLE state machine remains
 * decoupled from source-builder policy and entropy generation.
 * 1:1 port of Swift `FirstPairHandshakeResult`.
 */
data class FirstPairHandshakeResult(
    val preamble: FirstPairPreambleResult,
    val phase5Sent: Phase5Challenge,
    /** Raw 67-byte Phase 6 body received from the sensor. */
    val phase6Raw: ByteArray,
    val phase6: Phase6Response,
    val sessionMaterial: Phase6SessionMaterial,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstPairHandshakeResult) return false
        return preamble == other.preamble &&
            phase5Sent == other.phase5Sent &&
            phase6Raw.contentEquals(other.phase6Raw) &&
            phase6 == other.phase6 &&
            sessionMaterial == other.sessionMaterial
    }

    override fun hashCode(): Int {
        var h = preamble.hashCode()
        h = 31 * h + phase5Sent.hashCode()
        h = 31 * h + phase6Raw.contentHashCode()
        h = 31 * h + phase6.hashCode()
        h = 31 * h + sessionMaterial.hashCode()
        return h
    }
}

/**
 * The Phase 5/6 command-gated authorization result is typealiased to the
 * historical first-pair result shape because the bytes and returned material
 * are identical once the caller supplies the accepted 4-byte Phase 5 tail.
 * 1:1 port of the Swift `typealias CommandGatedAuthorizationHandshakeResult`.
 */
typealias CommandGatedAuthorizationHandshakeResult = FirstPairHandshakeResult

/**
 * Result of the command-gated fresh-pair handshake when the Phase 5 material
 * is derived by the clean-room first-pair source builder.
 * 1:1 port of Swift `FirstPairDerivedHandshakeResult`.
 */
data class FirstPairDerivedHandshakeResult(
    val handshake: FirstPairHandshakeResult,
    val phase5Material: FirstPairPhase5KeyMaterial,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FirstPairDerivedHandshakeResult) return false
        return handshake == other.handshake && phase5Material == other.phase5Material
    }

    override fun hashCode(): Int = 31 * handshake.hashCode() + phase5Material.hashCode()
}

/**
 * 65-byte X9.63 uncompressed encoding (`0x04 || X(32) || Y(32)`) of a parsed
 * [P256ScalarMultiplier.AffinePoint] — the Android stand-in for CryptoKit
 * `P256.KeyAgreement.PublicKey.x963Representation`.
 */
internal fun x963(point: P256ScalarMultiplier.AffinePoint): ByteArray {
    val out = ByteArray(65)
    out[0] = 0x04
    System.arraycopy(P256Ecdh.toBigEndian32(point.x), 0, out, 1, 32)
    System.arraycopy(P256Ecdh.toBigEndian32(point.y), 0, out, 33, 32)
    return out
}
