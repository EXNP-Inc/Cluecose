//
//  LibreDirectReader.swift
//  Cluecose
//
//  Reads a FreeStyle Libre 3 directly over BLE with NO Abbott account,
//  NO LibreLink cloud, and NO HealthKit:
//
//    1. NFC tap -> sensor identity + BLE PIN (0xA0 activate / 0xA8 switch receiver)
//    2. CoreBluetooth connect to the sensor's 0898… GATT service
//    3. Full command-gated authorization (Phases 1-6) via LibreCRKit; the
//       Phase-5 session key is derived ON-DEVICE from Abbott's own runtime
//       tables bundled with the library (whitebox key schedule, not ECDH)
//    4. Subscribe to the encrypted data plane, decrypt, stream glucose
//
//  Receiver-ID note: the sensor stores the receiver ID that activated it and
//  answers a later 0xA0/0xA8 with a different one using NFC error 0xB1. A fresh
//  sensor (or one activated through Cluecose) accepts any stable accountless
//  ID, so the common path needs no Abbott material at all. An already-active
//  sensor bound to the Libre app must be presented the SAME ID the app used,
//  which is derived locally from the LibreView Account ID (the account UUID —
//  not the user's login, and never sent anywhere).
//
//  Protocol + implementation reference (MIT):
//  https://github.com/pov-it/LibreCRKit  (clean-room; bundles phone cert +
//  all Abbott runtime tables as resources)
//

import CoreBluetooth
import CoreNFC
import Foundation
import LibreCRKit
import Security
import CluecoseCore

/// One-line console log. Written to stdout so `xcrun devicectl device process
/// launch --console` can stream it live while we watch a real tap/handshake.
@inline(__always)
func clog(_ message: String) {
    FileHandle.standardOutput.write(Data(("[Cluecose] " + message + "\n").utf8))
}

// MARK: - Errors

enum DirectReaderError: LocalizedError {
    case nfcUnavailable
    case nfcCancelled
    case nfcNoTag
    case nfcReadFailed(String)
    /// Sensor rejected our receiver ID (NFC 0xB1). Needs the ID the activating app used.
    case nfcReceiverMismatch
    case bleNotPoweredOn
    case bleNotFound
    case bleConnectFailed(String)
    case handshakeFailed(String)
    case entropySizeMismatch(expected: Int, actual: Int)
    case randomFailed
    case notPaired

    var errorDescription: String? {
        switch self {
        case .nfcUnavailable:
            return "NFC is not available on this device."
        case .nfcCancelled:
            return "The sensor tap was cancelled. Tap again when ready."
        case .nfcNoTag:
            return "No sensor detected. Hold the top edge of your phone against the sensor."
        case .nfcReadFailed(let detail):
            return "Could not talk to the sensor: \(detail)"
        case .nfcReceiverMismatch:
            return "The sensor is bound to a different receiver."
        case .bleNotPoweredOn:
            return "Bluetooth is turned off. Enable it in Settings."
        case .bleNotFound:
            return "Couldn't find the sensor on Bluetooth. Keep it on and try the tap again."
        case .bleConnectFailed(let detail):
            return "Bluetooth connection failed: \(detail)"
        case .handshakeFailed(let detail):
            return "Sensor authorization failed: \(detail)"
        case .entropySizeMismatch(let e, let a):
            return "Entropy size mismatch (expected \(e), got \(a))"
        case .randomFailed:
            return "The system random generator failed."
        case .notPaired:
            return "No sensor paired yet."
        }
    }
}

// MARK: - Persisted session state

/// Everything Cluecose needs to reconnect and keep streaming without another NFC tap.
struct DirectSensorState: Codable, Equatable {
    var serialNumber: String?
    var bleAddress: String?            // "AA:BB:CC:DD:EE:FF" as advertised by the sensor
    var blePIN: Data                   // 4-byte Phase-5 tail, from the NFC response
    var receiverIDLEHex: String        // our receiver ID, little-endian hex (4 bytes)
    var receiverIDSource: String       // "accountless" | "freeStyleLibre3" | "libreByAbbott"
    var activationTimeSeconds: UInt32? // sensor start, epoch seconds (NFC field)
    var warmupDurationMinutes: Int?
    var wearDurationMinutes: Int?
    var lastGlucoseLifeCount: UInt16?
    var lastGlucoseMgDL: UInt16?
    var pairedAt: Date
    var phase5RawKey: Data             // 16B; enables the fast cached reconnect path

    var receiverIDValue: UInt32 {
        Libre3ReceiverID.parseLittleEndianHex(receiverIDLEHex) ?? 0
    }
}

// MARK: - Reader

/// Drives the whole direct-BLE data path. Created and owned by `GlucoseModel`.
@MainActor
final class LibreDirectReader {

    // MARK: Events (wired up by GlucoseModel)

