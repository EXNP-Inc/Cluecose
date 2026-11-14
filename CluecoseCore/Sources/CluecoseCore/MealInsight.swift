//
//  MealInsight.swift
//  CluecoseCore
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
//    • if neither rule fires → nil → the UI shows NOTHING for that meal.
//
//  When BOTH fire, the larger absolute deviation wins the `kind`; the text
//  mentions both. `text` is a presentation string (not part of the parity
//  contract — only the numeric fields + kind are).
//

import Foundation

public enum MealInsightEngine: Sendable {

    /// Tunable thresholds. Defaults are the plan's: 30 min pre-meal baseline,
    /// 180 min post-meal window, ±30 mg/dL for spike / reactive low, and a
    /// ±10 mg/dL "back to baseline" band.
    public struct Config: Equatable, Sendable {
        public var baselineWindowMin: Double
        public var postWindowMin: Double
        public var spikeThresholdMgdl: Double
        public var lowThresholdMgdl: Double
        public var baselineBandMgdl: Double
        public init(baselineWindowMin: Double = 30,
                    postWindowMin: Double = 180,
                    spikeThresholdMgdl: Double = 30,
                    lowThresholdMgdl: Double = 30,
                    baselineBandMgdl: Double = 10) {
            self.baselineWindowMin = baselineWindowMin
            self.postWindowMin = postWindowMin
            self.spikeThresholdMgdl = spikeThresholdMgdl
            self.lowThresholdMgdl = lowThresholdMgdl
            self.baselineBandMgdl = baselineBandMgdl
        }
        public static let `default` = Config()
    }

    /// A single glucose point in the (pre/post) analysis window.
    public struct Sample: Equatable, Sendable {
        public var epochSeconds: Double
        public var mgdl: Double
        public init(epochSeconds: Double, mgdl: Double) {
            self.epochSeconds = epochSeconds
            self.mgdl = mgdl
        }
    }

    /// Everything the engine needs for one meal. `samples` is the merged
    /// glucose series (any window covering pre- and post-meal is fine — the
    /// engine filters). `nowEpochSeconds` gates the "final only" rule.
    public struct Input: Equatable, Sendable {
        public var mealDateEpochSeconds: Double
        /// Carbs from the entry, for the per-10 g normalization + text.
        public var carbsG: Double?
        /// Meal description (text only — used to make the insight specific).
        public var description: String?
        public var samples: [Sample]
        public var nowEpochSeconds: Double
        public init(mealDateEpochSeconds: Double,
                    carbsG: Double?,
                    description: String?,
                    samples: [Sample],
                    nowEpochSeconds: Double) {
            self.mealDateEpochSeconds = mealDateEpochSeconds
            self.carbsG = carbsG
            self.description = description
            self.samples = samples
            self.nowEpochSeconds = nowEpochSeconds
        }
    }

    // MARK: Engine

    public static func insight(for input: Input,
                               config: Config = .default,
                               crossMeal: CrossMealComparison? = nil) -> MealInsight? {
        let meal = input.mealDateEpochSeconds
        let baselineLo = meal - config.baselineWindowMin * 60
        let baselineHi = meal
        let postLo = meal
        let postHi = meal + config.postWindowMin * 60

        let baselineSamples = input.samples.filter { $0.epochSeconds >= baselineLo && $0.epochSeconds < baselineHi }
        // No pre-meal data → nothing to compare against → no insight, ever.
        guard !baselineSamples.isEmpty else { return nil }
        let baseline = baselineSamples.map(\.mgdl).reduce(0, +) / Double(baselineSamples.count)

        let postSamples = input.samples.filter { $0.epochSeconds >= postLo && $0.epochSeconds <= postHi }
        // Need actual post-meal data to make a claim.
        guard !postSamples.isEmpty else { return nil }

        // "Final only": evaluate only once the whole post window has elapsed.
        // (The model re-runs this as `now` advances; the result then sticks on
        // the entry, so it appears once, ~3 h after the meal.)
        guard input.nowEpochSeconds >= postHi else { return nil }

        // Peak (max) and trough (min) within the post window.
        var peak = postSamples[0], trough = postSamples[0]
        for s in postSamples where s.mgdl > peak.mgdl { peak = s }
        for s in postSamples where s.mgdl < trough.mgdl { trough = s }

        let peakDelta = peak.mgdl - baseline
        let minDelta = trough.mgdl - baseline

        // Return-to-baseline: first sample back within ±band of baseline
        // AFTER the peak, expressed in minutes from the meal.
        var returnToBaselineMin: Double?
        for s in postSamples where s.epochSeconds >= peak.epochSeconds {
            if abs(s.mgdl - baseline) <= config.baselineBandMgdl {
                returnToBaselineMin = (s.epochSeconds - meal) / 60
                break
            }
        }

        let spike = peakDelta >= config.spikeThresholdMgdl
        let low = minDelta <= -config.lowThresholdMgdl
        guard spike || low else { return nil }   // not insight-worthy → nothing

        let per10g = (input.carbsG ?? 0) > 0 ? peakDelta / (input.carbsG! / 10) : nil

        let kind: MealInsight.Kind
        if spike && low {
            kind = abs(peakDelta) >= abs(minDelta) ? .spike : .reactiveLow
        } else if spike {
            kind = .spike
        } else {
            kind = .reactiveLow
        }

        let timeToPeakMin = (peak.epochSeconds - meal) / 60
        let text = makeText(kind: kind,
                             baseline: baseline,
                             peak: peak.mgdl, peakDelta: peakDelta,
                             timeToPeakMin: timeToPeakMin,
                             returnToBaselineMin: returnToBaselineMin,
                             trough: trough.mgdl, minDelta: minDelta,
                             timeToTroughMin: (trough.epochSeconds - meal) / 60,
                             spikeFired: spike, lowFired: low,
                             per10g: per10g,
                             meal: input.description,
                             crossMeal: crossMeal)

        return MealInsight(kind: kind,
                           baselineMgdl: baseline,
                           peakMgdl: peak.mgdl,
                           peakDeltaMgdl: peakDelta,
                           timeToPeakMin: timeToPeakMin,
                           returnToBaselineMin: returnToBaselineMin,
                           minMgdl: trough.mgdl,
                           minDeltaMgdl: minDelta,
                           per10gCarbDeltaMgdl: per10g,
                           text: text)
    }

