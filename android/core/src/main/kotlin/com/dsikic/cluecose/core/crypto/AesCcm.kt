package com.dsikic.cluecose.core.crypto

import com.dsikic.cluecose.core.util.ByteUtil
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-128-CCM (NIST SP 800-38C) — pure Kotlin, 1:1 port of LibreCRKit
 * AESCCM.swift. The AES block primitive is injected as a closure, so the same
 * CCM core works for:
 *
 * - Standard AES under a session key: pass [aesBlock128] (JCE ECB block).
 * - White-box AES (e.g. wrapping persistent SKB blobs): pass [LibAES.blockEncrypt].
 *
 * Tag length is configurable; Libre 3 ground truth uses M=4 on the data plane.
 *
 * NOTE: JDK 17's SunJCE provider has NO AES/CCM mode (verified: no
 * CCMParameterSpec class, NoSuchAlgorithmException at runtime), so JCE cannot
 * be used for CCM itself. This pure port runs identically on the JVM (tests)
 * and Android.
 */
fun interface AesBlockEncrypt {
    fun encrypt(block16: ByteArray): ByteArray
}

/** Standard AES-128 ECB single-block encrypt, backed by JCE. */
fun aesBlock128(key: ByteArray): AesBlockEncrypt {
    require(key.size == 16) { "AES-128 requires a 16-byte key" }
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
    return AesBlockEncrypt { block16 ->
        require(block16.size == 16) { "AES block must be 16 bytes" }
        cipher.doFinal(block16)
    }
}

object AesCcm {

    sealed class AesCcmError : Exception("AES-CCM failure") {
        object InvalidParameters : AesCcmError()
        object MacMismatch : AesCcmError()
    }

    /** Returns ciphertext ‖ tag (tag length [tagLength]). */
    fun encrypt(
        nonce: ByteArray,
        plaintext: ByteArray,
        tagLength: Int = 4,
        aad: ByteArray = ByteArray(0),
        aes: AesBlockEncrypt,
    ): ByteArray {
        checkParameters(nonce, tagLength, plaintext.size)
        val mac = cbcMAC(nonce, aad, plaintext, tagLength, aes)
        val (ct, s0) = ctrApply(plaintext, nonce, aes)
        val tag = ByteUtil.xor(ByteUtil.slice(mac, 0, tagLength), ByteUtil.slice(s0, 0, tagLength))
        return ByteUtil.concat(ct, tag)
    }

    /** Expects ciphertext ‖ tag (tag length [tagLength]); verifies in constant time, returns plaintext. */
    fun decrypt(
        nonce: ByteArray,
        ciphertextWithTag: ByteArray,
        tagLength: Int = 4,
        aad: ByteArray = ByteArray(0),
        aes: AesBlockEncrypt,
    ): ByteArray {
        checkParameters(nonce, tagLength, ciphertextWithTag.size - tagLength)
        val ciphertext = ByteUtil.slice(ciphertextWithTag, 0, ciphertextWithTag.size - tagLength)
        val tag = ByteUtil.slice(ciphertextWithTag, ciphertextWithTag.size - tagLength, ciphertextWithTag.size)
        val (ptCandidate, s0) = ctrApply(ciphertext, nonce, aes)
        val mac = cbcMAC(nonce, aad, ptCandidate, tagLength, aes)
        val expected = ByteUtil.xor(ByteUtil.slice(mac, 0, tagLength), ByteUtil.slice(s0, 0, tagLength))
        if (!constantTimeEqual(expected, tag)) throw AesCcmError.MacMismatch
        return ptCandidate
    }

    // MARK: - CCM internals

    private fun checkParameters(nonce: ByteArray, tagLength: Int, plaintextLen: Int) {
        // L = 15 - len(nonce). Must be in [2, 8].
        val l = 15 - nonce.size
        if (l < 2 || l > 8) throw AesCcmError.InvalidParameters
        // Tag length M ∈ {4, 6, 8, 10, 12, 14, 16}.
        if (tagLength !in intArrayOf(4, 6, 8, 10, 12, 14, 16)) throw AesCcmError.InvalidParameters
        if (plaintextLen < 0) throw AesCcmError.InvalidParameters
        // plaintextLen must fit in L bytes
        if (l < 8) {
            val max = (1L shl (l * 8)) - 1
            if (plaintextLen > max) throw AesCcmError.InvalidParameters
        }
    }

