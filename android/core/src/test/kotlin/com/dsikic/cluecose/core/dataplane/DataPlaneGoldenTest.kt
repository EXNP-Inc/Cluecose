package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.crypto.AesCcm
import com.dsikic.cluecose.core.util.ByteUtil
import com.dsikic.cluecose.core.util.Hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 1:1 port of LibreCRKitTests DataPlaneTests.swift (live-capture golden
 * vectors). These vectors are the shared verification oracle for the
 * Android data plane: the same keys, frames, and plaintexts must round-trip
 * here as on iOS.
 */
class DataPlaneGoldenTest {

    private fun raw(hex: String): ByteArray = Hex.decode(hex)
    private fun hexOf(bytes: ByteArray): String = Hex.encode(bytes)

    private fun setU16(data: ByteArray, offset: Int, value: Int): ByteArray {
        val copy = data.copyOf()
        ByteUtil.putU16le(copy, offset, value)
        return copy
    }

    // MARK: - DataFrame

    @Test
    fun dataFrameRoundTrip() {
        val body = raw("aabbccddee0700")
        val frame = DataFrame.parse(body)
        assertContentEquals(raw("aabbccddee"), frame.encrypted)
        assertEquals(0x07, frame.seq)
        assertEquals(0x00, frame.type)
        assertContentEquals(body, frame.raw)
    }

    @Test
    fun dataFrameTooShort() {
        val error = assertFailsWith<DataFrameError.TooShort> {
            DataFrame.parse(byteArrayOf(0x01))
        }
        assertEquals(1, error.count)
    }

    @Test
    fun realtimeReadingFrameStructure() {
        // Captured 0x0014 notify from fresh_pair (rec 432, 18B body).
        val body = raw("9d7476cb096253cc0d492755b1caa0ad0100")
        val frame = DataFrame.parse(body)
        assertEquals(16, frame.encrypted.size, "realtime reading body is 16B encrypted")
        assertEquals(0x01, frame.seq)
        assertEquals(0x00, frame.type)
    }

    @Test
    fun sessionControlFrames() {
        // 0x0011 traffic from fresh_pair (recs 433, 437, 438, 453).
        val cases = listOf(
            Triple("ed7df1f64812b45afa57010100", 11, 0x01),
            Triple("665f5b31bd9f9add0100", 8, 0x01),
            Triple("0fdf3ccfe55e0045cb71920200", 11, 0x02),
            Triple("d4d552d97bd404680200", 8, 0x02),
        )
        for ((hex, encLen, seq) in cases) {
            val frame = DataFrame.parse(raw(hex))
            assertEquals(encLen, frame.encrypted.size, "case $hex")
            assertEquals(seq, frame.seq, "case $hex")
            assertEquals(0x00, frame.type, "case $hex")
        }
    }

    // MARK: - Channel mapping

    @Test
    fun dataPlaneChannelMappingPinsLiveDescriptors() {
        assertEquals(
            DataPlaneChannel.PATCH_STATUS,
            DataPlaneChannel.fromUuidString("08981482-EF89-11E9-81B4-2A2AE2DBCCE4"),
        )
        assertEquals(
            DataPlaneChannel.GLUCOSE_DATA,
            DataPlaneChannel.fromUuidString("0898177A-EF89-11E9-81B4-2A2AE2DBCCE4"),
        )
        assertEquals(
            DataPlaneChannel.HISTORIC_DATA,
            DataPlaneChannel.fromUuidString("0898195A-EF89-11E9-81B4-2A2AE2DBCCE4"),
        )
        assertEquals(DataPlanePacketKind.KIND2, DataPlaneChannel.PATCH_STATUS.preferredInboundKind)
        assertEquals(DataPlanePacketKind.KIND3, DataPlaneChannel.GLUCOSE_DATA.preferredInboundKind)
        assertEquals(DataPlanePacketKind.KIND4, DataPlaneChannel.HISTORIC_DATA.preferredInboundKind)
    }

    // MARK: - Live fragment reassembly + decrypt

