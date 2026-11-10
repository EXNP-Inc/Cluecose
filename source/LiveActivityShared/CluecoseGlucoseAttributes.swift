//
//  CluecoseGlucoseAttributes.swift
//  Cluecose
//
//  ActivityAttributes shared by the iPhone app and the Live Activity
//  extension. Both iOS-only targets compile this file directly (it lives in
//  source/LiveActivityShared, NOT in CluecoseCore, because ActivityKit does
//  not exist on watchOS). The phone owns the activity; the paired watch
//  inherits it automatically.
//

import ActivityKit
import Foundation

public struct CluecoseGlucoseAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        /// Latest glucose in mg/dL.
        public var mgdl: Double
        /// When the reading was taken.
        public var date: Date
        /// Wire value of the Trend enum ("flat", "rise", …).
        public var trend: String
        /// Whether the reading is still fresh (inside the ~25 min window).
        public var isFresh: Bool
    }

    /// Display unit the phone is currently using ("mg/dL" / "mmol/L").
    public var unit: String
    /// When the activity was started — used for the end-date math.
    public var startedAt: Date

    public init(unit: String, startedAt: Date) {
        self.unit = unit
        self.startedAt = startedAt
    }
}
