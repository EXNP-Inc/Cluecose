//
//  FoodTypes.swift
//  CluecoseCore
//
//  Domain types for the food / nutrition feature, shared by the iPhone and
//  Watch apps (and mirrored by the Android `:core` module so the two
//  platforms behave identically).
//
//  Cross-platform contract:
//    • The JSON field names below ARE the storage contract. The Android
//      `:core` `FoodEntry` uses matching @SerialNames, and both persistence
//      layers (iOS UserDefaults, Android SharedPreferences) write the same
//      schema under the key `cluecose.food.entries`.
//    • Dates are stored as `dateEpochSeconds` (seconds since 1970-01-01)
//      so the encoding is unambiguous across Swift (Codable) and Kotlin
//      (kotlinx.serialization) with no Date-strategy coupling.
//    • Nutrition values are nullable: an LLM estimate may be missing a
//      component, and a hand-entered row may leave one blank.
//
//  Nutrient set (deliberately limited — this is a CGM companion, not a
//  clinical diet app): calories, the three macros, sodium and caffeine, plus
//  two macro-level nutrients carried over from the ported product (alcohol
//  and saturated fat). There are NO vitamins or minerals by design.
//
//  Ported capabilities (see the feature set in the PR description):
//    • Up to 5 meal photos per entry (`photoFileNames`).
//    • A free-text `context` the person supplies that a photo cannot know
//      (portion, brand, preparation, hidden ingredients) — fed to the model
//      on reanalysis and stored on the record.
//    • `citations` — clickable source links a web-grounded estimate can carry.
//
//  All branding is Cluecose; no reference to the ported-from product, its
//  mascot, or any image-generation feature appears anywhere in this module.
//

import Foundation

// MARK: - Source citation

/// A clickable reference a web-grounded nutrition estimate can carry. Present
/// only when the model returned supporting sources; an empty/absent list means
/// the estimate was produced from general knowledge alone.
public struct SourceCitation: Codable, Equatable, Sendable {
    public var title: String
    public var url: String

    public init(title: String, url: String) {
        self.title = title
        self.url = url
    }
}

// MARK: - Meal entry

public struct FoodEntry: Identifiable, Codable, Equatable, Sendable {
    public enum Source: String, Codable, Sendable {
        case manual   // typed description (+ optional serving/context)
        case photo    // one or more meal photos analyzed by a vision model
    }

    public let id: String
    /// When the meal was eaten, as seconds since 1970-01-01 (UTC).
    public var dateEpochSeconds: Double
    /// Free-text description of the food (e.g. "two slices of turkey sandwich").
    public var description: String
    /// Optional portion/serving note fed to the estimator ("2 slices").
    public var serving: String?
    /// Free-text context the user knows that a photo cannot infer — quantity,
    /// ingredient, brand, preparation, hidden side. Fed to the estimator on
    /// (re)analysis and stored on the record.
    public var context: String?
    /// How the estimate was produced.
    public var source: Source
    /// File names (not paths) of the stored photos, when source == .photo.
    /// Up to 5; the first is the primary image.
    public var photoFileNames: [String]
    // Nutrition — all optional; null = unknown / not applicable.
    public var caloriesKcal: Double?
    public var proteinG: Double?
    public var carbsG: Double?
    public var fatG: Double?
    public var sodiumMg: Double?
    public var caffeineMg: Double?
    /// Total alcohol in grams (ported dimension; null = none/unknown).
    public var alcoholG: Double?
    /// Saturated fat in grams (ported fat-quality dimension; null = unknown).
    public var saturatedFatG: Double?
    /// Clickable sources supporting the estimate (web-grounded analyses).
    public var citations: [SourceCitation]?
    /// True while the values are still the model's estimate and the user has
    /// not edited them. Flips to false on the first manual edit.
    public var isEstimated: Bool
    /// Phase 2: a per-meal blood-sugar insight, present only when the
    /// post-meal curve was insight-worthy. nil = nothing to show.
    public var insight: MealInsight?

    public var date: Date { Date(timeIntervalSince1970: dateEpochSeconds) }