    @Test
    fun liveGlucoseDataFragmentsReassembleAndDecrypt() {
        val crypto = DataPlaneCrypto(
            kEnc = raw("7c536d2ec3c76c741a776de85596959d"),
            ivEnc = raw("00000000bcc696a2"),
        )
        val decoder = DataPlaneDecoder(crypto)
        val assembler = DataPlaneNotificationAssembler()
        val prefix = raw("3c937752c72e2dfdcfe05a5dce315b")
        val suffix = raw("6f3785f23dbc85066856d60027c5a41f45c90100")
        assertNull(assembler.feed(prefix, DataPlaneChannel.GLUCOSE_DATA))
        val rawFrame = assembler.feed(suffix, DataPlaneChannel.GLUCOSE_DATA)
        assertNotNull(rawFrame)
        val frame = DataFrame.parse(rawFrame)
        val packet = decoder.decrypt(frame, DataPlaneChannel.GLUCOSE_DATA)

        assertEquals(0x0001, frame.sequenceNumber)
        assertEquals(DataPlanePacketKind.KIND3, packet.kind)
        assertTrue(packet.usedPreferredKind)
        assertEquals(
            "da386700e3ff0000ac26c73871000b670071001a0abf03f33c59100000",
            hexOf(packet.plaintext),
        )
        val payload = packet.payload as DataPlaneDecodedPayload.RealtimeGlucose
        assertEquals(14554, payload.reading.lifeCount)
        assertEquals(103, payload.reading.currentGlucoseMgDL)
    }

    @Test
    fun assemblerPassesThroughNonGlucoseChannels() {
        val assembler = DataPlaneNotificationAssembler()
        val bytes = raw("aabbccddee0700")
        assertContentEquals(bytes, assembler.feed(bytes, DataPlaneChannel.PATCH_STATUS))
    }

    @Test
    fun livePatchStatusDecryptsThroughChannelDecoder() {
        val crypto = DataPlaneCrypto(
            kEnc = raw("7c536d2ec3c76c741a776de85596959d"),
            ivEnc = raw("00000000bcc696a2"),
        )
        val frame = DataFrame.parse(raw("4e6fe9573b6365476193e9c42009acd20100"))
        val packet = DataPlaneDecoder(crypto).decrypt(frame, DataPlaneChannel.PATCH_STATUS)

        assertEquals(0x0001, frame.sequenceNumber)
        assertEquals(DataPlanePacketKind.KIND2, packet.kind)
        assertEquals("9d280000e0000704db381607", hexOf(packet.plaintext))
        val payload = packet.payload as DataPlaneDecodedPayload.PatchStatusPayload
        assertEquals(10397, payload.status.lifeCount)
        assertEquals(14555, payload.status.currentLifeCount)
        assertEquals(22, payload.status.stackDisconnectReason)
        assertEquals(7, payload.status.appDisconnectReason)
    }

    @Test
    fun liveHistoricalDataDecryptsThroughChannelDecoder() {
        val crypto = DataPlaneCrypto(
            kEnc = raw("7c536d2ec3c76c741a776de85596959d"),
            ivEnc = raw("00000000bcc696a2"),
        )
        val frame = DataFrame.parse(raw("4637aa722274876d69a34f20096c49adced70100"))
        val packet = DataPlaneDecoder(crypto).decrypt(frame, DataPlaneChannel.HISTORIC_DATA)

        assertEquals(0x0001, frame.sequenceNumber)
        assertEquals(DataPlanePacketKind.KIND4, packet.kind)
        assertEquals("98036d006b0069006e006a006500", hexOf(packet.plaintext))
        val payload = packet.payload as DataPlaneDecodedPayload.HistoricalPagePayload
        assertEquals(920, payload.page.startLifeCount)
        assertContentEquals(intArrayOf(109, 107, 105, 110, 106, 101), payload.page.values)
    }

    // MARK: - Realtime glucose reading

    private data class RtcCase(
        val hex: String,
        val lifeCount: Int,
        val glucose: Int,
        val rateRaw: Int,
        val temp: Int,
        val fastData: String,
        val fastDataWords: IntArray,
        val projected: Int,
    )

