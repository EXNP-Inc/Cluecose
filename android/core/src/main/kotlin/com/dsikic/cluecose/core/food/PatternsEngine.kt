//
//  PatternsEngine.kt
//  CluecoseCore (Android `:core` mirror)
//
//  Kotlin mirror of `CluecoseCore/Sources/CluecoseCore/PatternsEngine.swift`.
//  Deterministic, on-device aggregation of the logged-food history into
//  period averages and a small set of coaching "follow-up questions."
//  This is the portable half of the "learn from your history" feature:
//  repeated records make recurring choices and nutrient patterns visible.
//
//  Cross-platform contract:
//    • Aggregation is defined on UTC calendar days (floor(epoch/86400)), so
//      the numbers are identical on Swift and Kotlin with no time-zone
//      coupling. The UI may relabel day boundaries in the local calendar.
//    • The NUMERIC fields of [NutritionPatterns] are the parity contract and
//      are golden-tested from the shared `patterns-*.json` fixtures (the
//      Swift tests run the same files).
//    • [PatternsEngine.coachingQuestions] builds human-readable questions
//      from those numbers (presentation-only, like `MealInsight.text`); the
//      underlying numbers are what must match across platforms.
//
//  Scope note: caffeine *timing* relative to bedtime and the nutrition/sleep
//  association are local-clock + HealthKit concerns and are computed in the
//  platform layer, not here. (TODO(defer): the nutrition-sleep association
//  itself is deferred out of this pass.)
//

package com.dsikic.cluecose.core.food

import kotlin.math.floor

/**
 * Aggregate statistics over a set of food entries within a trailing window.
 * Each average is the sum of a nutrient across the window divided by the
 * number of days in which that nutrient was reported at least once (null when
 * the nutrient was never reported in the window).
 */
data class NutritionPatterns(
    var entryCount: Int = 0,
    var distinctDays: Int = 0,
    var avgKcalPerDay: Double? = null,
    var avgProteinPerDay: Double? = null,
    var avgCarbsPerDay: Double? = null,
    var avgFatPerDay: Double? = null,
    var avgSodiumPerDay: Double? = null,
    var avgCaffeinePerDay: Double? = null,
    var avgAlcoholPerDay: Double? = null,
    var avgSaturatedFatPerDay: Double? = null,
    /** Total saturated fat / total fat across the window (0...1); null when
     *  the total fat is zero or unknown. Drives the fat-quality coaching
     *  question. */
    var saturatedFatToFatRatio: Double? = null,
)

/**
 * Deterministic patterns aggregation — the 1:1 Kotlin mirror of Swift's
 * `PatternsEngine`.
 */
object PatternsEngine {

    const val daySeconds: Double = 86_400.0

    private fun dayIndex(epochSeconds: Double): Double =
        floor(epochSeconds / daySeconds)

    /**
     * Aggregate [entries] over the trailing [inDays] UTC days ending at
     * [nowEpochSeconds].
     */
    fun patterns(entries: List<FoodEntry>, inDays: Int, nowEpochSeconds: Double): NutritionPatterns {
        val n = maxOf(inDays, 1)
        val nowDay = dayIndex(nowEpochSeconds)
        val firstDay = nowDay - n.toDouble() + 1.0

        val inRange = entries.filter { e ->
            val d = dayIndex(e.dateEpochSeconds)
            d >= firstDay && d <= nowDay
        }

        val p = NutritionPatterns()
        p.entryCount = inRange.size

        // Distinct days (any entry) and per-nutrient "days with a value".
        val daysAny = mutableSetOf<Double>()
        val daysKcal = mutableSetOf<Double>(); val daysProtein = mutableSetOf<Double>(); val daysCarbs = mutableSetOf<Double>()
        val daysFat = mutableSetOf<Double>(); val daysSodium = mutableSetOf<Double>(); val daysCaffeine = mutableSetOf<Double>()
        val daysAlcohol = mutableSetOf<Double>(); val daysSatFat = mutableSetOf<Double>()
        var sKcal = 0.0; var sProtein = 0.0; var sCarbs = 0.0; var sFat = 0.0; var sSodium = 0.0
        var sCaffeine = 0.0; var sAlcohol = 0.0; var sSatFat = 0.0

        for (e in inRange) {
            val d = dayIndex(e.dateEpochSeconds)
            daysAny.add(d)
            e.caloriesKcal?.let { sKcal += it; daysKcal.add(d) }
            e.proteinG?.let { sProtein += it; daysProtein.add(d) }
            e.carbsG?.let { sCarbs += it; daysCarbs.add(d) }
            e.fatG?.let { sFat += it; daysFat.add(d) }
            e.sodiumMg?.let { sSodium += it; daysSodium.add(d) }
            e.caffeineMg?.let { sCaffeine += it; daysCaffeine.add(d) }
            e.alcoholG?.let { sAlcohol += it; daysAlcohol.add(d) }
            e.saturatedFatG?.let { sSatFat += it; daysSatFat.add(d) }
        }

        p.distinctDays = daysAny.size
        p.avgKcalPerDay = avg(sKcal, daysKcal.size)
        p.avgProteinPerDay = avg(sProtein, daysProtein.size)
        p.avgCarbsPerDay = avg(sCarbs, daysCarbs.size)
        p.avgFatPerDay = avg(sFat, daysFat.size)
        p.avgSodiumPerDay = avg(sSodium, daysSodium.size)
        p.avgCaffeinePerDay = avg(sCaffeine, daysCaffeine.size)
        p.avgAlcoholPerDay = avg(sAlcohol, daysAlcohol.size)
        p.avgSaturatedFatPerDay = avg(sSatFat, daysSatFat.size)
        p.saturatedFatToFatRatio = if (sFat > 0) sSatFat / sFat else null
        return p
    }

    private fun avg(sum: Double, days: Int): Double? =
        if (days > 0) sum / days else null

    /**
     * Deterministic coaching follow-up questions, derived purely from the
     * numbers in [p]. Order is fixed so the output is stable. These are
     * presentation strings (like `MealInsight.text`); the thresholds below
     * are the only contract.
     *
     * NOTE: the em dashes (U+2014) in the question texts mirror the Swift
     * source byte-for-byte.
     */
    fun coachingQuestions(p: NutritionPatterns): List<String> {
        val out = mutableListOf<String>()
        if (p.avgCaffeinePerDay != null && p.avgCaffeinePerDay!! >= 300) {
            out.add(String.format("Your average daily caffeine is %.0f mg — above the 300 mg commonly cited as a moderate limit. Consider when and how much you consume.", p.avgCaffeinePerDay))
        }
        val pr = p.avgProteinPerDay
        if (pr != null && pr > 0 && pr < 40) {
            out.add(String.format("Your average daily protein is %.0f g, below 40 g. Adding a protein source at a meal could raise this.", pr))
        }
        val ratio = p.saturatedFatToFatRatio
        if (ratio != null && ratio > 0.4) {
            out.add(String.format("Saturated fat is %.0f%% of your total fat. Swapping in unsaturated fats would lower this share.", ratio * 100))
        }
        if (p.avgSodiumPerDay != null && p.avgSodiumPerDay!! > 2000) {
            out.add(String.format("Your average daily sodium is %.0f mg, above the 2000 mg commonly cited as a target.", p.avgSodiumPerDay))
        }
        if (p.avgAlcoholPerDay != null && p.avgAlcoholPerDay!! > 30) {
            out.add(String.format("Your average daily alcohol is %.0f g. That is above a light-drinking level; worth reviewing.", p.avgAlcoholPerDay))
        }
        return out
    }
}
