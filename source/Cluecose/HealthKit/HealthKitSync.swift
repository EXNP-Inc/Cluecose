//
//  HealthKitSync.swift
//  Cluecose
//
//  Two-way HealthKit bridge for blood glucose:
//
//    WRITE — every Cluecose reading (from the direct BLE link) is saved as an
//            HKQuantitySample in mg/dL, attributed to this app's source type.
//            Writes are incremental: only samples newer than the last one we
//            saved are sent, so relaunches never create duplicates.
//
//    READ  — glucose from any other source in the Health app (manual entries,
//            another CGM, older history) is pulled in so analytics can use a
//            complete series. Cluecose's own samples already appear in the
//            read-back and are deduped by the model.
//
//  Everything is on-device; HealthKit itself is Apple's local store.
//

import Foundation
import HealthKit
import Combine
import CluecoseCore

/// Bridges Cluecose glucose samples to/from the Health app.
/// Call all methods on the main actor (owned by `GlucoseModel`).
@MainActor
final class HealthKitSync: ObservableObject {

    /// User preference persisted key.
    static let enabledKey = "cluecose.healthkit.enabled"
    private static let lastWrittenKey = "cluecose.healthkit.lastWritten"
    /// One-shot self-test: when this flag is true at launch, `start()` writes a
    /// 99.5 mg/dL probe, reads it back, deletes it, and logs the outcome.
    /// Set it on a simulator to verify the write+read path without a sensor.
    static let testWriteKey = "cluecose.healthkit.testWrite"

    private let healthStore = HKHealthStore()
    private let sampleType = HKQuantityType(.bloodGlucose)
    /// HealthKit has no built-in mg/dL factory; the unit string form is canonical.
    private let mgdlUnit = HKUnit(from: "mg/dL")

    /// Whether the user has HealthKit integration switched on (default on).
    var enabled: Bool {
        get { UserDefaults.standard.object(forKey: Self.enabledKey) as? Bool ?? true }
        set { UserDefaults.standard.set(newValue, forKey: Self.enabledKey) }
    }

    /// Live status line for the UI.
    @Published var status: String = "Health app: not connected"
    /// True once the user has granted read+write access.
    @Published private(set) var authorized = false
    /// True on devices/simulators where HealthKit simply doesn't exist.
    private(set) var available = true

    init() {
        available = HKHealthStore.isHealthDataAvailable()
        if !available {
            status = "Health app unavailable on this device"
        }
    }

    /// Request read+write access (shows the system dialog once).
    /// Safe to call repeatedly; the dialog only appears for not-determined types.
    func ensureAuthorization() async {
        guard available else { return }
        do {
            try await healthStore.requestAuthorization(
                toShare: [sampleType],
                read: [sampleType]
            )
            authorized = true
            status = "Health app: on — syncing"
        } catch {
            authorized = false
            status = "Health app access denied: \(error.localizedDescription)"
            clog("HK authorization failed: \(error)")
        }
    }

    /// One-shot self-test used on a simulator (no sensor there): write a
    /// distinctive probe sample, read it back, delete it, log the outcome.
    /// Called from `GlucoseModel.start()` when `testWriteKey` is set; the flag
    /// is cleared after the run.
    func runProbeTest() async {
        guard available else {
            clog("HK probe: skipped — HealthKit unavailable")
            return
        }
        let uuid = UUID().uuidString
        let quantity = HKQuantity(unit: mgdlUnit, doubleValue: 99.5)
        let probe = HKQuantitySample(
            type: sampleType,
            quantity: quantity,
            start: Date(),
            end: Date()
        )
        do {
            try await healthStore.save(probe)
            clog("HK probe: wrote 99.5 mg/dL (id \(uuid))")
        } catch {
            clog("HK probe: write FAILED: \(error)")
            return
        }
        let back = await readRecentSamples(window: 300)
        if back.contains(where: { abs($0.mgdl - 99.5) < 0.01 }) {
            clog("HK probe: read-back OK — 99.5 mg/dL found in last 5 min")
        } else {
            clog("HK probe: read-back FAILED — probe not found")
        }
        // Clean up so the probe never pollutes analytics: delete the exact
        // object we saved (an app may only delete samples it wrote).
        do {
            try await healthStore.delete(probe)
            clog("HK probe: cleanup — probe sample deleted")
        } catch {
            clog("HK probe: cleanup FAILED: \(error)")
        }
    }

    /// Write new samples (anything newer than the last one we saved).
    func write(_ samples: [GlucoseSample]) async {
        guard available, authorized else { return }
        let lastWritten = (UserDefaults.standard.object(forKey: Self.lastWrittenKey) as? Date) ?? .distantPast
        let toWrite = samples
            .filter { $0.date > lastWritten }
            .sorted { $0.date < $1.date }
        guard !toWrite.isEmpty else { return }

        var written = 0
        for sample in toWrite {
            let quantity = HKQuantity(unit: mgdlUnit, doubleValue: sample.mgdl)
            let hkSample = HKQuantitySample(
                type: sampleType,
                quantity: quantity,
                start: sample.date,
                end: sample.date
            )
            do {
                try await healthStore.save(hkSample)
                written += 1
                UserDefaults.standard.set(sample.date, forKey: Self.lastWrittenKey)
            } catch {
                clog("HK write failed at \(sample.date): \(error)")
                break // don't hammer the store on a persistent failure
            }
        }
        if written > 0 {
            clog("HK wrote \(written) sample(s), newest \(toWrite.last!.date)")
        }
    }

    /// Read glucose from ALL sources over the last `window` seconds.
    func readRecentSamples(window: TimeInterval) async -> [GlucoseSample] {
        guard available else { return [] }
        let start = Date().addingTimeInterval(-window)
        let timePredicate = HKQuery.predicateForSamples(withStart: start, end: nil, options: .strictStartDate)
        let samplePredicate = HKSamplePredicate.quantitySample(type: sampleType, predicate: timePredicate)
        let descriptor = HKSampleQueryDescriptor(
            predicates: [samplePredicate],
            sortDescriptors: [SortDescriptor(\HKQuantitySample.startDate, order: .forward)],
            limit: nil
        )
        do {
            let results = try await descriptor.result(for: healthStore)
            return results.compactMap { quantitySample in
                // Convert whatever unit the source used (mg/dL, mmol/L) to mg/dL.
                let mg = quantitySample.quantity.doubleValue(for: mgdlUnit)
                guard mg > 0 else { return nil }
                return GlucoseSample(date: quantitySample.startDate, mgdl: mg)
            }
        } catch {
            clog("HK read failed: \(error)")
            return []
        }
    }
}