    @Test
    fun realtimeGlucoseReadingParsesLivePlaintext() {
        val cases = listOf(
            RtcCase(
                "e3386200d6ff0000f023d1386c000b62006c00200a9e03113d03100000",
                14563, 98, -42, 2592, "9e03113d03100000",
                intArrayOf(0x039e, 0x3d11, 0x1003, 0x0000), 9200,
            ),
            RtcCase(
                "e4386200d6ff0000f023d1386c000b62006c001f0a9b03093d0b100000",
                14564, 98, -42, 2591, "9b03093d0b100000",
                intArrayOf(0x039b, 0x3d09, 0x100b, 0x0000), 9200,
            ),
            RtcCase(
                "e5386100d9ff00008c23d1386c000b61006c001e0a9403053d12100000",
                14565, 97, -39, 2590, "9403053d12100000",
                intArrayOf(0x0394, 0x3d05, 0x1012, 0x0000), 9100,
            ),
            RtcCase(
                "e6386100daff00008c23d1386c000b61006c001d0a9203003dfb0f0000",
                14566, 97, -38, 2589, "9203003dfb0f0000",
                intArrayOf(0x0392, 0x3d00, 0x0ffb, 0x0000), 9100,
            ),
        )

        for (c in cases) {
            val r = RealtimeGlucoseReading.parse(raw(c.hex))
            assertEquals(c.lifeCount, r.lifeCount, c.hex)
            assertEquals(c.glucose, r.currentWord, c.hex)
            assertEquals(c.glucose, r.readingMgDL, c.hex)
            assertEquals(0, r.dqErrorRaw, c.hex)
            assertEquals(Libre3DataQualityError.GOOD, r.dqError, c.hex)
            assertEquals(0, r.sensorConditionRaw, c.hex)
            assertEquals(Libre3SensorCondition.OK, r.sensorCondition, c.hex)
            assertEquals(c.glucose, r.currentGlucoseMgDL, c.hex)
            assertTrue(r.isCurrentGlucoseValid, c.hex)
            assertTrue(r.isCurrentDQGood, c.hex)
            assertTrue(r.isCurrentGlucoseUsable, c.hex)
            assertEquals(c.rateRaw, r.rateOfChangeRaw, c.hex)
            assertEquals(c.rateRaw / 100.0, r.rateOfChangeMgDLPerMinute!!, 1e-4, c.hex)
            assertEquals(0, r.esaDuration, c.hex)
            assertEquals(0, r.temperatureStatus, c.hex)
            assertEquals(c.projected, r.projectedGlucose, c.hex)
            assertEquals(14545, r.historicalLifeCount, c.hex)
            assertEquals(108, r.historicalWord, c.hex)
            assertEquals(108, r.historicalReading, c.hex)
            assertEquals(0, r.historicalReadingDQErrorRaw, c.hex)
            assertEquals(Libre3DataQualityError.GOOD, r.historicalReadingDQError, c.hex)
            assertEquals(0, r.historicResultRangeStatusRaw, c.hex)
            assertEquals(Libre3ResultRangeStatus.IN_RANGE, r.historicResultRangeStatus, c.hex)
            assertEquals(108, r.historicalGlucoseMgDL, c.hex)
            assertTrue(r.isHistoricalGlucoseValid, c.hex)
            assertTrue(r.isHistoricalDQGood, c.hex)
            assertEquals(0x0b, r.trendAndStatusByte, c.hex)
            assertEquals(3, r.trendRaw, c.hex)
            assertEquals(3, r.trend, c.hex)
            assertEquals(Libre3Trend.STABLE, r.trendKind, c.hex)
            assertEquals(1, r.rest, c.hex)
            assertEquals(1, r.actionableStatus, c.hex)
            assertEquals(Libre3ActionableStatus.ACTIONABLE, r.actionability, c.hex)
            assertEquals(c.glucose, r.uncappedCurrentMgDL, c.hex)
            assertEquals(108, r.uncappedHistoricMgDL, c.hex)
            assertEquals(c.temp, r.temperature, c.hex)
            assertEquals(c.fastData, hexOf(r.fastData), c.hex)
            assertContentEquals(c.fastDataWords, r.fastDataWordsLE, c.hex)
            assertEquals(c.lifeCount, r.wordsLE[0], c.hex)
            assertEquals(c.glucose, r.wordsLE[1], c.hex)
            assertEquals(0, r.trailingByte, c.hex)
        }
    }

