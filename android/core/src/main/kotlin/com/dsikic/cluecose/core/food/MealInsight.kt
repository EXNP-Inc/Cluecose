//
//  MealInsight.kt
//  CluecoseCore (Android `:core` mirror)
//
//  Phase 2 — the per-meal blood-sugar insight engine. Pure and deterministic:
//  given a meal entry (date + carbs) and the merged glucose series, it decides
//  whether the post-meal response is "insight-worthy" and, if so, produces a
//  `MealInsight` with numeric fields that are the CROSS-PLATFORM CONTRACT
//  (golden-tested on both Swift and Kotlin against the same fixtures).
//
//  Model (documented, honest — an estimate, not a medical measurement):
//    • baseline   — mean glucose over `baselineWindowMin` before the meal
//                   (no pre-meal data → no insight, ever).
//    • window     — glucose from the meal up to `postWindowMin` after it.
//    • spike      — peak is at least `spikeThresholdMgdl` above baseline.
//    • reactiveLow— the curve falls at least `lowThresholdMgdl` BELOW baseline
//                   (post-meal dip; the CGM correlate of reactive hypoglycemia).
//    • "final only": the engine evaluates a meal only once the full post-meal
//      window has elapsed (meal is `postWindowMin` old at `now`). So an insight
//      appears ~3 h after the meal, once, and then persists on the entry —
//      no partial "rising, peak not reached" states.
//    • if neither rule fires → null → the UI shows NOTHING for that meal.
//
//  When BOTH fire, the larger absolute deviation wins the `kind`; the text
//  mentions both. `text` is a presentation string (not part of the parity
//  contract — only the numeric fields + kind are).
//

package com.dsikic.cluecose.core.food

/**
 * Tunable thresholds. Defaults are the plan's: 30 min pre-meal baseline,
 * 180 min post-meal window, ±30 mg/dL for spike / reactive low, and a
 * ±10 mg/dL "back to baseline" band. (Swift `Config`.)
 */
data class InsightConfig(
    var baselineWindowMin: Double = 30.0,
    var postWindowMin: Double = 180.0,
    var spikeThresholdMgdl: Double = 30.0,
    var lowThresholdMgdl: Double = 30.0,
    var baselineBandMgdl: Double = 10.0,
) {
    companion object {
        val DEFAULT = InsightConfig()
    }
}

/** A single glucose point in the (pre/post) analysis window. */
data class GlucoseSample(
    var epochSeconds: Double,
    var mgdl: Double,
)

/**
 * Everything the engine needs for one meal. `samples` is the merged glucose
 * series (any window covering pre- and post-meal is fine — the engine
 * filters). `nowEpochSeconds` gates the "final only" rule.
 */
data class InsightInput(
    var mealDateEpochSeconds: Double,
    /** Carbs from the entry, for the per-10 g normalization + text. */
    var carbsG: Double? = null,
    /** Meal description (text only — used to make the insight specific). */
    var description: String? = null,
    var samples: List<GlucoseSample> = emptyList(),
    var nowEpochSeconds: Double,
)

/**
 * Comparison against past instances of similar meals ("your usual response").
 * Built by the app model from PRIOR entries whose insight already fired and
 * whose descriptions are similar; the engine just folds it into the text. Not
 * part of the numeric parity contract. (Swift `CrossMealComparison`.)
 */
data class CrossMealComparison(
    val instanceCount: Int,
    val averagePeakDeltaMgdl: Double,
)

/**
 * Deterministic, on-device blood-sugar insight engine — the 1:1 Kotlin mirror
 * of Swift's `MealInsightEngine`.
 */
object MealInsightEngine {