    /// Human-readable progress line for the UI.
    var onStatus: ((String) -> Void)?
    /// Full sample table (48 h, oldest -> newest) after each ingest.
    var onSamples: (([GlucoseSample]) -> Void)?
    /// Link (encrypted BLE session) up/down.
    var onLink: ((Bool) -> Void)?
    /// Sensor age in minutes + whether the sensor is expired/terminated.
    var onSensorMeta: ((Int?, Bool) -> Void)?
    /// True while waiting on the system NFC sheet (tag tap pending).
    var onNFCAwaiting: ((Bool) -> Void)?

    // MARK: State

    private(set) var state: DirectSensorState? {
        didSet { persist() }
    }
    var hasSavedState: Bool { state != nil }
    /// True while an NFC tap / connect / handshake is in progress.
    private(set) var isWorking = false
    /// True while we're specifically waiting on the system NFC sheet + tag tap.
    /// Lets the UI show a big "hold the top of your phone" prompt that cannot
    /// be missed (a bare status line was too easy to overlook on the first run).
    private(set) var nfcAwaiting = false {
        didSet {
            guard nfcAwaiting != oldValue else { return }
            onNFCAwaiting?(nfcAwaiting)
        }
    }

    /// Stable accountless receiver identity for this install (no Abbott material).
    static let accountlessIDKey = "cluecose.libre.accountlessID"
    static let stateKey = "cluecose.libre.directState"
    static let samplesKey = "cluecose.libre.directSamples"
    static let window: TimeInterval = 48 * 60 * 60

    private let scanner = SensorScanner(
        configuration: SensorScannerConfiguration(
            restorationIdentifier: "com.dsikic.cluecose.central",
            notifyOnConnection: true,
            notifyOnDisconnection: true,
            notifyOnNotification: true
        )
    )
    private var session: SensorSession?
    private var crypto: DataPlaneCrypto?
    private var assembler: DataPlaneNotificationAssembler?
    private var listenerTask: Task<Void, Never>?
    private var backfillTask: Task<Void, Never>?
    private var connectionWatcherTask: Task<Void, Never>?
    private var reconnectAttempt = 0
    private var linkActive = false
    private var isReconnecting = false
    private var cancelled = false

    /// lifeCount -> (mg/dL, date). Life count is unique per sensor, so realtime,
    /// clinical and historical sources merge + dedupe by it.
    private var sampleStore: [UInt16: (mg: UInt16, date: Date)] = [:]
    private let storeLock = NSLock()

    /// Last data-plane notify timestamp.
    private var lastNotifyAt = Date.distantPast

    /// Last historical-page timestamp + page count. The realtime stream and
    /// the historic backfill share the notify pipeline, so backfill
    /// completion must track pages specifically — otherwise a steady
    /// realtime notify keeps the pipeline busy forever (or a single
    /// realtime sample makes a page-less backfill look "done").
    private var lastHistoricalPageAt = Date.distantPast
    private var historicalPageCount = 0

    // MARK: Lifecycle

    init() {
        if let data = UserDefaults.standard.data(forKey: Self.stateKey),
           let decoded = try? JSONDecoder().decode(DirectSensorState.self, from: data) {
            state = decoded
        }
        if let data = UserDefaults.standard.data(forKey: Self.samplesKey),
           let rows = try? JSONDecoder().decode([StoredSampleRow].self, from: data) {
            // Recompute dates from activation time; drop rows we can't place.
            if let t = state?.activationTimeSeconds {
                for row in rows {
                    let date = Date(timeIntervalSince1970: Double(t) + Double(row.lifeCount) * 60)
                    if Date().timeIntervalSince(date) <= Self.window {
                        sampleStore[row.lifeCount] = (row.mgdl, date)
                    }
                }
            }
        }
        // Background-mode connection events: with `bluetooth-central` in
        // UIBackgroundModes, iOS wakes the app when the sensor is seen again
        // after a drop (CBConnectionEvent .connected / .notFound). Consuming
        // this stream makes the reconnect immediate instead of waiting for
        // the next scheduled scan.
        scanner.registerForConnectionEvents()
        connectionWatcherTask = Task { [weak self] in
            guard let self else { return }
            for await event in self.scanner.connectionEvents() {
                // Only a `.peerConnected` observation means the sensor is
                // reachable right now → jump straight to the (cached) reconnect
                // instead of waiting for the next scheduled scan.
                // `.peerDisconnected` is left to the backoff loop so a storm of
                // "still not there" events can't bypass it.
                guard case .peerConnected = event.event else { continue }
                guard !self.cancelled, !self.linkActive, !self.isReconnecting,
                      self.listenerTask == nil, self.backfillTask == nil else { continue }
                clog("BLE sensor re-observed — jumping to cached reconnect (peripheral=\(event.peripheral.name ?? "nil"))")
                self.reconnect()
            }
        }
    }

    /// Start listening. Call once from the app's `.task` (and on foreground).
    func start() {
        cancelled = false
        clog("start(): hasSavedState=\(hasSavedState) linkActive=\(linkActive) (instrumented build live, stdout bridged)")
        if hasSavedState {
            reconnect()
        }
    }

