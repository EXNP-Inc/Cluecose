package com.dsikic.cluecose.ui

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Self-contained port of iOS `CluecoseCore/GlucoseAnalytics` — the numbers and
 * judgment the app renders. Kept out of :core on purpose: the stub UI must
 * stay self-contained until the real BLE-backed repository lands.
 *
 * Thresholds are the usual CGM conventions (70/180 mg/dL bands, 140 mg/dL
 * storage line, 36% CV, 70% TIR) — same as iOS.
 */
object DemoAnalytics {

    /** Storage-pressure threshold: AUC above 140 in mg/dL·min over the window. */
    const val STORAGE_AUC_24H = 2000.0

    private const val WINDOW_MS = 24L * 60 * 60 * 1000
    private const val MIN_MS = 60_000L

    /**
     * Libre-style trend from the slope over the last ~35 minutes of samples
     * (least-squares slope in mg/dL per minute).
     */
    fun computeTrend(samples: List<GlucoseSample>, nowMs: Long): Trend {
        val recent = samples.filter { nowMs - it.dateMs <= 35L * 60_000 }
        if (recent.size < 3) return Trend.UNKNOWN
        val n = recent.size.toDouble()
        // t = minutes ago (positive), v = mg/dL
        val meanT = recent.sumOf { nowMs - it.dateMs }.toDouble() / n / MIN_MS
        val meanV = recent.sumOf { it.mgdl } / n
        var num = 0.0
        var den = 0.0
        for (s in recent) {
            val t = (nowMs - s.dateMs).toDouble() / MIN_MS
            val v = s.mgdl
            num += (t - meanT) * (v - meanV)
            den += (t - meanT) * (t - meanT)
        }
        if (den <= 0.0) return Trend.UNKNOWN
        // t grows backwards, so slope d v/d t is sign-flipped vs d v/d(age).
        val slopePerMin = -num / den
        return when {
            slopePerMin < -1.2 -> Trend.SHARP_FALL
            slopePerMin < -0.4 -> Trend.FALL
            slopePerMin >= 1.2 -> Trend.SHARP_RISE
            slopePerMin >= 0.4 -> Trend.RISE
            else -> Trend.FLAT
        }
    }

    /**
     * Full stats block for the last `windowMs` (or everything if shorter).
     * Time-weighted like iOS: each sample "covers" until the next sample,
     * gaps over 15 min are dropped.
     */
    fun stats(samples: List<GlucoseSample>, nowMs: Long, windowMs: Long = WINDOW_MS): GlucoseStats {
        val recent = samples
            .filter { it.dateMs >= nowMs - windowMs }
            .sortedBy { it.dateMs }
        if (recent.isEmpty()) return GlucoseStats()

        val values = recent.map { it.mgdl }
        val mean = values.sum() / values.size
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val stdDev = sqrt(variance)

        var total = 0.0
        var inRange = 0.0
        var above = 0.0
        var below = 0.0
        var below2 = 0.0
        var auc = 0.0
        for (i in recent.indices) {
            val s = recent[i]
            val nextDateMs = if (i == recent.size - 1) {
                minOf(nowMs, s.dateMs + MIN_MS)
            } else {
                recent[i + 1].dateMs
            }
            val dt = (nextDateMs - s.dateMs).toDouble()
            if (dt <= 0.0 || dt > 15 * 60_000.0) continue
            total += dt
            val v = s.mgdl
            if (v in 70.0..180.0) inRange += dt
            if (v > 180.0) above += dt
            if (v < 70.0) below += dt
            if (v < 54.0) below2 += dt
            if (i < recent.size - 1) {
                // Trapezoid of (v - 140) above the 140 line.
                val v2 = recent[i + 1].mgdl
                val y1 = max(0.0, v - 140.0)
                val y2 = max(0.0, v2 - 140.0)
                auc += (y1 + y2) / 2.0 * dt
            }
        }

        return GlucoseStats(
            count = values.size,
            mean = mean,
            min = values.min(),
            max = values.max(),
            stdDev = stdDev,
            cv = if (mean > 0.0) stdDev / mean * 100.0 else 0.0,
            gmi = 3.31 + 0.0239 * mean,
            tir = if (total > 0.0) inRange / total else 0.0,
            tar = if (total > 0.0) above / total else 0.0,
            tbr = if (total > 0.0) below / total else 0.0,
            tbr2 = if (total > 0.0) below2 / total else 0.0,
            // AUC published in mg/dL·MIN (iOS does the same /60 from seconds).
            aucAbove140 = auc / 60_000.0,
            aucShare = if (total > 0.0) auc / total / 140.0 else 0.0,
        )
    }

