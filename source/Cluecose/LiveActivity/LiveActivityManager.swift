//
//  LiveActivityManager.swift
//  Cluecose
//
//  Owns the Cluecose glucose Live Activity (Dynamic Island + Lock Screen).
//  The phone is the activity's owner; the paired watch inherits it.
//  Toggled from Settings (cluecose.liveActivity), persisted in UserDefaults.
//  GlucoseModel drives it: one `sync(...)` per fresh reading, `end()` when
//  the toggle goes off.
//

import ActivityKit
import Foundation
import CluecoseCore

@MainActor
final class LiveActivityManager: ObservableObject {
    static let shared = LiveActivityManager()

    static let enabledKey = "cluecose.liveActivity"

    /// Start-or-update the activity with the latest reading. No-ops when the
    /// toggle is off or the user has disabled Live Activities system-wide.
    func sync(mgdl: Double, date: Date, trendWire: String, unitWire: String, isFresh: Bool) async {
        clog("LA sync called: mgdl=\(mgdl) fresh=\(isFresh)")
        let toggle = UserDefaults.standard.bool(forKey: Self.enabledKey)
        guard toggle else {
            clog("LA sync: toggle OFF — no-op")
            return
        }
        let info = ActivityAuthorizationInfo()
        guard info.areActivitiesEnabled else {
            clog("LA sync: areActivitiesEnabled=false — OS has not granted Live Activities for this app")
            return
        }
        let state = CluecoseGlucoseAttributes.ContentState(
            mgdl: mgdl,
            date: date,
            trend: trendWire,
            isFresh: isFresh
        )
        if let activity = Activity<CluecoseGlucoseAttributes>.activities.first {
            // Backstop: the sensor keeps pushing readings, but if the activity
            // has gone quiet for 8 h, end it rather than leave a stale card.
            if Date().timeIntervalSince(activity.attributes.startedAt) > 8 * 60 * 60 {
                await end()
                return
            }
            await activity.update(
                ActivityContent(state: state, staleDate: isFresh ? nil : date.addingTimeInterval(30 * 60))
            )
        } else {
            let attributes = CluecoseGlucoseAttributes(unit: unitWire, startedAt: Date())
            do {
                _ = try await Activity.request(
                    attributes: attributes,
                    content: ActivityContent(state: state, staleDate: nil)
                )
                clog("LA: started activity (mgdl=\(mgdl))")
            } catch {
                clog("LiveActivity start failed: \(error.localizedDescription)")
            }
        }
    }

    /// End the running activity (toggle off / no longer fresh).
    func end() async {
        for activity in Activity<CluecoseGlucoseAttributes>.activities {
            await activity.end(
                ActivityContent(
                    state: CluecoseGlucoseAttributes.ContentState(
                        mgdl: 0, date: Date(), trend: "unknown", isFresh: false
                    ),
                    staleDate: Date()
                ),
                dismissalPolicy: .immediate
            )
        }
    }
}