    func stop() {
        cancelled = true
        connectionWatcherTask?.cancel()
        connectionWatcherTask = nil
        teardownSession()
    }

    // MARK: Pairing (NFC tap -> BLE -> live)

    /// Taps the sensor over NFC with the given receiver ID, then connects over
    /// BLE, authorizes, and starts streaming. On `nfcReceiverMismatch` the caller
    /// should retry with the ID the activating app used (LibreView account fold).
    func nfcPair(receiverID: UInt32, receiverIDSource: String) async throws {
        setLink(false)
        isWorking = true
        nfcAwaiting = true
        defer {
            isWorking = false
            nfcAwaiting = false
        }
        clog("NFC pair start receiverID=0x\(String(receiverID, radix: 16)) source=\(receiverIDSource) readingAvailable=\(NFCTagReaderSession.readingAvailable)")
        status("Waiting for the NFC sheet — hold the TOP of your phone to the sensor now.")
        let reader = Libre3NFCActivationReader()
        let result: Libre3NFCScanResult
        do {
            clog("NFC scan(mode=.activateOrSwitchReceiver) begun — awaiting tag…")
            result = try await reader.scan(mode: .activateOrSwitchReceiver(receiverID: receiverID))
        } catch let error as Libre3NFCError {
            clog("NFC scan FAILED: \(error)")
            throw Self.classifyNFCError(error)
        }
        clog("NFC scan OK: serial=\(result.patchInfo.serialNumber) stateByte=0x\(String(result.patchInfo.stateByte, radix: 16)) cmd=\(String(describing: result.commandCode)) hasActivation=\(result.activationResponse != nil) patchRaw=\(Self.hexOf(result.patchInfo.inputRaw))")

        guard let activation = result.activationResponse else {
            throw DirectReaderError.nfcReadFailed("sensor returned no pairing data")
        }
        clog("NFC activation: bleAddr=\(activation.bleAddressDisplay) blePIN=\(Self.hexOf(activation.blePIN)) timeRaw=\(Self.hexOf(activation.activationTimeRaw))")

        let receiverIDStruct = Libre3ReceiverID(receiverID)
        let sensorState = try activation.sensorState(
            serialNumber: result.patchInfo.serialNumber,
            receiverID: receiverIDStruct,
            patchInfo: result.patchInfo
        )

        // Activation time (epoch seconds, little-endian) for life-count -> date math.
        var activationSeconds: UInt32? = nil
        if activation.activationTimeRaw.count == 4 {
            let raw = activation.activationTimeRaw
            let value = UInt32(raw[0]) |
                (UInt32(raw[1]) << 8) |
                (UInt32(raw[2]) << 16) |
                (UInt32(raw[3]) << 24)
            // Plausible sensor activation epochs: ~2019…2035.
            if (1_550_000_000...2_060_000_000).contains(value) {
                activationSeconds = value
            }
        }

        let newState = DirectSensorState(
            serialNumber: sensorState.serialNumber,
            bleAddress: sensorState.bleAddress,
            blePIN: sensorState.blePIN,
            receiverIDLEHex: receiverIDStruct.littleEndianHex,
            receiverIDSource: receiverIDSource,
            activationTimeSeconds: activationSeconds,
            warmupDurationMinutes: sensorState.warmupDurationMinutes,
            wearDurationMinutes: sensorState.wearDurationMinutes,
            lastGlucoseLifeCount: sensorState.lastGlucoseLifeCount,
            lastGlucoseMgDL: sensorState.lastGlucoseMgDL,
            pairedAt: Date(),
            phase5RawKey: Data()
        )

        status("NFC OK — connecting over Bluetooth…")
        try await connectAndAuthorize(
            blePIN: newState.blePIN,
            bleAddress: newState.bleAddress,
            newSessionState: newState,
            allowCached: false
        )
    }

    /// Reconnect from saved state (no NFC): BLE connect + authorization using
    /// the saved BLE PIN (fast cached 0x11 path first, full first-pair fallback),
    /// then resume streaming.
    func reconnect() {
        guard let saved = state else { return }
        cancelled = false
        if linkActive || isReconnecting { return }
        isReconnecting = true
        Task { [weak self] in
            guard let self else { return }
            defer { self.isReconnecting = false }
            do {
                try await self.connectAndAuthorize(
                    blePIN: saved.blePIN,
                    bleAddress: saved.bleAddress,
                    newSessionState: saved,
                    allowCached: true
                )
            } catch {
                self.status("Reconnect failed: \(String(describing: error))")
                self.scheduleReconnect()
            }
        }
    }

    func forget() {
        cancelled = true
        teardownSession()
        state = nil
        storeLock.lock()
        sampleStore.removeAll()
        storeLock.unlock()
        UserDefaults.standard.removeObject(forKey: Self.stateKey)
        UserDefaults.standard.removeObject(forKey: Self.samplesKey)
        onSamples?([])
        onSensorMeta?(nil, false)
    }

    // MARK: Receiver-ID candidates (no account by default)

