//
//  GlucoseAnalytics.swift
//  Cluecose
//
//  The analytics brain: turns a raw sample series (any time window) into the
//  numbers and the judgment the app is about:
//
//    • Standard CGM stats — TIR / TAR / TBR, CV, GMI, mean, min, max
//    • AUC above 140 mg/dL (trapezoid integration) — the glycaemic
//      "storage pressure" number. High post-prandial AUC>140 is the CGM
//      correlate of insulin-driven lipogenesis (fat storage); keeping it
//      low while glucose stays in range is the "burning" state.
//    • A storage-vs-burning verdict from AUC + current trend, framed
//      honestly: glucose is an ESTIMATE of insulin activity, not a direct
//      fat-oxidation meter.
//    • A small rule-based tips engine (informational, not medical).
//
//  All thresholds are the usual CGM conventions (70/180 mg/dL bands,
//  140 mg/dL storage threshold, 36% CV, 70% TIR).
//

import Foundation

public struct GlucoseStats: Equatable {
    public init() {}
    public var count = 0
    public var mean: Double = 0
    public var min: Double = 0
    public var max: Double = 0
    public var stdDev: Double = 0
    /// Coefficient of variation (stdDev / mean), as a percentage.
    public var cv: Double = 0
    /// Glycaemic management indicator: GMI = 3.31 + 0.0239 × mean (mg/dL), in %.
    public var gmi: Double = 0
    /// Fraction of minutes spent in range (70–180 mg/dL). 0…1.
    public var tir: Double = 0
    /// Fraction of minutes above 180.
    public var tar: Double = 0
    /// Fraction of minutes below 70.
    public var tbr: Double = 0
    /// Fraction of minutes below 54 (severe low).
    public var tbr2: Double = 0
    /// Area under the curve ABOVE 140 mg/dL, in mg/dL·min.
    public var aucAbove140: Double = 0
    /// AUC above 140 as a fraction of the total covered time (0…1).
    public var aucShare: Double = 0
    /// True when there were not enough samples to say anything.
    public var isMeaningful: Bool { count >= 4 }
}

public enum BurnMode: String, Equatable {
    case storing   // sustained elevation / rising — insulin pushing glucose in
    case balanced  // in range, flat
    case burning   // low AUC, falling or low — glycogen/fat being used

    public var label: String {
        switch self {
        case .storing:  "Storing"
        case .balanced: "Balanced"
        case .burning:  "Burning"
        }
    }

    public var symbol: String {
        switch self {
        case .storing:  "flame.fill"
        case .balanced: "equal"
        case .burning:  "flame.slash.fill"
        }
    }

    public var tintName: String {
        switch self {
        case .storing:  "orange"
        case .balanced: "green"
        case .burning:  "blue"
        }
    }
}

public struct GlucoseTip: Identifiable, Equatable {
    public enum Kind: String, Equatable, Sendable { case low, high, variability, range, storage, good }
    public let kind: Kind
    public let text: String

    public init(kind: Kind, text: String) {
        self.kind = kind
        self.text = text
    }

    public var id: String { "\(kind.rawValue):\(text)" }

    public var symbol: String {
        switch kind {
        case .low:          "arrow.down.to.line"
        case .high:         "arrow.up.to.line"
        case .variability:  "waveform.path.ecg"
        case .range:        "gauge.with.dots.needle.67percent"
        case .storage:      "flame"
        case .good:         "checkmark.seal"
        }
    }
}

public enum GlucoseAnalytics {

    /// Storage-pressure threshold: AUC above 140 in mg/dL·min over the window
    /// (mg/dL·min — one minute above the line counts 1; 1 h at 173 mg/dL ≈ 2000).
    /// A well-controlled day is well under this (a user measured at 686);
    /// sustained post-prandial highs over a day put it in the thousands.
    /// Heuristic, documented as such.
    public static let storageAUC24h: Double = 2000

    /// Compute the full stats block for the samples covering the last
    /// `windowSeconds` (or everything if shorter).
    public static func stats(_ samples: [GlucoseSample], windowSeconds: TimeInterval) -> GlucoseStats {
        let now = Date()
        let cutoff = now.addingTimeInterval(-windowSeconds)
        let recent = samples
            .filter { $0.date >= cutoff }
            .sorted { $0.date < $1.date }
        guard !recent.isEmpty else { return GlucoseStats() }

        let values = recent.map(\.mgdl)
        let mean = values.reduce(0, +) / Double(values.count)
        let variance = values.reduce(0) { $0 + ($1 - mean) * ($1 - mean) } / Double(values.count)
        let stdDev = sqrt(variance)

        // Time-based stats: weight each sample by the time it "covers".
        // A Libre 3 reading every minute covers one minute; gaps over 15 min
        // are dropped (no data → no claim about that interval).
        var total = 0.0, inRange = 0.0, above = 0.0, below = 0.0, below2 = 0.0
        var auc = 0.0
        for (i, s) in recent.enumerated() {
            let nextDate: Date
            if i == recent.count - 1 {
                nextDate = min(now, s.date.addingTimeInterval(60))
            } else {
                nextDate = recent[i + 1].date
            }
            let dt = nextDate.timeIntervalSince(s.date)
            guard dt > 0, dt <= 15 * 60 else { continue }
            total += dt
            let v = s.mgdl
            if (70...180).contains(v) { inRange += dt }
            if v > 180 { above += dt }
            if v < 70 { below += dt }
            if v < 54 { below2 += dt }
            // Trapezoid of (v - 140) above the 140 line.
            if i < recent.count - 1 {
                let v2 = recent[i + 1].mgdl
                let y1 = max(0, v - 140), y2 = max(0, v2 - 140)
                auc += (y1 + y2) / 2 * dt
            }
        }

        var stats = GlucoseStats()
        stats.count = values.count
        stats.mean = mean
        stats.min = values.min() ?? 0
        stats.max = values.max() ?? 0
        stats.stdDev = stdDev
        stats.cv = mean > 0 ? stdDev / mean * 100 : 0
        stats.gmi = 3.31 + 0.0239 * mean
        if total > 0 {
            stats.tir = inRange / total
            stats.tar = above / total
            stats.tbr = below / total
            stats.tbr2 = below2 / total
            // `auc` and `total` are accumulated in SECONDS (timeIntervalSince);
            // the published AUC is mg/dL·MIN — the unit storageAUC24h and the
            // UI label use. (Forgot this division once: the AUC read 60× too
            // big and the burn mode was stuck on "storing" forever.)
            stats.aucAbove140 = auc / 60
            stats.aucShare = auc / total / 140  // mean excess height / 140 scale (unit-free)
        }
        return stats
    }