    /** Build the CBC-MAC header: B_0 ‖ formatted_AAD (zero-padded to 16-byte boundary). */
    private fun formatHeader(nonce: ByteArray, aad: ByteArray, plaintextLen: Int, tagLength: Int): ByteArray {
        val l = 15 - nonce.size
        val b0 = ByteArray(16)
        val aFlag = if (aad.isEmpty()) 0 else 0x40
        val m = (tagLength - 2) / 2
        val lFlag = l - 1
        b0[0] = (aFlag or (m shl 3) or lFlag).toByte()
        System.arraycopy(nonce, 0, b0, 1, nonce.size)
        // Q (plaintext length) encoded as L-byte BE in last L bytes
        var q = plaintextLen.toLong()
        for (i in 0 until l) {
            b0[15 - i] = (q and 0xFF).toByte()
            q = q ushr 8
        }
        if (aad.isEmpty()) return b0

        // Encode AAD length per SP 800-38C §A.2.2
        val enc = java.io.ByteArrayOutputStream()
        val a = aad.size
        if (a < 0xFF00) {
            enc.write((a ushr 8) and 0xFF)
            enc.write(a and 0xFF)
        } else if (a <= 0xFFFFFFFFL) {
            enc.write(0xFF)
            enc.write(0xFE)
            enc.write((a ushr 24) and 0xFF)
            enc.write((a ushr 16) and 0xFF)
            enc.write((a ushr 8) and 0xFF)
            enc.write(a and 0xFF)
        } else {
            enc.write(0xFF)
            enc.write(0xFF)
            for (s in (56 downTo 0 step 8)) {
                enc.write(((a.toLong() shr s) and 0xFF).toInt())
            }
        }
        enc.write(aad)
        val padded = (16 - (enc.size() % 16)) % 16
        repeat(padded) { enc.write(0) }
        return ByteUtil.concat(b0, enc.toByteArray())
    }

    /** CBC-MAC over B_0 ‖ formatted_AAD ‖ zero-padded_plaintext per SP 800-38C. */
    private fun cbcMAC(nonce: ByteArray, aad: ByteArray, plaintext: ByteArray, tagLength: Int, aes: AesBlockEncrypt): ByteArray {
        val b = java.io.ByteArrayOutputStream()
        b.write(formatHeader(nonce, aad, plaintext.size, tagLength))
        b.write(plaintext)
        val pad = (16 - (b.size() % 16)) % 16
        repeat(pad) { b.write(0) }
        val all = b.toByteArray()
        var y = ByteArray(16)
        for (i in 0 until all.size / 16) {
            y = aes.encrypt(ByteUtil.xor(y, ByteUtil.slice(all, i * 16, (i + 1) * 16)))
        }
        return y
    }

    /** CTR mode: returns (output, S_0) where S_0 = AES(A_0). */
    private fun ctrApply(inputData: ByteArray, nonce: ByteArray, aes: AesBlockEncrypt): Pair<ByteArray, ByteArray> {
        val l = 15 - nonce.size
        // A_i = flags(L-1) ‖ nonce ‖ ctr_BE(L)
        val a = ByteArray(16)
        a[0] = (l - 1).toByte()
        System.arraycopy(nonce, 0, a, 1, nonce.size)
        // S_0 keystream block
        val s0Block = a.copyOf()
        for (i in (16 - l) until 16) s0Block[i] = 0
        val s0 = aes.encrypt(s0Block)
        // Encrypt input in 16-byte chunks with ctr starting at 1
        val out = java.io.ByteArrayOutputStream()
        var ctr = 1L
        var idx = 0
        while (idx < inputData.size) {
            val ai = a.copyOf()
            var c = ctr
            for (j in 0 until l) {
                ai[15 - j] = (c and 0xFF).toByte()
                c = c ushr 8
            }
            val s = aes.encrypt(ai)
            val take = minOf(16, inputData.size - idx)
            for (k in 0 until take) {
                out.write((inputData[idx + k].toInt() xor s[k].toInt()) and 0xFF)
            }
            idx += take
            ctr += 1
        }
        return out.toByteArray() to s0
    }

    private fun constantTimeEqual(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