    /// Stable per-install accountless receiver identity.
    static func accountlessReceiverID() -> UInt32 {
        let defaults = UserDefaults.standard
        if let existing = defaults.string(forKey: accountlessIDKey) {
            return NFCActivationCommand.accountlessReceiverID(from: existing)
        }
        let created = UUID().uuidString.lowercased()
        defaults.set(created, forKey: accountlessIDKey)
        return NFCActivationCommand.accountlessReceiverID(from: created)
    }

    /// Ordered receiver-ID candidates to try. With a LibreView Account ID
    /// (the account UUID from the Libre app — not the login) we fold it the way
    /// the two Abbott apps do; otherwise we use our own accountless ID.
    static func receiverIDCandidates(accountID: String?) -> [(value: UInt32, source: String)] {
        var out: [(UInt32, String)] = []
        let normalized = (accountID ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if normalized.count >= 8 {
            let f3 = Libre3ReceiverID(accountID: normalized, derivation: .freeStyleLibre3).value
            let la = Libre3ReceiverID(accountID: normalized, derivation: .libreByAbbott).value
            out.append((f3, "freeStyleLibre3"))
            if la != f3 {
                out.append((la, "libreByAbbott"))
            }
        }
        let accountless = accountlessReceiverID()
        if !out.contains(where: { $0.0 == accountless }) {
            out.append((accountless, "accountless"))
        }
        return out
    }

    // MARK: BLE connect + authorization

    private func connectAndAuthorize(
        blePIN: Data,
        bleAddress: String?,
        newSessionState: DirectSensorState,
        allowCached: Bool
    ) async throws {
        // Mutable copy so we can stash the Phase-5 key learned during the
        // handshake before persisting.
        var newSessionState = newSessionState
        clog("connectAndAuthorize begin: target=\(bleAddress ?? "nil") allowCached=\(allowCached) savedPhase5=\(newSessionState.phase5RawKey.count)B")
        try await scanner.waitUntilReady()
        status("Looking for the sensor… (it opens a connection window about every minute)")

        let targetName = Self.normalizedBLEAddress(bleAddress)
        let peripheral: CBPeripheral

        // Prefer a peripheral we already have connected, matched by the
        // NFC-reported BLE address (a different connected device must not
        // be grabbed by accident).
        if let targetName {
            let connected = await scanner.retrieveConnectedPeripherals()
            clog("retrieveConnectedPeripherals: \(connected.map { $0.name ?? "?" }.joined(separator: ", "))")
            if let match = connected.first(where: { Self.normalizedBLEAddress($0.name) == targetName }) {
                clog("BLE using already-connected peripheral '\(match.name ?? "?")'")
                peripheral = match
            } else {
                clog("BLE scanning for target \(targetName) (up to 75 s)…")
                peripheral = try await firstLibreDiscovery(timeout: 75, targetName: targetName).peripheral
                clog("BLE discovered peripheral '\(peripheral.name ?? "?")'")
            }
        } else {
            clog("BLE scanning for any Libre (up to 75 s)…")
            peripheral = try await firstLibreDiscovery(timeout: 75, targetName: nil).peripheral
            clog("BLE discovered peripheral '\(peripheral.name ?? "?")'")
        }

        status("Connected — running the security handshake…")
        clog("BLE connecting to '\(peripheral.name ?? "?")'…")
        let sensorSession = try await scanner.connect(peripheral, timeout: 120)
        self.session = sensorSession

        var dataCrypto: DataPlaneCrypto
        var phase5RawKey: Data? = nil
        var phase6NoncePrefix: UInt16? = nil
        clog("Attempting \(allowCached && newSessionState.phase5RawKey.count == 16 ? "CACHED (0x11)" : "FULL first-pair (03 03)") handshake…")
        if allowCached,
           newSessionState.phase5RawKey.count == 16,
           let (fast, fastNoncePrefix) = try? await runCachedReconnect(
                blePIN: blePIN,
                session: sensorSession
           ) {
            dataCrypto = fast
            phase6NoncePrefix = fastNoncePrefix
        } else {
            let (crypto, material, noncePrefix) = try await runFirstPairHandshake(
                blePIN: blePIN,
                session: sensorSession
            )
            dataCrypto = crypto
            phase5RawKey = material?.rawKey
            phase6NoncePrefix = noncePrefix
        }
        self.crypto = dataCrypto
        self.assembler = DataPlaneNotificationAssembler()

        if let rawKey = phase5RawKey {
            newSessionState.phase5RawKey = rawKey
        }

        status("Authorized — arming the data stream…")
        // Post-Phase-6 the sensor stays silent until each data-plane CCCD gets
        // a fresh write; refreshDataPlaneNotifications does the off->on toggle.
        try await sensorSession.refreshDataPlaneNotifications()

        state = newSessionState
        setLink(true)
        startListener(sensorSession)
        startBackfill(sensorSession, crypto: dataCrypto, phase6NoncePrefix: phase6NoncePrefix)
        status("Live — readings update about once a minute.")
    }

    /// The proven first-pair/candidate handshake: `03 03` phone cert, native
    /// phone ephemeral generated up-front, and its null-entropy replayed as the
    /// FIXED Phase-5 entropy source (maxAttempts = 1) so the sent ephemeral
    /// point matches the on-device derived Phase-5 key.
    /// Returns (crypto, phase5Material, phase6NoncePrefix) — the nonce prefix
    /// (low 16 bits of the sensor's Phase-6 nonce) is the sensor's current
    /// life count and seeds the 24 h backfill when nothing else is known.
    private func runFirstPairHandshake(
        blePIN: Data,
        session: SensorSession
    ) async throws -> (DataPlaneCrypto, FirstPairPhase5KeyMaterial?, UInt16?) {
        let phoneCert = try PhoneCert.bundled162b()
        let nativeEphemeral = try await Task.detached(priority: .userInitiated) {
            try SessionKey.makeFirstPairNativeEphemeral { requestedCount in
                try Self.secureRandom(count: requestedCount)
            }
        }.value

        let transport = SensorSessionTransport(session: session)
        let flow = PairingFlow(
            transport: transport,
            phoneCert: phoneCert,
            phoneEph: nativeEphemeral.keyPair
        )
        let result = try await flow.runCommandGatedFirstPairHandshake(
            blePIN: blePIN,
            maxEntropyAttempts: 1,
            entropySource: { requestedCount in
                guard requestedCount == nativeEphemeral.nullEntropy11A.count else {
                    throw DirectReaderError.entropySizeMismatch(
                        expected: nativeEphemeral.nullEntropy11A.count,
                        actual: requestedCount
                    )
                }
                return nativeEphemeral.nullEntropy11A
            },
            r2Provider: { try Self.secureRandom(count: 16) }
        )
        let crypto = try DataPlaneCrypto(sessionMaterial: result.handshake.sessionMaterial)
        return (crypto, result.phase5Material, Self.phase6NoncePrefix(fromNonce: result.handshake.phase6.nonce))
    }

    /// Fast reconnect: StartAuthorization 0x11 + cached Phase-5 raw key.
    private func runCachedReconnect(
        blePIN: Data,
        session: SensorSession
    ) async throws -> (DataPlaneCrypto, UInt16?) {
        guard let rawKey = state?.phase5RawKey, rawKey.count == 16 else {
            throw DirectReaderError.handshakeFailed("no cached key")
        }
        let transport = SensorSessionTransport(session: session)
        let flow = PairingFlow(transport: transport)
        let result = try await flow.runCachedReconnectHandshake(
            tail4: blePIN,
            phase5RawKey: rawKey,
            r2Provider: { try Self.secureRandom(count: 16) }
        )
        let crypto = try DataPlaneCrypto(sessionMaterial: result.sessionMaterial)
        return (crypto, Self.phase6NoncePrefix(fromNonce: result.phase6.nonce))
    }

    /// Low 16 bits of the Phase-6 sensor nonce (little-endian) = the sensor's
    /// current life count at authorization time (see the PoC's backfill start).
    private static func phase6NoncePrefix(fromNonce nonce: Data) -> UInt16? {
        guard nonce.count >= 2 else { return nil }
        return UInt16(nonce[nonce.startIndex]) | (UInt16(nonce[nonce.startIndex + 1]) << 8)
    }

    /// Scan for the sensor. If a target BLE address (from the NFC read) is
    /// known, wait for that exact advert until the timeout; if the sensor never
    /// advertised under that name (e.g. it's already connected elsewhere), fall
    /// back to the strongest Libre advert seen. Without a target, take the
    /// first Libre advert.
    private func firstLibreDiscovery(
        timeout: TimeInterval,
        targetName: String?
    ) async throws -> DiscoveredSensor {
        let timeoutTask = Task { [scanner] in
            try? await Task.sleep(nanoseconds: UInt64(timeout * 1_000_000_000))
            scanner.stopScan()
        }
        defer {
            timeoutTask.cancel()
            scanner.stopScan()
        }

        let stream = scanner.startScan(filter: [LibreSensorGATT.serviceUUID])
        var best: DiscoveredSensor?
        for await found in stream {
            if let targetName {
                if Self.normalizedBLEAddress(found.name) == targetName {
                    return found
                }
                // Keep the strongest non-target advert as a fallback.
                if best == nil || found.rssi > best!.rssi {
                    best = found
                }
                continue
            }
            return found
        }
        guard let best else {
            throw DirectReaderError.bleNotFound
        }
        return best
    }

    // MARK: Data-plane listener

    private func startListener(_ sensorSession: SensorSession) {
        listenerTask?.cancel()
        guard let crypto else { return }
        let assembler = self.assembler!
        var loggedFirstNotify = false
        var loggedFirstFrame = false
        var decryptFailures = 0
        listenerTask = Task { [weak self, sensorSession] in
            for await event in sensorSession.notifications() {
                if !loggedFirstNotify {
                    loggedFirstNotify = true
                    clog("LISTENER first notify on \(event.characteristic.uuidString) (\(event.fragment.count)B)")
                }
                guard let channel = DataPlaneChannel(uuidString: event.characteristic.uuidString) else {
                    continue
                }
                self?.lastNotifyAt = event.receivedAt
                guard let frameRaw = assembler.feed(fragment: event.fragment, channel: channel) else {
                    continue // partial glucoseData fragment, buffered by the assembler
                }
                do {
                    let frame = try DataFrame.parse(frameRaw)
                    let packet = try DataPlaneDecoder(crypto: crypto).decrypt(frame: frame, channel: channel)
                    if !loggedFirstFrame {
                        loggedFirstFrame = true
                        clog("LISTENER first decrypted frame: channel=\(channel) kind=\(String(describing: packet.kind)) payload=\(Self.payloadName(packet.payload))")
                    }
                    self?.handlePacket(packet, receivedAt: event.receivedAt)
                } catch {
                    decryptFailures += 1
                    if decryptFailures == 1 || decryptFailures % 25 == 0 {
                        clog("LISTENER decrypt failure #\(decryptFailures) on \(event.characteristic.uuidString): \(error)")
                    }
                }
            }
            // Notify stream ended = link dropped.
            clog("LISTENER notify stream ended (link dropped)")
            self?.handleLinkDropped()
        }
    }

    private func handleLinkDropped() {
        guard listenerTask != nil else { return }
        listenerTask = nil
        setLink(false)
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        guard !cancelled, hasSavedState else { return }
        guard listenerTask == nil, backfillTask == nil else { return }
        reconnectAttempt += 1
        // Persistent: a paired sensor should be re-acquired on its own. The
        // app runs with the bluetooth-central background mode, so this loop
        // keeps healing the link while the phone is locked too. Cadence:
        // quick for the first few tries (typical app-suspension drops), then
        // a steady 30 s — the sensor only answers when its connection window
        // is open, so a scan can miss one round and hit the next.
        let delay: TimeInterval
        switch reconnectAttempt {
        case 1: delay = 2
        case 2: delay = 4
        case 3: delay = 8
        case 4: delay = 16
        default: delay = 30
        }
        if reconnectAttempt <= 4 {
            status("Link lost — reconnecting in \(Int(delay)) s…")
        } else if reconnectAttempt == 5 {
            status("Still searching for the sensor — trying every 30 s…")
        }
        clog("scheduleReconnect #\(reconnectAttempt) in \(Int(delay)) s")
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard let self, !self.cancelled, !self.linkActive else { return }
            self.reconnect()
        }
    }

