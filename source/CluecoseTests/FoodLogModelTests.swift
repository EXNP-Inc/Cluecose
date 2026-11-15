//
//  FoodLogModelTests.swift
//  CluecoseTests
//
//  App-level unit tests for the food-log save/analysis/analytics logic.
//  Runs against a stub estimator (no network) and a suite-scoped
//  UserDefaults (no production data touched).
//

import XCTest
import CluecoseCore
@testable import Cluecose

// MARK: - Stub estimator

/// Deterministic stand-in for the LLM client: returns a fixed estimate and
/// counts calls so tests can assert WHEN the model is (not) consulted.
final class StubEstimator: FoodEstimating, @unchecked Sendable {
    let estimate: NutritionPrompt.Estimate
    /// Only ever touched from the MainActor test context.
    private let _calls = NSLock()
    private var callCount = 0

    init(estimate: NutritionPrompt.Estimate) {
        self.estimate = estimate
    }

    var calls: Int {
        _calls.lock(); defer { _calls.unlock() }
        return callCount
    }

    func resetCalls() {
        _calls.lock(); defer { _calls.unlock() }
        callCount = 0
    }

    func estimate(_ request: NutritionPrompt.Request,
                  photos: [Data]) async throws -> NutritionPrompt.Estimate {
        _calls.lock(); defer { _calls.unlock() }
        callCount += 1
        return estimate
    }

    func fallbackEstimate(description: String,
                          serving: String?,
                          context: String?) -> NutritionPrompt.Estimate {
        estimate
    }
}

/// Estimator whose endpoint is UNUSABLE (every call throws) — like a build
/// whose key is invalid/out-of-credits, or an offline device. Its fallback
/// must return an EMPTY estimate (the real client never fabricates values).
final class FailingEstimator: FoodEstimating, @unchecked Sendable {
    func estimate(_ request: NutritionPrompt.Request,
                  photos: [Data]) async throws -> NutritionPrompt.Estimate {
        throw FoodLLMError.noAPIKey
    }

    func fallbackEstimate(description: String,
                          serving: String?,
                          context: String?) -> NutritionPrompt.Estimate {
        NutritionPrompt.Estimate(description: description.isEmpty ? nil : description)
    }
}

// MARK: - Test harness

@MainActor
final class FoodLogModelTests: XCTestCase {

    private var suiteName: String!
    private var defaults: UserDefaults!
    private var model: FoodLogModel!
    private var stub: StubEstimator!
    /// The stub's canned estimate: a full-portion baseline (400 kcal etc.).
    private let base = NutritionPrompt.Estimate(
        caloriesKcal: 400, proteinG: 30, carbsG: 40, fatG: 16,
        saturatedFatG: 5, sodiumMg: 600, caffeineMg: 0, alcoholG: 0,
        description: nil, citations: [])

    private func makeModel() {
        suiteName = "cluecose.foodlogmodel.tests.\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suiteName)
        defaults?.removePersistentDomain(forName: suiteName)
        let store = FoodLogStore(defaults: defaults!)
        stub = StubEstimator(estimate: base)
        model = FoodLogModel(store: store, llm: stub)
    }

    override func setUp() {
        super.setUp()
        makeModel()
    }

    override func tearDown() {
        defaults?.removePersistentDomain(forName: suiteName)
        model = nil
        defaults = nil
        super.tearDown()
    }

    // MARK: Gate

    /// A meal with no data at all (no values, no photos, empty description)
    /// must NOT be persisted — the model-level guarantee behind the
    /// disabled Save button.
    func testSaveGateRefusesEmptyMeal() async {
        let values = FoodLogModel.MealValues(description: "", portionMultiplier: 1.0)
        let saved = await model.saveEntry(existing: nil, values: values, photos: [])
        XCTAssertEqual(model.entries.count, 0, "empty meal must not be persisted")
        XCTAssertFalse(stub.calls > 0, "no analysis may run for a refused save")
        XCTAssertNil(saved.caloriesKcal, "the returned entry carries no data")
    }

