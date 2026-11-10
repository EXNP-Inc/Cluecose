//
//  WatchReceiver.swift
//  Cluecose Watch
//
//  The watch side of the live-push channel. The iPhone's WatchPusher calls
//  `updateApplicationContext`, which arrives here. We're a plain NSObject
//  delegate (not @MainActor) to mirror the phone's WatchPusher and to avoid
//  the @MainActor + WCSessionDelegate conformance compiler crash; the model
//  hops to MainActor itself when it applies the value.
//

import Foundation
import WatchConnectivity

final class WatchReceiver: NSObject, WCSessionDelegate {
    static let shared = WatchReceiver()

    /// Called on an arbitrary thread with the pushed payload.
    var onLive: ((_ mgdl: Double, _ date: Date, _ trendWire: String, _ unitWire: String,
                  _ tir: Double?, _ burnWire: String?) -> Void)?

    func activate() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    nonisolated func session(_ session: WCSession,
                             activationDidCompleteWith state: WCSessionActivationState,
                             error: Error?) {}

    nonisolated func session(_ session: WCSession,
                             didReceiveApplicationContext applicationContext: [String : Any]) {
        guard let mgdl = applicationContext["mgdl"] as? Double,
              let date = applicationContext["date"] as? TimeInterval else { return }
        let trendWire = applicationContext["trend"] as? String ?? "unknown"
        let unitWire = applicationContext["unit"] as? String ?? "mg/dL"
        let tir = applicationContext["tir"] as? Double
        let burnWire = applicationContext["burn"] as? String
        onLive?(mgdl, Date(timeIntervalSince1970: date), trendWire, unitWire, tir, burnWire)
    }
}
