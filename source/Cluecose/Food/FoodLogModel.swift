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
//  Saving is the analysis: `saveEntry` is the only save path (the sheet and
//  the detail view both funnel through it). Saving always runs the estimate
//  (model endpoint, or the local keyword fallback when the endpoint is
//  unreachable), scales it by the portion multiplier, honors the user's
//  hand-edited rows, and re-arms the per-meal insight. A meal with no data at
//  all (no nutrient values AND no photos) cannot be saved.
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

    // MARK: Meal analytics state (per-meal blood-sugar insight)

    /// The engine's post-meal analysis window, in minutes. The meal's glucose
    /// response can only be judged once this whole window has elapsed
    /// ("final only" — see `MealInsightEngine`), so the UI can tell the user
    /// exactly WHEN the analysis lands and WHAT it will do.
    static var postWindowMin: Double { MealInsightEngine.Config.default.postWindowMin }

    /// The per-meal analytics state for one entry, as of `now`:
    ///
    ///  • `.ready`   — the insight has been computed and is on the entry.
    ///  • `.pending` — the post-meal window hasn't fully elapsed yet: the
    ///    curve is still forming, and the analysis will run (and the insight
    ///    appear) at `readyAt`, about `remainingMin` from now.
    ///  • `.notYet`  — the meal is still inside the pre/post window but the
    ///    baseline hasn't been established either (meal just logged); same
    ///    readiness instant as `.pending`.
    ///  • `.noData`  — the app has no glucose series at all (no sensor data
    ///    and not in demo mode), so nothing can be analyzed yet.
    enum MealAnalytics: Equatable {
        case ready
        case pending(readyAt: Date, remainingMin: Int)
        case noData

        /// True while the insight is still being awaited (not yet computed).
        var isPending: Bool {
            if case .pending = self { return true }
            return false
        }

        /// The instant the insight will be computed (window elapsed).
        var readyAt: Date? {
            if case .pending(let at, _) = self { return at }
            return nil
        }
    }

    /// Compute the analytics state for `entry` right now.
    func analytics(for entry: FoodEntry, now: Date = Date()) -> MealAnalytics {
        if entry.insight != nil { return .ready }
        guard let samples = glucoseModel?.allSamples, !samples.isEmpty else {
            return .noData
        }
        let readyAt = entry.date.addingTimeInterval(Self.postWindowMin * 60)
        let remaining = readyAt.timeIntervalSince(now)
        if remaining > 0 {
            return .pending(readyAt: readyAt,
                            remainingMin: Int((remaining / 60).rounded(.up)))
        }
        // Window elapsed but no insight yet: the engine found the response
        // wasn't insight-worthy (flat curve), or the recompute hasn't run.
        return .ready
    }

    // MARK: Save / re-analyze

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
        /// Portion slider (0.1…4.0, default 1.0) — how much of the described
        /// portion the user actually ate.
        var portionMultiplier: Double = 1.0
    }

    /// Run the LLM estimate (exposes `isEstimating` for the UI spinner).
    func runEstimate(request: NutritionPrompt.Request,
                     photos: [Data]) async throws -> NutritionPrompt.Estimate {
        isEstimating = true
        defer { isEstimating = false }
        return try await llm.estimate(request, photos: photos)
    }

    /// Save a meal, for a NEW entry or an EDIT of `existing`. This is the
    /// ONLY save path — saving always runs the analysis:
    ///
    ///  • The model endpoint (or its local keyword fallback when the endpoint
    ///    is unreachable) produces a base estimate of the FULL described
    ///    portion, scaled by `values.portionMultiplier` (0.1…4.0).
    ///  • The user's hand-edited nutrient rows are respected verbatim — the
    ///    user is in control.
    ///  • A change to the description / serving / context / source re-runs the
    ///    analysis immediately; the portion slider rescales the stored
    ///    estimate without re-calling the model (instant).
    ///  • A meal with no data at all (no nutrient values AND no photos)
    ///    cannot be saved — it is returned unchanged and NOT persisted.
    ///
    /// Photos in `photos` replace the entry's stored photos (old files are
    /// removed); for manual entries pass an empty array. The entry's date is
    /// preserved on edit and is "now" for new entries.
    @discardableResult
    func saveEntry(existing: FoodEntry?,
                   values: MealValues,
                   photos: [Data] = []) async -> FoodEntry {
        let id = existing?.id ?? UUID().uuidString
        // The store is the source of truth for the CURRENT state (an `existing`
        // copy may already carry a newer portion value, e.g. straight off the
        // slider). All "what changed" comparisons use the stored entry.
        let current: FoodEntry? = id == existing?.id
            ? store.all().first { $0.id == id }
            : nil
        let date = current?.date ?? Date()

        var entry = FoodEntry(
            id: id,
            dateEpochSeconds: date.timeIntervalSince1970,
            description: values.description.isEmpty ? (existing?.description ?? "Meal") : values.description,
            serving: values.serving,
            context: values.context,
            source: values.source,
            photoFileNames: current?.photoFileNames ?? (existing?.photoFileNames ?? []),
            portionMultiplier: values.portionMultiplier
        )
        entry.citations = values.citations.isEmpty ? (current?.citations ?? existing?.citations) : values.citations

        // ---- Analyze (always on save) ------------------------------------
        let analysisChanged = current == nil
            || current?.description != entry.description
            || current?.serving != values.serving
            || current?.context != values.context
            || current?.source != values.source
        let estimate: NutritionPrompt.Estimate
        if analysisChanged {
            let request = NutritionPrompt.Request(
                description: entry.description,
                serving: values.serving,
                context: values.context,
                mode: values.source == .photo ? .photo : .manual,
                photoCount: values.source == .photo
                    ? photos.count
                    : (current?.photoFileNames.count ?? 0)
            )
            let photosToSend = values.source == .photo ? photos : []
            // Model first; local keyword fallback when the endpoint is down.
            // (isEstimating drives the Save-button spinner while in flight.)
            isEstimating = true
            do {
                estimate = try await llm.estimate(request, photos: photosToSend)
            } catch {
                estimate = llm.fallbackEstimate(description: request.description,
                                                serving: request.serving,
                                                context: request.context)
            }
            isEstimating = false
            // A fresh photo estimate may rename the meal (its own description).
            if values.source == .photo, let d = estimate.description,
               !d.isEmpty, values.description.isEmpty {
                entry.description = d
            }
        } else {
            // Same analysis inputs: recover the unscaled base from the STORED
            // (already scaled) values, then rescale below if the portion
            // changed.
            let base: (Double?) -> Double? = { v in
                v.map { $0 / ((current?.portionMultiplier) ?? 1.0) }
            }
            estimate = NutritionPrompt.Estimate(
                caloriesKcal: base(current?.caloriesKcal),
                proteinG: base(current?.proteinG),
                carbsG: base(current?.carbsG),
                fatG: base(current?.fatG),
                saturatedFatG: base(current?.saturatedFatG),
                sodiumMg: base(current?.sodiumMg),
                caffeineMg: base(current?.caffeineMg),
                alcoholG: base(current?.alcoholG),
                description: nil,
                citations: values.citations
            )
        }
        apply(scaled(estimate, by: values.portionMultiplier), to: &entry)
        entry.isEstimated = true

        // ---- Honor the user's hand-edited rows ---------------------------
        // Every row the user explicitly entered (non-nil) overrides the
        // estimate; blank rows keep the (scaled) estimate.
        if let v = values.caloriesKcal { entry.caloriesKcal = v }
        if let v = values.proteinG { entry.proteinG = v }
        if let v = values.carbsG { entry.carbsG = v }
        if let v = values.fatG { entry.fatG = v }
        if let v = values.saturatedFatG { entry.saturatedFatG = v }
        if let v = values.sodiumMg { entry.sodiumMg = v }
        if let v = values.caffeineMg { entry.caffeineMg = v }
        if let v = values.alcoholG { entry.alcoholG = v }

        // ---- Gate: no data at all -> not savable --------------------------
        // (The sheet blocks the button too; this is the model-level
        // guarantee. Nothing is persisted in that case.)
        let hasNutrition = [entry.caloriesKcal, entry.proteinG, entry.carbsG,
                            entry.fatG, entry.saturatedFatG, entry.sodiumMg,
                            entry.caffeineMg, entry.alcoholG]
            .contains { $0 != nil }
        let hasPhotos = values.source == .photo && !photos.isEmpty
        guard hasNutrition || hasPhotos else { return entry }

        // ---- Photos --------------------------------------------------------
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

        // Editing the meal re-arms its insight: the curve is re-evaluated
        // against the (possibly new) carb value once the window allows.
        if current != nil && current?.insight != nil {
            entry.insight = nil
        }

        if current != nil {
            store.update(entry)
        } else {
            store.add(entry)
        }
        refresh()
        recomputeInsights(force: true)
        return entry
    }

    /// Update an existing entry (detail view: portion slider or edited rows).
    /// Reuses `saveEntry` so analysis + insight re-arm are identical to the
    /// sheet's save path.
    @discardableResult
    func updateEntry(_ updated: FoodEntry) async -> FoodEntry {
        let photos = store.photoData(for: updated)
        return await saveEntry(existing: updated,
                               values: values(from: updated),
                               photos: photos)
    }

    /// The `MealValues` equivalent of an entry (for `updateEntry`).
    func values(from entry: FoodEntry) -> MealValues {
        MealValues(
            description: entry.description,
            serving: entry.serving,
            context: entry.context,
            source: entry.source,
            caloriesKcal: entry.caloriesKcal,
            proteinG: entry.proteinG,
            carbsG: entry.carbsG,
            fatG: entry.fatG,
            saturatedFatG: entry.saturatedFatG,
            sodiumMg: entry.sodiumMg,
            caffeineMg: entry.caffeineMg,
            alcoholG: entry.alcoholG,
            citations: entry.citations ?? [],
            portionMultiplier: entry.portionMultiplier
        )
    }

    /// Scale every non-nil estimate value by `multiplier` (portion adjust).
    private func scaled(_ e: NutritionPrompt.Estimate, by multiplier: Double) -> NutritionPrompt.Estimate {
        let s: (Double?) -> Double? = { v in v.map { ($0 * multiplier).rounded() } }
        return NutritionPrompt.Estimate(
            caloriesKcal: s(e.caloriesKcal),
            proteinG: s(e.proteinG),
            carbsG: s(e.carbsG),
            fatG: s(e.fatG),
            saturatedFatG: s(e.saturatedFatG),
            sodiumMg: s(e.sodiumMg),
            caffeineMg: s(e.caffeineMg),
            alcoholG: s(e.alcoholG),
            description: e.description,
            citations: e.citations
        )
    }

    // MARK: Remove

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
