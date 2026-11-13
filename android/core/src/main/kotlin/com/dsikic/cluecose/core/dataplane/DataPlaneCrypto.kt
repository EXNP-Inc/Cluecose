package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.crypto.AesCcm
import com.dsikic.cluecose.core.crypto.aesBlock128
import com.dsikic.cluecose.core.util.Hex

/**
 * Post-authorization data-plane AES-CCM helper (1:1 port of
 * LibreCRKit DataPlaneCrypto.swift).
 *
 *   key      = Phase 6 kEnc (16B)
 *   nonce13  = seq16_LE ‖ packetDescriptor3 ‖ ivEnc8
 *   tag      = 4B CCM tag appended to ciphertext
 *
 * The packet descriptors are small static constants. Live validation can try
 * all descriptors and accept only a valid CCM tag, which keeps the app useful
 * while descriptor-to-characteristic ownership is still being pinned down.
 */
enum class DataPlanePacketKind(val raw: Int) {
    KIND0(0),
    HANDSHAKE(1),
    KIND2(2),
    KIND3(3),
    KIND4(4),
    KIND5(5),
    KIND6(6),
    PATCH_DATA(7);

    val descriptor: ByteArray
        get() = when (this) {
            KIND0 -> byteArrayOf(0x00, 0x00, 0x00)
            HANDSHAKE -> byteArrayOf(0x00, 0x00, 0x0F)
            KIND2 -> byteArrayOf(0x00, 0x00, 0xF0.toByte())
            KIND3 -> byteArrayOf(0x00, 0x0F, 0x00)
            KIND4 -> byteArrayOf(0x00, 0xF0.toByte(), 0x00)
            KIND5 -> byteArrayOf(0x0F, 0x00, 0x00)
            KIND6 -> byteArrayOf(0xF0.toByte(), 0x00, 0x00)
            PATCH_DATA -> byteArrayOf(0x44, 0x00, 0x00)
        }

    companion object {
        /** Outbound patchControl command writes. */
        val PATCH_CONTROL_WRITE: DataPlanePacketKind = KIND0
    }
}

data class DataPlaneDecryptResult(val kind: DataPlanePacketKind, val plaintext: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataPlaneDecryptResult) return false
        return kind == other.kind && plaintext.contentEquals(other.plaintext)
    }

    override fun hashCode(): Int = (kind.hashCode() * 31) xor plaintext.contentHashCode()
}

sealed class DataPlaneCryptoError : Exception() {
    data class WrongKEncSize(val size: Int) : DataPlaneCryptoError()
    data class WrongIVEncSize(val size: Int) : DataPlaneCryptoError()
    data class PayloadTooShort(val size: Int) : DataPlaneCryptoError()
    object NoDescriptorMatched : DataPlaneCryptoError()
}

class DataPlaneCrypto(val kEnc: ByteArray, val ivEnc: ByteArray) {

    companion object {
        const val TAG_SIZE = 4
    }

    init {
        if (kEnc.size != 16) throw DataPlaneCryptoError.WrongKEncSize(kEnc.size)
        if (ivEnc.size != 8) throw DataPlaneCryptoError.WrongIVEncSize(ivEnc.size)
    }

    private val aes = aesBlock128(kEnc)

    /** nonce13 = seq16_LE ‖ descriptor3 ‖ ivEnc8 */
    fun nonce(sequence: Int, kind: DataPlanePacketKind): ByteArray {
        val out = ByteArray(13)
        out[0] = (sequence and 0xFF).toByte()
        out[1] = ((sequence ushr 8) and 0xFF).toByte()
        System.arraycopy(kind.descriptor, 0, out, 2, 3)
        System.arraycopy(ivEnc, 0, out, 5, 8)
        return out
    }

    fun decrypt(frame: DataFrame, kind: DataPlanePacketKind): ByteArray {
        if (frame.encrypted.size < TAG_SIZE) throw DataPlaneCryptoError.PayloadTooShort(frame.encrypted.size)
        return AesCcm.decrypt(
            nonce = nonce(sequence = frame.sequenceNumber, kind = kind),
            ciphertextWithTag = frame.encrypted,
            tagLength = TAG_SIZE,
            aes = aes,
        )
    }

    fun decryptTryingAllKinds(frame: DataFrame): DataPlaneDecryptResult {
        for (kind in DataPlanePacketKind.entries) {
            val plaintext = try {
                decrypt(frame, kind)
            } catch (e: AesCcm.AesCcmError) {
                continue
            } catch (e: DataPlaneCryptoError) {
                continue
            }
            return DataPlaneDecryptResult(kind, plaintext)
        }
        throw DataPlaneCryptoError.NoDescriptorMatched
    }

    fun encrypt(plaintext: ByteArray, sequence: Int, kind: DataPlanePacketKind): DataFrame {
        val ctWithTag = AesCcm.encrypt(
            nonce = nonce(sequence = sequence, kind = kind),
            plaintext = plaintext,
            tagLength = TAG_SIZE,
            aes = aes,
        )
        return DataFrame(
            encrypted = ctWithTag,
            seq = (sequence and 0xFF),
            type = ((sequence ushr 8) and 0xFF),
        )
    }

    override fun toString(): String =
        "DataPlaneCrypto(kEnc=${Hex.encode(kEnc.copyOfRange(0, 4))}…, ivEnc=${Hex.encode(ivEnc)})"
}
