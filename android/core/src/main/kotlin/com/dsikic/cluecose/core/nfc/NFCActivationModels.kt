package com.dsikic.cluecose.core.nfc

import com.dsikic.cluecose.core.util.ByteUtil
import com.dsikic.cluecose.core.util.Hex

/**
 * Parsers for the ISO 15693 frames the Libre 3 sensor returns over NFC
 * (1:1 port of LibreCRKit Pairing/NFCActivationModels.swift).
 *
 * CoreNFC sometimes prepends a `0x00` status byte and repeats the `0xa5` pad;
 * `normalizeResponse` collapses both so the parser sees a clean
 * `00 a5 <body>` frame.
 */
class Libre3NFCPatchInfo(raw: ByteArray) {

    val inputRaw: ByteArray
    val frame: ByteArray
    val stateByte: Int
    val productType: Int
    val generation: Int
    val wearDurationMinutes: Int
    val warmupMinutes: Int
    val firmwareVersion: String
    val serialNumber: String

    init {
        val frame = normalizeResponse(raw)
        if (frame.size < 29 || frame[0].toInt() and 0xFF != 0x00 || frame[1].toInt() and 0xFF != 0xA5) {
            throw Libre3NFCError.InvalidPatchInfo(raw)
        }
        this.inputRaw = raw
        this.frame = frame
        this.generation = ByteUtil.u16le(frame, 7)
        this.wearDurationMinutes = ByteUtil.u16le(frame, 9)
        this.firmwareVersion =
            "%d.%d.%d.%d".format(
                ByteUtil.u8(frame, 14),
                ByteUtil.u8(frame, 13),
                ByteUtil.u8(frame, 12),
                ByteUtil.u8(frame, 11),
            )
        this.productType = ByteUtil.u8(frame, 15)
        this.warmupMinutes = ByteUtil.u8(frame, 16) * 5
        this.stateByte = ByteUtil.u8(frame, 17)
        val serialBytes = ByteUtil.slice(frame, 18, 27)
        this.serialNumber = decodeAsciiOrHex(serialBytes)
    }

    val recommendedCommandCode: Int
        get() = if (stateByte == 0x01) NFCActivationCommand.CODE_ACTIVATE else NFCActivationCommand.CODE_SWITCH_RECEIVER

    companion object {
        private fun normalizeResponse(raw: ByteArray): ByteArray {
            var frame = raw
            if (frame.isNotEmpty() && frame[0].toInt() and 0xFF == 0xA5) {
                frame = ByteUtil.concat(byteArrayOf(0x00), frame)
            }
            if (frame.size >= 2 && frame[0].toInt() and 0xFF == 0x00 && frame[1].toInt() and 0xFF == 0xA5) {
                var bodyStart = 2
                while (bodyStart < frame.size && frame[bodyStart].toInt() and 0xFF == 0xA5) bodyStart++
                if (bodyStart > 2) {
                    return ByteUtil.concat(byteArrayOf(0x00, 0xA5.toByte()), ByteUtil.slice(frame, bodyStart, frame.size))
                }
                return frame
            }
            return raw
        }

        private fun decodeAsciiOrHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size)
            var ascii = true
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                if (v !in 0x20..0x7E) { ascii = false; break }
                sb.append(v.toChar())
            }
            return if (ascii) sb.toString() else Hex.encode(bytes)
        }
    }
}

class Libre3NFCActivationResponse(raw: ByteArray) {

    val frame: ByteArray
    val bleAddressLittleEndian: ByteArray
    val blePIN: ByteArray
    val activationTimeRaw: ByteArray
    val trailingCRC: ByteArray

    init {
        val frame = normalizeResponse(raw)
        if (frame.size != 19 ||
            frame[0].toInt() and 0xFF != 0x00 ||
            frame[1].toInt() and 0xFF != 0xA5 ||
            frame[2].toInt() and 0xFF != 0x00
        ) {
            throw Libre3NFCError.InvalidActivationResponse(raw)
        }
        this.frame = frame
        this.bleAddressLittleEndian = ByteUtil.slice(frame, 3, 9)
        this.blePIN = ByteUtil.slice(frame, 9, 13)
        this.activationTimeRaw = ByteUtil.slice(frame, 13, 17)
        this.trailingCRC = ByteUtil.slice(frame, 17, 19)
    }

    val bleAddressDisplay: String
        get() {
            val reversed = bleAddressLittleEndian.reversedArray()
            return reversed.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        }

    companion object {
        private fun normalizeResponse(raw: ByteArray): ByteArray {
            if (raw.size >= 2 && raw[0].toInt() and 0xFF == 0x00 && raw[1].toInt() and 0xFF == 0xA5) return raw
            if (raw.isNotEmpty() && raw[0].toInt() and 0xFF == 0xA5) return ByteUtil.concat(byteArrayOf(0x00), raw)
            return raw
        }
    }
}

sealed class Libre3NFCError : Exception("NFC failure") {
    object ReaderUnavailable : Libre3NFCError()
    object SessionAlreadyActive : Libre3NFCError()
    object NoTag : Libre3NFCError()
    object MultipleTags : Libre3NFCError()
    object NonISO15693Tag : Libre3NFCError()
    data class InvalidPatchInfo(val raw: ByteArray) : Libre3NFCError() {
        override fun toString() = "invalidPatchInfo(${raw.size} bytes: ${Hex.encode(raw)})"
    }
    data class InvalidActivationResponse(val raw: ByteArray) : Libre3NFCError() {
        override fun toString() = "invalidActivationResponse(${raw.size} bytes: ${Hex.encode(raw)})"
    }
}