    @Test
    fun realtimeGlucoseReadingUsesCurrentNormalization() {
        val base = raw("e3386200d6ff0000f023d1386c000b62006c00200a9e03113d03100000")

        val low = RealtimeGlucoseReading.parse(setU16(base, 15, 38))
        assertEquals(98, low.readingMgDL)
        assertEquals(38, low.uncappedCurrentMgDL)
        assertEquals(39, low.currentGlucoseMgDL)
        assertEquals(Libre3GlucoseValueStatus.BelowDisplayRange(38, 39), low.currentGlucoseStatus)
        assertTrue(low.isCurrentGlucoseValid)

        val high = RealtimeGlucoseReading.parse(setU16(base, 15, 600))
        assertEquals(600, high.uncappedCurrentMgDL)
        assertEquals(501, high.currentGlucoseMgDL)
        assertEquals(Libre3GlucoseValueStatus.AboveDisplayRange(600, 501), high.currentGlucoseStatus)
        assertTrue(high.isCurrentGlucoseValid)

        val invalidZero = RealtimeGlucoseReading.parse(setU16(base, 15, 0))
        assertNull(invalidZero.currentGlucoseMgDL)
        assertEquals(Libre3GlucoseValueStatus.Unavailable(0), invalidZero.currentGlucoseStatus)
        assertFalse(invalidZero.isCurrentGlucoseValid)

        val invalidHigh = RealtimeGlucoseReading.parse(setU16(base, 15, 1000))
        assertNull(invalidHigh.currentGlucoseMgDL)
        assertEquals(Libre3GlucoseValueStatus.Unavailable(1000), invalidHigh.currentGlucoseStatus)
        assertFalse(invalidHigh.isCurrentGlucoseValid)
    }

    @Test
    fun realtimeGlucoseReadingParsesAbbottDQAndStatusBits() {
        val base = raw("e3386200d6ff0000f023d1386c000b62006c00200a9e03113d03100000")

        val currentDQ = RealtimeGlucoseReading.parse(setU16(base, 2, 0x8062))
        assertEquals(98, currentDQ.readingMgDL)
        assertEquals(0x8062, currentDQ.dqErrorRaw)
        assertEquals(Libre3DataQualityError.NOT_DISPLAYABLE, currentDQ.dqError)
        assertEquals(0, currentDQ.sensorConditionRaw)
        assertEquals(Libre3SensorCondition.OK, currentDQ.sensorCondition)
        assertFalse(currentDQ.isCurrentDQGood)
        assertFalse(currentDQ.isCurrentGlucoseUsable)

        val currentSensorCondition = RealtimeGlucoseReading.parse(setU16(base, 2, 0x2062))
        assertEquals(98, currentSensorCondition.readingMgDL)
        assertEquals(0, currentSensorCondition.dqErrorRaw)
        assertEquals(Libre3DataQualityError.GOOD, currentSensorCondition.dqError)
        assertEquals(1, currentSensorCondition.sensorConditionRaw)
        assertEquals(Libre3SensorCondition.INVALID, currentSensorCondition.sensorCondition)
        assertFalse(currentSensorCondition.isCurrentDQGood)

        val historicalDQ = RealtimeGlucoseReading.parse(setU16(base, 12, 0x806c))
        assertEquals(108, historicalDQ.historicalReading)
        assertEquals(0x806c, historicalDQ.historicalReadingDQErrorRaw)
        assertEquals(Libre3DataQualityError.NOT_DISPLAYABLE, historicalDQ.historicalReadingDQError)
        assertEquals(0, historicalDQ.historicResultRangeStatusRaw)
        assertEquals(Libre3ResultRangeStatus.IN_RANGE, historicalDQ.historicResultRangeStatus)
        assertFalse(historicalDQ.isHistoricalDQGood)

        val historicalRange = RealtimeGlucoseReading.parse(setU16(base, 12, 0x406c))
        assertEquals(108, historicalRange.historicalReading)
        assertEquals(0, historicalRange.historicalReadingDQErrorRaw)
        assertEquals(Libre3DataQualityError.GOOD, historicalRange.historicalReadingDQError)
        assertEquals(2, historicalRange.historicResultRangeStatusRaw)
        assertEquals(Libre3ResultRangeStatus.ABOVE_RANGE, historicalRange.historicResultRangeStatus)
        assertFalse(historicalRange.isHistoricalDQGood)

        val notActionableBytes = base.copyOf()
        notActionableBytes[14] = 0x03
        val notActionable = RealtimeGlucoseReading.parse(notActionableBytes)
        assertEquals(3, notActionable.trend)
        assertEquals(Libre3Trend.STABLE, notActionable.trendKind)
        assertEquals(0, notActionable.actionableStatus)
        assertEquals(Libre3ActionableStatus.NOT_ACTIONABLE, notActionable.actionability)
        // Actionability is advisory: an otherwise-clean non-actionable reading
        // stays usable.
        assertTrue(notActionable.isCurrentGlucoseUsable)

        val temperatureStatus = RealtimeGlucoseReading.parse(setU16(base, 6, 0x0201))
        assertEquals(0x0201, temperatureStatus.temperatureStatus)
    }