    public init(id: String = UUID().uuidString,
                dateEpochSeconds: Double,
                description: String,
                serving: String? = nil,
                context: String? = nil,
                source: Source = .manual,
                photoFileNames: [String] = [],
                caloriesKcal: Double? = nil,
                proteinG: Double? = nil,
                carbsG: Double? = nil,
                fatG: Double? = nil,
                sodiumMg: Double? = nil,
                caffeineMg: Double? = nil,
                alcoholG: Double? = nil,
                saturatedFatG: Double? = nil,
                citations: [SourceCitation]? = nil,
                isEstimated: Bool = false,
                insight: MealInsight? = nil) {
        self.id = id
        self.dateEpochSeconds = dateEpochSeconds
        self.description = description
        self.serving = serving
        self.context = context
        self.source = source
        self.photoFileNames = photoFileNames
        self.caloriesKcal = caloriesKcal
        self.proteinG = proteinG
        self.carbsG = carbsG
        self.fatG = fatG
        self.sodiumMg = sodiumMg
        self.caffeineMg = caffeineMg
        self.alcoholG = alcoholG
        self.saturatedFatG = saturatedFatG
        self.citations = citations
        self.isEstimated = isEstimated
        self.insight = insight
    }

    // Custom Codable: lenient on decode (missing/new fields default cleanly),
    // exact key names on encode so the Android mirror round-trips the same JSON.
    private enum CodingKeys: String, CodingKey {
        case id, dateEpochSeconds, description, serving, context, source, photoFileNames
        case caloriesKcal, proteinG, carbsG, fatG, sodiumMg, caffeineMg
        case alcoholG, saturatedFatG, citations, isEstimated, insight
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        // Lenient: a missing id gets a fresh one (test fixtures may omit it);
        // encode always writes one.
        id = try c.decodeIfPresent(String.self, forKey: .id) ?? UUID().uuidString
        dateEpochSeconds = try c.decode(Double.self, forKey: .dateEpochSeconds)
        description = try c.decode(String.self, forKey: .description)
        serving = try c.decodeIfPresent(String.self, forKey: .serving)
        context = try c.decodeIfPresent(String.self, forKey: .context)
        source = try c.decodeIfPresent(Source.self, forKey: .source) ?? .manual
        // Lenient: accept the newer array, an absent field, or a legacy
        // single-string `photoFileName` so older records still load.
        if let arr = try c.decodeIfPresent([String].self, forKey: .photoFileNames) {
            photoFileNames = arr
        } else {
            photoFileNames = []
        }
        caloriesKcal = try c.decodeIfPresent(Double.self, forKey: .caloriesKcal)
        proteinG = try c.decodeIfPresent(Double.self, forKey: .proteinG)
        carbsG = try c.decodeIfPresent(Double.self, forKey: .carbsG)
        fatG = try c.decodeIfPresent(Double.self, forKey: .fatG)
        sodiumMg = try c.decodeIfPresent(Double.self, forKey: .sodiumMg)
        caffeineMg = try c.decodeIfPresent(Double.self, forKey: .caffeineMg)
        alcoholG = try c.decodeIfPresent(Double.self, forKey: .alcoholG)
        saturatedFatG = try c.decodeIfPresent(Double.self, forKey: .saturatedFatG)
        citations = try c.decodeIfPresent([SourceCitation].self, forKey: .citations)
        isEstimated = try c.decodeIfPresent(Bool.self, forKey: .isEstimated) ?? false
        insight = try c.decodeIfPresent(MealInsight.self, forKey: .insight)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(dateEpochSeconds, forKey: .dateEpochSeconds)
        try c.encode(description, forKey: .description)
        try c.encodeIfPresent(serving, forKey: .serving)
        try c.encodeIfPresent(context, forKey: .context)
        try c.encode(source, forKey: .source)
        // Always encode the array (possibly empty) so the schema is stable.
        try c.encode(photoFileNames, forKey: .photoFileNames)
        try c.encodeIfPresent(caloriesKcal, forKey: .caloriesKcal)
        try c.encodeIfPresent(proteinG, forKey: .proteinG)
        try c.encodeIfPresent(carbsG, forKey: .carbsG)
        try c.encodeIfPresent(fatG, forKey: .fatG)
        try c.encodeIfPresent(sodiumMg, forKey: .sodiumMg)
        try c.encodeIfPresent(caffeineMg, forKey: .caffeineMg)
        try c.encodeIfPresent(alcoholG, forKey: .alcoholG)
        try c.encodeIfPresent(saturatedFatG, forKey: .saturatedFatG)
        try c.encodeIfPresent(citations, forKey: .citations)
        try c.encode(isEstimated, forKey: .isEstimated)
        try c.encodeIfPresent(insight, forKey: .insight)
    }
}

