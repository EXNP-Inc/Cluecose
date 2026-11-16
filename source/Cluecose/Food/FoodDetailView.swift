//
//  FoodDetailView.swift
//  Cluecose
//
//  The per-meal detail + analytics view, opened from the arrow on a Food card
//  entry (or its insight callout). Shows:
//
//  • the meal's blood-sugar analysis — either the computed `MealInsight`
//    (with its numbers) or, while the post-meal window is still forming, a
//    "collecting the curve" state that says exactly what will happen and
//    WHEN the insight lands (the 3 h window, with a clock and a progress
//    bar). The 60 s insight recompute timer keeps the model object
//    updating, so the view flips to the result automatically.
//  • the full nutrition breakdown with the portion slider (0.1×–4.0× in
//    0.1 steps) — moving it re-scales the entry live via `updateEntry`,
//    which re-runs the analysis path and re-arms the insight.
//  • the description / serving / context rows (editing these re-runs the
//    estimate on save, via the same `saveEntry` path).
//

import SwiftUI
import CluecoseCore

struct FoodDetailView: View {
    @ObservedObject var model: FoodLogModel
    @ObservedObject var glucose: GlucoseModel
    /// The entry being viewed. Value-copied on present; re-saved through
    /// `model.updateEntry` on any change (which keeps the store current).
    @State private var entry: FoodEntry
    @Environment(\.dismiss) private var dismiss

    /// Local edit state for the text fields (committed on Save).
    @State private var descriptionText = ""
    @State private var servingText = ""
    @State private var contextText = ""
    /// Local portion value; the slider writes through immediately so the
    /// user sees the scaled numbers move as they drag.
    @State private var portion: Double = 1.0
    @State private var isSaving = false

