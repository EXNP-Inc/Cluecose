package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.ByteUtil
import com.dsikic.cluecose.core.util.Hex

/**
 * Post-pairing data plane wire format (1:1 port of LibreCRKit DataFrame.swift).
 *
 * Decoded from live BLE captures: encrypted post-authorization notifications
 * share the same outer framing:
 *
 *     [ encrypted_payload | seq_byte | type_byte (often 0x00) ]
 *
 * `DataFrame` parses the OUTER framing; it does NOT decrypt. Callers pass
 * the parsed `encrypted` bytes to [DataPlaneCrypto] once a session key is
 * available.
 */
data class DataFrame(
    val encrypted: ByteArray,
    val seq: Int,
    val type: Int,
) {
    init {
        require(seq in 0..0xFF) { "seq out of range: $seq" }
        require(type in 0..0xFF) { "type out of range: $type" }
    }

    companion object {
        fun parse(raw: ByteArray): DataFrame {
            if (raw.size < 3) throw DataFrameError.TooShort(raw.size)
            val trailerStart = raw.size - 2
            return DataFrame(
                encrypted = ByteUtil.slice(raw, 0, trailerStart),
                seq = ByteUtil.u8(raw, trailerStart),
                type = ByteUtil.u8(raw, trailerStart + 1),
            )
        }
    }

    /**
     * The final two bytes are carried on the wire little-endian; data-plane
     * CCM nonce construction uses them as the 16-bit packet sequence.
     */
    val sequenceNumber: Int
        get() = (seq and 0xFF) or ((type and 0xFF) shl 8)

    /** Encode for the wire — round-trip tests and phone-side frame builds. */
    val raw: ByteArray
        get() = ByteUtil.concat(encrypted, byteArrayOf(seq.toByte(), type.toByte()))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DataFrame) return false
        return encrypted.contentEquals(other.encrypted) && seq == other.seq && type == other.type
    }

    override fun hashCode(): Int =
        (encrypted.contentHashCode() * 31) xor seq xor (type shl 8)

    override fun toString(): String =
        "DataFrame(seq=0x${"%02x".format(seq)}, type=0x${"%02x".format(type)}, " +
            "enc=${Hex.encode(encrypted)})"
}

sealed class DataFrameError : Exception() {
    data class TooShort(val count: Int) : DataFrameError() {
        override fun toString() = "frame too short: $count bytes"
    }
}
