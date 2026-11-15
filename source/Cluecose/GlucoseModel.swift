import Foundation
import Combine
import ActivityKit
import BackgroundTasks
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
    /// Live Activity (Dynamic Island + Lock Screen, mirrored on the watch).
    @Published var liveActivityEnabled: Bool {
        didSet {
            guard liveActivityEnabled != oldValue else { return }
            UserDefaults.standard.set(liveActivityEnabled, forKey: LiveActivityManager.enabledKey)
            if liveActivityEnabled {
                Task { [weak self] in
                    guard let self else { return }
                    if let latest = self.latest, self.isFresh {
                        await LiveActivityManager.shared.sync(
                            mgdl: latest.mgdl, date: latest.date,
                            trendWire: self.trend.wireValue, unitWire: self.unit.rawValue,
                            isFresh: true
                        )
                    }
                }
            } else {
                Task { await LiveActivityManager.shared.end() }
            }
        }
    }

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
    /// The food-log brain (LLM estimates + on-device insights + patterns +
    /// nutrition-sleep). Created here and attached to `self` (it holds a
    /// weak reference back). Owned by the app model so its recompute timer
    /// lives for the app's lifetime.
    let foodLog: FoodLogModel

    /// Simulator demo mode (Settings toggle, persisted). The simulators have
    /// no NFC, so a real sensor can't be paired; demo mode injects a synthetic
    /// 48 h glucose series + sample meals through the same `ingest()` path so
    /// every surface populates without a sensor.
    @Published var demoMode: Bool {
        didSet {
            UserDefaults.standard.set(demoMode, forKey: Self.demoModeKey)
            applyDemoMode()
        }
    }

    private static let demoModeKey = "cluecose.demoMode"
    /// One re-injection of demo data per 60 s (keeps the series "live" while
    /// the toggle is on).
    private var demoRefreshTask: Task<Void, Never>?
    /// True while the demo-mode-off HealthKit purge is in flight — the 60 s
    /// external refresh must not pull the not-yet-deleted demo rows back in.
    private var demoPurgePending = false
    /// Set once after the one-time cleanup of demo-mode rows that OLDER builds
    /// (which wrote them while demo mode was on) left in the Health app.
    private static let demoHKPurgedKey = "cluecose.demo.hkPurged"

    /// Blood-glucose readings older than this are considered stale.
    private let freshWindow: TimeInterval = 25 * 60
    private var healthKitObserver: AnyCancellable?
    private var externalRefreshTask: Task<Void, Never>?

    // MARK: Background link-keeper

    /// The system's periodic wake. While the phone is locked the app is
    /// suspended, so the in-process 30 s reconnect loop freezes. Because the
    /// Libre is a GATT *server* (it never re-dials the phone), a dropped link
    /// then stays down until something re-wakes the central. A
    /// `BGAppRefreshTask` wakes the app periodically; we re-kick the
    /// reconnect, and once the BLE connection is up the `bluetooth-central`
    /// background mode keeps the process alive to stream. This bounds the
    /// worst-case outage from hours to the system's refresh interval.
    static let linkKeeperID = "com.dsikic.cluecose.linkkeeper"
    private static let linkKeeperRegistered = NSLock()
    private static var linkKeeperRegisteredDone = false
    /// Set by init so the BG handler can re-kick this instance's reconnect.
    private static var linkKeeperKick: (@MainActor () -> Void)?

    private static func registerLinkKeeperOnce() {
        linkKeeperRegistered.lock()
        defer { linkKeeperRegistered.unlock() }
        guard !linkKeeperRegisteredDone else { return }
        linkKeeperRegisteredDone = true
        BGTaskScheduler.shared.register(forTaskWithIdentifier: linkKeeperID, using: nil) { task in
            guard let bg = task as? BGAppRefreshTask else { fatalError("wrong task") }
            clog("LINKKEEPER BG task fired — re-kicking reconnect")
            let handler = Task { @MainActor in
                Self.linkKeeperKick?()
                bg.setTaskCompleted(success: true)
            }
            bg.expirationHandler = { handler.cancel() }
        }
    }

    /// Re-kick the sensor reconnect only (light — safe for a ~30 s BG window).
    func kickReconnect() {
        // Healthy link → `bluetooth-central` is maintaining it; nothing to do.
        guard !linkActive else { return }
        directReader.start()
        // Chain the next wake so a still-down link keeps getting retry
        // chances instead of going silent until the next foreground.
        scheduleLinkKeeper()
    }

    /// Schedule the next background link-keeper wake. Called on launch and
    /// from the handler so the chain continues.
    func scheduleLinkKeeper() {
        let request = BGAppRefreshTaskRequest(identifier: Self.linkKeeperID)
        // Ask in ~15 min; the system may coalesce to its own cadence.
        request.earliestBeginDate = Date().addingTimeInterval(15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: Init

    init() {
        let saved = UserDefaults.standard.string(forKey: "cluecose.unit")
        if let saved, let u = GlucoseUnit(rawValue: saved) {
            unit = u
        } else {
            unit = .mmol
        }
        libreViewAccountID = UserDefaults.standard.string(forKey: Self.accountIDKey) ?? ""
        healthKitEnabled = UserDefaults.standard.object(forKey: HealthKitSync.enabledKey) as? Bool ?? true
        liveActivityEnabled = UserDefaults.standard.bool(forKey: LiveActivityManager.enabledKey)
        // Demo mode defaults ON on the SIMULATOR (it has no NFC, so a fresh
        // launch should immediately show a working app with data, not the
        // pair card) and OFF on a physical device (a phone that can pair a
        // real sensor must never start on fake data). An explicit choice
        // (stored true/false) always wins on subsequent launches.
        #if targetEnvironment(simulator)
        demoMode = (UserDefaults.standard.object(forKey: Self.demoModeKey) as? Bool) ?? true
        #else
        demoMode = (UserDefaults.standard.object(forKey: Self.demoModeKey) as? Bool) ?? false
        #endif

        directReader = LibreDirectReader()
        // Create the food-log brain (it holds a weak reference back to us).
        // Attach at the end of init, once this model is fully initialized.
        foodLog = FoodLogModel()
        // Register the background link-keeper once per launch and point the
        // BG handler at THIS instance's reconnect. (After directReader exists
        // so the weak-self closure can form.)
        Self.registerLinkKeeperOnce()
        Self.linkKeeperKick = { [weak self] in self?.kickReconnect() }
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
        // Attach the food model now that this model is fully initialized
        // (it observes $allSamples + starts its recompute timer).
        foodLog.attach(self)
        // If demo mode was on before the last launch, re-inject the synthetic
        // series now (the didSet in init didn't run — this is the initial load).
        if demoMode {
            applyDemoMode()
        }
    }

    // MARK: App lifecycle

    func start() {
        mirrorReader()
        // In demo mode there is no sensor to talk to; skip the reader entirely
        // (it would just log a failed BLE attempt) and keep the synthetic
        // series flowing instead.
        if demoMode {
            injectDemoGlucose()
            startDemoRefresh()
            clog("DEMO: started in demo mode (no NFC) — \(samples.count) synthetic samples, \(foodLog.store.all().count) meal entries; sensor pairing not required")
        } else {
            directReader.start()
        }
        refreshDerived()
        startHealthKit()
        // Kick the nutrition-sleep refresh once on launch (throttled in the
        // model; a no-op while HealthKit is unavailable/denied).
        Task { [weak self] in
            await self?.foodLog.refreshSleepInsights()
        }
        WatchPusher.shared.activate()
        // One-time migration: older builds wrote the synthetic demo series to
        // Health while demo mode was on (the read-back then kept pulling it
        // back in forever). This build never writes demo data — but rows
        // already in Health need a single cleanup, so on the first launch of
        // this build, if demo mode is OFF, purge our own 48 h of Health rows
        // and re-sync the read-back. (While demo mode is ON, those rows are
        // expected and the cleanup runs when it is switched off.)
        if !demoMode,
           !UserDefaults.standard.bool(forKey: Self.demoHKPurgedKey) {
            UserDefaults.standard.set(true, forKey: Self.demoHKPurgedKey)
            clog("DEMO: one-time cleanup — purging demo-mode rows older builds left in Health")
            scheduleDemoHKPurge()
        }
        // Chain the background link-keeper so a locked, suspended app still
        // re-acquires the sensor within the system's refresh cadence.
        scheduleLinkKeeper()
        // Restore the Live Activity on launch: the toggle's didSet only fires
        // on a change, so an app relaunch (or the sensor reconnecting after a
        // reinstall) needs an explicit sync to bring the activity back.
        if liveActivityEnabled, let latest = latest, isFresh {
            Task { [weak self] in
                guard let self else { return }
                await LiveActivityManager.shared.sync(
                    mgdl: latest.mgdl, date: latest.date,
                    trendWire: self.trend.wireValue, unitWire: self.unit.rawValue,
                    isFresh: self.isFresh
                )
            }
        }
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
                    // Don't re-pull the not-yet-purged demo rows back in
                    // while the demo-mode-off HealthKit purge is in flight.
                    guard !self.demoPurgePending else { continue }
                    await self.refreshExternal()
                }
            }
        }
    }

    func stop() {
        directReader.stop()
        stopDemoRefresh()
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
                    lastError = "This sensor was registered by another app's account. That app's Account ID is a UUID — open it (or its website) and copy the Account ID from your account / report page, paste it in the field above, then tap the sensor again. It never leaves your phone."
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

    // MARK: Demo mode (simulators have no NFC)

    /// Turn demo mode on/off. When on: seed the food log (only on the first
    /// turn-on, so a running session's edits aren't clobbered), inject a fresh
    /// synthetic 48 h series through `ingest()`, and refresh it every ~60 s so
    /// the hero reading stays "Live" and insights land as their 3 h windows
    /// pass. When off: stop the refresh, DROP all synthetic state (the
    /// in-memory series, anything the Health read-back re-surfaced, the demo
    /// meals, and the demo rows this app wrote to Health), and start the real
    /// reader if a sensor is paired — turning off demo mode must leave a
    /// clean, sensor-driven app, never a mix of fake and real data.
    func applyDemoMode() {
        if demoMode {
            // Inject the synthetic series FIRST so the merged series is
            // non-empty when the insight engine runs, then seed meals (only if
            // the store is empty — don't clobber a running session's entries).
            injectDemoGlucose()
            if foodLog.store.all().isEmpty {
                seedDemoMeals()
            }
            // Force an immediate insight recompute (bypasses the 120 s throttle)
            // so finalized meals light up their insight right away.
            foodLog.recomputeInsights(force: true)
            startDemoRefresh()
        } else {
            stopDemoRefresh()
            // Demo data must not survive the switch — and it can't be
            // distinguished by freshness, so it is removed by provenance:
            // the in-memory synthetic series, the Health read-back copy, the
            // seeded demo meals (known descriptions), and the rows this app
            // pushed to Health during demo sessions.
            samples = []
            externalSamples = []
            foodLog.removeDemoMeals()
            recomputeAll()
            // A real sensor paired during/after the demo session takes over
            // now (start() skipped the reader while demo mode was on).
            if directReader.hasSavedState {
                directReader.start()
            }
            // Purge the demo rows from Health, then re-sync the read-back so
            // the merged series reflects real data only. (The 60 s external
            // refresh is guarded while the purge is in flight, so the stale
            // rows can't be pulled back in mid-purge.)
            scheduleDemoHKPurge()
        }
        refreshDerived()
    }

    /// Push a fresh synthetic 48 h series through the real `ingest()` path, so
    /// every downstream consumer (analytics, charts, insight engine, watch
    /// push) sees it exactly as it would a real sensor stream.
    private func injectDemoGlucose() {
        let now = Date()
        ingest(DemoData.glucoseSeries(now: now))
    }

    /// Seed the food store with the sample meals (once). Done on the first
    /// demo-mode turn-on so an in-progress session's entries aren't replaced.
    /// (`applyDemoMode` runs a force insight recompute right after seeding.)
    private func seedDemoMeals() {
        let now = Date()
        for entry in DemoData.foodEntries(now: now) {
            foodLog.store.add(entry)
        }
        foodLog.refresh()
    }

    /// Keep re-injecting the synthetic series while demo mode is on so the
    /// latest reading stays within the fresh window and the curve advances
    /// (a real reader does this every ~15 s; 60 s is plenty for a demo).
    private func startDemoRefresh() {
        demoRefreshTask?.cancel()
        demoRefreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                guard let self, !Task.isCancelled else { break }
                guard self.demoMode else { break }
                self.injectDemoGlucose()
            }
        }
    }

    private func stopDemoRefresh() {
        demoRefreshTask?.cancel()
        demoRefreshTask = nil
    }

    /// Kick off the HealthKit demo-row purge (and, when done, the read-back
    /// re-sync), guarding the 60 s external refresh for its duration.
    private func scheduleDemoHKPurge() {
        demoPurgePending = true
        Task { [weak self] in
            guard let self else { return }
            await self.healthKit.purgeDemoGlucose()
            self.demoPurgePending = false
            if self.healthKitEnabled {
                await self.refreshExternal()
            }
        }
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
        // Demo samples must never reach the Health app: the read-back
        // (`refreshExternal`) would pull them straight back in as "external"
        // data, so fake history would survive demo mode being switched off.
        if healthKitEnabled, !demoMode {
            Task { await healthKit.write(samples) }
        }
        // Push the live reading to the watch (WatchConnectivity). The watch's
        // HealthKit replica lags, so the phone is the real-time source. We
        // also piggyback today's food totals + the latest meal insight so the
        // watch can show a one-line food summary (both optional — nil when
        // there is none, so the existing call path is unchanged).
        if let latest = merged.last {
            let foodTotals = foodLog.todayTotals
            WatchPusher.shared.push(
                mgdl: latest.mgdl,
                date: latest.date,
                trendWire: trend.wireValue,
                unitWire: unit.rawValue,
                tir: stats.isMeaningful ? stats.tir : nil,
                burnWire: burnMode.rawValue,
                foodKcal: foodTotals.kcal,
                foodProtein: foodTotals.proteinG,
                foodCarbs: foodTotals.carbsG,
                foodFat: foodTotals.fatG,
                foodSodium: foodTotals.sodiumMg,
                foodCaffeine: foodTotals.caffeineMg,
                insightText: foodLog.latestInsightText
            )
            // Keep the Live Activity (Dynamic Island / Lock Screen / watch)
            // in step with every fresh reading.
            if liveActivityEnabled {
                Task {
                    await LiveActivityManager.shared.sync(
                        mgdl: latest.mgdl,
                        date: latest.date,
                        trendWire: trend.wireValue,
                        unitWire: unit.rawValue,
                        isFresh: isFresh
                    )
                }
            }
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

        // Recompute meal insights (cheap + throttled: it no-ops unless the
        // recompute interval has passed) and, rarely, the nutrition-sleep
        // association lines. Both are on-device; sleep needs HealthKit.
        foodLog.recomputeInsights()
        Task { [weak self] in
            await self?.foodLog.refreshSleepInsights()
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
