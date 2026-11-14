//
//  FoodLogModel.swift
//  Cluecose
//
//  The brain of the food-log feature. Owns the persistence store, the LLM
//  client, and the on-device insight / patterns / sleep computation.
//
//  • Insights run the shared `MealInsightEngine` on the merged glucose series
//    (injected `GlucoseModel`), gated on "final only": an entry gets an
//    insight once its full 180-min post-meal window has elapsed and it has
//    none yet. A cross-meal comparison ("your usual response") is built from
//    PRIOR similar entries whose insight already fired.
//  • Patterns / coaching come from the shared `PatternsEngine` (7-day window).
//  • Nutrition-sleep is iOS-only (HealthKit sleep) and emits ASSOCIATION, not
//    causation, strings.
//

import Foundation
import Combine
import CluecoseCore

@MainActor
final class FoodLogModel: ObservableObject {

    // MARK: Published state

    /// All logged entries (insertion order = oldest -> newest).
    @Published var entries: [FoodEntry] = []
    /// The day the food card is showing (default today).
    @Published var selectedDay: Date = Date()
    /// True while an LLM estimate is in flight.
    @Published var isEstimating = Bool()
    /// Nutrition-sleep association lines (iOS only; empty when HealthKit is
    /// unavailable or denied — the card then shows nothing).
    @Published private(set) var sleepInsights: [String] = []

    // MARK: Dependencies

    /// The app model, for the merged glucose series used by the insight engine.
    private weak var glucoseModel: GlucoseModel?
    /// Persistence (UserDefaults + photo files).
    let store = FoodLogStore()
    /// The LLM endpoint client (stateless; reads config per call).
    private let llm = FoodLLMClient.shared

    /// How often the lightweight recompute timer may run (seconds).
    private let recomputeInterval: TimeInterval = 120
    private var lastRecompute = Date.distantPast
    private var recomputeTimer: Timer?
    private var sampleObserver: AnyCancellable?

    init() {
        entries = store.all()
    }

