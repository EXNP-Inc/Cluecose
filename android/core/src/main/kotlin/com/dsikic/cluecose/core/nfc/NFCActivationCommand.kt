package com.dsikic.cluecose.core.nfc

import com.dsikic.cluecose.core.util.ByteUtil

/**
 * ISO 15693 activation APDUs (1:1 port of LibreCRKit Pairing/NFCActivationCommand.swift).
 *
 * The sensor is an ISO 15693 tag. `0xA0` activates (binds) a new receiver,
 * `0xA8` switches the bound receiver. Both carry the METCRC body:
 *
 *     metcrc = timeSeconds_LE4 ‖ receiverID_LE4 ‖ abbottCRC16_LE2
 *
 * command = 02 ‖ code ‖ 0x7a ‖ metcrc   (0x7a = ISO15693 manufacturer code)
 *
 * A sensor that stores a different receiver ID answers with NFC error `0xB1`,
 * so the receiver ID must match the one from activation.
 */
object NFCActivationCommand {

    const val CODE_ACTIVATE = 0xA0
    const val CODE_SWITCH_RECEIVER = 0xA8
    const val MANUFACTURER_CODE = 0x7A

    /** ISO15693 "read user memory" request; CoreNFC inserts 0x7a itself. */
    val READ_PATCH_INFO: ByteArray = byteArrayOf(0x02, 0xA1.toByte(), MANUFACTURER_CODE.toByte())

    /** CoreNFC inserts the ISO15693 manufacturer code itself for A1. */
    val READ_PATCH_INFO_CUSTOM_REQUEST_PARAMETERS: ByteArray = ByteArray(0)

    fun accountlessReceiverId(uniqueId: String): Int =
        Libre3ReceiverID.accountlessValue(uniqueId)

    fun metcrc(timeSeconds: Int, receiverId: Int): ByteArray {
        val body = ByteArray(8)
        ByteUtil.putU32le(body, 0, timeSeconds.toLong())
        ByteUtil.putU32le(body, 4, receiverId.toLong())
        val crc = abbottCrc16(body)
        return ByteUtil.concat(body, byteArrayOf((crc and 0xFF).toByte(), ((crc ushr 8) and 0xFF).toByte()))
    }

    fun customRequestParameters(timeSeconds: Int, receiverId: Int): ByteArray =
        metcrc(timeSeconds, receiverId)

    fun command(code: Int, timeSeconds: Int, receiverId: Int): ByteArray =
        ByteUtil.concat(
            byteArrayOf(0x02, code.toByte(), MANUFACTURER_CODE.toByte()),
            metcrc(timeSeconds, receiverId),
        )

    /**
     * Abbott CRC-16 (1:1 with Swift `abbottCRC16`):
     * init 0xFFFF, per-byte: `crc ^= reverse8(byte) << 8`, then 8 left shifts
     * with XOR 0x1021 on a set MSB.
     */
    fun abbottCrc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (reverse8(b.toInt() and 0xFF) shl 8)
            repeat(8) {
                if ((crc and 0x8000) != 0) crc = ((crc shl 1) xor 0x1021) and 0xFFFF
                else crc = (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }

    /** Bit-reverse a byte (Swift `reverse8`). */
    private fun reverse8(v: Int): Int {
        var x = v
        var out = 0
        repeat(8) {
            out = (out shl 1) or (x and 1)
            x = x ushr 1
        }
        return out
    }
}
