package com.dsikic.cluecose.core.dataplane

import com.dsikic.cluecose.core.util.ByteUtil

/**
 * Historical paged backfill record (0x0898195a). 1:1 port of
 * LibreCRKit HistoricalReadingPage.swift.
 *
 * 14-byte plaintext = seven LE u16: word0 = start life count, then six
 * glucose samples at 5-minute stride.
 */
class HistoricalReadingPage(val startLifeCount: Int, val values: IntArray) {

    companion object {
        const val PLAINTEXT_SIZE = 14
        const val SAMPLE_SPACING_LIFE_COUNTS = 5

        fun parse(plaintext: ByteArray): HistoricalReadingPage {
            if (plaintext.size != PLAINTEXT_SIZE) {
                throw HistoricalReadingPageError.WrongPlaintextSize(plaintext.size)
            }
            val words = IntArray(7) { i -> ByteUtil.u16le(plaintext, 2 * i) }
            return HistoricalReadingPage(startLifeCount = words[0], values = words.copyOfRange(1, 7))
        }
    }

    val samples: List<HistoricalReadingSample>
        get() = values.mapIndexed { index, value ->
            HistoricalReadingSample(
                lifeCount = startLifeCount + index * SAMPLE_SPACING_LIFE_COUNTS,
                rawValue = value,
            )
        }

    val endLifeCount: Int
        get() = startLifeCount + (values.size - 1) * SAMPLE_SPACING_LIFE_COUNTS

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistoricalReadingPage) return false
        return startLifeCount == other.startLifeCount && values.contentEquals(other.values)
    }

    override fun hashCode(): Int = (startLifeCount * 31) xor values.contentHashCode()

    override fun toString(): String =
        "HistoricalReadingPage(startLC=$startLifeCount, endLC=$endLifeCount, " +
            "values=${values.joinToString(",") { "%04x".format(it) }})"
}

data class HistoricalReadingSample(val lifeCount: Int, val rawValue: Int) {
    val glucoseMgDL: Int?
        get() = glucoseStatus.displayMgDL

    val glucoseStatus: Libre3GlucoseValueStatus
        get() = Libre3GlucoseValueStatus.fromRawSensorValue(rawValue)
}

sealed class HistoricalReadingPageError : Exception() {
    data class WrongPlaintextSize(val size: Int) : HistoricalReadingPageError()
}
