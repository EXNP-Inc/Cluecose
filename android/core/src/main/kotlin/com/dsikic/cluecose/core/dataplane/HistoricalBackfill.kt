package com.dsikic.cluecose.core.dataplane

/**
 * Backfill bookkeeping (1:1 port of LibreCRKit HistoricalBackfill.swift).
 *
 * Collects historical pages as they arrive and computes the gaps between
 * committed samples — the gap list drives the bounded backfill retry policy
 * (a gap the sensor won't fill is recorded and skipped, never retried).
 */
class HistoricalBackfill {

    val pages: MutableList<HistoricalReadingPage> = mutableListOf()

    fun append(page: HistoricalReadingPage) {
        pages.add(page)
    }

    val samples: List<HistoricalReadingSample>
        get() = pages.flatMap { it.samples }.sortedBy { it.lifeCount }

    val firstLifeCount: Int?
        get() = samples.firstOrNull()?.lifeCount

    val lastLifeCount: Int?
        get() = samples.lastOrNull()?.lifeCount

    val gaps: List<HistoricalBackfillGap>
        get() {
            val ordered = samples
            if (ordered.size <= 1) return emptyList()
            val out = mutableListOf<HistoricalBackfillGap>()
            for (i in 0 until ordered.size - 1) {
                val previous = ordered[i]
                val next = ordered[i + 1]
                if (next.lifeCount > previous.lifeCount + HistoricalReadingPage.SAMPLE_SPACING_LIFE_COUNTS) {
                    out.add(HistoricalBackfillGap(previous.lifeCount, next.lifeCount))
                }
            }
            return out
        }

    val isContiguous: Boolean
        get() = gaps.isEmpty()
}

data class HistoricalBackfillGap(val afterLifeCount: Int, val beforeLifeCount: Int) {
    val size: Int
        get() = beforeLifeCount - afterLifeCount

    override fun toString(): String =
        "gap after LC $afterLifeCount, before LC $beforeLifeCount ($size counts)"
}

enum class BackfillStream(val wireValue: Int, val label: String) {
    HISTORICAL(0x00, "historic"),
    CLINICAL(0x01, "clinical"),
}
