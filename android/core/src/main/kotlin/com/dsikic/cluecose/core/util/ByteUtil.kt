package com.dsikic.cluecose.core.util

/**
 * Little-endian byte helpers for the Libre data plane.
 * 1:1 port of the extension helpers used across LibreCRKit.
 */
object ByteUtil {

    /** Little-endian UInt16 at [offset]. */
    fun u16le(b: ByteArray, offset: Int): Int {
        require(offset + 1 < b.size)
        return (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
    }

    /** Little-endian UInt32 at [offset]. */
    fun u32le(b: ByteArray, offset: Int): Long {
        require(offset + 3 < b.size)
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[offset + i].toLong() and 0xFF)
        return v
    }

    /** Little-endian UInt32 read from a 4-byte window, unsigned. */
    fun u32leUnsigned(b: ByteArray, offset: Int): Long {
        return u32le(b, offset)
    }

    fun u8(b: ByteArray, offset: Int): Int = b[offset].toInt() and 0xFF

    /** Write [v] as little-endian 16-bit into [b] at [offset]. */
    fun putU16le(b: ByteArray, offset: Int, v: Int) {
        b[offset] = (v and 0xFF).toByte()
        b[offset + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    /** Write [v] as little-endian 32-bit into [b] at [offset]. */
    fun putU32le(b: ByteArray, offset: Int, v: Long) {
        for (i in 0..3) b[offset + i] = ((v ushr (8 * i)) and 0xFF).toByte()
    }

    fun slice(b: ByteArray, start: Int, endExclusive: Int): ByteArray =
        b.copyOfRange(start, endExclusive)

    fun concat(vararg parts: ByteArray): ByteArray {
        val total = parts.sumOf { it.size }
        val out = ByteArray(total)
        var pos = 0
        for (p in parts) {
            System.arraycopy(p, 0, out, pos, p.size)
            pos += p.size
        }
        return out
    }

    fun equal(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) if (a[i] != b[i]) return false
        return true
    }

    /** 32-bit circular right rotate. */
    fun ror32(v: Int, n: Int): Int = Integer.rotateRight(v, n)

    /** 32-bit addition (wrap). */
    fun add32(a: Int, b: Int): Int = a + b

    /** Xor two equal-length byte arrays. */
    fun xor(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size)
        val out = ByteArray(a.size)
        for (i in a.indices) out[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        return out
    }
}

/** Hex helpers (lowercase, no separators) — same convention as the Swift suite. */
object Hex {
    private const val CHARS = "0123456789abcdef"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(CHARS[v ushr 4]).append(CHARS[v and 0xF])
        }
        return sb.toString()
    }

    fun decode(hex: String): ByteArray {
        val clean = hex.replace(" ", "").replace(":", "").lowercase()
        require(clean.length % 2 == 0) { "odd-length hex: $hex" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = clean[2 * i].digitToInt(16)
            val lo = clean[2 * i + 1].digitToInt(16)
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
