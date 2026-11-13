package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.P256Ecdh
import com.dsikic.cluecose.core.crypto.P256ScalarMultiplier
import java.math.BigInteger

/**
 * Phase 3/4 ephemeral P-256 ECDH.
 * 1:1 port of LibreCRKit `Pairing/EphemeralExchange.swift`.
 *
 * The phone generates a fresh P-256 keypair, sends the uncompressed
 * pubkey (65B) on handle 0x002d, and receives the sensor's ephemeral
 * pubkey (65B notify on the same handle).
 *
 * The 3DH-style key agreement combines:
 *   - phone_eph × sensor_static  (binds to the cert pubkey)
 *   - phone_eph × sensor_eph     (binds to the per-session ephemeral)
 *   - phone_static × sensor_eph  (proves phone identity to sensor)
 *
 * CryptoKit's `P256.KeyAgreement` exposes raw X9.63 public-key encoding
 * (`x963Representation`) which IS the 65B uncompressed-point format the
 * protocol uses on the wire. On Android the same role is filled by
 * [P256Ecdh] over [P256ScalarMultiplier].
 *
 * CryptoKit type mapping (kept in lockstep with the iOS reference):
 *   - `P256.KeyAgreement.PrivateKey` → [P256PrivateKey] (the 32-byte BE scalar)
 *   - `P256.KeyAgreement.PublicKey`  → [P256ScalarMultiplier.AffinePoint]
 */

/**
 * P-256 private key (the 32-byte scalar in the raw `rawRepresentation`
 * big-endian form). Mirrors CryptoKit `P256.KeyAgreement.PrivateKey` for the
 * purposes of [EphemeralKeyPair] / [EphemeralExchange] — ECDH only.
 */
class P256PrivateKey(val raw: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is P256PrivateKey && raw.contentEquals(other.raw)

    override fun hashCode(): Int = raw.contentHashCode()
}

/**
 * Ephemeral P-256 keypair: private scalar + the 65B uncompressed public
 * point sent on the wire (`0x04 ‖ X ‖ Y`). Mirrors Swift `EphemeralKeyPair`.
 *
 * Construct via the companion factories (the Swift `init`s):
 * [EphemeralKeyPair.generate] (`EphemeralKeyPair()`),
 * [EphemeralKeyPair.fromPrivateKey] (`init(privateKey:)`),
 * [EphemeralKeyPair.fromNativeScalarWindow] (`init(nativeScalarWindowLE:)`
 * and `init(nativeScalarWindowLE:publicKey65Override:)`).
 */