    // MARK: Backfill (24 h history so the chart is filled immediately)

    private func startBackfill(
        _ sensorSession: SensorSession,
        crypto: DataPlaneCrypto,
        phase6NoncePrefix: UInt16?
    ) {
        backfillTask?.cancel()
        backfillTask = Task { [weak self, sensorSession] in
            guard let self else { return }
            // Wait a bit for the first patchStatus/realtime to learn the current life count.
            var currentLC: UInt16? = self.state?.lastGlucoseLifeCount
            if currentLC == nil {
                for _ in 0..<45 {
                    if let lc = self.state?.lastGlucoseLifeCount {
                        currentLC = lc
                        break
                    }
                    try? await Task.sleep(for: .seconds(2))
                }
            }
            let start: UInt16
            if let currentLC {
                // Re-fetch a small overlap around the last known value — the
                // 24 h of history arrives in 5-minute pages.
                start = max(0, currentLC - 1440)
            } else if let phase6NoncePrefix {
                // No life count observed yet: the Phase-6 nonce prefix IS the
                // sensor's current life count; look back ~3 h of committed data.
                start = phase6NoncePrefix > 180 ? phase6NoncePrefix - 180 : 0
            } else {
                start = 0
            }
            await self.runBackfill(via: sensorSession, crypto: crypto, fromLifeCount: start)
            self.backfillTask = nil
        }
    }