    /// A typed description with no photos IS a meal with data: the estimate
    /// fills the rows and the entry is saved.
    func testSaveGateAllowsDescribedMeal() async {
        let values = FoodLogModel.MealValues(description: "oatmeal with berries")
        let saved = await model.saveEntry(existing: nil, values: values, photos: [])
        XCTAssertEqual(model.entries.count, 1)
        XCTAssertEqual(saved.caloriesKcal, 400)
        XCTAssertEqual(saved.carbsG, 40)
        XCTAssertTrue(saved.isEstimated)
        XCTAssertEqual(stub.calls, 1)
    }

    /// A photo-only meal (no description, no rows) is allowed when photos
    /// are attached.
    func testSaveGateAllowsPhotoMeal() async {
        let values = FoodLogModel.MealValues(description: "", source: .photo)
        let saved = await model.saveEntry(existing: nil, values: values,
                                          photos: [Data([0xFF, 0xD8, 0xFF])])
        XCTAssertEqual(model.entries.count, 1)
        XCTAssertEqual(saved.photoFileNames.count, 1)
        XCTAssertEqual(stub.calls, 1)
    }

    // MARK: Failed analysis (endpoint unusable — invalid/out-of-credits key, offline)

    /// A described meal whose analysis FAILED must still be saved — with NO
    /// nutrient values and isEstimated=false — never with fabricated macros.
    /// (Regression: "2 tsp Metamucil" was once logged as ~475 kcal by the old
    /// keyword-guess fallback.)
    @MainActor
    func testFailedAnalysisSavesBlankNotFabricated() async {
        let store = FoodLogStore(defaults: defaults)
        let m = FoodLogModel(store: store, llm: FailingEstimator())
        let saved = await m.saveEntry(
            existing: nil,
            values: FoodLogModel.MealValues(description: "2 teaspoons of metamucil with stevia and zero sugar"),
            photos: [])
        XCTAssertEqual(m.entries.count, 1, "a described meal must be saved even when the analysis fails")
        XCTAssertEqual(saved.caloriesKcal, nil, "no fabricated calories")
        XCTAssertEqual(saved.carbsG, nil, "no fabricated carbs")
        XCTAssertEqual(saved.proteinG, nil, "no fabricated protein")
        XCTAssertEqual(saved.fatG, nil, "no fabricated fat")
        XCTAssertFalse(saved.isEstimated, "a failed analysis is not an estimate")
        XCTAssertEqual(saved.description, "2 teaspoons of metamucil with stevia and zero sugar")
    }

    /// A meal with NO data at all still refuses to save when the analysis
    /// fails (the gate: nothing to log).
    @MainActor
    func testFailedAnalysisStillRefusesEmptyMeal() async {
        let store = FoodLogStore(defaults: defaults)
        let m = FoodLogModel(store: store, llm: FailingEstimator())
        _ = await m.saveEntry(existing: nil, values: FoodLogModel.MealValues(description: ""), photos: [])
        XCTAssertEqual(m.entries.count, 0, "an empty meal is still unsavable")
    }

    // MARK: Portion

    /// New meal, portion 2.0 → stored values = estimate × 2.
    func testNewEntryPortionScalesEstimate() async {
        let values = FoodLogModel.MealValues(description: "pasta bowl",
                                             portionMultiplier: 2.0)
        let saved = await model.saveEntry(existing: nil, values: values, photos: [])
        XCTAssertEqual(saved.caloriesKcal, 800)
        XCTAssertEqual(saved.carbsG, 80)
        XCTAssertEqual(saved.proteinG, 60)
    }

    /// Editing a meal WITHOUT changing its description must NOT call the
    /// model (no LLM round-trip for a portion-only change).
    func testSameMealEditDoesNotReanalyze() async {
        _ = await model.saveEntry(existing: nil,
                                  values: FoodLogModel.MealValues(description: "pasta bowl"),
                                  photos: [])
        stub.resetCalls()

        let stored = model.entries[0]
        let values = FoodLogModel.MealValues(
            description: "pasta bowl", source: stored.source,
            portionMultiplier: stored.portionMultiplier)
        _ = await model.saveEntry(existing: stored, values: values, photos: [])
        XCTAssertEqual(stub.calls, 0, "portion-only edits must not hit the model")
    }

