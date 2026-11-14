//
//  DemoData.swift
//  CluecoseCore
//
//  Deterministic synthetic data for Cluecose's SIMULATOR "demo mode".
//
//  The iOS simulators have no NFC, so a real FreeStyle Libre cannot be paired
//  and the app would sit on the "pair your sensor" screen with no data. Demo
//  mode injects a plausible 48 h CGM series (a gentle diurnal baseline plus
//  post-meal glucose humps) and a handful of sample meal entries, so the hero
//  reading, 24 h stats, history chart, food log, per-meal insights and the
//  7-day patterns view all populate end to end — through the SAME code paths a
//  real sensor and LLM estimate use (ingest → analytics → insight engine).
//
//  Pure and deterministic: no RNG and no wall clock other than the `now` the
//  caller passes in, so it is golden-tested in CluecoseCoreTests and safe to
//  call from any platform. The public surface is just the two generators; the
//  meal tables below are private and kept in one place so the glucose humps
//  and the logged meals stay perfectly aligned (an insight only fires when the
//  curve around a meal is insight-worthy, which the humps are built to be).
//

import Foundation

public enum DemoData {

    /// A single sample meal: when it was eaten (relative to `now`), the shape
    /// of the glucose hump it drives, and its logged nutrition values.
    private struct Meal {
        var minutesAgo: Double       // 0 < minutesAgo < 48h for the "recent" set
        var baselineMgdl: Double     // pre-meal level the hump rises from
        var peakMgdl: Double         // top of the hump (peakMgdl - baselineMgdl ≥ 30 → spike)
        var peakOffsetMin: Double    // minutes after the meal the peak lands
        var decayMin: Double         // minutes from peak back to baseline
        var description: String
        var carbsG: Double
        var proteinG: Double
        var fatG: Double
        var saturatedFatG: Double
        var sodiumMg: Double
        var caffeineMg: Double
        var caloriesKcal: Double
    }

    /// The recent meals that drive BOTH the glucose humps and the food-log
    /// entries. All four are within the 48 h series. The first three are ≥ 3 h
    /// old (past the "final only" gate) and each hump rises ≥ +30 mg/dL, so each
    /// of those meals fires a spike insight; the 1.5 h meal is inside its
    /// 180-min window, so it correctly shows NO insight yet.
    private static let recentMeals: [Meal] = [
        Meal(minutesAgo: 1.5 * 60, baselineMgdl: 88, peakMgdl: 125, peakOffsetMin: 40, decayMin: 100,
             description: "Greek yogurt with granola", carbsG: 28, proteinG: 15, fatG: 5,
             saturatedFatG: 3, sodiumMg: 70, caffeineMg: 0, caloriesKcal: 240),
        Meal(minutesAgo: 3.5 * 60, baselineMgdl: 90, peakMgdl: 150, peakOffsetMin: 45, decayMin: 120,
             description: "Oatmeal with berries and honey", carbsG: 55, proteinG: 12, fatG: 6,
             saturatedFatG: 3, sodiumMg: 240, caffeineMg: 0, caloriesKcal: 410),
        Meal(minutesAgo: 6.5 * 60, baselineMgdl: 94, peakMgdl: 165, peakOffsetMin: 55, decayMin: 125,
             description: "Chicken rice bowl with greens", carbsG: 58, proteinG: 34, fatG: 14,
             saturatedFatG: 6, sodiumMg: 720, caffeineMg: 0, caloriesKcal: 560),
        Meal(minutesAgo: 9.5 * 60, baselineMgdl: 91, peakMgdl: 150, peakOffsetMin: 60, decayMin: 120,
             description: "Pasta with tomato and parmesan", carbsG: 64, proteinG: 18, fatG: 12,
             saturatedFatG: 7, sodiumMg: 610, caffeineMg: 0, caloriesKcal: 540),
    ]