// MARK: - Per-meal insight (Phase 2)

/// A deterministic, on-device blood-sugar insight for one meal, computed from
/// the pre-meal baseline and the post-meal curve. Present only when the
/// response was "insight-worthy" (a meaningful spike or a reactive dip);
/// otherwise the entry's `insight` is nil and the UI shows nothing.
///
/// The NUMERIC fields are the cross-platform contract — both the Swift and
/// Kotlin engines are golden-tested against the same `insight-*.json`
/// fixtures. `text` is a presentation string built from those numbers
/// (localized rendering is a platform concern); it is not part of the
/// parity contract.
public struct MealInsight: Codable, Equatable, Sendable {
    public enum Kind: String, Codable, Sendable {
        case spike        // a clear post-meal rise above baseline
        case reactiveLow  // a post-meal dip below baseline (reactive hypoglycemia)
    }

    public var kind: Kind
    /// Mean glucose over the pre-meal baseline window (mg/dL).
    public var baselineMgdl: Double
    /// Highest glucose reached in the post-meal window (mg/dL).
    public var peakMgdl: Double
    /// peakMgdl - baselineMgdl.
    public var peakDeltaMgdl: Double
    /// Minutes from the meal to the peak.
    public var timeToPeakMin: Double
    /// Minutes from the meal to the first return to within ±10 mg/dL of
    /// baseline after the peak; nil if it had not returned by window end.
    public var returnToBaselineMin: Double?
    /// Lowest glucose in the post-meal window (mg/dL).
    public var minMgdl: Double
    /// minMgdl - baselineMgdl (negative when below baseline).
    public var minDeltaMgdl: Double
    /// peakDeltaMgdl normalized per 10 g of carbs from the entry; nil when the
    /// entry has no carb value.
    public var per10gCarbDeltaMgdl: Double?
    /// One-to-two sentence summary built from the fields above.
    public var text: String

    public init(kind: Kind,
                baselineMgdl: Double,
                peakMgdl: Double,
                peakDeltaMgdl: Double,
                timeToPeakMin: Double,
                returnToBaselineMin: Double?,
                minMgdl: Double,
                minDeltaMgdl: Double,
                per10gCarbDeltaMgdl: Double?,
                text: String) {
        self.kind = kind
        self.baselineMgdl = baselineMgdl
        self.peakMgdl = peakMgdl
        self.peakDeltaMgdl = peakDeltaMgdl
        self.timeToPeakMin = timeToPeakMin
        self.returnToBaselineMin = returnToBaselineMin
        self.minMgdl = minMgdl
        self.minDeltaMgdl = minDeltaMgdl
        self.per10gCarbDeltaMgdl = per10gCarbDeltaMgdl
        self.text = text
    }
}

// MARK: - Daily / period totals

/// Sums of a set of entries for the header rows of the food and patterns
/// surfaces. Each value is nil when none of the entries reported that
/// nutrient (nil-aware: a missing value never zeroes or skews the sum).
public struct FoodTotals: Equatable, Sendable {
    public var kcal: Double?
    public var proteinG: Double?
    public var carbsG: Double?
    public var fatG: Double?
    public var sodiumMg: Double?
    public var caffeineMg: Double?
    public var alcoholG: Double?
    public var saturatedFatG: Double?
    public var entryCount: Int = 0

    public init() {}

    /// Fold a set of entries into running totals.
    public static func sum(_ entries: [FoodEntry]) -> FoodTotals {
        var t = FoodTotals()
        t.entryCount = entries.count
        for e in entries {
            t.kcal = add(t.kcal, e.caloriesKcal)
            t.proteinG = add(t.proteinG, e.proteinG)
            t.carbsG = add(t.carbsG, e.carbsG)
            t.fatG = add(t.fatG, e.fatG)
            t.sodiumMg = add(t.sodiumMg, e.sodiumMg)
            t.caffeineMg = add(t.caffeineMg, e.caffeineMg)
            t.alcoholG = add(t.alcoholG, e.alcoholG)
            t.saturatedFatG = add(t.saturatedFatG, e.saturatedFatG)
        }
        return t
    }

    private static func add(_ acc: Double?, _ v: Double?) -> Double? {
        switch (acc, v) {
        case (nil, nil): return nil
        case (let a?, nil): return a
        case (nil, let b?): return b
        case (let a?, let b?): return a + b
        }
    }
}
