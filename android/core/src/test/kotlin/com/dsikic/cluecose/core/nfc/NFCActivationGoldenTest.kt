package com.dsikic.cluecose.core.nfc

import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 1:1 port of LibreCRKitTests NFCActivationCommandTests.swift — captured
 * wire vectors for the ISO 15693 activation path the Sony device will
 * exercise (0xA0 activate / 0xA8 switch, METCRC, patch-info + activation
 * response parsing).
 */
class NFCActivationGoldenTest {

    private fun hexOf(b: ByteArray): String = Hex.encode(b)
    private fun raw(h: String): ByteArray = Hex.decode(h)

    private fun littleEndianHex(value: Int): String {
        val d = byteArrayOf(
            (value and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte(),
        )
        return hexOf(d)
    }

    @Test
    fun accountlessReceiverIDMatchesCapturedPristineUUIDs() {
        val freshPair = NFCActivationCommand.accountlessReceiverId(
            "5abb0ad8-dc2e-4ede-9e2d-67472a3e630e"
        )
        assertEquals(0x6f0d8378, freshPair)
        assertEquals("78830d6f", littleEndianHex(freshPair))
        assertEquals(
            "78830d6f",
            Libre3ReceiverID.fromAccountlessUniqueId("5abb0ad8-dc2e-4ede-9e2d-67472a3e630e").littleEndianHex,
        )

        val freshInstall = NFCActivationCommand.accountlessReceiverId(
            "6147368e-c060-44a5-9e96-1c02333f43c0"
        )
        assertEquals(0xc3251f23.toInt(), freshInstall)
        assertEquals("231f25c3", littleEndianHex(freshInstall))
    }

    @Test
    fun receiverIDRecoveryEncodingParsesLittleEndianHex() {
        val receiverID = Libre3ReceiverID.fromLittleEndianHex("78:83:0d:6f")
        assertEquals(0x6f0d8378, receiverID.value)
        assertEquals("78830d6f", hexOf(receiverID.littleEndianData))
        assertEquals("78830d6f", receiverID.littleEndianHex)
        assertEquals("0x6f0d8378 / 78830d6f", receiverID.displayString)
        assertEquals(0x6f0d8378, Libre3ReceiverID.parseLittleEndianHex("0x78830d6f"))
    }

    @Test
    fun receiverIDRejectsInvalidHex() {
        assertFailsWith<ReceiverIDError.InvalidLittleEndianHex> {
            Libre3ReceiverID.fromLittleEndianHex("zz")
        }
        assertFailsWith<ReceiverIDError.InvalidLittleEndianHex> {
            Libre3ReceiverID.fromLittleEndianHex("123")
        }
    }

    @Test
    fun abbottCRC16MatchesAcceptedFreshPairVector() {
        val prefix = raw("fc2bee6978830d6f")
        assertEquals(0x71b4, NFCActivationCommand.abbottCrc16(prefix))
        assertEquals(
            "fc2bee6978830d6fb471",
            hexOf(NFCActivationCommand.metcrc(1777216508, 0x6f0d8378)),
        )
    }

    @Test
    fun activationAndSwitchCommandsMatchCapturedWire() {
        assertEquals(
            "02a07afc2bee6978830d6fb471",
            hexOf(NFCActivationCommand.command(NFCActivationCommand.CODE_ACTIVATE, 1777216508, 0x6f0d8378)),
        )
        assertEquals(
            "02a87a8f9cfb69231f25c3aac8",
            hexOf(NFCActivationCommand.command(NFCActivationCommand.CODE_SWITCH_RECEIVER, 1778097295, 0xc3251f23.toInt())),
        )
        assertEquals(
            "02a87a75a8fb6978830d6f4cd4",
            hexOf(NFCActivationCommand.command(NFCActivationCommand.CODE_SWITCH_RECEIVER, 1778100341, 0x6f0d8378)),
        )
    }

    @Test
    fun coreNFCPayloadsExcludeRequestFlagAndCommandCode() {
        // CoreNFC inserts the ISO15693 manufacturer code itself, so the A1
        // custom command carries no app-supplied parameters.
        assertEquals("", hexOf(NFCActivationCommand.READ_PATCH_INFO_CUSTOM_REQUEST_PARAMETERS))
        assertEquals(
            "fc2bee6978830d6fb471",
            hexOf(NFCActivationCommand.customRequestParameters(1777216508, 0x6f0d8378)),
        )
    }

    @Test
    fun patchInfoParserMatchesFreshAndActiveCaptures() {
        val fresh = Libre3NFCPatchInfo(raw("00a50001000200010060541e020401040c0130525243393839415151ff17"))
        assertEquals("0RRC989AQ", fresh.serialNumber)
        assertEquals(0x01, fresh.stateByte)
        assertEquals(21600, fresh.wearDurationMinutes)
        assertEquals(NFCActivationCommand.CODE_ACTIVATE, fresh.recommendedCommandCode)
        assertEquals("1.4.2.30", fresh.firmwareVersion)
        assertEquals(1, fresh.generation)
        assertEquals(4, fresh.productType)
        assertEquals(60, fresh.warmupMinutes)

        val active = Libre3NFCPatchInfo(raw("00a50001000200010060541e020401040c04305252433938394151c6ca"))
        assertEquals("0RRC989AQ", active.serialNumber)
        assertEquals(0x04, active.stateByte)
        assertEquals(21600, active.wearDurationMinutes)
        assertEquals(NFCActivationCommand.CODE_SWITCH_RECEIVER, active.recommendedCommandCode)
        assertEquals("1.4.2.30", active.firmwareVersion)
        assertEquals(1, active.generation)
        assertEquals(4, active.productType)
        assertEquals(60, active.warmupMinutes)
    }

    @Test
    fun patchInfoParserAcceptsCoreNFCResponseParameters() {
        val response = Libre3NFCPatchInfo(raw("a50001000200010060541e020401040c04305252433938394151c6ca"))
        assertEquals("00a50001000200010060541e020401040c04305252433938394151c6ca", hexOf(response.frame))
        assertEquals("0RRC989AQ", response.serialNumber)
        assertEquals(0x04, response.stateByte)
    }

    @Test
    fun patchInfoParserCollapsesRepeatedA5PadFromCoreNFC() {
        val response = Libre3NFCPatchInfo(
            raw("00a5a5a5a5a5a5a5a50001000200010060541e020401040c04305252433938394151c6ca"),
        )
        assertEquals(
            "00a5a5a5a5a5a5a5a50001000200010060541e020401040c04305252433938394151c6ca",
            hexOf(response.inputRaw),
        )
        assertEquals(
            "00a50001000200010060541e020401040c04305252433938394151c6ca",
            hexOf(response.frame),
        )
        assertEquals("0RRC989AQ", response.serialNumber)
        assertEquals(0x04, response.stateByte)
        assertEquals(NFCActivationCommand.CODE_SWITCH_RECEIVER, response.recommendedCommandCode)
    }

    @Test
    fun patchInfoParserRejectsInvalidFrames() {
        assertFailsWith<Libre3NFCError.InvalidPatchInfo> {
            Libre3NFCPatchInfo(raw("000000"))
        }
    }

    @Test
    fun activationResponseParserUsesCorrectedBlePinBoundary() {
        val response = Libre3NFCActivationResponse(raw("00a50058f9b8df22cc3225ec7200000000ad06"))
        assertEquals("58f9b8df22cc", hexOf(response.bleAddressLittleEndian))
        assertEquals("CC:22:DF:B8:F9:58", response.bleAddressDisplay)
        assertEquals("3225ec72", hexOf(response.blePIN))
        assertEquals("00000000", hexOf(response.activationTimeRaw))
        assertEquals("ad06", hexOf(response.trailingCRC))
    }

    @Test
    fun switchResponseWithOriginalReceiverParsesAsActivationLikePayload() {
        val response = Libre3NFCActivationResponse(raw("00a50058f9b8df22cc02bafbbbfc2bee698e6e"))
        assertEquals("58f9b8df22cc", hexOf(response.bleAddressLittleEndian))
        assertEquals("CC:22:DF:B8:F9:58", response.bleAddressDisplay)
        assertEquals("02bafbbb", hexOf(response.blePIN))
        assertEquals("fc2bee69", hexOf(response.activationTimeRaw))
        assertEquals("8e6e", hexOf(response.trailingCRC))
    }

    @Test
    fun activationResponseParserAcceptsCoreNFCResponseParameters() {
        val response = Libre3NFCActivationResponse(raw("a50058f9b8df22cc3225ec7200000000ad06"))
        assertEquals("00a50058f9b8df22cc3225ec7200000000ad06", hexOf(response.frame))
        assertEquals("CC:22:DF:B8:F9:58", response.bleAddressDisplay)
        assertEquals("3225ec72", hexOf(response.blePIN))
    }

    @Test
    fun activationResponseParserRejectsInvalidFrames() {
        assertFailsWith<Libre3NFCError.InvalidActivationResponse> {
            Libre3NFCActivationResponse(raw("00a500"))
        }
        // Wrong length (20 bytes; the parser requires exactly 19).
        assertFailsWith<Libre3NFCError.InvalidActivationResponse> {
            Libre3NFCActivationResponse(raw("00a5000102030405060708090a0b0c0d0e0f0011"))
        }
    }
}