    /// Wire up to the app model (call once after `GlucoseModel` is
    /// constructed, since it holds a `weak` back-reference to it). Observes
    /// the merged glucose series so insights land as samples stream in, and
    /// starts the lightweight recompute timer.
    func attach(_ glucoseModel: GlucoseModel) {
        self.glucoseModel = glucoseModel
        // Recompute insights whenever the glucose series changes.
        sampleObserver = glucoseModel.$allSamples
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                guard let self else { return }
                self.recomputeInsights(force: false)
            }
        startRecomputeTimer()
    }

    deinit {
        recomputeTimer?.invalidate()
        sampleObserver?.cancel()
    }

    // MARK: Derived

    /// Totals for the day the card is showing.
    var todayTotals: FoodTotals {
        FoodTotals.sum(store.entries(on: selectedDay))
    }

    /// 7-day nutrition patterns (shared engine).
    var patterns: NutritionPatterns {
        PatternsEngine.patterns(entries, inDays: 7,
                                nowEpochSeconds: Date().timeIntervalSince1970)
    }

    /// Coaching questions derived from the 7-day patterns.
    var coaching: [String] {
        PatternsEngine.coachingQuestions(patterns)
    }

    /// Today's entries (for the card), newest first.
    var dayEntries: [FoodEntry] {
        store.entries(on: selectedDay).sorted { $0.date > $1.date }
    }

    /// The most recent insight that has fired (for the watch push).
    var latestInsightText: String? {
        entries
            .compactMap(\.insight)
            .sorted { ($0.peakMgdl - $0.baselineMgdl) < ($1.peakMgdl - $1.baselineMgdl) }
            .last?.text
    }

    // MARK: LLM estimation

    /// Log a meal typed by hand: description -> LLM estimate -> stored entry
    /// (isEstimated=true). Throws if the estimate fails or is empty, so the
    /// sheet can fall back to manual value entry.
    func addManual(description: String,
                  serving: String?,
                  context: String?,
                  at date: Date = Date()) async throws {
        isEstimating = true
        defer { isEstimating = false }
        let request = NutritionPrompt.Request(
            description: description,
            serving: serving,
            context: context,
            mode: .manual,
            photoCount: 0
        )
        let estimate = try await llm.estimate(request)
        var entry = FoodEntry(
            dateEpochSeconds: date.timeIntervalSince1970,
            description: description,
            serving: serving,
            context: context,
            source: .manual,
            isEstimated: true
        )
        apply(estimate, to: &entry)
        store.add(entry)
        refresh()
        recomputeInsights(force: false)
    }

    /// Log a meal from photos: vision LLM estimate -> stored entry with the
    /// photos persisted to Documents/`photos`. The model's own description is
    /// used to prefill when the user left the description empty.
    func addPhoto(description: String?,
                 serving: String?,
                 context: String?,
                 photos: [Data],
                 at date: Date = Date()) async throws {
        isEstimating = true
        defer { isEstimating = false }
        let text = description?.isEmpty == false ? description! : ""
        let request = NutritionPrompt.Request(
            description: text.isEmpty ? "meal photo" : text,
            serving: serving,
            context: context,
            mode: .photo,
            photoCount: photos.count
        )
        let estimate = try await llm.estimate(request, photos: photos)
        var entry = FoodEntry(
            dateEpochSeconds: date.timeIntervalSince1970,
            description: estimate.description ?? text,
            serving: serving,
            context: context,
            source: .photo,
            isEstimated: true
        )
        apply(estimate, to: &entry)
        // Persist the photos and record their file names on the entry.
        var names: [String] = []
        for (index, data) in photos.enumerated() {
            names.append(store.savePhoto(data, for: entry.id, index: index))
        }
        entry.photoFileNames = names
        store.add(entry)
        refresh()
        recomputeInsights(force: false)
    }

    /// Re-run the estimate for an existing entry using its current
    /// description / context / photos, and update it (isEstimated=true).
    func reanalyze(_ entry: FoodEntry) async throws {
        isEstimating = true
        defer { isEstimating = false }
        let photos = store.photoData(for: entry)
        let request = NutritionPrompt.Request(
            description: entry.description,
            serving: entry.serving,
            context: entry.context,
            mode: entry.source == .photo ? .photo : .manual,
            photoCount: entry.photoFileNames.count
        )
        let estimate = entry.source == .photo
            ? try await llm.estimate(request, photos: photos)
            : try await llm.estimate(request)
        var updated = entry
        apply(estimate, to: &updated)
        // A fresh photo estimate may rename the meal (its own description).
        if entry.source == .photo, let desc = estimate.description, !desc.isEmpty {
            updated.description = desc
        }
        updated.isEstimated = true
        store.update(updated)
        refresh()
        recomputeInsights(force: false)
    }

    /// Save a manual edit (flips isEstimated=false — the values are now the
    /// user's, not the model's).
    func edit(_ entry: FoodEntry) {
        var updated = entry
        updated.isEstimated = false
        store.update(updated)
        refresh()
        recomputeInsights(force: false)
    }

    /// Remove an entry and its photos.
    func delete(_ entry: FoodEntry) {
        store.delete(id: entry.id)
        refresh()
    }

    /// Remove one photo from an entry (file + name), persisting the change.
    func deletePhoto(_ entry: FoodEntry, index: Int) {
        guard entry.photoFileNames.indices.contains(index) else { return }
        var updated = entry
        let name = updated.photoFileNames[index]
        updated.photoFileNames.remove(at: index)
        // Re-index the remaining files so names stay `{id}_{i}.jpg`.
        var renamed: [String] = []
        for (i, existing) in updated.photoFileNames.enumerated() {
            let newName = "\(entry.id)_\(i).jpg"
            if newName != existing {
                let dir = FoodLogStore.photosDirectory
                try? FileManager.default.moveItem(
                    at: dir.appendingPathComponent(existing),
                    to: dir.appendingPathComponent(newName)
                )
            }
            renamed.append(newName)
        }
        try? FileManager.default.removeItem(
            at: FoodLogStore.photosDirectory.appendingPathComponent(name)
        )
        updated.photoFileNames = renamed
        store.update(updated)
        refresh()
    }

    // MARK: Insight recompute

    /// Compute (once) a `MealInsight` for any entry whose full post-meal window
    /// has elapsed and which has no insight yet. Cheap: it only reads the
    /// series and the engine filters; throttled to at most once per
    /// `recomputeInterval` unless `force`.
    func recomputeInsights(force: Bool = false) {
        let now = Date()
        guard force || now.timeIntervalSince(lastRecompute) >= recomputeInterval else { return }
        lastRecompute = now

        guard let samples = glucoseModel?.allSamples else { return }
        let nowEpoch = now.timeIntervalSince1970
        let samplePoints = samples.map {
            MealInsightEngine.Sample(epochSeconds: $0.date.timeIntervalSince1970,
                                     mgdl: $0.mgdl)
        }
        let config = MealInsightEngine.Config.default
        var changed = false
        for i in entries.indices {
            let e = entries[i]
            guard e.insight == nil else { continue }
            // "Final only": the whole post window must have elapsed.
            let postHi = e.dateEpochSeconds + config.postWindowMin * 60
            guard nowEpoch >= postHi else { continue }
            let crossMeal = crossMealComparison(for: e)
            let input = MealInsightEngine.Input(
                mealDateEpochSeconds: e.dateEpochSeconds,
                carbsG: e.carbsG,
                description: e.description,
                samples: samplePoints,
                nowEpochSeconds: nowEpoch
            )
            if let insight = MealInsightEngine.insight(for: input,
                                                       config: config,
                                                       crossMeal: crossMeal) {
                entries[i].insight = insight
                changed = true
            }
        }
        if changed {
            store.persistAll(entries)
            objectWillChange.send()
        }
    }

    /// Call at app start (and on foreground) so a lightweight timer keeps
    /// insights landing as `now` advances past each meal's 3 h mark.
    func startRecomputeTimer() {
        recomputeTimer?.invalidate()
        recomputeTimer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.recomputeInsights(force: false)
            }
        }
    }

    /// Build a "your usual response" comparison from PRIOR similar entries
    /// whose insight already fired (instance count + average peak delta).
    private func crossMealComparison(for entry: FoodEntry) -> MealInsightEngine.CrossMealComparison? {
        let prior = entries
            .filter { $0.id != entry.id && $0.dateEpochSeconds < entry.dateEpochSeconds }
            .filter { Self.descriptionsSimilar($0.description, entry.description) }
            .compactMap(\.insight)
        guard prior.count >= 2 else { return nil }
        let avg = prior.map(\.peakDeltaMgdl).reduce(0, +) / Double(prior.count)
        return MealInsightEngine.CrossMealComparison(
            instanceCount: prior.count + 1,
            averagePeakDeltaMgdl: avg
        )
    }

    /// Similar = shared description, or a common content word of 4+ letters
    /// (so "bagel with cream cheese" and "bagel" compare).
    private static func descriptionsSimilar(_ a: String, _ b: String) -> Bool {
        let norm: (String) -> [String] = { s in
            s.lowercased()
                .split(whereSeparator: { !($0.isLetter || $0.isNumber) })
                .map(String.init)
                .filter { $0.count >= 4 }
        }
        let wa = Set(norm(a)), wb = Set(norm(b))
        if !wa.isEmpty && !wb.isEmpty {
            return !wa.isDisjoint(with: wb)
        }
        return a.lowercased() == b.lowercased()
    }

    // MARK: Nutrition-sleep (iOS only)

    /// Refresh the nutrition-sleep association lines. Delegates to the
    /// dedicated `NutritionSleep` surface (HealthKit sleep + evening entries).
    /// Throttled to at most once every 15 min (HealthKit reads are
    /// relatively expensive and the data changes slowly). Returns [] when
    /// HealthKit is unavailable/denied — the card then shows nothing rather
    /// than a misleading empty claim.
    private var lastSleepRefresh = Date.distantPast

    func refreshSleepInsights() async {
        let now = Date()
        guard now.timeIntervalSince(lastSleepRefresh) >= 15 * 60 || sleepInsights.isEmpty else { return }
        lastSleepRefresh = now
        sleepInsights = await NutritionSleep.refresh(
            healthKit: glucoseModel?.healthKit,
            entries: entries
        )
    }

    // MARK: Sheet-facing save/estimate

    /// The structured values the sheet collects from its editable rows.
    struct MealValues {
        var description: String = ""
        var serving: String? = nil
        var context: String? = nil
        var source: FoodEntry.Source = .manual
        var caloriesKcal: Double? = nil
        var proteinG: Double? = nil
        var carbsG: Double? = nil
        var fatG: Double? = nil
        var saturatedFatG: Double? = nil
        var sodiumMg: Double? = nil
        var caffeineMg: Double? = nil
        var alcoholG: Double? = nil
        var citations: [SourceCitation] = []
    }

    /// Run the LLM estimate the sheet's "Estimate"/"Reanalyze" buttons use
    /// (exposes `isEstimating` for the UI spinner).
    func runEstimate(request: NutritionPrompt.Request,
                     photos: [Data]) async throws -> NutritionPrompt.Estimate {
        isEstimating = true
        defer { isEstimating = false }
        return try await llm.estimate(request, photos: photos)
    }

    /// Save a meal from the sheet, for a NEW entry or an EDIT of `existing`.
    /// Photos in `photos` replace the entry's stored photos (old files are
    /// removed); for manual entries pass an empty array. The entry's date is
    /// preserved on edit and is "now" for new entries.
    @discardableResult
    func saveEntry(existing: FoodEntry?,
                   values: MealValues,
                   isEstimated: Bool,
                   photos: [Data] = []) -> FoodEntry {
        let date = existing?.date ?? Date()
        let id = existing?.id ?? UUID().uuidString
        var entry = FoodEntry(
            id: id,
            dateEpochSeconds: date.timeIntervalSince1970,
            description: values.description.isEmpty ? (existing?.description ?? "Meal") : values.description,
            serving: values.serving,
            context: values.context,
            source: values.source,
            isEstimated: isEstimated
        )
        entry.caloriesKcal = values.caloriesKcal
        entry.proteinG = values.proteinG
        entry.carbsG = values.carbsG
        entry.fatG = values.fatG
        entry.saturatedFatG = values.saturatedFatG
        entry.sodiumMg = values.sodiumMg
        entry.caffeineMg = values.caffeineMg
        entry.alcoholG = values.alcoholG
        entry.citations = values.citations.isEmpty ? existing?.citations : values.citations
        // Editing a stored value clears any earlier insight — the curve is
        // re-evaluated against the (possibly new) carb value.
        if existing != nil && existing?.insight != nil {
            entry.insight = nil
            // Re-arm: if the window already elapsed, the next recompute lands
            // a fresh insight from the new carbs.
        }

        // Replace stored photos: drop the old files, write the new ones under
        // the stable `{id}_{index}.jpg` names.
        store.deletePhotos(for: id)
        var names: [String] = []
        if values.source == .photo {
            for (i, data) in photos.enumerated() {
                names.append(store.savePhoto(data, for: id, index: i))
            }
        }
        entry.photoFileNames = names
        if existing != nil {
            store.update(entry)
        } else {
            store.add(entry)
        }
        refresh()
        recomputeInsights(force: true)
        return entry
    }

    // MARK: Helpers

    /// Fold an LLM estimate into an entry (keeps any value the estimate left
    /// null; does NOT clear the description — the caller decides on that).
    private func apply(_ estimate: NutritionPrompt.Estimate, to entry: inout FoodEntry) {
        entry.caloriesKcal = estimate.caloriesKcal
        entry.proteinG = estimate.proteinG
        entry.carbsG = estimate.carbsG
        entry.fatG = estimate.fatG
        entry.sodiumMg = estimate.sodiumMg
        entry.caffeineMg = estimate.caffeineMg
        entry.alcoholG = estimate.alcoholG
        entry.saturatedFatG = estimate.saturatedFatG
        if !estimate.citations.isEmpty {
            entry.citations = estimate.citations
        }
    }

    /// Reload entries from the store into the published state.
    func refresh() {
        entries = store.all()
    }
}