class EphemeralKeyPair(val privateKey: P256PrivateKey, val publicKey65: ByteArray) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EphemeralKeyPair) return false
        return privateKey == other.privateKey && publicKey65.contentEquals(other.publicKey65)
    }

    override fun hashCode(): Int = 31 * privateKey.hashCode() + publicKey65.contentHashCode()

    companion object {
        // P-256 group order n (NIST/SECG P-256; CryptoKit samples PrivateKey() in [1, n)).
        private val ORDER: BigInteger = BigInteger(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16,
        )

        /**
         * Generate a fresh random ephemeral keypair (Swift `EphemeralKeyPair()`).
         * The random scalar is sampled in [1, n) — the same domain CryptoKit
         * `PrivateKey()` uses.
         */
        fun generate(): EphemeralKeyPair {
            val raw = rawKey()
            return EphemeralKeyPair(P256PrivateKey(raw), P256Ecdh.publicKey65FromScalar(raw))
        }

        /**
         * Construct from a previously-saved private key (e.g. for replay tests).
         * Swift `EphemeralKeyPair.init(privateKey:)`.
         */
        fun fromPrivateKey(privateKey: P256PrivateKey): EphemeralKeyPair =
            EphemeralKeyPair(privateKey, P256Ecdh.publicKey65FromScalar(privateKey.raw))

        /**
         * Construct from a private scalar while overriding the public point sent
         * on the wire. Native first-pair `process2(5)` derives the Phase 3 public
         * point through a separate fixed-point path, while Phase 5 still needs the
         * accepted null-branch scalar/entropy.
         *
         * Swift `EphemeralKeyPair.init(nativeScalarWindowLE:publicKey65Override:)`.
         */
        fun fromNativeScalarWindow(nativeScalarWindowLE: ByteArray, publicKey65Override: ByteArray): EphemeralKeyPair {
            if (publicKey65Override.size != 65 || (publicKey65Override[0].toInt() and 0xFF) != 0x04) {
                throw EphemeralExchangeError.InvalidEncoding
            }
            val raw = rawPrivateKeyRepresentation(nativeScalarWindowLE)
            return EphemeralKeyPair(P256PrivateKey(raw), publicKey65Override)
        }

        /**
         * Construct the Phase 3 ephemeral key from the native first-pair
         * null-branch scalar window. Native stores this scalar little-endian in a
         * 70-byte window; CryptoKit (and [P256Ecdh]) expect the 32-byte scalar
         * big-endian.
         *
         * Swift `EphemeralKeyPair.init(nativeScalarWindowLE:)`.
         */
        fun fromNativeScalarWindow(nativeScalarWindowLE: ByteArray): EphemeralKeyPair {
            val raw = rawPrivateKeyRepresentation(nativeScalarWindowLE)
            return fromPrivateKey(P256PrivateKey(raw))
        }

        /** Swift `EphemeralKeyPair.publicKey65(nativeScalarWindowLE:)`. */
        fun publicKey65(nativeScalarWindowLE: ByteArray): ByteArray =
            fromNativeScalarWindow(nativeScalarWindowLE).publicKey65

        /** Swift `EphemeralKeyPair.rawPrivateKeyRepresentation(nativeScalarWindowLE:)`. */
        private fun rawPrivateKeyRepresentation(nativeScalarWindowLE: ByteArray): ByteArray {
            if (nativeScalarWindowLE.size < 32) {
                throw EphemeralExchangeError.InvalidScalarWindowLength(nativeScalarWindowLE.size)
            }
            return nativeScalarWindowLE.copyOfRange(0, 32).also { it.reverse() }
        }

        /**
         * Sample a fresh random scalar in [1, n) — 32-byte big-endian.
         * Swift's `P256.KeyAgreement.PrivateKey()` does the equivalent.
         */
        private fun rawKey(): ByteArray {
            val secure = java.security.SecureRandom()
            val big = ByteArray(32)
            while (true) {
                secure.nextBytes(big)
                val candidate = BigInteger(1, big)
                if (candidate.signum() != 0 && candidate < ORDER) break
            }
            return big
        }
    }
}

/**
 * ECDH helpers. Mirrors Swift `EphemeralExchange`.
 */
object EphemeralExchange {

    /**
     * Parse a 65-byte uncompressed P-256 point. Swift
     * `EphemeralExchange.parsePeerPubkey(_:)` (CryptoKit x963 → [AffinePoint]).
     *
     * @throws EphemeralExchangeError.InvalidEncoding when not 65B or no 0x04 prefix.
     * @throws P256ScalarMultiplier.P256ScalarMultiplierError.InvalidPoint when
     *   the point is off the curve (CryptoKit's x963 parser rejects those too).
     */
    fun parsePeerPubkey(raw65: ByteArray): P256ScalarMultiplier.AffinePoint {
        if (raw65.size != 65 || (raw65[0].toInt() and 0xFF) != 0x04) {
            throw EphemeralExchangeError.InvalidEncoding
        }
        return P256ScalarMultiplier.AffinePoint.fromBigEndian(
            xBE = raw65.copyOfRange(1, 33),
            yBE = raw65.copyOfRange(33, 65),
        )
    }

    /**
     * Compute a single ECDH shared secret as raw 32 bytes — the big-endian X
     * coordinate of `[privateKey] · peer` (Swift `EphemeralExchange.sharedSecret`).
     */
    fun sharedSecret(privateKey: P256PrivateKey, peer: P256ScalarMultiplier.AffinePoint): ByteArray {
        val peer65 = ByteArray(65).also { out ->
            out[0] = 0x04
            System.arraycopy(P256Ecdh.toBigEndian32(peer.x), 0, out, 1, 32)
            System.arraycopy(P256Ecdh.toBigEndian32(peer.y), 0, out, 33, 32)
        }
        return P256Ecdh.sharedSecret(privateScalar32BE = privateKey.raw, peerPub65 = peer65)
    }
}

/**
 * 1:1 port of Swift `EphemeralExchangeError` (same cases, so error
 * equality matches).
 */
sealed class EphemeralExchangeError(message: String = "ephemeral exchange failure") : Exception(message) {

    /** Peer pubkey not 65B / no 0x04 prefix, or a bad x963 point
     * (Swift `.invalidEncoding`). */
    object InvalidEncoding : EphemeralExchangeError("invalid public key encoding")

    /** `nativeScalarWindowLE.count < 32` (Swift `.invalidScalarWindowLength`). */
    data class InvalidScalarWindowLength(val length: Int) :
        EphemeralExchangeError("invalid scalar window length: $length")
}