    /// Re-saving an untouched entry must round-trip the stored values
    /// EXACTLY (no base-recovery drift), including the portion scaling.
    func testUnchangedSaveRoundTripsValues() async {
        let first = await model.saveEntry(existing: nil,
                                          values: FoodLogModel.MealValues(description: "rice bowl",
                                                                          portionMultiplier: 1.5),
                                          photos: [])
        XCTAssertEqual(first.caloriesKcal, 600, "400 × 1.5")

        // Simulate the sheet: same text, same rows (nil = untouched),
        // same portion.
        let values = FoodLogModel.MealValues(
            description: "rice bowl", source: first.source,
            portionMultiplier: 1.5)
        let second = await model.saveEntry(existing: first, values: values, photos: [])
        XCTAssertEqual(second.caloriesKcal, 600, "must not collapse to the base 400")
        XCTAssertEqual(second.carbsG, 60)
        XCTAssertEqual(second.portionMultiplier, 1.5)
    }

    // MARK: Portion rescale

    /// Same meal, portion moved 1.0 → 0.5 → stored values halved (estimate
    /// values: 400/30/40/16/600/5).
    func testPortionRescaleDown() async {
        let first = await model.saveEntry(existing: nil,
                                          values: FoodLogModel.MealValues(description: "chicken bowl"),
                                          photos: [])
        XCTAssertEqual(first.caloriesKcal, 400)
        let stored = model.entries[0]
        let values = FoodLogModel.MealValues(
            description: "chicken bowl", source: stored.source,
            portionMultiplier: 0.5)
        let second = await model.saveEntry(existing: stored, values: values, photos: [])
        XCTAssertEqual(second.caloriesKcal, 200)
        XCTAssertEqual(second.proteinG, 15)
        XCTAssertEqual(second.carbsG, 20)
        XCTAssertEqual(second.fatG, 8)
        XCTAssertEqual(second.portionMultiplier, 0.5)
    }

    /// Same meal, portion moved 2.0 → 4.0 → stored values doubled again
    /// (base 400 → 800). Verifies rescale is relative to the STORED portion,
    /// not to 1.0.
    func testPortionRescaleFromTwoToFour() async {
        let first = await model.saveEntry(existing: nil,
                                          values: FoodLogModel.MealValues(description: "salad",
                                                                          portionMultiplier: 2.0),
                                          photos: [])
        XCTAssertEqual(first.caloriesKcal, 800)
        let stored = model.entries[0]
        let values = FoodLogModel.MealValues(
            description: "salad", source: stored.source,
            portionMultiplier: 4.0)
        let second = await model.saveEntry(existing: stored, values: values, photos: [])
        XCTAssertEqual(second.caloriesKcal, 1600, "base 400 × 4")
        XCTAssertEqual(second.carbsG, 160)
    }

    // MARK: Hand-edited rows

    /// A row the user explicitly entered overrides the estimate — and a
    /// LATER portion change rescales it like any other value (the whole
    /// meal scales).
    func testHandEnteredRowOverridesEstimate() async {
        var values = FoodLogModel.MealValues(description: "pizza")
        values.caloriesKcal = 999
        let first = await model.saveEntry(existing: nil, values: values, photos: [])
        XCTAssertEqual(first.caloriesKcal, 999)

        let stored = model.entries[0]
        // The sheet's rows hold the FULL-portion value (its prefill divides
        // the stored value by the portion). At portion 2.0 that 999-kcal
        // full pizza stores as 1998 — the hand value wins over the estimate
        // AND scales by the portion like the estimate does.
        var edit = FoodLogModel.MealValues(description: "pizza",
                                           source: stored.source,
                                           portionMultiplier: 2.0)
        edit.caloriesKcal = 999
        let second = await model.saveEntry(existing: stored, values: edit, photos: [])
        XCTAssertEqual(second.caloriesKcal, 1998, "hand value (full portion) × 2")
        // Untouched rows (nil) follow the whole-meal rescale: 40 g × 2.
        XCTAssertEqual(second.carbsG, 80)
    }