    private func runBackfill(
        via session: SensorSession,
        crypto: DataPlaneCrypto,
        fromLifeCount lifeCount: UInt16
    ) async {
        status("Filling in the last 24 h of history…")
        // A 24 h pull is ~288 historical pages (6 samples at 5-min stride
        // per page). After a long outage the sensor takes several seconds to
        // start streaming the backlog, and the stream then runs for minutes.
        // The old 8-second "give up" cut the pull short and left the outage
        // hole in the chart (only the live stream's first point arrived).
        let firstActivityTimeout: TimeInterval = 15
        let quietSeconds: TimeInterval = 5
        let maxSeconds: TimeInterval = 300
        do {
            try await session.setNotify(true, for: LibreSensorGATT.Char.historicData, timeout: 10)
            try? await Task.sleep(for: .milliseconds(150))
            var sequence: UInt16 = 0x0001
            for attempt in 1...3 {
                let command = PatchControlCommand.historicalBackfillGreaterEqual(lifeCount: lifeCount)
                let frame = try crypto.encrypt(
                    plaintext: command.plaintext,
                    sequence: sequence,
                    kind: .patchControlWrite
                )
                try await session.writeRaw(frame.raw, to: LibreSensorGATT.Char.patchControl, timeout: 10)

                // Wait for the historic stream to finish: a full pull only
                // ends when the sensor goes quiet AFTER it has streamed
                // pages, or when the hard cap is hit. Track pages
                // specifically — the realtime stream keeps the shared
                // notify pipeline busy and would mask the true end.
                // No pages within the first-activity window → re-issue the
                // request (the sensor occasionally ignores a backfill
                // command issued right after Phase 6).
                let pagesBefore = historicalPageCount
                let attemptStart = Date()
                while true {
                    let now = Date()
                    let gotPages = historicalPageCount > pagesBefore
                    if gotPages,
                       now.timeIntervalSince(lastHistoricalPageAt) >= quietSeconds { break }
                    if !gotPages, now.timeIntervalSince(attemptStart) >= firstActivityTimeout { break }
                    if now.timeIntervalSince(attemptStart) >= maxSeconds { break }
                    try? await Task.sleep(for: .seconds(0.25))
                }
                if historicalPageCount > pagesBefore || attempt == 3 { break }
                clog("backfill: no history pages within \(Int(firstActivityTimeout))s — re-requesting (\(attempt + 1)/3)")
                sequence = sequence &+ 1
            }
            try? await session.setNotify(false, for: LibreSensorGATT.Char.historicData, timeout: 10)
            clog("backfill from lifeCount \(lifeCount): \(historicalPageCount) history pages total, store now \(sampleCount()) points")
        } catch {
            try? await session.setNotify(false, for: LibreSensorGATT.Char.historicData, timeout: 10)
        }
        status("Live — readings update about once a minute.")
    }