    /// Storage vs burning verdict for the given window + current trend.
    /// Honest by construction: it's an estimate from glycaemic load, not a
    /// direct fat-oxidation measurement.
    public static func burnMode(stats: GlucoseStats, trend: Trend) -> BurnMode {
        guard stats.isMeaningful else { return .balanced }
        // Scale the storage threshold to the window (we design around 24 h).
        let hoursCovered = Double(stats.count) / 60.0
        let threshold = storageAUC24h * max(1, hoursCovered / 24)
        if stats.aucAbove140 > threshold {
            return .storing
        }
        if stats.aucAbove140 < threshold / 4 {
            // Low storage pressure: burn when the trend is falling/flat-low,
            // otherwise just "balanced in range".
            switch trend {
            case .fall, .sharpFall: return .burning
            default: return stats.mean < 110 ? .burning : .balanced
            }
        }
        return .balanced
    }

    /// Rule-based tips, most urgent first. Informational only — the app is
    /// not a medical device, and the UI says so.
    public static func tips(stats: GlucoseStats, latest: GlucoseSample?, trend: Trend,
                     storageThreshold: Double = storageAUC24h) -> [GlucoseTip] {
        var tips: [GlucoseTip] = []
        guard let latest else { return tips }

        if latest.mgdl < 54 {
            tips.append(.init(kind: .low, text: "Glucose is very low right now. If you have symptoms, take fast-acting carbs and recheck in 15 minutes."))
        } else if latest.mgdl < 70 {
            tips.append(.init(kind: .low, text: "You're below range. Fast-acting carbs (15 g) and a recheck in 15 minutes is the usual response if you feel off."))
        } else if latest.mgdl > 250 {
            tips.append(.init(kind: .high, text: "Sustained high. Water and a walk tend to help; check ketones if this has been going on a while and you feel unwell."))
        } else if latest.mgdl > 180 {
            tips.append(.init(kind: .high, text: "Above range right now. A 10–15 min walk after eating is the single best thing that blunts the next part of this spike."))
        }

        if stats.isMeaningful {
            if stats.tbr > 0.04 {
                tips.append(.init(kind: .low, text: String(format: "%.0f%% of the last day was below 70 mg/dL — that's the pattern worth breaking. Check what happens before bedtime.", stats.tbr * 100)))
            }
            if stats.cv > 36 {
                tips.append(.init(kind: .variability, text: "Your swings are large (CV \(Int(stats.cv))% — the target is under 36%). Consistent meal timing and carb amounts do most of the work here."))
            }
            if stats.tir < 0.70 && stats.tar > stats.tbr {
                tips.append(.init(kind: .range, text: String(format: "Time in range is %.0f%%. The wins are in your biggest excursions — look at the chart and find the two worst stretches.", stats.tir * 100)))
            }
            if stats.aucAbove140 > storageThreshold {
                tips.append(.init(kind: .storage, text: "Storage pressure was high this window — repeated time above 140 means insulin was working overtime, and that's when glucose gets shunted to fat. Pairing carbs with protein/fibre and moving after meals is the direct fix."))
            }
            if stats.tir >= 0.90 && stats.aucAbove140 < storageThreshold / 4 {
                tips.append(.init(kind: .good, text: "Excellent window: \(Int(stats.tir * 100))% in range with low storage pressure. This is the state where the body burns fuel instead of storing it."))
            }
        }
        if tips.isEmpty {
            tips.append(.init(kind: .good, text: "Looking steady. Keep doing what you're doing."))
        }
        return tips
    }
}

// MARK: - Trend (shared)

extension GlucoseAnalytics {
    /// Libre-style trend from the slope over the last ~35 minutes of samples.
    public static func computeTrend(from samples: [GlucoseSample]) -> Trend {
        let now = Date()
        let recent = samples.filter { now.timeIntervalSince($0.date) <= 35 * 60 }
        guard recent.count >= 3 else { return .unknown }
        let n = Double(recent.count)
        let ts = recent.map { now.timeIntervalSince($0.date) }
        let vs = recent.map(\.mgdl)
        let meanT = ts.reduce(0, +) / n
        let meanV = vs.reduce(0, +) / n
        var num = 0.0, den = 0.0
        for (t, v) in zip(ts, vs) {
            num += (t - meanT) * (v - meanV)
            den += (t - meanT) * (t - meanT)
        }
        guard den > 0 else { return .unknown }
        let slope = num / den
        switch slope {
        case ..<(-1.2): return .sharpFall
        case ..<(-0.4): return .fall
        case 0.4...:
            return slope >= 1.2 ? .sharpRise : .rise
        default:
            return .flat
        }
    }
}