    // MARK: - Patch status

    @Test
    fun patchStatusParsesLivePlaintext() {
        val status = PatchStatus.parse(raw("9d280000e0000704e4381300"))

        assertEquals(10397, status.lifeCount)
        assertEquals(0, status.errorData)
        assertEquals(224, status.eventDataRaw)
        assertEquals(4224, status.eventData)
        assertEquals(7, status.index)
        assertEquals(8, status.totalEvents)
        assertEquals(4, status.patchState)
        assertEquals(Libre3PatchState(4), status.patchStateKind)
        assertTrue(status.isPatchStateActive)
        assertEquals(14564, status.currentLifeCount)
        assertEquals(19, status.stackDisconnectReason)
        assertEquals(0, status.appDisconnectReason)
        assertFalse(status.hasErrorData)
        assertTrue(status.hasDisconnectReason)
        assertEquals(Libre3SensorError.None, status.sensorError)
        assertFalse(status.isShutdownTerminated)
        assertFalse(status.isInsertionFailure)

        val lifecycle = status.lifecycle(wearDurationMinutes = 20160)
        assertEquals(SensorLifecyclePhase.ACTIVE, lifecycle.phase)
        assertTrue(lifecycle.isWarmupComplete)
        assertFalse(lifecycle.isExpired)
        assertEquals(5596L, lifecycle.remainingWearMinutes)
    }

    @Test
    fun patchStatusDecodesSensorErrorCodes() {
        // Synthetic frames: the healthy live frame with only the errorData
        // word (offset 2-3, LE) swapped.
        fun status(errHex: String): PatchStatus =
            PatchStatus.parse(raw("9d28${errHex}e0000704e4381300"))

        assertEquals(Libre3SensorError.None, status("0000").sensorError)
        assertEquals(Libre3SensorError.InsertionFailure, status("0300").sensorError)
        assertEquals(Libre3SensorError.Expired, status("0500").sensorError)
        assertEquals(Libre3SensorError.Terminated, status("0600").sensorError)
        assertEquals(Libre3SensorError.TransmissionError, status("0700").sensorError)
        assertEquals(Libre3SensorError.Terminated, status("0800").sensorError)
        assertEquals(Libre3SensorError.Unknown(9), status("0900").sensorError)

        assertEquals(Libre3SensorAttention.None, status("0000").sensorAttention)
        assertEquals(Libre3SensorAttention.CheckSensor, status("0300").sensorAttention)
        assertEquals(Libre3SensorAttention.SensorEnded, status("0500").sensorAttention)
        assertEquals(Libre3SensorAttention.SensorEnded, status("0600").sensorAttention)
        assertEquals(Libre3SensorAttention.CheckSensor, status("0700").sensorAttention)
        assertEquals(Libre3SensorAttention.ReplaceSensor, status("0800").sensorAttention)
        assertEquals(Libre3SensorAttention.Unknown(9), status("0900").sensorAttention)
        // Code 7 is a transient transmission error: notifies the user (soft
        // check-sensor) but is NOT a replace condition. Code 8 is the terminal replace.
        assertFalse(status("0700").shouldNotifyReplaceSensor)
        assertTrue(status("0700").shouldNotifyUser)
        assertTrue(status("0800").shouldNotifyReplaceSensor)
        assertFalse(status("0500").shouldNotifyReplaceSensor)
        assertTrue(status("0300").shouldNotifyUser)
        assertFalse(status("0000").shouldNotifyUser)

        // Expiry remains BLE-visible until shutdown; terminated is the
        // post-shutdown/no-advertising state. Insertion failure is separate.
        assertTrue(status("0300").isInsertionFailure)
        assertFalse(status("0000").isInsertionFailure)
        assertFalse(status("0500").isInsertionFailure)
        assertFalse(status("0600").isInsertionFailure)
        assertFalse(status("0700").isInsertionFailure)
        assertFalse(status("0800").isInsertionFailure)
        assertFalse(status("0900").isInsertionFailure)

        assertTrue(status("0600").isShutdownTerminated)
        assertTrue(status("0800").isShutdownTerminated)
        assertFalse(status("0000").isShutdownTerminated)
        assertFalse(status("0300").isShutdownTerminated)
        assertFalse(status("0500").isShutdownTerminated)
        assertFalse(status("0700").isShutdownTerminated)
        assertFalse(status("0900").isShutdownTerminated)
    }