    // MARK: Packet handling

    private func handlePacket(_ packet: DataPlaneDecodedPacket, receivedAt: Date) {
        let activationSeconds = state?.activationTimeSeconds
        func dateFor(lifeCount: UInt16) -> Date? {
            guard let activationSeconds else { return receivedAt }
            return Date(timeIntervalSince1970: Double(activationSeconds) + Double(lifeCount) * 60)
        }

        switch packet.payload {
        case .realtimeGlucose(let reading):
            clog("DATA first realtime: lc=\(reading.lifeCount) mgdl=\(String(describing: reading.currentGlucoseMgDL)) valid=\(reading.isCurrentGlucoseValid)")
            if let mg = reading.currentGlucoseMgDL, mg > 0 {
                record(
                    lifeCount: reading.lifeCount,
                    mg: mg,
                    date: dateFor(lifeCount: reading.lifeCount) ?? receivedAt
                )
                var s = state
                s?.lastGlucoseLifeCount = reading.lifeCount
                s?.lastGlucoseMgDL = mg
                state = s
            }

        case .clinicalReadingRecord(let record):
            if let mg = record.currentGlucoseMgDL, mg > 0 {
                self.record(
                    lifeCount: record.lifeCount,
                    mg: mg,
                    date: dateFor(lifeCount: record.lifeCount) ?? receivedAt
                )
            }

        case .historicalReadingPage(let page):
            self.lastHistoricalPageAt = receivedAt
            for sample in page.samples {
                if let mg = sample.glucoseMgDL, mg > 0 {
                    record(
                        lifeCount: sample.lifeCount,
                        mg: mg,
                        date: dateFor(lifeCount: sample.lifeCount) ?? receivedAt
                    )
                }
            }

        case .patchStatus(let status):
            let ageMinutes = Int(status.currentLifeCount)
            let expired = !status.isPatchStateActive && status.isPatchStateExpiredOrError
            if ageMinutes > 0 {
                var s = state
                let asU16 = UInt16(min(max(ageMinutes, 0), 65535))
                let previous = s?.lastGlucoseLifeCount ?? 0
                s?.lastGlucoseLifeCount = max(previous, asU16)
                state = s
                onSensorMeta?(ageMinutes, expired)
            } else {
                onSensorMeta?(state?.lastGlucoseLifeCount.map { Int($0) }, false)
            }

        case .raw:
            break
        }
    }

    private func record(lifeCount: UInt16, mg: UInt16, date: Date) {
        storeLock.lock()
        sampleStore[lifeCount] = (mg, date)
        let all = sampleStore
        storeLock.unlock()
        persistSamples(all)
        emitSamples(all)
    }

    private func sampleCount() -> Int {
        storeLock.lock()
        defer { storeLock.unlock() }
        return sampleStore.count
    }

    private func emitSamples(_ all: [UInt16: (mg: UInt16, date: Date)]) {
        let rows = all
            .filter { Date().timeIntervalSince($0.value.date) <= Self.window }
            .sorted { $0.value.date < $1.value.date }
            .map { GlucoseSample(date: $0.value.date, mgdl: Double($0.value.mg)) }
        onSamples?(rows)
    }

    private func persistSamples(_ all: [UInt16: (mg: UInt16, date: Date)]) {
        if let data = try? JSONEncoder().encode(
            all.map { StoredSampleRow(lifeCount: $0.key, mgdl: $0.value.mg) }
        ) {
            UserDefaults.standard.set(data, forKey: Self.samplesKey)
        }
    }