    // MARK: Cross-meal memory (Phase 2 bell & whistle)

    /// Comparison against past instances of similar meals ("your usual
    /// response"). Built by the app model from PRIOR entries whose insight
    /// already fired and whose descriptions are similar (see
    /// `FoodLogModel`); the engine just folds it into the text. Not part of
    /// the numeric parity contract.
    public struct CrossMealComparison: Equatable, Sendable {
        public var instanceCount: Int
        public var averagePeakDeltaMgdl: Double
        public init(instanceCount: Int, averagePeakDeltaMgdl: Double) {
            self.instanceCount = instanceCount
            self.averagePeakDeltaMgdl = averagePeakDeltaMgdl
        }
    }

    /// Text is a presentation string — not part of the cross-platform parity
    /// contract (numeric fields + kind are). Keep it short (the watch shows it
    /// too) and grounded in the numbers.
    private static func makeText(kind: MealInsight.Kind,
                                 baseline: Double,
                                 peak: Double, peakDelta: Double,
                                 timeToPeakMin: Double,
                                 returnToBaselineMin: Double?,
                                 trough: Double, minDelta: Double,
                                 timeToTroughMin: Double,
                                 spikeFired: Bool, lowFired: Bool,
                                 per10g: Double?,
                                 meal: String?,
                                 crossMeal: CrossMealComparison? = nil) -> String {
        let m = { (v: Double) -> String in String(format: "%.0f", v) }
        var parts: [String] = []
        switch kind {
        case .spike:
            var s = "Glucose peaked at \(m(peak)) mg/dL (+\(m(peakDelta)) above your pre-meal level of \(m(baseline))) about \(m(timeToPeakMin)) min after"
            s += mealPhrase(meal)
            if let rtb = returnToBaselineMin {
                s += " — back to baseline in about \(m(rtb)) min"
            }
            parts.append(s + ".")
            if let p = per10g, p > 0 {
                parts.append("That's about \(m(p)) mg/dL per 10 g of carbs.")
            }
            if lowFired {
                parts.append("It then dipped to \(m(trough)) mg/dL (−\(m(abs(minDelta))) below baseline) — a reactive dip after the spike.")
            }
        case .reactiveLow:
            var s = "Glucose dropped to \(m(trough)) mg/dL (−\(m(abs(minDelta))) below your pre-meal level of \(m(baseline))) about \(m(timeToTroughMin)) min after"
            s += mealPhrase(meal)
            parts.append(s + ".")
            if spikeFired {
                parts.append("This followed a +\(m(peakDelta)) mg/dL spike about \(m(timeToPeakMin)) min in — a classic reactive dip.")
            }
        }
        if let cm = crossMeal, cm.instanceCount >= 2 {
            let avg = cm.averagePeakDeltaMgdl
            parts.append(String(format: "Across %d similar meals your average rise is about %.0f mg/dL.",
                                cm.instanceCount, avg))
        }
        return parts.joined(separator: " ")
    }

    private static func mealPhrase(_ meal: String?) -> String {
        guard let d = meal?.trimmingCharacters(in: .whitespacesAndNewlines), !d.isEmpty else { return "eating" }
        return "this meal"  // keep the sentence tight; the card already shows the food name
    }
}
