//
//  PatternsEngine.swift
//  CluecoseCore
//
//  Deterministic, on-device aggregation of the logged-food history into
//  period averages and a small set of coaching "follow-up questions."
//  This is the portable half of the "learn from your history" feature:
//  repeated records make recurring choices and nutrient patterns visible.
//
//  Cross-platform contract:
//    • Aggregation is defined on UTC calendar days (floor(epoch/86400)), so
//      the numbers are identical on Swift and Kotlin with no time-zone
//      coupling. The UI may relabel day boundaries in the local calendar.
//    • The NUMERIC fields of `NutritionPatterns` are the parity contract and
//      are golden-tested from the shared `patterns-*.json` fixtures (the
//      Android `:core` tests run the same files).
//    • `coachingQuestions` builds human-readable questions from those
//      numbers (presentation-only, like `MealInsight.text`); the underlying
//      numbers are what must match across platforms.
//
//  Scope note: caffeine *timing* relative to bedtime and the nutrition/sleep
//  association are local-clock + HealthKit concerns and are computed in the
//  platform layer, not here.
//

import Foundation

/// Aggregate statistics over a set of food entries within a trailing window.
/// Each average is the sum of a nutrient across the window divided by the
/// number of days in which that nutrient was reported at least once (nil when
/// the nutrient was never reported in the window).
public struct NutritionPatterns: Equatable, Sendable {
    public var entryCount: Int = 0
    public var distinctDays: Int = 0
    public var avgKcalPerDay: Double?
    public var avgProteinPerDay: Double?
    public var avgCarbsPerDay: Double?
    public var avgFatPerDay: Double?
    public var avgSodiumPerDay: Double?
    public var avgCaffeinePerDay: Double?
    public var avgAlcoholPerDay: Double?
    public var avgSaturatedFatPerDay: Double?
    /// Total saturated fat / total fat across the window (0...1); nil when the
    /// total fat is zero or unknown. Drives the fat-quality coaching question.
    public var saturatedFatToFatRatio: Double?

    public init() {}
}

public enum PatternsEngine {

    public static let daySeconds = 86_400.0

    private static func dayIndex(_ epochSeconds: Double) -> Double {
        floor(epochSeconds / daySeconds)
    }

    /// Aggregate `entries` over the trailing `inDays` UTC days ending at
    /// `nowEpochSeconds`.
    public static func patterns(_ entries: [FoodEntry], inDays: Int, nowEpochSeconds: Double) -> NutritionPatterns {
        let n = max(inDays, 1)
        let nowDay = dayIndex(nowEpochSeconds)
        let firstDay = nowDay - Double(n - 1)

        let inRange = entries.filter { e in
            let d = dayIndex(e.dateEpochSeconds)
            return d >= firstDay && d <= nowDay
        }

        var p = NutritionPatterns()
        p.entryCount = inRange.count

        // Distinct days (any entry) and per-nutrient "days with a value".
        var daysAny = Set<Double>()
        var daysKcal = Set<Double>(), daysProtein = Set<Double>(), daysCarbs = Set<Double>()
        var daysFat = Set<Double>(), daysSodium = Set<Double>(), daysCaffeine = Set<Double>()
        var daysAlcohol = Set<Double>(), daysSatFat = Set<Double>()
        var sKcal = 0.0, sProtein = 0.0, sCarbs = 0.0, sFat = 0.0, sSodium = 0.0
        var sCaffeine = 0.0, sAlcohol = 0.0, sSatFat = 0.0

        for e in inRange {
            let d = dayIndex(e.dateEpochSeconds)
            daysAny.insert(d)
            if let v = e.caloriesKcal { sKcal += v; daysKcal.insert(d) }
            if let v = e.proteinG { sProtein += v; daysProtein.insert(d) }
            if let v = e.carbsG { sCarbs += v; daysCarbs.insert(d) }
            if let v = e.fatG { sFat += v; daysFat.insert(d) }
            if let v = e.sodiumMg { sSodium += v; daysSodium.insert(d) }
            if let v = e.caffeineMg { sCaffeine += v; daysCaffeine.insert(d) }
            if let v = e.alcoholG { sAlcohol += v; daysAlcohol.insert(d) }
            if let v = e.saturatedFatG { sSatFat += v; daysSatFat.insert(d) }
        }

        p.distinctDays = daysAny.count
        p.avgKcalPerDay = avg(sKcal, daysKcal.count)
        p.avgProteinPerDay = avg(sProtein, daysProtein.count)
        p.avgCarbsPerDay = avg(sCarbs, daysCarbs.count)
        p.avgFatPerDay = avg(sFat, daysFat.count)
        p.avgSodiumPerDay = avg(sSodium, daysSodium.count)
        p.avgCaffeinePerDay = avg(sCaffeine, daysCaffeine.count)
        p.avgAlcoholPerDay = avg(sAlcohol, daysAlcohol.count)
        p.avgSaturatedFatPerDay = avg(sSatFat, daysSatFat.count)
        p.saturatedFatToFatRatio = (sFat > 0) ? (sSatFat / sFat) : nil
        return p
    }

    private static func avg(_ sum: Double, _ days: Int) -> Double? {
        days > 0 ? sum / Double(days) : nil
    }

    /// Deterministic coaching follow-up questions, derived purely from the
    /// numbers in `p`. Order is fixed so the output is stable. These are
    /// presentation strings (like `MealInsight.text`); the thresholds below
    /// are the only contract.
    public static func coachingQuestions(_ p: NutritionPatterns) -> [String] {
        var out: [String] = []
        if let c = p.avgCaffeinePerDay, c >= 300 {
            out.append(String(format: "Your average daily caffeine is %.0f mg — above the 300 mg commonly cited as a moderate limit. Consider when and how much you consume.", c))
        }
        if let pr = p.avgProteinPerDay, pr > 0, pr < 40 {
            out.append(String(format: "Your average daily protein is %.0f g, below 40 g. Adding a protein source at a meal could raise this.", pr))
        }
        if let r = p.saturatedFatToFatRatio, r > 0.4 {
            out.append(String(format: "Saturated fat is %.0f%% of your total fat. Swapping in unsaturated fats would lower this share.", r * 100))
        }
        if let s = p.avgSodiumPerDay, s > 2000 {
            out.append(String(format: "Your average daily sodium is %.0f mg, above the 2000 mg commonly cited as a target.", s))
        }
        if let a = p.avgAlcoholPerDay, a > 30 {
            out.append(String(format: "Your average daily alcohol is %.0f g. That is above a light-drinking level; worth reviewing.", a))
        }
        return out
    }
}
