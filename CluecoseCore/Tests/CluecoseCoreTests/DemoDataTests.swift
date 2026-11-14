//
//  DemoDataTests.swift
//  CluecoseCoreTests
//
//  Golden tests for the simulator demo data. The contract being verified:
//  the synthetic series + meals produced by `DemoData` are good enough that
//  the REAL insight engine fires a spike for the meals that are old enough —
//  i.e. turning demo mode on actually lights up the per-meal insight UI, which
//  is the entire reason demo mode exists.
//

import XCTest
import CluecoseCore

final class DemoDataTests: XCTestCase {

    private func now() -> Date {
        // A fixed instant keeps the test deterministic (DemoData takes `now`
        // explicitly, so no wall clock is involved).
        Date(timeIntervalSince1970: 1_750_000_000)
    }

    /// A 48 h series has ~287 ten-minute samples, is non-empty, and its latest
    /// sample is fresh (within the 25 min "live" window).
    func testSeriesIsFreshAndSized() {
        let t = now()
        let series = DemoData.glucoseSeries(now: t)
        XCTAssertGreaterThanOrEqual(series.count, 250)
        XCTAssertLessThanOrEqual(series.count, 300)
        // Sorted oldest -> newest.
        for i in 1..<series.count {
            XCTAssertLessThan(series[i - 1].date, series[i].date)
        }
        // The newest sample is within a couple of minutes of `now`.
        let age = t.timeIntervalSince(series.last!.date)
        XCTAssertGreaterThanOrEqual(age, 0)
        XCTAssertLessThanOrEqual(age, 60)
        // Values stay in a plausible physiological band.
        for s in series {
            XCTAssertGreaterThanOrEqual(s.mgdl, 60)
            XCTAssertLessThanOrEqual(s.mgdl, 220)
        }
    }

    /// Determinism: two calls at the same `now` are identical.
    func testSeriesIsDeterministic() {
        let t = now()
        let a = DemoData.glucoseSeries(now: t).map(\.mgdl)
        let b = DemoData.glucoseSeries(now: t).map(\.mgdl)
        XCTAssertEqual(a, b)
    }

    /// The meal that is ~3.5 h old (oatmeal, 64g-class carbs, +60 mg/dL hump)
    /// must produce a spike insight through the real engine. This is the
    /// assertion that demo mode actually works.
    func testOldMealFiresSpikeInsight() {
        let t = now()
        let samples = DemoData.glucoseSeries(now: t).map {
            MealInsightEngine.Sample(epochSeconds: $0.date.timeIntervalSince1970, mgdl: $0.mgdl)
        }
        let entries = DemoData.foodEntries(now: t)

        // Find a meal that is past the 180-min final-only gate.
        let nowEpoch = t.timeIntervalSince1970
        let finalized = entries.first {
            (nowEpoch - $0.dateEpochSeconds) >= 180 * 60
        }
        XCTAssertNotNil(finalized, "expected at least one finalized demo meal")

        let insight = MealInsightEngine.insight(
            for: MealInsightEngine.Input(
                mealDateEpochSeconds: finalized!.dateEpochSeconds,
                carbsG: finalized!.carbsG,
                description: finalized!.description,
                samples: samples,
                nowEpochSeconds: nowEpoch
            ),
            config: .default
        )
        // The hump is built to rise well above the 30 mg/dL spike threshold.
        XCTAssertNotNil(insight, "demo meal should be insight-worthy")
        XCTAssertEqual(insight?.kind, .spike)
        if let insight {
            XCTAssertGreaterThanOrEqual(insight.peakDeltaMgdl, 30)
        }
    }

    /// The most recent meal (~1.5 h old) is still inside its 180-min window, so
    /// the engine must NOT emit an insight for it yet (the "final only" rule).
    func testRecentMealHasNoInsightYet() {
        let t = now()
        let samples = DemoData.glucoseSeries(now: t).map {
            MealInsightEngine.Sample(epochSeconds: $0.date.timeIntervalSince1970, mgdl: $0.mgdl)
        }
        let entries = DemoData.foodEntries(now: t)
        let nowEpoch = t.timeIntervalSince1970
        let recent = entries.max { $0.dateEpochSeconds < $1.dateEpochSeconds }!
        XCTAssertLessThan(nowEpoch - recent.dateEpochSeconds, 180 * 60)
        let insight = MealInsightEngine.insight(
            for: MealInsightEngine.Input(
                mealDateEpochSeconds: recent.dateEpochSeconds,
                carbsG: recent.carbsG,
                description: recent.description,
                samples: samples,
                nowEpochSeconds: nowEpoch
            ),
            config: .default
        )
        XCTAssertNil(insight, "a meal still inside its window must show nothing")
    }

    /// Food entries: the recent set has all 8 nutrients populated (so the
    /// totals + patterns views have data) and the 30-day retention window is
    /// respected by the store (entries are all within a week).
    func testFoodEntriesArePopulatedAndRecent() {
        let t = now()
        let entries = DemoData.foodEntries(now: t)
        XCTAssertFalse(entries.isEmpty)
        for e in entries {
            XCTAssertNotNil(e.caloriesKcal)
            XCTAssertNotNil(e.proteinG)
            XCTAssertNotNil(e.carbsG)
            XCTAssertNotNil(e.fatG)
            XCTAssertNotNil(e.sodiumMg)
            // All within the retention window (6 days < 30 days).
            XCTAssertLessThan(t.timeIntervalSince(e.date), 30 * 86_400)
        }
    }
}
