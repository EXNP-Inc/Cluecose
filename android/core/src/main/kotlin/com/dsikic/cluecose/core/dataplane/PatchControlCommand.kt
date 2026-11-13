package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.Hex

/**
 * Plaintext builders for patchControl writes (0x08981338). 1:1 port of
 * LibreCRKit PatchControlCommand.swift.
 *
 * Each command is exactly 7 bytes before the post-auth AES-CCM data-plane
 * wrapper turns it into an 11-byte encrypted body plus a 2-byte sequence.
 */
class PatchControlCommand(val label: String, val plaintext: ByteArray) {

    init {
        if (plaintext.size != 7) throw PatchControlCommandError.WrongPlaintextSize(plaintext.size)
    }

    companion object {

        @JvmStatic
        fun historicalBackfillGreaterEqual(lifeCount: Int, selector: Int = 0x01): PatchControlCommand =
            backfillGreaterEqual(BackfillStream.HISTORICAL, lifeCount, selector)

        @JvmStatic
        fun clinicalBackfillGreaterEqual(lifeCount: Int, selector: Int = 0x01): PatchControlCommand =
            backfillGreaterEqual(BackfillStream.CLINICAL, lifeCount, selector)

        @JvmStatic
        fun backfillGreaterEqual(stream: BackfillStream, lifeCount: Int, selector: Int = 0x01): PatchControlCommand {
            return makeRangeCommand(
                label = "${stream.label} >= $lifeCount",
                command = 0x01,
                stream = stream.wireValue,
                selector = selector,
                lifeCount = lifeCount,
                aux = null,
            )
        }

        @JvmStatic
        fun eventLog(index: Int): PatchControlCommand =
            PatchControlCommand(
                label = "event log >= $index",
                plaintext = byteArrayOf(0x04, index.toByte(), 0, 0, 0, 0, 0),
            )

        @JvmStatic
        fun factoryData(): PatchControlCommand =
            PatchControlCommand(
                label = "factory data",
                plaintext = byteArrayOf(0x06, 0, 0, 0, 0, 0, 0),
            )

        /** Request sensor shutdown/end-session. Terminal command — not needed
         *  for routine disconnect, reconnect, or backfill workflows. */
        @JvmStatic
        fun shutdownPatch(): PatchControlCommand =
            PatchControlCommand(
                label = "shutdown patch",
                plaintext = byteArrayOf(0x05, 0, 0, 0, 0, 0, 0),
            )

        private fun makeRangeCommand(
            label: String,
            command: Int,
            stream: Int,
            selector: Int,
            lifeCount: Int,
            aux: Int?,
        ): PatchControlCommand {
            val bytes = ByteArray(7)
            bytes[0] = command.toByte()
            bytes[1] = stream.toByte()
            bytes[2] = selector.toByte()
            bytes[3] = (lifeCount and 0xFF).toByte()
            bytes[4] = ((lifeCount ushr 8) and 0xFF).toByte()
            bytes[5] = 0
            bytes[6] = 0
            if (aux != null) {
                bytes[5] = (aux and 0xFF).toByte()
                bytes[6] = ((aux ushr 8) and 0xFF).toByte()
            }
            return PatchControlCommand(label, bytes)
        }
    }

    override fun toString(): String =
        "PatchControlCommand($label, ${Hex2.encode(plaintext)})"
}

private object Hex2 {
    fun encode(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
}

sealed class PatchControlCommandError : Exception() {
    data class WrongPlaintextSize(val size: Int) : PatchControlCommandError()
}
