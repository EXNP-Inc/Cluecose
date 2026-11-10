//
//  WatchGlucoseModel.swift
//  Cluecose Watch
//
//  Reads blood glucose from the Health app on the watch (the iPhone app
//  writes the Libre 3 readings there) and computes the same stats the phone
//  shows, via CluecoseCore.
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
    @Published var status: String = "Reading Health data…"
    @Published var isFresh = false

    private let healthStore = HKHealthStore()
    private let sampleType = HKQuantityType(.bloodGlucose)
    private let mgdlUnit = HKUnit(from: "mg/dL")
    private var observer: AnyCancellable?

    func start() async {
        guard HKHealthStore.isHealthDataAvailable() else {
            status = "Health unavailable on watch"
            return
        }
        do {
            // Reading on the watch does not prompt for write access.
            try await healthStore.requestAuthorization(toShare: [], read: [sampleType])
        } catch {
            status = "Health access denied"
            return
        }
        await refresh()
        // Poll: the watch has no push from the phone's HealthKit writes;
        // a 30 s cadence keeps the glance fresh without burning battery.
        observer = Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                Task { @MainActor in await self?.refresh() }
            }
    }

    func refresh() async {
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
            latest = samples.last
            trend = GlucoseAnalytics.computeTrend(from: samples)
            isFresh = latest.map { Date().timeIntervalSince($0.date) < 25 * 60 } ?? false
            let stats = GlucoseAnalytics.stats(samples, windowSeconds: 24 * 60 * 60)
            tir24h = stats.isMeaningful ? stats.tir : nil
            burnMode = GlucoseAnalytics.burnMode(stats: stats, trend: trend)
            status = latest.map { _ in "via Health app" } ?? "No glucose in the Health app yet — run Cluecose on your iPhone."
        } catch {
            status = "Health read failed"
        }
    }
}
