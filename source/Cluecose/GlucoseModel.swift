import Foundation
import Combine
import CluecoseCore

/// High-level app phase, derived from the reader's state.
enum LibrePhase: Equatable {
    case setupNeeded          // no sensor paired yet
    case waitingBLE           // paired, link not up yet
    case listening            // link up (or reconnecting); no fresh data yet
    case live                 // fresh readings flowing
    case error(String)
}

// MARK: - Model

/// Direct-BLE model: all primary data comes from the Libre 3 over Bluetooth
/// via `LibreDirectReader` (LibreCRKit). No Abbott account, no cloud.
///
/// HealthKit is an OPTIONAL two-way side channel: Cluecose writes its own
/// readings into the Health app and reads glucose back from other sources
/// (manual entries, older CGMs) so analytics see a complete series. The
/// LibreView Account ID (an optional UUID the user can paste in) is used ONLY
/// to derive the receiver ID needed when taking over a sensor that was
/// activated by the Libre app. It is never sent anywhere.
@MainActor
final class GlucoseModel: ObservableObject {
    // MARK: Data

    @Published var samples: [GlucoseSample] = []        // last 48h, oldest -> newest (direct BLE)
    /// Glucose pulled from the Health app, deduped against direct samples.
    @Published var externalSamples: [GlucoseSample] = []
    /// Direct + external, merged and sorted — what charts and analytics use.
    @Published var allSamples: [GlucoseSample] = []
    @Published var trend: Trend = .unknown
    @Published var unit: GlucoseUnit {
        didSet { UserDefaults.standard.set(unit.rawValue, forKey: "cluecose.unit") }
    }
    @Published var lastError: String?
    @Published var phase: LibrePhase = .setupNeeded
    /// Live progress line from the reader (NFC/BLE/handshake status).
    @Published var status: String = ""
    /// Recomputed whenever samples change: stats + verdict + tips per window.
    @Published var analytics24h: GlucoseStats = GlucoseStats()
    @Published var burnMode: BurnMode = .balanced
    @Published var tips: [GlucoseTip] = []
    /// Health app integration (toggle in Settings; default on).
    let healthKit = HealthKitSync()
    @Published var healthKitEnabled: Bool {
        didSet {
            guard healthKitEnabled != oldValue else { return }
            healthKit.enabled = healthKitEnabled
            if healthKitEnabled {
                startHealthKit()
            }
        }
    }
    @Published var healthKitStatus: String = "Health app: not connected"

    // MARK: Sensor / link state

    @Published var sensorName: String?
    @Published var sensorAgeMinutes: Int?
    @Published var sensorExpired = false
    @Published var linkActive: Bool = false
    @Published var pairingInProgress = false
    @Published var nfcAwaiting = false
    @Published var needsReceiverID = false
    @Published var libreViewAccountID: String {
        didSet { UserDefaults.standard.set(libreViewAccountID, forKey: Self.accountIDKey) }
    }

    private static let accountIDKey = "cluecose.libre.accountID"

    private let directReader: LibreDirectReader

    /// Blood-glucose readings older than this are considered stale.
    private let freshWindow: TimeInterval = 25 * 60
    private var healthKitObserver: AnyCancellable?
    private var externalRefreshTask: Task<Void, Never>?

    // MARK: Init

    init() {
        let saved = UserDefaults.standard.string(forKey: "cluecose.unit")
        if let saved, let u = GlucoseUnit(rawValue: saved) {
            unit = u
        } else {
            let region = Locale.current.region?.identifier ?? ""
            unit = region == "US" ? .mgdl : .mmol
        }
        libreViewAccountID = UserDefaults.standard.string(forKey: Self.accountIDKey) ?? ""
        healthKitEnabled = UserDefaults.standard.object(forKey: HealthKitSync.enabledKey) as? Bool ?? true

        directReader = LibreDirectReader()
        directReader.onStatus = { [weak self] text in
            Task { @MainActor [weak self] in
                self?.status = text
                self?.refreshDerived()
            }
        }
        directReader.onSamples = { [weak self] fresh in
            Task { @MainActor [weak self] in
                self?.ingest(fresh)
            }
        }
        directReader.onLink = { [weak self] active in
            Task { @MainActor [weak self] in
                self?.linkActive = active
                self?.refreshDerived()
            }
        }
        directReader.onSensorMeta = { [weak self] age, expired in
            Task { @MainActor [weak self] in
                self?.sensorAgeMinutes = age
                self?.sensorExpired = expired
                self?.refreshDerived()
            }
        }
        directReader.onNFCAwaiting = { [weak self] awaiting in
            Task { @MainActor [weak self] in
                self?.nfcAwaiting = awaiting
            }
        }
        mirrorReader()
    }

