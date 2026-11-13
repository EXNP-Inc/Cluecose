package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Clinical-stream record (0x08981ab8). 1:1 port of
 * LibreCRKit ClinicalReadingRecord.swift.
 *
 * Distinct from [HistoricalReadingPage]: clinical pages are NOT six samples
 * at 5-minute stride — each page is a single time-point record with seven
 * 16-bit fields, emitted once per minute while the sensor is connected.
 *
 * | word | bytes | meaning                                              |
 * | ---- | ----- | ---------------------------------------------------- |
 * |  0   | 0–1   | `lifeCount` — current minute (page emitted per minute)|
 * |  1   | 2–3   | raw sensor channel (rises with glucose)              |
 * |  2   | 4–5   | raw sensor channel                                   |
 * |  3   | 6–7   | raw sensor channel (high byte pinned 0x0e; temp?)    |
 * |  4   | 8–9   | reserved / zero (only 0x0000 observed)               |
 * |  5   | 10–11 | current-minute glucose — equals realtime current     |
 * |  6   | 12–13 | most recent 5-min committed glucose                  |
 *
 * Gap-fill behavior: the clinical CCCD buffers records sensor-side while the
 * host is disconnected and replays the buffered window in a burst on
 * resubscribe — the modeled way to recover minute-resolution glucose for a
 * disconnect window.
 */
class ClinicalReadingRecord(plaintext: ByteArray) {

    companion object {
        const val PLAINTEXT_SIZE = 14

        fun parse(plaintext: ByteArray): ClinicalReadingRecord {
            if (plaintext.size != PLAINTEXT_SIZE) {
                throw ClinicalReadingRecordError.WrongPlaintextSize(plaintext.size)
            }
            return ClinicalReadingRecord(plaintext)
        }
    }

    /** LifeCount of this clinical record (per-minute granularity). */
    val lifeCount: Int

    /** Raw sensor channels (words 1–3). Not glucose; diagnostics only. */
    val rawSensorWord1: Int
    val rawSensorWord2: Int
    val rawSensorWord3: Int

    /** Word 4 — only 0x0000 observed. */
    val reservedWord: Int

    /** Raw 16-bit current-minute glucose value, keyed at [lifeCount]. */
    val currentGlucoseRaw: Int

    /** Raw 16-bit most-recent 5-minute committed glucose value. Its lifeCount
     *  is the most recent 5-minute boundary, NOT this record's [lifeCount]. */
    val historicGlucoseRaw: Int

    val currentGlucose: Libre3GlucoseValueStatus
        get() = Libre3GlucoseValueStatus.fromRawSensorValue(currentGlucoseRaw)

    val currentGlucoseMgDL: Int?
        get() = currentGlucose.displayMgDL

    val historicGlucose: Libre3GlucoseValueStatus
        get() = Libre3GlucoseValueStatus.fromRawSensorValue(historicGlucoseRaw)

    val historicGlucoseMgDL: Int?
        get() = historicGlucose.displayMgDL

    /**
     * Best-effort estimate of the lifeCount that [historicGlucoseRaw]
     * represents: snap to the last 5-min boundary at `lifeCount − 17`
     * (Abbott's HISTORIC_POINT_LATENCY). `null` when the arithmetic
     * underflows (very early in sensor life). Prefer the realtime frame's
     * own [RealtimeGlucoseReading.historicalLifeCount] when available.
     */
    val historicLifeCountEstimate: Int?
        get() {
            val lagged = lifeCount - 17
            if (lagged < 0) return null
            return lagged - (lagged % 5)
        }

    init {
        val words = IntArray(7) { i -> ByteUtil.u16le(plaintext, 2 * i) }
        lifeCount = words[0]
        rawSensorWord1 = words[1]
        rawSensorWord2 = words[2]
        rawSensorWord3 = words[3]
        reservedWord = words[4]
        currentGlucoseRaw = words[5]
        historicGlucoseRaw = words[6]
    }

    override fun toString(): String =
        "ClinicalReadingRecord(lc=$lifeCount, currentRaw=0x${"%04x".format(currentGlucoseRaw)}, " +
            "historicRaw=0x${"%04x".format(historicGlucoseRaw)})"
}

sealed class ClinicalReadingRecordError : Exception() {
    data class WrongPlaintextSize(val size: Int) : ClinicalReadingRecordError()
}