    private struct StoredSampleRow: Codable {
        let lifeCount: UInt16
        let mgdl: UInt16
    }

    // MARK: Teardown + helpers

    private func teardownSession() {
        listenerTask?.cancel()
        listenerTask = nil
        backfillTask?.cancel()
        backfillTask = nil
        if let session {
            scanner.disconnect(session)
        }
        self.session = nil
        self.crypto = nil
        self.assembler = nil
        setLink(false)
    }

    private func setLink(_ active: Bool) {
        guard linkActive != active else { return }
        linkActive = active
        if active { reconnectAttempt = 0 }
        onLink?(active)
    }

    private func status(_ text: String) {
        onStatus?(text)
    }

    private func persist() {
        guard let state else { return }
        if let data = try? JSONEncoder().encode(state) {
            UserDefaults.standard.set(data, forKey: Self.stateKey)
        }
    }

    // MARK: Error classification

    private static func classifyNFCError(_ error: Libre3NFCError) -> DirectReaderError {
        if looksLikeReceiverMismatch(error) {
            return .nfcReceiverMismatch
        }
        switch error {
        case .readerUnavailable: return .nfcUnavailable
        case .noTag: return .nfcNoTag
        case .multipleTags: return .nfcReadFailed("more than one tag detected — move other cards/tags away")
        case .nonISO15693Tag: return .nfcReadFailed("tag is not a supported CGM sensor")
        case .sessionAlreadyActive: return .nfcCancelled
        case .invalidActivationResponse(let raw):
            // Not a 0xB1 mismatch but an unexpected frame. Surface the exact
            // bytes so a new rejection variant is diagnosable from the UI.
            return .nfcReadFailed("sensor returned an unexpected pairing response (\(hexOf(raw)))")
        case .invalidActivationResponseForPatch(_, _, let raw):
            return .nfcReadFailed("sensor rejected the activation (\(hexOf(raw)))")
        case .invalidPatchInfo(let raw):
            return .nfcReadFailed("couldn't read the sensor tag (\(hexOf(raw)))")
        default: break
        }
        return .nfcReadFailed(error.description)
    }

    private static func hexOf(_ data: Data) -> String {
        data.map { String(format: "%02x", $0) }.joined()
    }

    private static func payloadName(_ payload: DataPlaneDecodedPayload) -> String {
        switch payload {
        case .realtimeGlucose: return "realtimeGlucose"
        case .patchStatus: return "patchStatus"
        case .historicalReadingPage: return "historicalReadingPage"
        case .clinicalReadingRecord: return "clinicalReadingRecord"
        case .raw: return "raw"
        @unknown default: return "unknown"
        }
    }

    /// 0xB1 arrives as a short error frame (flag 0x01, code byte 0xB1) where a
    /// 19-byte pairing response is expected. It is raised as
    /// `.invalidActivationResponseForPatch` (activation command) or
    /// `.invalidActivationResponse` (raw), both carrying the raw bytes.
    static func looksLikeReceiverMismatch(_ error: Libre3NFCError) -> Bool {
        let raw: Data?
        switch error {
        case .invalidActivationResponse(let data):
            raw = data
        case .invalidActivationResponseForPatch(_, _, let data):
            raw = data
        default:
            return false
        }
        guard let raw else { return false }
        var body = raw
        while let first = body.first, first == 0x00 || first == 0xA5 {
            body.removeFirst()
        }
        guard !body.isEmpty else { return false }
        // The receiver-mismatch error is a short frame whose payload is the
        // flag byte 0x01 followed by code 0xB1 — or, on some firmwares, just
        // the 0xB1 code after the 00/A5 header. A genuine success is always a
        // 19-byte `00 A5 00 …` frame (already rejected upstream), so matching
        // a short 0xB1 frame here cannot false-positive.
        if body.count >= 2, body[body.startIndex] == 0x01, body[body.startIndex + 1] == 0xB1 {
            return true
        }
        if body.count == 1, body[body.startIndex] == 0xB1 {
            return true
        }
        return false
    }

    // MARK: Small utilities

    private static func normalizedBLEAddress(_ value: String?) -> String? {
        guard let value else { return nil }
        let normalized = value.filter { $0.isHexDigit }.uppercased()
        return normalized.isEmpty ? nil : normalized
    }

    nonisolated private static func secureRandom(count: Int) throws -> Data {
        guard count >= 0 else { throw DirectReaderError.randomFailed }
        var bytes = [UInt8](repeating: 0, count: count)
        let status = bytes.withUnsafeMutableBytes { rawBuffer in
            guard let baseAddress = rawBuffer.baseAddress else { return errSecParam }
            return SecRandomCopyBytes(kSecRandomDefault, rawBuffer.count, baseAddress)
        }
        guard status == errSecSuccess else { throw DirectReaderError.randomFailed }
        return Data(bytes)
    }
}