    // MARK: App lifecycle

    func start() {
        mirrorReader()
        directReader.start()
        refreshDerived()
        startHealthKit()
        WatchPusher.shared.activate()
    }

    private func startHealthKit() {
        guard healthKitEnabled, healthKit.available, healthKitObserver == nil else { return }
        healthKitObserver = healthKit.$status
            .receive(on: RunLoop.main)
            .sink { [weak self] in self?.healthKitStatus = $0 }
        Task { [weak self] in
            guard let self else { return }
            await self.healthKit.ensureAuthorization()
            if UserDefaults.standard.bool(forKey: HealthKitSync.testWriteKey) {
                UserDefaults.standard.removeObject(forKey: HealthKitSync.testWriteKey)
                clog("HK test-write flag set — running probe")
                await self.healthKit.runProbeTest()
            }
            await self.refreshExternal()
            await self.healthKit.write(self.samples)
            // Keep external sources fresh while the link is up: 60 s cadence.
            self.externalRefreshTask = Task { [weak self] in
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(60))
                    guard let self, !Task.isCancelled else { break }
                    await self.refreshExternal()
                }
            }
        }
    }

    func stop() {
        directReader.stop()
        externalRefreshTask?.cancel()
        externalRefreshTask = nil
    }

    // MARK: Actions

    /// Taps the sensor over NFC, then connects over BLE and starts streaming.
    /// Tries the receiver-ID candidates in order; on a 0xB1 (sensor bound to a
    /// different receiver) it asks the user to tap again with the next ID, and
    /// when the ladder is exhausted it asks for the LibreView Account ID.
    func pair() async {
        guard !pairingInProgress else { return }
        pairingInProgress = true
        defer { pairingInProgress = false }
        lastError = nil
        needsReceiverID = false
        do {
            let candidates = LibreDirectReader.receiverIDCandidates(accountID: libreViewAccountID)
            var index = 0
            while index < candidates.count {
                let candidate = candidates[index]
                do {
                    try await directReader.nfcPair(
                        receiverID: candidate.value,
                        receiverIDSource: candidate.source
                    )
                    refreshDerived()
                    return
                } catch DirectReaderError.nfcReceiverMismatch {
                    index += 1
                    if index < candidates.count {
                        status = "That receiver ID was rejected by the sensor — tap it again to try the next one…"
                        continue
                    }
                    // Ladder exhausted: the sensor was activated by an Abbott
                    // app and our IDs don't match. Ask for the account UUID.
                    needsReceiverID = true
                    lastError = "This sensor was registered under a LibreView account. Its Account ID is a UUID — open the LibreView app (or log in at libreview.com) and copy the Account ID from your account / report link. Paste it in the field above, then tap the sensor again. It never leaves your phone."
                    break
                }
            }
            refreshDerived()
        } catch {
            lastError = error.localizedDescription
            refreshDerived()
        }
    }

    /// Kick a reconnect from saved state (no NFC needed).
    func reconnectNow() {
        lastError = nil
        if directReader.hasSavedState {
            directReader.reconnect()
        }
        refreshDerived()
    }

    /// Forget the paired sensor and start over.
    func clearSensor() {
        directReader.forget()
        needsReceiverID = false
        lastError = nil
        status = ""
        sensorAgeMinutes = nil
        sensorExpired = false
        refreshDerived()
    }

    // MARK: Derived

    private func mirrorReader() {
        if let state = directReader.state {
            sensorName = state.serialNumber
            if let age = state.lastGlucoseLifeCount {
                sensorAgeMinutes = Int(age)
            }
        }
    }

    private func ingest(_ fresh: [GlucoseSample]) {
        samples = fresh
        recomputeAll()
        refreshDerived()
    }

    /// Pull glucose from the Health app (other sources: manual entries,
    /// older CGMs), deduped against the direct-BLE samples.
    func refreshExternal() async {
        let external = await healthKit.readRecentSamples(window: LibreDirectReader.window)
        let directKeys = Set(samples.map { Self.round($0.date) })
        externalSamples = external.filter { !directKeys.contains(Self.round($0.date)) }
        recomputeAll()
        refreshDerived()
    }

    /// Direct + external merged, deduped on minute, sorted oldest -> newest.
    private func recomputeAll() {
        let merged = (samples + externalSamples)
            .reduce(into: [Int: GlucoseSample]()) { dict, s in
                // One slot per minute; direct samples win on collision.
                let key = Self.round(s.date)
                if dict[key] == nil { dict[key] = s }
            }
            .values
            .sorted { $0.date < $1.date }
        allSamples = merged
        trend = Self.computeTrend(from: merged)
        let stats = GlucoseAnalytics.stats(merged, windowSeconds: 24 * 60 * 60)
        analytics24h = stats
        burnMode = GlucoseAnalytics.burnMode(stats: stats, trend: trend)
        tips = GlucoseAnalytics.tips(stats: stats, latest: merged.last, trend: trend)
        if healthKitEnabled {
            Task { await healthKit.write(samples) }
        }
        // Push the live reading to the watch (WatchConnectivity). The watch's
        // HealthKit replica lags, so the phone is the real-time source.
        if let latest = merged.last {
            WatchPusher.shared.push(
                mgdl: latest.mgdl,
                date: latest.date,
                trendWire: trend.wireValue,
                unitWire: unit.rawValue,
                tir: stats.isMeaningful ? stats.tir : nil,
                burnWire: burnMode.rawValue
            )
        }
    }

    private static func round(_ date: Date) -> Int {
        Int(date.timeIntervalSince1970 / 60)
    }

    private func refreshDerived() {
        if let serial = directReader.state?.serialNumber {
            sensorName = serial
        }

        if !directReader.hasSavedState {
            phase = .setupNeeded
        } else if sensorExpired {
            phase = .error("This sensor is expired — apply a new one, then tap it with your phone.")
        } else if let error = lastError, samples.isEmpty, !pairingInProgress {
            phase = .error(error)
        } else if isFresh {
            phase = .live
        } else if linkActive {
            phase = .listening
        } else {
            phase = .waitingBLE
        }
    }

    // MARK: Presentations

    var hasPairedSensor: Bool { directReader.hasSavedState }

    var latest: GlucoseSample? { allSamples.last }

    var isFresh: Bool {
        guard let latest else { return false }
        return Date().timeIntervalSince(latest.date) < freshWindow
    }

    var readingAgeText: String? {
        guard let latest else { return nil }
        let minutes = Int(Date().timeIntervalSince(latest.date) / 60)
        if minutes < 1 { return "just now" }
        if minutes == 1 { return "1 min ago" }
        if minutes < 60 { return "\(minutes) min ago" }
        let hours = minutes / 60
        return hours == 1 ? "1 h ago" : "\(hours) h ago"
    }

    var sensorAgeText: String? {
        guard let age = sensorAgeMinutes else { return nil }
        let days = age / (24 * 60)
        let hours = (age % (24 * 60)) / 60
        if days > 0 { return "\(days) d \(hours) h" }
        if hours > 0 { return "\(hours) h" }
        return "\(age) min"
    }

    /// Warmup: whatever the sensor's patch info reported (24 h on most Libres).
    var isWarmingUp: Bool {
        guard let age = sensorAgeMinutes else { return false }
        let warmup = directReader.state?.warmupDurationMinutes ?? 24 * 60
        return age <= warmup
    }

    var stats24h: (min: Double, max: Double, inRangePct: Double, count: Int) {
        let now = Date()
        let values = allSamples
            .filter { now.timeIntervalSince($0.date) <= 24 * 60 * 60 }
            .map(\.mgdl)
        guard !values.isEmpty else { return (0, 0, 0, 0) }
        let inRange = values.filter { (70...180).contains($0) }
        return (values.min() ?? 0, values.max() ?? 0,
                Double(inRange.count) / Double(values.count) * 100, values.count)
    }

    // MARK: Trend

    /// Libre-style trend from the slope over the last ~35 minutes of samples.
    static func computeTrend(from samples: [GlucoseSample]) -> Trend {
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