    init(model: FoodLogModel,
         glucose: GlucoseModel,
         entry: FoodEntry) {
        self.model = model
        self.glucose = glucose
        _entry = State(initialValue: entry)
        _descriptionText = State(initialValue: entry.description)
        _servingText = State(initialValue: entry.serving ?? "")
        _contextText = State(initialValue: entry.context ?? "")
        _portion = State(initialValue: entry.portionMultiplier)
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    mealHeader
                    analyticsSection
                    portionSection
                    nutritionSection
                    aboutSection
                    if entry.citations?.isEmpty == false {
                        sourcesSection
                    }
                    Spacer(minLength: 8)
                }
                .padding(.horizontal, 18)
                .padding(.top, 12)
                .padding(.bottom, 96)
            }
            // The 60 s recompute timer sets `insight` on the STORED entry
            // while this view is open. Pull it in (only the insight — the
            // local text/portion edits are preserved) and dismiss if the
            // entry was deleted from the log meanwhile.
            .onChange(of: model.entries) { _, stored in
                guard let current = stored.first(where: { $0.id == entry.id }) else {
                    dismiss()
                    return
                }
                guard current.insight != entry.insight else { return }
                var e = entry
                e.insight = current.insight
                entry = e
            }
            .navigationTitle(entry.date.formatted(.dateTime.weekday(.wide).month().day()))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await commitEdits() }
                    } label: {
                        if isSaving {
                            ProgressView()
                        } else {
                            Text("Save")
                                .font(.body.weight(.semibold))
                        }
                    }
                    .disabled(isSaving)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: Meal header

    private var mealHeader: some View {
        HStack(spacing: 12) {
            if let data = model.store.photoData(for: entry).first,
               let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 56, height: 56)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                RoundedRectangle(cornerRadius: 12)
                    .fill(.white.opacity(0.08))
                    .frame(width: 56, height: 56)
                    .overlay(Image(systemName: "fork.knife")
                        .foregroundStyle(.white.opacity(0.4)))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(entry.description)
                    .font(.headline)
                    .foregroundStyle(.white)
                Text(entry.date, format: .dateTime.hour().minute())
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.5))
            }
            Spacer()
        }
    }

    // MARK: Analytics (blood sugar)

    @ViewBuilder
    private var analyticsSection: some View {
        let a = model.analytics(for: entry)
        VStack(alignment: .leading, spacing: 10) {
            Label("Blood sugar", systemImage: "waveform.path.ecg")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)

            if let insight = entry.insight {
                insightResult(insight)
            } else if case .pending(let readyAt, let remainingMin) = a {
                collectingState(readyAt: readyAt, remainingMin: remainingMin)
            } else if case .noData = a {
                noDataState
            } else if case .noWindowData = a {
                noWindowDataState
            } else {
                settledState
            }
        }
        .glassPanel()
    }

    /// The computed insight: the text plus the underlying numbers.
    private func insightResult(_ insight: MealInsight) -> some View {
        let color: Color = insight.kind == .spike
            ? Color(red: 1.0, green: 0.62, blue: 0.35)
            : Color(red: 0.45, green: 0.78, blue: 1.0)
        let symbol = insight.kind == .spike ? "arrow.up.right" : "arrow.down"
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 7) {
                Image(systemName: symbol)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(color)
                Text(insight.text)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.3), lineWidth: 1))

            HStack(spacing: 8) {
                metric("Baseline", value: glucose.unit.format(fromMgdl: insight.baselineMgdl) + " " + glucose.unit.rawValue)
                metric("Peak", value: glucose.unit.format(fromMgdl: insight.peakMgdl) + " " + glucose.unit.rawValue)
                metric("Δ from baseline", value: (insight.peakDeltaMgdl >= 0 ? "+" : "") + glucose.unit.formatted(fromMgdl: insight.peakDeltaMgdl, decimals: glucose.unit == .mmol ? 1 : 0))
            }
            HStack(spacing: 8) {
                metric("Time to peak", value: String(format: "%.0f min", insight.timeToPeakMin))
                if let rtb = insight.returnToBaselineMin {
                    metric("Back to baseline", value: String(format: "%.0f min", rtb))
                }
                if let p10 = insight.per10gCarbDeltaMgdl {
                    metric("Per 10 g carbs", value: String(format: "%+.0f mg/dL", p10))
                }
            }
        }
    }

    /// The window is still forming: say WHAT will happen and WHEN it lands.
    private func collectingState(readyAt: Date, remainingMin: Int) -> some View {
        let windowMin = FoodLogModel.postWindowMin
        let fraction = min(1, max(0, entry.date.timeIntervalSinceNow / -(windowMin * 60)))
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "hourglass")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color(red: 0.45, green: 0.78, blue: 1.0))
                Text("Collecting your blood-sugar curve for this meal…")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white)
            }
            Text("We measure your pre-meal baseline, then watch your glucose for \(Int(windowMin)) minutes after eating. When the window closes at \(readyAt, format: .dateTime.hour().minute()) (in about \(remainingMin) min), we'll compute how this meal moved your blood sugar — a spike, a reactive dip, or no meaningful change — and show the result here automatically.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.65))
                .fixedSize(horizontal: false, vertical: true)
            ProgressView(value: fraction)
                .tint(Color(red: 0.45, green: 0.78, blue: 1.0))
            Text("\(Int(fraction * 100))% of the analysis window elapsed")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.45))
        }
    }

    /// No glucose series at all (no sensor data, not in demo mode).
    private var noDataState: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "sensor.tag.radiowaves.forward.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.5))
                Text("Waiting for glucose data")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white)
            }
            Text("No glucose readings are available yet, so this meal's blood-sugar response can't be analyzed. Pair your sensor (or turn on Demo mode in Settings) and the analysis will run automatically once the post-meal window has elapsed.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.65))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// There IS a glucose series, but none of it falls inside this meal's
    /// analysis window (30 min before → 3 h after): a meal logged before the
    /// sensor started, across an app gap, or into an era with no readings.
    /// The analysis cannot run and never will — distinct from
    /// `settledState` (the window had data and was flat). (Android
    /// `NoWindowDataState`.)
    private var noWindowDataState: some View {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEE, MMM d · h:mm a"
        let mealLabel = formatter.string(from: entry.date)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "hourglass")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.5))
                Text("No glucose data in this meal's window")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white)
            }
            Text("This meal was logged for \(mealLabel), and no glucose readings exist between 30 minutes before it and 3 hours after it (the analysis window). The sensor wasn't streaming then — it started later, or the data isn't available on this device — so this meal's blood-sugar response can't be analyzed. Meals logged from now on get analyzed automatically once their 3-hour window closes.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.65))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The 3 h window has fully elapsed and the analysis ran on real data in
    /// the window but found nothing worth flagging: the result is final — no
    /// insight will ever appear for this meal. A terminal state, not "waiting".
    private var settledState: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color(red: 0.45, green: 0.85, blue: 0.6))
                Text("Analysis complete — no notable response")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white)
            }
            Text("We analyzed your blood sugar for \(Int(FoodLogModel.postWindowMin)) minutes after this meal: no spike or dip worth flagging — it stayed within ±30 mg/dL of your pre-meal level. Nothing to act on here; the analysis for this meal is complete.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.65))
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func metric(_ label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.45))
            Text(value)
                .font(.footnote.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: Portion

    private var portionSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("How much did you eat?")
                    .font(.system(size: 14, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                Spacer()
                Text(String(format: "%.1f×", portion))
                    .font(.footnote.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(.white)
            }
            // Live while dragging; persisted (debounced) shortly after the
            // drag settles — this SDK's Slider has no onEditingChanged, so a
            // plain release-commit isn't observable, and a per-tick write
            // would hit the store up to 39 times per drag.
            Slider(value: $portion, in: 0.1...4.0, step: 0.1)
                .onChange(of: portion) { _, newValue in
                    applyLivePortion(newValue)
                    schedulePortionPersist()
                }
            Text("The estimate describes the full portion; this scales everything above and below to what you actually ate. Changing it re-runs the meal's blood-sugar analysis against the new carb amount.")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.5))
                .fixedSize(horizontal: false, vertical: true)
        }
        .glassPanel()
    }

    // Debounced persist for the portion slider (see onChange above).
    @State private var portionPersistTask: Task<Void, Never>?
    private let portionPersistDebounce: TimeInterval = 0.5

    /// The slider changed: cancel any pending write, and if the change has
    /// settled for `portionPersistDebounce` seconds, write the entry to the
    /// store exactly once (which re-arms the insight). The on-screen numbers
    /// are already updated live by `applyLivePortion`.
    private func schedulePortionPersist() {
        portionPersistTask?.cancel()
        portionPersistTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(portionPersistDebounce))
            guard !Task.isCancelled else { return }
            let target = portion
            let saved = await model.updateEntry(entry)
            guard !Task.isCancelled else { return }
            // The user kept moving the slider while the write was in
            // flight: the local entry is already live-rescaled to the
            // current position, so just schedule the next write.
            if saved.portionMultiplier != target {
                schedulePortionPersist()
                return
            }
            entry = saved
        }
    }

    /// A change that happened OUTSIDE the slider (the About-section save)
    /// must not leave a stale slider write pending.
    private func cancelPendingPortionPersist() {
        portionPersistTask?.cancel()
        portionPersistTask = nil
    }

    /// While the slider is being dragged: rescale the on-screen numbers
    /// from the STORE's values (not from the local entry — the local one is
    /// already mid-rescale, and rescaling it again per tick would compound).
    /// The store is untouched until the drag commits.
    private func applyLivePortion(_ newPortion: Double) {
        guard let stored = model.entries.first(where: { $0.id == entry.id }) else {
            return
        }
        let old = stored.portionMultiplier == 0 ? 1 : stored.portionMultiplier
        let f: (Double?) -> Double? = { v in
            v.map { (($0 / old) * newPortion).rounded() }
        }
        var e = entry
        e.portionMultiplier = newPortion
        e.caloriesKcal = f(stored.caloriesKcal)
        e.proteinG = f(stored.proteinG)
        e.carbsG = f(stored.carbsG)
        e.fatG = f(stored.fatG)
        e.saturatedFatG = f(stored.saturatedFatG)
        e.sodiumMg = f(stored.sodiumMg)
        e.caffeineMg = f(stored.caffeineMg)
        e.alcoholG = f(stored.alcoholG)
        entry = e
    }

    // MARK: Nutrition

    private var nutritionSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Nutrition")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            HStack(spacing: 8) {
                metric("Calories", value: entry.caloriesKcal.map { String(format: "%.0f kcal", $0) } ?? "–")
                metric("Protein", value: entry.proteinG.map { String(format: "%.0f g", $0) } ?? "–")
                metric("Carbs", value: entry.carbsG.map { String(format: "%.0f g", $0) } ?? "–")
            }
            HStack(spacing: 8) {
                metric("Fat", value: entry.fatG.map { String(format: "%.0f g", $0) } ?? "–")
                metric("Sat fat", value: entry.saturatedFatG.map { String(format: "%.0f g", $0) } ?? "–")
                metric("Sodium", value: entry.sodiumMg.map { String(format: "%.0f mg", $0) } ?? "–")
            }
            HStack(spacing: 8) {
                metric("Caffeine", value: entry.caffeineMg.map { String(format: "%.0f mg", $0) } ?? "–")
                metric("Alcohol", value: entry.alcoholG.map { String(format: "%.0f g", $0) } ?? "–")
            }
            if entry.isEstimated {
                Text("Values are an estimate — edit them in the meal sheet to make them exact.")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.45))
            }
            if [entry.caloriesKcal, entry.proteinG, entry.carbsG, entry.fatG,
                entry.saturatedFatG, entry.sodiumMg, entry.caffeineMg, entry.alcoholG]
                .allSatisfy({ $0 == nil }) {
                Text("Nutrition wasn't analyzed (the model endpoint is unavailable right now). The meal is logged as-is — open it and enter values, or save it again later to retry.")
                    .font(.caption2)
                    .foregroundStyle(.orange.opacity(0.8))
            }
        }
        .glassPanel()
    }

    // MARK: About (editable)

    private var aboutSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("About this meal")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            TextField("What did you eat?", text: $descriptionText)
                .textFieldStyle(.roundedBorder)
            TextField("Serving / portion (optional)", text: $servingText)
                .textFieldStyle(.roundedBorder)
            TextField("Context (optional)", text: $contextText)
                .textFieldStyle(.roundedBorder)
            Text("Changing these re-analyzes the meal with the model when you save (your entered values are kept).")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.45))
        }
        .glassPanel()
    }

    // MARK: Sources

    private var sourcesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Sources")
                .font(.system(size: 14, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            ForEach(Array((entry.citations ?? []).enumerated()), id: \.offset) { _, c in
                Link(destination: URL(string: c.url) ?? URL(string: "https://www.example.com")!) {
                    HStack {
                        Image(systemName: "link")
                            .font(.caption)
                        Text(c.title.isEmpty ? c.url : c.title)
                            .font(.footnote)
                        Spacer()
                    }
                }
            }
        }
        .glassPanel()
    }

    // MARK: Commit

    /// Save the About-section edits (portion already writes through on drag).
    ///
    /// The nutrient rows in this view are READ-ONLY, so they are passed as nil
    /// — that way a change to the description/serving/context re-runs the
    /// analysis and the fresh estimate (scaled by the portion) fills the
    /// values. When nothing changed, the stored values round-trip unchanged.
    private func commitEdits() async {
        isSaving = true
        defer { isSaving = false }
        cancelPendingPortionPersist()
        var e = entry
        let values = FoodLogModel.MealValues(
            description: descriptionText,
            serving: servingText.isEmpty ? nil : servingText,
            context: contextText.isEmpty ? nil : contextText,
            source: e.source,
            // nil = "no hand edit here" → the (re)estimate / stored values win.
            caloriesKcal: nil, proteinG: nil, carbsG: nil, fatG: nil,
            saturatedFatG: nil, sodiumMg: nil, caffeineMg: nil, alcoholG: nil,
            citations: e.citations ?? [],
            portionMultiplier: portion
        )
        let photos = model.store.photoData(for: e)
        let saved = await model.saveEntry(existing: e, values: values, photos: photos)
        entry = saved
        descriptionText = saved.description
    }
}

