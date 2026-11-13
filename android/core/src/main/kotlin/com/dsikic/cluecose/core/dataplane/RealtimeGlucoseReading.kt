package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Realtime glucose stream record (0x0898177a). 1:1 port of
 * LibreCRKit RealtimeGlucoseReading.swift (29-byte plaintext layout).
 *
 * Packed words: glucose value in bits 0–12, DQ error flag bit 15,
 * status bits 13–14.
 */
class RealtimeGlucoseReading(plaintext: ByteArray) {

    companion object {
        const val PLAINTEXT_SIZE = 29

        fun parse(plaintext: ByteArray): RealtimeGlucoseReading {
            if (plaintext.size != PLAINTEXT_SIZE) {
                throw RealtimeGlucoseReadingError.WrongPlaintextSize(plaintext.size)
            }
            return RealtimeGlucoseReading(plaintext)
        }
    }

    val lifeCount: Int
    val currentWord: Int
    val readingMgDL: Int
    val dqErrorRaw: Int
    val dqError: Libre3DataQualityError
    val sensorConditionRaw: Int
    val sensorCondition: Libre3SensorCondition
    val currentGlucoseMgDL: Int?
    val isCurrentGlucoseValid: Boolean
    val rateOfChangeRaw: Int
    val rateOfChangeMgDLPerMinute: Double?
    val trendRaw: Int
    val esaDuration: Int = 0
    val temperatureStatus: Int
    val projectedGlucose: Int
    val historicalLifeCount: Int
    val historicalWord: Int
    val historicalReading: Int
    val historicalReadingDQErrorRaw: Int
    val historicalReadingDQError: Libre3DataQualityError
    val historicResultRangeStatusRaw: Int
    val historicResultRangeStatus: Libre3ResultRangeStatus
    val historicalGlucoseMgDL: Int?
    val isHistoricalGlucoseValid: Boolean
    val trendAndStatusByte: Int
    val trend: Int
    val trendKind: Libre3Trend
    val rest: Int
    val actionableStatus: Int
    val actionability: Libre3ActionableStatus
    val uncappedCurrentMgDL: Int
    val uncappedHistoricMgDL: Int
    val temperature: Int
    val fastData: ByteArray
    val fastDataWordsLE: IntArray
    val wordsLE: IntArray
    val trailingByte: Int

    val statusBits: Int get() = rest

    val currentGlucoseStatus: Libre3GlucoseValueStatus
        get() = Libre3GlucoseValueStatus.fromRawSensorValue(uncappedCurrentMgDL)

    val historicalGlucoseStatus: Libre3GlucoseValueStatus
        get() = Libre3GlucoseValueStatus.fromRawSensorValue(uncappedHistoricMgDL)

    val rateOfChangeAvailable: Boolean
        get() = rateOfChangeMgDLPerMinute != null

    val isCurrentDQGood: Boolean
        get() = dqError.isGood && sensorCondition == Libre3SensorCondition.OK

    val isHistoricalDQGood: Boolean
        get() = historicalReadingDQError.isGood && historicResultRangeStatus == Libre3ResultRangeStatus.IN_RANGE

    val isCurrentGlucoseUsable: Boolean
        get() = isCurrentGlucoseValid && isCurrentDQGood

    init {
        val rateOfChangeRaw = u16(plaintext, 4).toShort().toInt() // Int16 bit pattern
        val currentWord = u16(plaintext, 2)
        val historicalWord = u16(plaintext, 12)
        val trendAndRest = ByteUtil.u8(plaintext, 14)
        val trend = trendAndRest and 0x07
        val actionableStatus = if ((trendAndRest and 0x08) == 0) 0 else 1
        val uncappedCurrentMgDL = u16(plaintext, 15)
        val currentGlucoseMgDL = Libre3GlucoseValueStatus.fromRawSensorValue(uncappedCurrentMgDL).displayMgDL
        val uncappedHistoricMgDL = u16(plaintext, 17)
        val historicalGlucoseMgDL = Libre3GlucoseValueStatus.fromRawSensorValue(uncappedHistoricMgDL).displayMgDL

        this.lifeCount = u16(plaintext, 0)
        this.currentWord = currentWord
        this.readingMgDL = glucoseValueFromPackedWord(currentWord)
        this.dqErrorRaw = dqErrorRawFromPackedWord(currentWord)
        this.dqError = Libre3DataQualityError.fromRaw(this.dqErrorRaw)
        this.sensorConditionRaw = statusBits13To14(currentWord)
        this.sensorCondition = Libre3SensorCondition.fromRaw(this.sensorConditionRaw)
        this.currentGlucoseMgDL = currentGlucoseMgDL
        this.isCurrentGlucoseValid = currentGlucoseMgDL != null
        this.rateOfChangeRaw = rateOfChangeRaw
        this.rateOfChangeMgDLPerMinute =
            if (rateOfChangeRaw == Short.MIN_VALUE.toInt()) null else rateOfChangeRaw / 100.0
        this.trendRaw = trend
        this.temperatureStatus = u16(plaintext, 6)
        this.projectedGlucose = u16(plaintext, 8)
        this.historicalLifeCount = u16(plaintext, 10)
        this.historicalWord = historicalWord
        this.historicalReading = glucoseValueFromPackedWord(historicalWord)
        this.historicalReadingDQErrorRaw = dqErrorRawFromPackedWord(historicalWord)
        this.historicalReadingDQError = Libre3DataQualityError.fromRaw(this.historicalReadingDQErrorRaw)
        this.historicResultRangeStatusRaw = statusBits13To14(historicalWord)
        this.historicResultRangeStatus = Libre3ResultRangeStatus.fromRaw(this.historicResultRangeStatusRaw)
        this.historicalGlucoseMgDL = historicalGlucoseMgDL
        this.isHistoricalGlucoseValid = historicalGlucoseMgDL != null
        this.trendAndStatusByte = trendAndRest
        this.trend = trend
        this.trendKind = Libre3Trend.fromRaw(trend)
        this.rest = (trendAndRest ushr 3) and 0x1F
        this.actionableStatus = actionableStatus
        this.actionability = Libre3ActionableStatus.fromRaw(actionableStatus)
        this.uncappedCurrentMgDL = uncappedCurrentMgDL
        this.uncappedHistoricMgDL = uncappedHistoricMgDL
        this.temperature = u16(plaintext, 19)
        this.fastData = ByteUtil.slice(plaintext, 21, 29)
        this.fastDataWordsLE = intArrayOf(
            u16(plaintext, 21), u16(plaintext, 23), u16(plaintext, 25), u16(plaintext, 27),
        )

        val words = ArrayList<Int>(14)
        var offset = 0
        while (offset + 1 < plaintext.size) {
            words.add(u16(plaintext, offset))
            offset += 2
        }
        this.wordsLE = words.toIntArray()
        this.trailingByte = ByteUtil.u8(plaintext, plaintext.size - 1)
    }

    private fun u16(data: ByteArray, offset: Int): Int = ByteUtil.u16le(data, offset)

    private fun glucoseValueFromPackedWord(value: Int): Int = value and 0x1FFF

    private fun dqErrorRawFromPackedWord(value: Int): Int = if ((value and 0x8000) == 0) 0 else value

    private fun statusBits13To14(value: Int): Int = (value ushr 13) and 0x03
}

sealed class RealtimeGlucoseReadingError : Exception() {
    data class WrongPlaintextSize(val size: Int) : RealtimeGlucoseReadingError()
}
