//
//  NutritionSleep.swift
//  Cluecose
//
//  iOS-only nutrition-sleep surface: reads the last ~7 nights of sleep from
//  HealthKit (`HKCategoryType(.sleepAnalysis)`, via the existing
//  `HealthKitSync` store + authorization) and correlates dinner-ish entries
//  (after ~12:00 local) — caffeine, carbs, alcohol — with total sleep
//  duration.
//
//  Every emitted string is an ASSOCIATION ("averaged N min less sleep"),
//  never a causal claim ("X causes Y"). When HealthKit is unavailable or
//  denied, or there is no sleep data, the list is empty and the card shows
//  nothing.
//

import Foundation
import CluecoseCore

enum NutritionSleep {

    /// Number of trailing nights analyzed.
    static let nights = 7

    /// One analyzed night: its local day and total minutes asleep.
    struct Night: Sendable {
        let day: Date
        let asleepMinutes: Double
    }

    /// Read sleep from HealthKit and produce the association strings.
    /// Returns an empty list when HealthKit is unavailable, access is
    /// denied, or there is no sleep data in the window (the card then shows
    /// nothing rather than a misleading empty claim).
    @MainActor
    static func refresh(healthKit: HealthKitSync?, entries: [FoodEntry]) async -> [String] {
        guard let healthKit, healthKit.available else { return [] }
        do {
            try await healthKit.requestSleepReadAuthorization()
        } catch {
            return []
        }
        guard let pairs = await healthKit.readSleepNights(days: nights) else { return [] }
        let nightList = pairs.map { Night(day: $0.day, asleepMinutes: $0.asleepMinutes) }
        return correlations(nights: nightList, entries: entries)
    }

    /// Pure correlation over analyzed nights + logged entries. Compares the
    /// AVERAGE sleep of nights meeting a dinner threshold against nights not
    /// meeting it; a line is emitted only when the difference is >= 20 min
    /// (below that the sample is too small to say anything honest).
    static func correlations(nights: [Night], entries: [FoodEntry]) -> [String] {
        guard !nights.isEmpty else { return [] }
        let calendar = Calendar.current
        let evening = 12 // "dinner-ish": local hour >= 12

        func dinnerEntries(on day: Date) -> [FoodEntry] {
            entries.filter { e in
                calendar.isDate(e.date, inSameDayAs: day) && hour(of: e.date) >= evening
            }
        }
        func hour(of date: Date) -> Int {
            calendar.component(.hour, from: date)
        }
        func avg(_ minutes: [Double]) -> Double? {
            minutes.isEmpty ? nil : minutes.reduce(0, +) / Double(minutes.count)
        }
        /// Emits a line only when the two group averages differ by >= 20 min.
        func line(_ title: String, hi: [Double], lo: [Double]) -> String? {
            guard let a = avg(hi), let b = avg(lo) else { return nil }
            let diff = a - b
            guard abs(diff) >= 20 else { return nil }
            let direction = diff < 0 ? "less" : "more"
            return String(format: "%s nights averaged %.0f min %s sleep.",
                          title, abs(diff), direction)
        }

        var out: [String] = []

        // Evening caffeine (after 6 pm), > 100 mg on that night.
        var hiCaff: [Double] = [], loCaff: [Double] = []
        for night in nights {
            let caffeine = dinnerEntries(on: night.day)
                .filter { hour(of: $0.date) >= 18 && ($0.caffeineMg ?? 0) > 0 }
                .compactMap(\.caffeineMg)
                .reduce(0, +)
            if caffeine > 100 { hiCaff.append(night.asleepMinutes) } else { loCaff.append(night.asleepMinutes) }
        }
        if let l = line("Nights with more than 100 mg of caffeine after 6 pm", hi: hiCaff, lo: loCaff) {
            out.append(l)
        }

        // Evening carbs (after 12:00), > 60 g on that night.
        var hiCarb: [Double] = [], loCarb: [Double] = []
        for night in nights {
            let carbs = dinnerEntries(on: night.day)
                .compactMap(\.carbsG)
                .reduce(0, +)
            if carbs > 60 { hiCarb.append(night.asleepMinutes) } else { loCarb.append(night.asleepMinutes) }
        }
        if let l = line("Nights with more than 60 g of carbs after 12:00", hi: hiCarb, lo: loCarb) {
            out.append(l)
        }

        // Evening alcohol (after 12:00), any reported that night.
        var hiAlc: [Double] = [], loAlc: [Double] = []
        for night in nights {
            let alcohol = dinnerEntries(on: night.day)
                .compactMap(\.alcoholG)
                .reduce(0, +)
            if alcohol > 0 { hiAlc.append(night.asleepMinutes) } else { loAlc.append(night.asleepMinutes) }
        }
        if let l = line("Nights with alcohol after 12:00", hi: hiAlc, lo: loAlc) {
            out.append(l)
        }

        return out
    }
}