    /** Storage vs burning verdict (iOS `GlucoseAnalytics.burnMode`). */
    fun burnMode(stats: GlucoseStats, trend: Trend): BurnMode {
        if (!stats.isMeaningful) return BurnMode.BALANCED
        // Scale the storage threshold to the window (designed around 24 h).
        val hoursCovered = stats.count / 60.0
        val threshold = STORAGE_AUC_24H * max(1.0, hoursCovered / 24.0)
        if (stats.aucAbove140 > threshold) return BurnMode.STORING
        if (stats.aucAbove140 < threshold / 4.0) {
            return when (trend) {
                Trend.FALL, Trend.SHARP_FALL -> BurnMode.BURNING
                else -> if (stats.mean < 110.0) BurnMode.BURNING else BurnMode.BALANCED
            }
        }
        return BurnMode.BALANCED
    }

    /**
     * Rule-based tips, most urgent first — verbatim iOS copy. Informational
     * only: the app is not a medical device.
     */
    fun tips(
        stats: GlucoseStats,
        latest: GlucoseSample?,
        trend: Trend,
        storageThreshold: Double = STORAGE_AUC_24H,
    ): List<GlucoseTip> {
        val tips = mutableListOf<GlucoseTip>()
        if (latest != null) {
            when {
                latest.mgdl < 54 -> tips += GlucoseTip(
                    GlucoseTipKind.LOW,
                    "Glucose is very low right now. If you have symptoms, take fast-acting carbs and recheck in 15 minutes.",
                )
                latest.mgdl < 70 -> tips += GlucoseTip(
                    GlucoseTipKind.LOW,
                    "You're below range. Fast-acting carbs (15 g) and a recheck in 15 minutes is the usual response if you feel off.",
                )
                latest.mgdl > 250 -> tips += GlucoseTip(
                    GlucoseTipKind.HIGH,
                    "Sustained high. Water and a walk tend to help; check ketones if this has been going on a while and you feel unwell.",
                )
                latest.mgdl > 180 -> tips += GlucoseTip(
                    GlucoseTipKind.HIGH,
                    "Above range right now. A 10–15 min walk after eating is the single best thing that blunts the next part of this spike.",
                )
            }
        }

        if (stats.isMeaningful) {
            if (stats.tbr > 0.04) {
                tips += GlucoseTip(
                    GlucoseTipKind.LOW,
                    "${(stats.tbr * 100).toInt()}% of the last day was below 70 mg/dL — that's the pattern worth breaking. Check what happens before bedtime.",
                )
            }
            if (stats.cv > 36) {
                tips += GlucoseTip(
                    GlucoseTipKind.VARIABILITY,
                    "Your swings are large (CV ${stats.cv.toInt()}% — the target is under 36%). Consistent meal timing and carb amounts do most of the work here.",
                )
            }
            if (stats.tir < 0.70 && stats.tar > stats.tbr) {
                tips += GlucoseTip(
                    GlucoseTipKind.RANGE,
                    "Time in range is ${(stats.tir * 100).toInt()}%. The wins are in your biggest excursions — look at the chart and find the two worst stretches.",
                )
            }
            if (stats.aucAbove140 > storageThreshold) {
                tips += GlucoseTip(
                    GlucoseTipKind.STORAGE,
                    "Storage pressure was high this window — repeated time above 140 means insulin was working overtime, and that's when glucose gets shunted to fat. Pairing carbs with protein/fibre and moving after meals is the direct fix.",
                )
            }
            if (stats.tir >= 0.90 && stats.aucAbove140 < storageThreshold / 4.0) {
                tips += GlucoseTip(
                    GlucoseTipKind.GOOD,
                    "Excellent window: ${(stats.tir * 100).toInt()}% in range with low storage pressure. This is the state where the body burns fuel instead of storing it.",
                )
            }
        }
        if (tips.isEmpty()) {
            tips += GlucoseTip(GlucoseTipKind.GOOD, "Looking steady. Keep doing what you're doing.")
        }
        return tips
    }

    /** iOS `readingAgeText`. */
    fun readingAgeText(latest: GlucoseSample?, nowMs: Long): String? {
        if (latest == null) return null
        val minutes = (nowMs - latest.dateMs) / MIN_MS
        return when {
            minutes < 1 -> "just now"
            minutes == 1L -> "1 min ago"
            minutes < 60 -> "$minutes min ago"
            minutes / 60 == 1L -> "1 h ago"
            else -> "${minutes / 60} h ago"
        }
    }

    /** iOS `sensorAgeText` from minutes since sensor application. */
    fun sensorAgeText(ageMinutes: Int?): String? {
        if (ageMinutes == null) return null
        val days = ageMinutes / (24 * 60)
        val hours = (ageMinutes % (24 * 60)) / 60
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h"
            else -> "$ageMinutes min"
        }
    }
}
