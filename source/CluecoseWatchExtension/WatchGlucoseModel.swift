//
//  WatchGlucoseModel.swift
//  Cluecose Watch
//
//  Two data sources, in priority order:
//
//    1. LIVE push over WatchConnectivity — the iPhone (the real data source)
//       pushes the current reading + 24 h stats here the moment it has a new
//       sample (~1/min). This is what makes the watch number actually move.
//       Delivered by `WatchReceiver`.
//
//    2. HealthKit fallback — the watch's Health app is a *sync replica* of the
//       phone's Health and lags by minutes. We read it so the watch still shows
//       something when the phone app isn't pushing (backgrounded/quit), but a
//       fresh live push always wins over it.
//
//  The number is tappable to cycle mg/dL <-> mmol/L (persisted on the watch).
//

import Foundation
import Combine
import HealthKit
import CluecoseCore

@MainActor
final class WatchGlucoseModel: ObservableObject {
    @Published var latest: GlucoseSample?
    @Published var trend: Trend = .unknown
    @Published var tir24h: Double?
    @Published var burnMode: BurnMode = .balanced
    @Published var status: String = "Reading…"
    @Published var isFresh = false
    @Published var unit: GlucoseUnit = .mgdl {
        didSet { UserDefaults.standard.set(unit.rawValue, forKey: Self.unitKey) }
    }

    /// A live push from the phone within this many seconds is treated as the
    /// authoritative source and overrides the (lagging) HealthKit replica.
    private let liveWindow: TimeInterval = 60

    private let healthStore = HKHealthStore()
    private let sampleType = HKQuantityType(.bloodGlucose)
    private let mgdlUnit = HKUnit(from: "mg/dL")
    private var observer: AnyCancellable?

    private var lastPushAt: Date = .distantPast
    /// Set when the user taps the number on the watch — after that the phone's
    /// unit no longer overwrites the local choice.
    private var unitOverride = false

    private static let unitKey = "cluecose.unit"
    private static let overrideKey = "cluecose.unit.override"

    // MARK: Unit

    func toggleUnit() {
        unit = (unit == .mgdl) ? .mmol : .mgdl
        unitOverride = true
        UserDefaults.standard.set(true, forKey: Self.overrideKey)
    }

    // MARK: Lifecycle

    func start() async {
        unitOverride = UserDefaults.standard.bool(forKey: Self.overrideKey)
        let saved = UserDefaults.standard.string(forKey: Self.unitKey)
        if let saved, let u = GlucoseUnit(rawValue: saved) {
            unit = u
        } else {
            let region = Locale.current.region?.identifier ?? ""
            unit = region == "US" ? .mgdl : .mmol
        }

        // Live push channel (primary).
        WatchReceiver.shared.onLive = { [weak self] mgdl, date, trendWire, unitWire, tir, burnWire in
            Task { @MainActor in
                self?.applyLive(mgdl: mgdl, date: date, trendWire: trendWire,
                                unitWire: unitWire, tir: tir, burnWire: burnWire)
            }
        }
        WatchReceiver.shared.activate()

        // HealthKit fallback (secondary).
        guard HKHealthStore.isHealthDataAvailable() else {
            status = "Waiting for the iPhone…"
            return
        }
        do {
            try await healthStore.requestAuthorization(toShare: [], read: [sampleType])
        } catch {
            status = "Waiting for the iPhone…"
        }
        await refreshHealthKit()
        observer = Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                Task { @MainActor in await self?.refreshHealthKit() }
            }
    }

    // MARK: Live push (from the phone)

    private func applyLive(mgdl: Double, date: Date, trendWire: String,
                           unitWire: String, tir: Double?, burnWire: String?) {
        lastPushAt = Date()
        latest = GlucoseSample(date: date, mgdl: mgdl)
        trend = Trend(wireValue: trendWire)
        tir24h = tir
        burnMode = burnWire.flatMap { BurnMode(rawValue: $0) } ?? .balanced
        isFresh = Date().timeIntervalSince(date) < 25 * 60
        // Mirror the phone's unit unless the user overrode it on the watch.
        if !unitOverride, let phoneUnit = GlucoseUnit(rawValue: unitWire), phoneUnit != unit {
            unit = phoneUnit
        }
        status = "live · via iPhone"
    }

    // MARK: HealthKit fallback

    private func refreshHealthKit() async {
        let start = Date().addingTimeInterval(-48 * 60 * 60)
        let predicate = HKSamplePredicate.quantitySample(
            type: sampleType,
            predicate: HKQuery.predicateForSamples(withStart: start, end: nil, options: .strictStartDate)
        )
        let descriptor = HKSampleQueryDescriptor(
            predicates: [predicate],
            sortDescriptors: [SortDescriptor(\HKQuantitySample.startDate, order: .forward)],
            limit: nil
        )
        do {
            let results = try await descriptor.result(for: healthStore)
            let samples = results.compactMap { s -> GlucoseSample? in
                let mg = s.quantity.doubleValue(for: mgdlUnit)
                guard mg > 0 else { return nil }
                return GlucoseSample(date: s.startDate, mgdl: mg)
            }
            guard let healthLatest = samples.last else {
                if latest == nil { status = "No glucose yet — run Cluecose on your iPhone." }
                return
            }
            // A fresh live push wins; otherwise the Health replica is the best
            // we have (covers the phone-app-quit case).
            if Date().timeIntervalSince(lastPushAt) < liveWindow {
                isFresh = Date().timeIntervalSince(latest?.date ?? .distantPast) < 25 * 60
                return
            }
            latest = healthLatest
            trend = GlucoseAnalytics.computeTrend(from: samples)
            isFresh = Date().timeIntervalSince(healthLatest.date) < 25 * 60
            let stats = GlucoseAnalytics.stats(samples, windowSeconds: 24 * 60 * 60)
            tir24h = stats.isMeaningful ? stats.tir : nil
            burnMode = GlucoseAnalytics.burnMode(stats: stats, trend: trend)
            status = "via Health app"
        } catch {
            if latest == nil { status = "Waiting for the iPhone…" }
        }
    }
}
