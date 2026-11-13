package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.AesBlockEncrypt
import com.dsikic.cluecose.core.crypto.AesCcm
import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Phase 6 sensor -> phone wire format on handle 0x002a.
 * 1:1 port of LibreCRKit `Pairing/Phase6Response.swift`.
 *
 * Ground truth (2026-05-06 live iPhone and Android pristine runs):
 *   [0..56)   ciphertext for plaintext56 = R2 || R1 || kEnc || ivEnc8
 *   [56..60)  CCM tag, M=4
 *   [60..67)  sensor nonce7
 *
 * Decryption uses the same block primitive the Phase 5 data plane uses
 * (`LibAES.phase5BlockEncrypt`); until that lands in `:core`, callers pass any
 * 16-byte-block [AesBlockEncrypt] — for standard-key tests, [aesBlock128] works.
 */
data class Phase6SessionMaterial(
    val phoneR2: ByteArray,
    val sensorR1: ByteArray,
    val kEnc: ByteArray,
    val ivEnc: ByteArray,
) {
    init {
        require(phoneR2.size == 16) { "phoneR2 must be 16 bytes, got ${phoneR2.size}" }
        require(sensorR1.size == 16) { "sensorR1 must be 16 bytes, got ${sensorR1.size}" }
        require(kEnc.size == 16) { "kEnc must be 16 bytes, got ${kEnc.size}" }
        require(ivEnc.size == 8) { "ivEnc must be 8 bytes, got ${ivEnc.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Phase6SessionMaterial) return false
        return phoneR2.contentEquals(other.phoneR2) &&
            sensorR1.contentEquals(other.sensorR1) &&
            kEnc.contentEquals(other.kEnc) &&
            ivEnc.contentEquals(other.ivEnc)
    }

    override fun hashCode(): Int {
        var h = phoneR2.contentHashCode()
        h = 31 * h + sensorR1.contentHashCode()
        h = 31 * h + kEnc.contentHashCode()
        h = 31 * h + ivEnc.contentHashCode()
        return h
    }
}

/**
 * One Phase 6 notification from the sensor: 56B ciphertext + 4B CCM tag + 7B nonce.
 */
data class Phase6Response(
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val nonce: ByteArray,
) {
    init {
        require(ciphertext.size == CIPHERTEXT_SIZE) { "ciphertext must be $CIPHERTEXT_SIZE bytes, got ${ciphertext.size}" }
        require(tag.size == TAG_SIZE) { "tag must be $TAG_SIZE bytes, got ${tag.size}" }
        require(nonce.size == 7) { "nonce must be 7 bytes, got ${nonce.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Phase6Response) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            tag.contentEquals(other.tag) &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var h = ciphertext.contentHashCode()
        h = 31 * h + tag.contentHashCode()
        h = 31 * h + nonce.contentHashCode()
        return h
    }

    /**
     * Decrypt the 56-byte plaintext (R2 || R1 || kEnc || ivEnc) and verify the M=4 tag.
     * Throws [Phase6ResponseError.TagMismatch] when the tag does not verify
     * (i.e. the wrong raw key was supplied).
     */
    fun decrypt(aes: AesBlockEncrypt, aad: ByteArray = ByteArray(0)): Phase6SessionMaterial {
        val plaintextWithTag = ByteUtil.concat(ciphertext, tag)
        val plaintext = try {
            AesCcm.decrypt(nonce = nonce, ciphertextWithTag = plaintextWithTag, tagLength = 4, aad = aad, aes = aes)
        } catch (e: AesCcm.AesCcmError) {
            throw Phase6ResponseError.TagMismatch(e)
        }
        require(plaintext.size == PLAINTEXT_SIZE) { "plaintext must be $PLAINTEXT_SIZE bytes, got ${plaintext.size}" }
        return Phase6SessionMaterial(
            phoneR2 = ByteUtil.slice(plaintext, 0, 16),
            sensorR1 = ByteUtil.slice(plaintext, 16, 32),
            kEnc = ByteUtil.slice(plaintext, 32, 48),
            ivEnc = ByteUtil.slice(plaintext, 48, 56),
        )
    }

    companion object {
        const val PLAINTEXT_SIZE: Int = 56
        const val CIPHERTEXT_SIZE: Int = 56
        const val TAG_SIZE: Int = 4
        const val LOGICAL_SIZE: Int = 60
        const val WIRE_SIZE: Int = 67

        /** Split a 67-byte wire buffer into its three fields. */
        fun decode(raw: ByteArray): Phase6Response {
            require(raw.size == WIRE_SIZE) { "wire must be $WIRE_SIZE bytes, got ${raw.size}" }
            return Phase6Response(
                ciphertext = ByteUtil.slice(raw, 0, CIPHERTEXT_SIZE),
                tag = ByteUtil.slice(raw, CIPHERTEXT_SIZE, LOGICAL_SIZE),
                nonce = ByteUtil.slice(raw, LOGICAL_SIZE, WIRE_SIZE),
            )
        }
    }
}

/**
 * Errors for the Phase 6 response decode/decrypt path. Mirrors
 * `Phase6ResponseError`; the tag-check outcome is distinct so callers can
 * tell "wrong key" from "bad bytes".
 */
sealed class Phase6ResponseError(message: String) : Exception(message) {
    /** CCM tag verification failed — the raw key is wrong (or the bytes were altered). */
    class TagMismatch(cause: Exception) : Phase6ResponseError("Phase 6 CCM tag mismatch (wrong raw key?)") {
        init {
            initCause(cause)
        }
    }
}
