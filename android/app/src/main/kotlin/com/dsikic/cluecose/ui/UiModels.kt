package com.dsikic.cluecose.ui

import kotlin.math.roundToInt

/**
 * UI-state types mirroring the iOS app (CluecoseCore `GlucoseTypes` /
 * `GlucoseAnalytics`). Plain values the Compose layer renders; the data layer
 * (ViewModel + repository) produces them.
 */

data class GlucoseSample(val dateMs: Long, val mgdl: Double)

/** iOS `Trend` — Libre-style trend arrows and labels. */
enum class Trend(val arrow: String, val label: String) {
    SHARP_RISE("↑", "Rising sharply"),
    RISE("↗", "Rising"),
    FLAT("→", "Steady"),
    FALL("↘", "Falling"),
    SHARP_FALL("↓", "Falling sharply"),
    UNKNOWN("—", "No trend"),
}

/**
 * Glucose range category. iOS has the standard 3-way split
 * (low < 70 <= high > 180); Android mirrors the same display colors and adds
 * the severe sub-thresholds (54 / 250) that the tips engine already knows.
 */
enum class RangeCategory {
    VERY_LOW, LOW, IN_RANGE, HIGH, VERY_HIGH;

    companion object {
        fun from(mgdl: Double): RangeCategory = when {
            mgdl < 54 -> VERY_LOW
            mgdl < 70 -> LOW
            mgdl <= 180 -> IN_RANGE
            mgdl <= 250 -> HIGH
            else -> VERY_HIGH
        }
    }
}

/** iOS `BurnMode` — the storage-vs-burning verdict. */
enum class BurnMode(val label: String) {
    STORING("Storing"),
    BALANCED("Balanced"),
    BURNING("Burning"),
}

/**
 * iOS `GlucoseStats` — 24 h analytics block. tir/tar/tbr/tbr2 are fractions
 * 0…1; AUC is mg/dL·min.
 */
data class GlucoseStats(
    val count: Int = 0,
    val mean: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val stdDev: Double = 0.0,
    val cv: Double = 0.0,
    val gmi: Double = 0.0,
    val tir: Double = 0.0,
    val tar: Double = 0.0,
    val tbr: Double = 0.0,
    val tbr2: Double = 0.0,
    val aucAbove140: Double = 0.0,
    val aucShare: Double = 0.0,
) {
    val isMeaningful: Boolean get() = count >= 4
}

/** iOS `GlucoseTip.Kind`. */
enum class GlucoseTipKind {
    LOW, HIGH, VARIABILITY, RANGE, STORAGE, GOOD,
}

data class GlucoseTip(val kind: GlucoseTipKind, val text: String)

/** iOS `GlucoseUnit` — 1 mmol/L == 18.016 mg/dL. */
enum class GlucoseUnit(val label: String) {
    MGDL("mg/dL"),
    MMOL("mmol/L");

    companion object {
        const val FACTOR = 18.016
    }

    /** Hero value: mg/dL -> 0 decimals, mmol/L -> 1 decimal. */
    fun display(mgdl: Double): String = when (this) {
        MGDL -> mgdl.roundToInt().toString()
        MMOL -> String.format("%.1f", mgdl / FACTOR)
    }

    /** Stat cells: same as [display] (iOS `format`). */
    fun format(mgdl: Double): String = display(mgdl)
}

/** Connection lifecycle; drives the Listening vs Main screen switch. */
enum class ConnectionState {
    IDLE, LISTENING, CONNECTING, CONNECTED, FAILED, RECONNECTING,
}

/**
 * Whole UI snapshot the screens render from. Recomputed by the ViewModel
 * whenever anything changes (iOS `refreshDerived` / `recomputeAll`).
 */
data class UiState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val nowMs: Long = System.currentTimeMillis(),
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val samples: List<GlucoseSample> = emptyList(),
    val trend: Trend = Trend.UNKNOWN,
    val stats: GlucoseStats = GlucoseStats(),
    val burnMode: BurnMode = BurnMode.BALANCED,
    val tips: List<GlucoseTip> = emptyList(),
    val latest: GlucoseSample? = null,
    /** Live progress line from the reader (iOS `status`). */
    val status: String = "",
    /** "This sensor is expired — apply a new one…" style error, if any. */
    val error: String? = null,
    val sensorAgeText: String? = null,
    /** Paired sensor serial (settings "Paired sensor" row); null when unpaired. */
    val sensorName: String? = null,
    /** True while an NFC pairing exchange is awaiting a tag hold. */
    val nfcAwaiting: Boolean = false,
    /** True while the hero should show "Live" instead of "Last reading…". */
    val isFresh: Boolean = false,
    val isWarmingUp: Boolean = false,
    /** Reading age like "1 min ago" / "2 h ago" (iOS `readingAgeText`). */
    val readingAgeText: String? = null,
) {
    /** iOS `liveReading` guard: hero only for data we can trust. */
    val hasLiveReading: Boolean
        get() = latest != null && (isFresh || connectionState == ConnectionState.CONNECTED)
}
