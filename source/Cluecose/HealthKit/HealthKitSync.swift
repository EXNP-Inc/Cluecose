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

    /// Delete the glucose samples THIS app wrote over the last 48 h. Used when
    /// demo mode switches off: while demo mode was on, the synthetic series
    /// (and any real samples before it) may have been pushed to Health, and
    /// `readRecentSamples` would keep pulling them back in as "external" data
    /// forever. Only our own source's rows are touched — samples from other
    /// apps (manual entries, other CGMs) are never deleted (HealthKit only
    /// allows deleting what you wrote, and this filters to our source anyway).
    /// A paired real sensor re-streams and re-writes its data within minutes.
    /// Awaitable so the caller can re-sync the external read-back AFTER the
    /// purge finishes (no stale read-back race).
    func purgeDemoGlucose() async {
        guard available, authorized else { return }
        let start = Date().addingTimeInterval(-48 * 3600)
        let timePredicate = HKQuery.predicateForSamples(withStart: start, end: nil, options: .strictStartDate)
        let predicate = HKSamplePredicate.quantitySample(
            type: sampleType, predicate: timePredicate
        )
        let descriptor = HKSampleQueryDescriptor(
            predicates: [predicate],
            sortDescriptors: [SortDescriptor(\HKQuantitySample.startDate, order: .forward)],
            limit: nil
        )
        let results: [HKQuantitySample]
        do {
            results = try await descriptor.result(for: healthStore)
        } catch {
            clog("HK purge: query failed: \(error)")
            return
        }
        // Delete only rows THIS app wrote (bundle match). HealthKit also
        // enforces it — deleting another app's sample throws and is skipped.
        let ourBundle = Bundle.main.bundleIdentifier
        var deleted = 0
        for sample in results where sample.sourceRevision.source.bundleIdentifier == ourBundle {
            do {
                try await healthStore.delete(sample)
                deleted += 1
            } catch {
                clog("HK purge: delete failed (\(sample.startDate)): \(error)")
            }
        }
        if deleted > 0 {
            clog("HK purge: removed \(deleted) Cluecose sample(s) from the last 48 h (demo-mode cleanup)")
        }
    }

    // MARK: Sleep (read-only, for the nutrition-sleep association)

    /// The sleep-analysis type, read-only.
    private let sleepType = HKCategoryType(.sleepAnalysis)

    /// Request read access to sleep analysis (a second grant beyond glucose).
    /// Safe to call repeatedly; the system dialog appears only once for the
    /// new type. Throws when HealthKit is unavailable or access is denied —
    /// callers then surface nothing (an empty association list).
    func requestSleepReadAuthorization() async throws {
        guard available else { throw CQError.hkUnavailable }
        var granted = false
        try await healthStore.requestAuthorization(toShare: [], read: [sleepType]) { success, error in
            if let error {
                granted = false
                clog("HK sleep authorization failed: \(error)")
            } else {
                granted = success
            }
        }
        guard granted else { throw CQError.accessDenied }
    }

    /// Total asleep minutes for each of the last `days` nights (local
    /// calendar days), as `(day, asleepMinutes)` pairs, oldest -> newest.
    /// Returns nil when HealthKit is unavailable, denied, or there is no
    /// sleep data in the window.
    func readSleepNights(days: Int) async -> [(day: Date, asleepMinutes: Double)]? {
        guard available else { return nil }
        let calendar = Calendar.current
        let now = Date()
        let start = now.addingTimeInterval(-Double(days) * 86_400)
        let samplePredicate = HKSamplePredicate.categorySample(
            type: sleepType,
            predicate: HKQuery.predicateForSamples(withStart: start, end: nil, options: .strictStartDate)
        )
        let descriptor = HKSampleQueryDescriptor(
            predicates: [samplePredicate],
            sortDescriptors: [SortDescriptor(\HKCategorySample.startDate, order: .forward)],
            limit: nil
        )
        let results: [HKCategorySample]
        do {
            results = try await descriptor.result(for: healthStore)
        } catch {
            clog("HK sleep read failed: \(error)")
            return nil
        }
        // Accumulate asleep minutes per local day.
        var perDay: [Int: Double] = [:]
        var daysSeen = 0
        for sample in results {
            // Asleep = InBed(0) and Awake(2) excluded; i.e. the asleep stages.
            let sleepValue = HKCategoryValueSleepAnalysis(rawValue: sample.value) ?? .awake
            guard HKCategoryValueSleepAnalysis.allAsleepValues.contains(sleepValue) else { continue }
            let day = calendar.ordinality(of: .day, in: .era, for: sample.startDate) ?? 0
            perDay[day, default: 0] += Double(sample.endDate.timeIntervalSince(sample.startDate) / 60)
            daysSeen += 1
        }
        guard daysSeen > 0 else { return nil }
        // Map back to actual dates (use the newest day as the anchor).
        let latestDay = calendar.ordinality(of: .day, in: .era, for: now) ?? 0
        var out: [(Date, Double)] = []
        for (day, minutes) in perDay {
            let anchor = Self.startOfDay(calendar.date(byAdding: .day, value: (day - latestDay), to: now) ?? now)
            out.append((anchor, minutes))
        }
        return out.sorted { $0.0 < $1.0 }.map { (day: $0.0, asleepMinutes: $0.1) }
    }

    private enum CQError: LocalizedError {
        case hkUnavailable, accessDenied
        var errorDescription: String? {
            switch self {
            case .hkUnavailable: return "Health app unavailable"
            case .accessDenied: return "Sleep access not granted"
            }
        }
    }

    /// The start of the local day containing this date.
    private static func startOfDay(_ date: Date) -> Date {
        Calendar.current.startOfDay(for: date)
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