    @Test
    fun patchStatusExposesPatchStateGroups() {
        fun status(patchState: Int): PatchStatus {
            val bytes = raw("9d280000e0000704e4381300")
            bytes[7] = patchState.toByte()
            return PatchStatus.parse(bytes)
        }

        val active = status(4)
        assertEquals(Libre3PatchState(4), active.patchStateKind)
        assertTrue(active.isPatchStateActive)
        assertFalse(active.isPatchStateExpiredOrError)
        assertFalse(active.isPatchStateTerminated)

        for (state in intArrayOf(3, 5, 7)) {
            val value = status(state)
            assertEquals(Libre3PatchState(state), value.patchStateKind)
            assertFalse(value.isPatchStateActive)
            assertTrue(value.isPatchStateExpiredOrError)
            assertFalse(value.isPatchStateTerminated)
        }

        assertEquals(Libre3SensorAttention.CheckSensor, status(3).sensorAttention)
        assertEquals(Libre3SensorAttention.SensorEnded, status(5).sensorAttention)
        assertEquals(Libre3SensorAttention.SensorEnded, status(6).sensorAttention)
        assertEquals(Libre3SensorAttention.ReplaceSensor, status(7).sensorAttention)
        assertEquals(Libre3SensorAttention.ReplaceSensor, status(8).sensorAttention)
        assertTrue(status(7).shouldNotifyReplaceSensor)

        for (state in intArrayOf(6, 8)) {
            val value = status(state)
            assertEquals(Libre3PatchState(state), value.patchStateKind)
            assertFalse(value.isPatchStateActive)
            assertFalse(value.isPatchStateExpiredOrError)
            assertTrue(value.isPatchStateTerminated)
        }

        val unknown = status(9)
        assertEquals(Libre3PatchState(9), unknown.patchStateKind)
        assertFalse(unknown.isPatchStateActive)
        assertFalse(unknown.isPatchStateExpiredOrError)
        assertFalse(unknown.isPatchStateTerminated)
    }

    // MARK: - Data-plane crypto

    @Test
    fun dataPlaneNonceUsesSequenceDescriptorAndPhase6IV() {
        val crypto = DataPlaneCrypto(
            kEnc = raw("4bbce496a63cc9a435adeeb4f78e1617"),
            ivEnc = raw("0000000067c72c01"),
        )
        assertEquals(
            "01004400000000000067c72c01",
            hexOf(crypto.nonce(0x0001, DataPlanePacketKind.PATCH_DATA)),
        )
    }

    @Test
    fun dataPlaneCryptoRoundTripsAndFindsDescriptor() {
        val crypto = DataPlaneCrypto(
            kEnc = raw("4bbce496a63cc9a435adeeb4f78e1617"),
            ivEnc = raw("0000000067c72c01"),
        )
        val plaintext = raw("00112233445566778899aabb")
        val frame = crypto.encrypt(plaintext, 0x0201, DataPlanePacketKind.PATCH_DATA)

        assertEquals(0x01, frame.seq)
        assertEquals(0x02, frame.type)
        assertEquals(0x0201, frame.sequenceNumber)
        assertContentEquals(plaintext, crypto.decrypt(frame, DataPlanePacketKind.PATCH_DATA))

        val found = crypto.decryptTryingAllKinds(frame)
        assertEquals(DataPlanePacketKind.PATCH_DATA, found.kind)
        assertContentEquals(plaintext, found.plaintext)

        assertFailsWith<AesCcm.AesCcmError.MacMismatch> {
            crypto.decrypt(frame, DataPlanePacketKind.KIND0)
        }
    }