    /// Changing the description re-runs the estimate; the fresh estimate
    /// replaces the old values (scaled by the portion).
    func testDescriptionChangeReestimates() async {
        let first = await model.saveEntry(existing: nil,
                                          values: FoodLogModel.MealValues(description: "coffee"),
                                          photos: [])
        XCTAssertEqual(first.caloriesKcal, 400)
        stub.resetCalls()

        let stored = model.entries[0]
        let values = FoodLogModel.MealValues(description: "double espresso and a croissant")
        let second = await model.saveEntry(existing: stored, values: values, photos: [])
        XCTAssertEqual(stub.calls, 1, "a re-described meal must be analyzed")
        XCTAssertEqual(second.description, "double espresso and a croissant")
        XCTAssertEqual(second.caloriesKcal, 400, "fresh estimate at portion 1.0")
    }

    // MARK: Insight re-arm

    private func entryWithInsight() -> FoodEntry {
        FoodEntry(
            dateEpochSeconds: Date().timeIntervalSince1970 - 4 * 3600,
            description: "insightful meal",
            portionMultiplier: 1.0,
            caloriesKcal: 400, carbsG: 40,
            insight: MealInsight(kind: .spike, baselineMgdl: 90, peakMgdl: 150,
                                 peakDeltaMgdl: 60, timeToPeakMin: 55,
                                 returnToBaselineMin: 140, minMgdl: 88,
                                 minDeltaMgdl: -2, per10gCarbDeltaMgdl: 15,
                                 text: "test insight"))
    }

    /// Editing a meal that already has an insight re-arms it: the stored
    /// entry's insight is cleared so the 180-min window is evaluated again
    /// against the (possibly new) values.
    func testEditReArmsInsight() async {
        let seeded = entryWithInsight()
        model.store.add(seeded)
        model.refresh()
        XCTAssertEqual(model.entries.first?.insight?.text, "test insight")

        let stored = model.entries[0]
        let values = FoodLogModel.MealValues(
            description: stored.description, source: stored.source,
            portionMultiplier: stored.portionMultiplier)
        let saved = await model.saveEntry(existing: stored, values: values, photos: [])
        XCTAssertNil(saved.insight, "editing must re-arm the insight")
        XCTAssertNil(model.entries.first?.insight)
    }

    // MARK: Analytics state

    /// The pure analytics decision: insight → ready; no samples → noData;
    /// window open → pending with the right ETA; window closed → settled.
    func testAnalyticsStateTransitions() {
        let meal = Date(timeIntervalSince1970: 1_000_000)
        let window = MealInsightEngine.Config.default.postWindowMin
        let mid = meal.addingTimeInterval(window * 30)        // 30 min in
        let closed = meal.addingTimeInterval(window * 60 + 60) // just after

        XCTAssertEqual(FoodLogModel.analyticsState(insight: nil, hasSamples: false,
                                                   mealDate: meal, now: mid),
                       .noData)
        XCTAssertEqual(FoodLogModel.analyticsState(
                   insight: MealInsight(kind: .spike, baselineMgdl: 90, peakMgdl: 150,
                                        peakDeltaMgdl: 60, timeToPeakMin: 55,
                                        returnToBaselineMin: nil, minMgdl: 88,
                                        minDeltaMgdl: -2, per10gCarbDeltaMgdl: nil,
                                        text: "x"),
                   hasSamples: false, mealDate: meal, now: mid),
                   .ready)

        let pending = FoodLogModel.analyticsState(insight: nil, hasSamples: true,
                                                  mealDate: meal, now: mid)
        guard case .pending(let readyAt, let remaining) = pending else {
            return XCTFail("mid-window meal must be .pending, got \(pending)")
        }
        XCTAssertEqual(readyAt, meal.addingTimeInterval(window * 60))
        XCTAssertEqual(remaining, 90, "120 - 30")

        XCTAssertEqual(FoodLogModel.analyticsState(insight: nil, hasSamples: true,
                                                   mealDate: meal, now: closed),
                       .settled)
    }
}
