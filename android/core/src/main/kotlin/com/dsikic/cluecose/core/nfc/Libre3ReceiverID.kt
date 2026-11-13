package com.dsikic.cluecose.core.nfc

/**
 * Receiver ID the sensor stores from its activation
 * (1:1 port of LibreCRKit Pairing/Libre3ReceiverID.swift).
 *
 * The sensor answers a later `0xA0`/`0xA8` carrying a different receiver ID
 * with NFC error `0xB1`, so this value must match exactly what activated the
 * sensor. Persisted in the paired state as `receiverIDLEHex`.
 */
class Libre3ReceiverID(val value: Int) {

    companion object {
        fun fromAccountlessUniqueId(uniqueId: String): Libre3ReceiverID =
            Libre3ReceiverID(accountlessValue(uniqueId))

        /**
         * Parse an 8-hex-digit little-endian receiver ID (e.g. `d82eec8f`).
         * Strips a `0x` prefix; keeps only hex digits; requires exactly 8.
         */
        fun fromLittleEndianHex(raw: String): Libre3ReceiverID {
            val value = parseLittleEndianHex(raw)
                ?: throw ReceiverIDError.InvalidLittleEndianHex(raw)
            return Libre3ReceiverID(value)
        }

        /**
         * FNV-1a-style fold over UTF-16 code units (Swift folds `utf16`).
         * Kotlin `String` is UTF-16 native, so this is byte-for-byte 1:1.
         * Deliberately does NOT normalize the input — the long-validated path.
         */
        fun accountlessValue(uniqueId: String): Int {
            var v = 0
            for (unit in uniqueId) {
                // 32-bit wraparound multiplication (Swift `&*`); Int overflow
                // wraps two's-complement, which is bit-identical.
                v = v * FNV_PRIME xor unit.code
            }
            return v
        }

        /** 0x811C9DC5 as Int (the hex literal alone is a Long in Kotlin). */
        private const val FNV_PRIME = 0x811C9DC5.toInt()

        fun parseLittleEndianHex(raw: String): Int? {
            var cleaned = raw.replace(Regex("0x", RegexOption.IGNORE_CASE), "")
            cleaned = cleaned.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            if (cleaned.length != 8) return null
            var v = 0
            for (i in 3 downTo 0) {
                val byte = cleaned.substring(2 * i, 2 * i + 2).toInt(16)
                v = v or (byte shl (8 * i))
            }
            return v
        }
    }

    /** 4-byte little-endian encoding used on the wire and in persistence. */
    val littleEndianData: ByteArray
        get() = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte(),
        )

    val littleEndianHex: String
        get() = littleEndianData.joinToString("") {
            "%02x".format(it.toInt() and 0xFF)
        }

    val displayString: String
        get() = "0x%08x / $littleEndianHex".format(value)

    override fun equals(other: Any?): Boolean =
        other is Libre3ReceiverID && other.value == value

    override fun hashCode(): Int = value

    override fun toString(): String = displayString

    /**
     * LibreView Account ID → receiver ID folds.
     *
     * Abbott ships two phone apps that pair Libre 3 sensors and both take the
     * same lowercase-dashed UUID but fold it differently. The caller has to
     * know which app activated the sensor; guessing produces a receiver ID
     * the sensor rejects with `0xB1`.
     */
    enum class Derivation {
        /** The classic "FreeStyle Libre 3" app (same fold as [accountlessValue]). */
        FREE_STYLE_LIBRE3,

        /**
         * The newer US "Libre by Abbott" app: UTF-8 bytes read as consecutive
         * 4-byte big-endian words, summed with 32-bit wraparound. A trailing
         * partial word is left-padded (a 36-char account ID never reaches it).
         */
        LIBRE_BY_ABBOTT;

        fun valueForAccountID(accountID: String): Int {
            val normalized = accountID.lowercase()
            return when (this) {
                FREE_STYLE_LIBRE3 -> accountlessValue(normalized)
                LIBRE_BY_ABBOTT -> wordSumValue(normalized)
            }
        }

        private fun wordSumValue(accountID: String): Int {
            val bytes = accountID.toByteArray(Charsets.UTF_8)
            var sum = 0
            var word = 0
            var filled = 0
            for (b in bytes) {
                word = (word shl 8) or (b.toInt() and 0xFF)
                filled += 1
                if (filled == 4) {
                    sum += word // 32-bit wraparound (Int overflow)
                    word = 0
                    filled = 0
                }
            }
            if (filled > 0) sum += word // trailing partial word, left-padded
            return sum
        }
    }

    constructor(accountID: String, derivation: Derivation) : this(derivation.valueForAccountID(accountID))
}

sealed class ReceiverIDError : Exception("receiver id failure") {
    data class InvalidLittleEndianHex(val raw: String) : ReceiverIDError()
}