    /// Older meals (2–6 days back) that only feed the 7-day patterns view.
    /// They fall outside the 48 h glucose window, so they get no insight —
    /// they exist to give the patterns/coaching surface real history.
    private static let historyMeals: [Meal] = [
        Meal(minutesAgo: 2 * 24 * 60, baselineMgdl: 90, peakMgdl: 0, peakOffsetMin: 0, decayMin: 0,
             description: "Espresso and butter croissant", carbsG: 30, proteinG: 6, fatG: 20,
             saturatedFatG: 12, sodiumMg: 420, caffeineMg: 180, caloriesKcal: 380),
        Meal(minutesAgo: 3 * 24 * 60, baselineMgdl: 90, peakMgdl: 0, peakOffsetMin: 0, decayMin: 0,
             description: "Grilled salmon with quinoa salad", carbsG: 24, proteinG: 34, fatG: 18,
             saturatedFatG: 3, sodiumMg: 480, caffeineMg: 0, caloriesKcal: 470),
        Meal(minutesAgo: 5 * 24 * 60, baselineMgdl: 90, peakMgdl: 0, peakOffsetMin: 0, decayMin: 0,
             description: "Beef stir-fry with jasmine rice", carbsG: 55, proteinG: 28, fatG: 16,
             saturatedFatG: 9, sodiumMg: 980, caffeineMg: 0, caloriesKcal: 590),
        Meal(minutesAgo: 6 * 24 * 60, baselineMgdl: 90, peakMgdl: 0, peakOffsetMin: 0, decayMin: 0,
             description: "Pancakes with maple syrup", carbsG: 68, proteinG: 9, fatG: 12,
             saturatedFatG: 6, sodiumMg: 560, caffeineMg: 0, caloriesKcal: 520),
    ]

    // MARK: Glucose series

    /// A synthetic 48 h CGM series: 10-minute samples from 48 h before `now`,
    /// capped by an explicit final point 1 min before `now` so the latest
    /// reading is "fresh" (inside the 25-min live window) and the hero card
    /// shows Live. Deterministic.
    public static func glucoseSeries(now: Date) -> [GlucoseSample] {
        let step: Double = 10 * 60
        let nowS = now.timeIntervalSince1970
        let startS = nowS - 48 * 3600
        let endS = nowS - 1 * 60          // final point 1 min before now
        var out: [GlucoseSample] = []
        var t = startS
        while t < endS {                  // walk the 10-min grid, stopping before the cap
            out.append(GlucoseSample(date: Date(timeIntervalSince1970: t),
                                     mgdl: glucose(at: t, nowS: nowS)))
            t += step
        }
        // Append the fresh final point (always present; 1 min before now).
        out.append(GlucoseSample(date: Date(timeIntervalSince1970: endS),
                                 mgdl: glucose(at: endS, nowS: nowS)))
        return out
    }

    /// Glucose at a single instant: a gentle diurnal baseline plus the sum of
    /// each recent meal's post-meal hump.
    private static func glucose(at t: Double, nowS: Double) -> Double {
        let hourOfDay = (t / 3600.0).truncatingRemainder(dividingBy: 24)
        var v = 85 + 8 * sin(2 * .pi * hourOfDay / 24)
        for meal in recentMeals {
            let mealS = nowS - meal.minutesAgo * 60
            let m = (t - mealS) / 60      // minutes after this meal (can be negative)
            v += bump(m: m, lift: meal.peakMgdl - meal.baselineMgdl,
                      peakOffset: meal.peakOffsetMin, decay: meal.decayMin)
        }
        return v
    }

    /// A smooth post-meal hump, gated to start exactly at the meal (so the
    /// 30-min pre-meal baseline stays clean): ease-in from baseline to peak
    /// over `peakOffset`, ease-out back to baseline over `decay`.
    private static func bump(m: Double, lift: Double, peakOffset: Double, decay: Double) -> Double {
        guard m >= 0, lift > 0, peakOffset > 0, decay > 0 else { return 0 }
        let total = peakOffset + decay
        guard m <= total else { return 0 }
        if m <= peakOffset {
            return lift * 0.5 * (1 - cos(.pi * m / peakOffset))            // 0 → 1 (ease-in)
        } else {
            return lift * 0.5 * (1 + cos(.pi * (m - peakOffset) / decay))  // 1 → 0 (ease-out)
        }
    }

    // MARK: Food entries

    /// The sample meal entries demo mode seeds into the food store. The recent
    /// meals align 1:1 with the glucose humps above (same `minutesAgo`), and the
    /// older meals give the 7-day patterns view real history. Deterministic.
    public static func foodEntries(now: Date) -> [FoodEntry] {
        (recentMeals + historyMeals).map { meal in
            FoodEntry(dateEpochSeconds: now.timeIntervalSince1970 - meal.minutesAgo * 60,
                      description: meal.description,
                      source: .manual,
                      caloriesKcal: meal.caloriesKcal,
                      proteinG: meal.proteinG,
                      carbsG: meal.carbsG,
                      fatG: meal.fatG,
                      sodiumMg: meal.sodiumMg,
                      caffeineMg: meal.caffeineMg,
                      saturatedFatG: meal.saturatedFatG,
                      isEstimated: false)
        }
    }
}