    /**
     * Decide whether the post-meal response is insight-worthy and, if so,
     * produce a [MealInsight]. Returns null when there is no pre-meal
     * baseline, no post-meal data, the post window hasn't elapsed, or neither
     * the spike nor reactive-low rule fires.
     * (Swift `insight(for:config:crossMeal:)`.)
     */
    fun insight(
        input: InsightInput,
        config: InsightConfig = InsightConfig.DEFAULT,
        crossMeal: CrossMealComparison? = null,
    ): MealInsight? {
        val meal = input.mealDateEpochSeconds
        val baselineLo = meal - config.baselineWindowMin * 60
        val baselineHi = meal
        val postLo = meal
        val postHi = meal + config.postWindowMin * 60

        val baselineSamples = input.samples.filter { it.epochSeconds >= baselineLo && it.epochSeconds < baselineHi }
        // No pre-meal data → nothing to compare against → no insight, ever.
        if (baselineSamples.isEmpty()) return null
        val baseline = baselineSamples.sumOf { it.mgdl } / baselineSamples.size

        val postSamples = input.samples.filter { it.epochSeconds >= postLo && it.epochSeconds <= postHi }
        // Need actual post-meal data to make a claim.
        if (postSamples.isEmpty()) return null

        // "Final only": evaluate only once the whole post window has elapsed.
        // (The model re-runs this as `now` advances; the result then sticks on
        // the entry, so it appears once, ~3 h after the meal.)
        if (input.nowEpochSeconds < postHi) return null

        // Peak (max) and trough (min) within the post window. First
        // occurrence wins on a tie (same as Swift's `>` / `<` scans).
        var peak = postSamples[0]
        var trough = postSamples[0]
        for (s in postSamples) {
            if (s.mgdl > peak.mgdl) peak = s
            if (s.mgdl < trough.mgdl) trough = s
        }

        val peakDelta = peak.mgdl - baseline
        val minDelta = trough.mgdl - baseline

        // Return-to-baseline: first sample back within ±band of baseline
        // AFTER the peak, expressed in minutes from the meal.
        var returnToBaselineMin: Double? = null
        for (s in postSamples) {
            if (s.epochSeconds >= peak.epochSeconds) {
                if (kotlin.math.abs(s.mgdl - baseline) <= config.baselineBandMgdl) {
                    returnToBaselineMin = (s.epochSeconds - meal) / 60
                    break
                }
            }
        }

        val spike = peakDelta >= config.spikeThresholdMgdl
        val low = minDelta <= -config.lowThresholdMgdl
        if (!spike && !low) return null   // not insight-worthy → nothing

        val per10g = if ((input.carbsG ?: 0.0) > 0) peakDelta / (input.carbsG!! / 10) else null

        val kind = when {
            spike && low -> if (kotlin.math.abs(peakDelta) >= kotlin.math.abs(minDelta)) MealInsight.Kind.SPIKE else MealInsight.Kind.REACTIVE_LOW
            spike -> MealInsight.Kind.SPIKE
            else -> MealInsight.Kind.REACTIVE_LOW
        }

        val timeToPeakMin = (peak.epochSeconds - meal) / 60
        val timeToTroughMin = (trough.epochSeconds - meal) / 60
        val text = makeText(
            kind = kind,
            baseline = baseline,
            peak = peak.mgdl, peakDelta = peakDelta,
            timeToPeakMin = timeToPeakMin,
            returnToBaselineMin = returnToBaselineMin,
            trough = trough.mgdl, minDelta = minDelta,
            timeToTroughMin = timeToTroughMin,
            spikeFired = spike, lowFired = low,
            per10g = per10g,
            meal = input.description,
            crossMeal = crossMeal,
        )

        return MealInsight(
            kind = kind,
            baselineMgdl = baseline,
            peakMgdl = peak.mgdl,
            peakDeltaMgdl = peakDelta,
            timeToPeakMin = timeToPeakMin,
            returnToBaselineMin = returnToBaselineMin,
            minMgdl = trough.mgdl,
            minDeltaMgdl = minDelta,
            per10gCarbDeltaMgdl = per10g,
            text = text,
        )
    }

    // MARK: Text (presentation — not part of the parity contract)

    /**
     * Text is a presentation string — not part of the cross-platform parity
     * contract (numeric fields + kind are). Keep it short (the watch shows it
     * too) and grounded in the numbers. Mirrors Swift `makeText`.
     *
     * NOTE: the dip/drop phrases use the Unicode minus (U+2212 "−") and the
     * connectors use the em dash (U+2014 "—"), matching the Swift source
     * byte-for-byte.
     */
    private fun makeText(
        kind: MealInsight.Kind,
        baseline: Double,
        peak: Double, peakDelta: Double,
        timeToPeakMin: Double,
        returnToBaselineMin: Double?,
        trough: Double, minDelta: Double,
        timeToTroughMin: Double,
        spikeFired: Boolean, lowFired: Boolean,
        per10g: Double?,
        meal: String?,
        crossMeal: CrossMealComparison? = null,
    ): String {
        val parts = mutableListOf<String>()
        when (kind) {
            MealInsight.Kind.SPIKE -> {
                var s = "Glucose peaked at ${fmt(peak)} mg/dL (+${fmt(peakDelta)} above your pre-meal level of ${fmt(baseline)}) about ${fmt(timeToPeakMin)} min after"
                s += mealPhrase(meal)
                if (returnToBaselineMin != null) {
                    s += " — back to baseline in about ${fmt(returnToBaselineMin)} min"
                }
                parts.add("$s.")
                if (per10g != null && per10g > 0) {
                    parts.add("That's about ${fmt(per10g)} mg/dL per 10 g of carbs.")
                }
                if (lowFired) {
                    parts.add("It then dipped to ${fmt(trough)} mg/dL (−${fmt(kotlin.math.abs(minDelta))} below baseline) — a reactive dip after the spike.")
                }
            }
            MealInsight.Kind.REACTIVE_LOW -> {
                var s = "Glucose dropped to ${fmt(trough)} mg/dL (−${fmt(kotlin.math.abs(minDelta))} below your pre-meal level of ${fmt(baseline)}) about ${fmt(timeToTroughMin)} min after"
                s += mealPhrase(meal)
                parts.add("$s.")
                if (spikeFired) {
                    parts.add("This followed a +${fmt(peakDelta)} mg/dL spike about ${fmt(timeToPeakMin)} min in — a classic reactive dip.")
                }
            }
        }
        if (crossMeal != null && crossMeal.instanceCount >= 2) {
            val avg = crossMeal.averagePeakDeltaMgdl
            parts.add(String.format("Across %d similar meals your average rise is about %.0f mg/dL.", crossMeal.instanceCount, avg))
        }
        return parts.joinToString(" ")
    }

    /** Swift's `m` formatter: `String(format: "%.0f", v)`. */
    private fun fmt(v: Double): String = String.format("%.0f", v)

    private fun mealPhrase(meal: String?): String {
        val d = meal?.trim()
        if (d.isNullOrEmpty()) return "eating"
        return "this meal"  // keep the sentence tight; the card already shows the food name
    }
}
