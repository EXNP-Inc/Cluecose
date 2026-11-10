//
//  WatchPusher.swift
//  Cluecose
//
//  Pushes the live Libre reading to the Apple Watch companion over
//  WatchConnectivity. The watch's HealthKit is only a *sync replica* of the
//  phone's Health (it lags by minutes), so for real-time display the phone —
//  the actual data source — is what pushes the current value to the watch.
//  The watch falls back to its HealthKit read only when it has no live push
//  (e.g. the phone was backgrounded and the app was force-quit).
//
//  Uses `updateApplicationContext`: small, key-value, auto-delivered to the
//  watch on (re)connect, and it overwrites — so the watch always ends up on
//  the newest value. One push per new sample (≈1/min), so it's cheap.
//
//  Structured like the watch-side WatchReceiver (NSObject, no explicit init,
//  an activate() method) so the @objc WCSessionDelegate conformance compiles
//  cleanly in this toolchain.
//

import Foundation
import WatchConnectivity
import CluecoseCore

/// Throttled, fire-and-forget push of the current reading to the watch.
final class WatchPusher: NSObject, WCSessionDelegate {
    static let shared = WatchPusher()

    /// How often (at most) to push, even if several samples land in a burst.
    private let minInterval: TimeInterval = 5
    private var lastPush: Date = .distantPast
    private var pending = false

    /// Set up the session. Idempotent; call once from the app model's start().
    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Push the current reading + 24 h stats to the watch.
    func push(mgdl: Double, date: Date, trendWire: String, unitWire: String, tir: Double?, burnWire: String?) {
        guard WCSession.default.activationState == .activated else { return }
        guard Date().timeIntervalSince(lastPush) >= minInterval else {
            pending = true
            return
        }
        lastPush = Date()
        pending = false

        var ctx: [String: Any] = [
            "mgdl": mgdl,
            "date": date.timeIntervalSince1970,
            "trend": trendWire,
            "unit": unitWire,
        ]
        if let tir { ctx["tir"] = tir }
        if let burnWire { ctx["burn"] = burnWire }
        do {
            try WCSession.default.updateApplicationContext(ctx)
        } catch {
            clog("WATCH push failed: \(error.localizedDescription)")
        }
    }

    // MARK: WCSessionDelegate

    func session(_ session: WCSession,
                 activationDidCompleteWith state: WCSessionActivationState,
                 error: Error?) {
        clog("WATCH session activated: \(state.rawValue) err=\(String(describing: error))")
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) {}
}
