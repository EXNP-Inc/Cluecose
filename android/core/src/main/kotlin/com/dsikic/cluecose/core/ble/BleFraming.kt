package com.dsikic.cluecose.core.ble

/**
 * Wire framing for the Libre 3 GATT layer (decoded from
 * captures/fresh_pair_2026_04_26/btsnoop_hci.log; see
 * findings/generateKAuth/BLE_PAIRING_TRANSCRIPT.md).
 *
 *   Writes  (phone → sensor):  2-byte LE byte-offset prefix + payload chunk
 *   Notifies (sensor → phone): 1-byte sequence prefix + payload chunk
 *
 * Effective payload per fragment is 18 bytes for writes and 19 bytes for
 * notifies (set by ATT MTU). The receiver reassembles fragments into the
 * original logical message; the caller drives reassembly by either polling
 * "do we have N bytes yet" or awaiting on the equivalent of
 * `receive(exactly:)`.
 *
 * 1:1 port of LibreCRKit `BLE/FrameAssembler.swift`.
 *
 * Pure Kotlin, ByteArray-based (Data → ByteArray), no Android, no
 * coroutines, so it unit-tests cleanly on the JVM.
 */
object BleFraming {

    /** Default ATT-MTU-derived chunk sizes for the Libre 3 protocol. */
    const val WRITE_CHUNK_PAYLOAD: Int = 18
    const val NOTIFY_CHUNK_PAYLOAD: Int = 19

    // MARK: - Write fragmenter

    /**
     * Splits a logical message into ATT-write fragments. Each fragment is
     * `[offset_lo, offset_hi, chunk_bytes...]`. The last fragment is whatever
     * remains; the caller may pre-pad the message if the protocol requires it.
     */
    fun fragmentForWrite(
        message: ByteArray,
        chunkSize: Int = WRITE_CHUNK_PAYLOAD,
    ): List<ByteArray> {
        require(chunkSize > 0)
        val out = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < message.size) {
            val end = minOf(offset + chunkSize, message.size)
            val frag = ByteArray(2 + (end - offset))
            frag[0] = (offset and 0xFF).toByte()
            frag[1] = ((offset ushr 8) and 0xFF).toByte()
            System.arraycopy(message, offset, frag, 2, end - offset)
            out.add(frag)
            offset = end
        }
        return out
    }

    /** Inverse of [fragmentForWrite], used in tests + for any future write-back path. */
    fun reassembleWrite(fragments: List<ByteArray>): ByteArray {
        // Sort by offset, then concatenate. Validate continuity.
        val parsed: List<Pair<Int, ByteArray>> = fragments.map { f ->
            if (f.size < 2) throw BleFramingError.FragmentTooShort
            val offset = (f[0].toInt() and 0xFF) or ((f[1].toInt() and 0xFF) shl 8)
            offset to f.copyOfRange(2, f.size)
        }.sortedBy { it.first }

        val pieces = mutableListOf<ByteArray>()
        var count = 0
        for ((offset, body) in parsed) {
            if (offset != count) throw BleFramingError.DiscontinuousOffsets
            pieces.add(body)
            count += body.size
        }
        val out = ByteArray(count)
        var pos = 0
        for (body in pieces) {
            System.arraycopy(body, 0, out, pos, body.size)
            pos += body.size
        }
        return out
    }

    // MARK: - Notify reassembler

    /**
     * Streaming reassembler for sensor → phone notifies. Each fragment must
     * carry a 1-byte sequence number followed by the payload chunk. The
     * reassembler verifies sequence monotonicity and concatenates payloads.
     * Use [take] to consume the first N reassembled bytes.
     */
    class NotifyReassembler {
        private var buffer = ByteArray(0)
        private var nextExpectedSeq: Int? = null // UInt8, unset until first fragment

        val availableBytes: Int get() = buffer.size

        /**
         * Append a single notify fragment. Returns the new total reassembled
         * length.
         */
        fun feed(fragment: ByteArray): Int {
            if (fragment.isEmpty()) throw BleFramingError.FragmentTooShort
            val seq = fragment[0].toInt() and 0xFF
            val expected = nextExpectedSeq
            if (expected != null && seq != expected) {
                throw BleFramingError.SequenceGap(expected, seq)
            }
            nextExpectedSeq = (seq + 1) and 0xFF
            if (fragment.size > 1) {
                val grown = ByteArray(buffer.size + fragment.size - 1)
                System.arraycopy(buffer, 0, grown, 0, buffer.size)
                System.arraycopy(fragment, 1, grown, buffer.size, fragment.size - 1)
                buffer = grown
            }
            return buffer.size
        }

        /**
         * Consume the first [n] bytes of the reassembled stream. Throws if
         * fewer than [n] bytes have been received.
         */
        fun take(n: Int): ByteArray {
            if (buffer.size < n) throw BleFramingError.NotEnoughBytes(buffer.size, n)
            val head = buffer.copyOfRange(0, n)
            buffer = buffer.copyOfRange(n, buffer.size)
            return head
        }

        /** Discard everything; reset the seq counter. */
        fun reset() {
            buffer = ByteArray(0)
            nextExpectedSeq = null
        }
    }
}

sealed class BleFramingError : Exception("BLE framing failure") {
    object FragmentTooShort : BleFramingError()
    object DiscontinuousOffsets : BleFramingError()
    data class SequenceGap(val expected: Int, val got: Int) : BleFramingError()
    data class NotEnoughBytes(val have: Int, val want: Int) : BleFramingError()
}
