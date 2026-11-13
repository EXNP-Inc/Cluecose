package com.dsikic.cluecose.core.pairing

import com.dsikic.cluecose.core.crypto.AesBlockEncrypt
import com.dsikic.cluecose.core.crypto.AesCcm
import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Phase 5 phone -> sensor wire format on handle 0x002a.
 * 1:1 port of LibreCRKit `Pairing/ChallengeMessage.swift`.
 *
 * Ground truth (2026-05-06):
 *   [0..36)   ciphertext for plaintext36 = R1 || R2 || tail4
 *   [36..40)  CCM tag, M=4
 *   [40..54)  zero pad so the fixed 3 x 18B BLE write shape lines up
 *
 * The CCM nonce is the 7-byte trailer from the sensor's 23B challenge notify:
 *   sensor_A = R1(16) || nonce7(7)
 *
 * The block primitive is the Phase-5 white-box AES (`LibAES.phase5BlockEncryptor`)
 * or standard AES (`aesBlock128`) for offline tests, supplied as [AesBlockEncrypt].
 */
class Phase5Challenge(val ciphertext: ByteArray, val tag: ByteArray) {

    init {
        require(ciphertext.size == CIPHERTEXT_SIZE) { "ciphertext must be $CIPHERTEXT_SIZE bytes, got ${ciphertext.size}" }
        require(tag.size == TAG_SIZE) { "tag must be $TAG_SIZE bytes, got ${tag.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Phase5Challenge) return false
        return ciphertext.contentEquals(other.ciphertext) && tag.contentEquals(other.tag)
    }

    override fun hashCode(): Int {
        var h = ciphertext.contentHashCode()
        h = 31 * h + tag.contentHashCode()
        return h
    }

    /** Build by AES-CCM encrypting the 36B `R1 || R2 || tail4` plaintext under [aes]. */
    fun decrypt(aes: AesBlockEncrypt, nonce: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        require(nonce.size == 7) { "nonce must be 7 bytes, got ${nonce.size}" }
        return AesCcm.decrypt(nonce = nonce, ciphertextWithTag = logicalBytes, tagLength = TAG_SIZE, aad = aad, aes = aes)
    }

    /** The 40-byte logical message (ct36 || tag4). */
    val logicalBytes: ByteArray
        get() = ByteUtil.concat(ciphertext, tag)

    /** The 54-byte wire payload (logical + 14B zero pad). */
    val wireBytes: ByteArray
        get() = ByteUtil.concat(logicalBytes, ByteArray(WIRE_SIZE - LOGICAL_SIZE))

    companion object {
        const val PLAINTEXT_SIZE: Int = 36
        const val CIPHERTEXT_SIZE: Int = 36
        const val TAG_SIZE: Int = 4
        const val LOGICAL_SIZE: Int = 40 // ct36 + tag4
        const val WIRE_SIZE: Int = 54 // 3 x 18B fragmented writes

        /** Build by AES-CCM encrypting the 36B `R1 || R2 || tail4` plaintext under [aes]. */
        fun encrypt(plaintext: ByteArray, aes: AesBlockEncrypt, nonce: ByteArray, aad: ByteArray = ByteArray(0)): Phase5Challenge {
            require(plaintext.size == PLAINTEXT_SIZE) { "plaintext must be $PLAINTEXT_SIZE bytes, got ${plaintext.size}" }
            require(nonce.size == 7) { "nonce must be 7 bytes, got ${nonce.size}" }
            val ctTag = AesCcm.encrypt(nonce = nonce, plaintext = plaintext, tagLength = TAG_SIZE, aad = aad, aes = aes)
            return Phase5Challenge(ctTag.copyOfRange(0, CIPHERTEXT_SIZE), ctTag.copyOfRange(CIPHERTEXT_SIZE, CIPHERTEXT_SIZE + TAG_SIZE))
        }

        /** Parse the 54-byte wire payload (or the 40-byte logical form). */
        fun decode(raw: ByteArray): Phase5Challenge {
            require(raw.size == WIRE_SIZE || raw.size == LOGICAL_SIZE) { "wire must be $WIRE_SIZE or $LOGICAL_SIZE bytes, got ${raw.size}" }
            return Phase5Challenge(ByteUtil.slice(raw, 0, CIPHERTEXT_SIZE), ByteUtil.slice(raw, CIPHERTEXT_SIZE, LOGICAL_SIZE))
        }
    }
}