    // MARK: - Patch control commands

    @Test
    fun patchControlCommandPlaintextsMatchIOSBuilders() {
        assertEquals(
            "01000105000000",
            hexOf(PatchControlCommand.historicalBackfillGreaterEqual(5).plaintext),
        )
        assertEquals(
            "01010101000000",
            hexOf(PatchControlCommand.clinicalBackfillGreaterEqual(1).plaintext),
        )
        assertEquals(
            "0100018b030000",
            hexOf(PatchControlCommand.backfillGreaterEqual(BackfillStream.HISTORICAL, 907).plaintext),
        )
        assertEquals(
            "04010000000000",
            hexOf(PatchControlCommand.eventLog(1).plaintext),
        )
        assertEquals(
            "06000000000000",
            hexOf(PatchControlCommand.factoryData().plaintext),
        )
        assertEquals(
            "05000000000000",
            hexOf(PatchControlCommand.shutdownPatch().plaintext),
        )
        val error = assertFailsWith<PatchControlCommandError.WrongPlaintextSize> {
            PatchControlCommand("bad", byteArrayOf(0x01))
        }
        assertEquals(1, error.size)
    }

    @Test
    fun patchControlCommandEncryptsTo13ByteWriteFrame() {
        val crypto = DataPlaneCrypto(
            kEnc = raw("4bbce496a63cc9a435adeeb4f78e1617"),
            ivEnc = raw("0000000067c72c01"),
        )
        val command = PatchControlCommand.historicalBackfillGreaterEqual(5)
        val frame = crypto.encrypt(command.plaintext, 0x0001, DataPlanePacketKind.PATCH_CONTROL_WRITE)

        assertEquals(13, frame.raw.size)
        assertEquals(11, frame.encrypted.size)
        assertEquals(0x01, frame.seq)
        assertEquals(0x00, frame.type)
        assertContentEquals(command.plaintext, crypto.decrypt(frame, DataPlanePacketKind.PATCH_CONTROL_WRITE))
    }

    // MARK: - Historical pages + backfill

    @Test
    fun historicalReadingPageParsesLivePlaintext() {
        val page = HistoricalReadingPage.parse(raw("8e0369006b006d006b0069006e00"))

        assertEquals(910, page.startLifeCount)
        assertEquals(935, page.endLifeCount)
        assertContentEquals(intArrayOf(105, 107, 109, 107, 105, 110), page.values)
        val samples = page.samples
        assertEquals(
            listOf(910 to 105, 915 to 107, 920 to 109, 925 to 107, 930 to 105, 935 to 110),
            samples.map { it.lifeCount to it.rawValue },
        )
        assertEquals(105, samples[0].glucoseMgDL)
        assertEquals(Libre3GlucoseValueStatus.Valid(105), samples[0].glucoseStatus)
    }

    @Test
    fun historicalBackfillSummarizesCoverageAndGaps() {
        val first = HistoricalReadingPage.parse(raw("8e0369006b006d006b0069006e00"))
        val second = HistoricalReadingPage.parse(raw("ac036f00710070006c006a006b00"))
        val third = HistoricalReadingPage.parse(raw("e803780079007a007b007c007d00"))

        val backfill = HistoricalBackfill()
        backfill.append(second)
        backfill.append(first)

        assertEquals(910, backfill.firstLifeCount)
        assertEquals(965, backfill.lastLifeCount)
        assertTrue(backfill.isContiguous)
        assertTrue(backfill.gaps.isEmpty())
        assertEquals((910..965 step 5).toList(), backfill.samples.map { it.lifeCount })

        backfill.append(third)
        assertFalse(backfill.isContiguous)
        assertEquals(listOf(HistoricalBackfillGap(965, 1000)), backfill.gaps)
    }
}
