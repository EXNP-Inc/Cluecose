//
//  GlucoseTypes.swift
//  CluecoseCore
//
//  Domain types shared by the iPhone and Watch apps.
//

import Foundation

public enum GlucoseUnit: String, CaseIterable, Sendable {
    case mgdl = "mg/dL"
    case mmol = "mmol/L"

    /// 1 mmol/L glucose == 18.016 mg/dL
    public func format(fromMgdl mgdl: Double) -> String {
        switch self {
        case .mgdl:
            return String(format: "%.0f", mgdl)
        case .mmol:
            return String(format: "%.1f", mgdl / 18.016)
        }
    }

    public func formatted(fromMgdl mgdl: Double, decimals: Int) -> String {
        switch self {
        case .mgdl:
            return String(format: "%.\(decimals)f", mgdl)
        case .mmol:
            return String(format: "%.\(decimals)f", mgdl / 18.016)
        }
    }
}

public enum Trend: Equatable, Sendable {
    case sharpRise, rise, flat, fall, sharpFall, unknown

    /// Stable string form for cross-process transfer (phone → watch).
    public var wireValue: String {
        switch self {
        case .sharpRise: "sharpRise"
        case .rise: "rise"
        case .flat: "flat"
        case .fall: "fall"
        case .sharpFall: "sharpFall"
        case .unknown: "unknown"
        }
    }

    public init(wireValue: String) {
        switch wireValue {
        case "sharpRise": self = .sharpRise
        case "rise": self = .rise
        case "flat": self = .flat
        case "fall": self = .fall
        case "sharpFall": self = .sharpFall
        default: self = .unknown
        }
    }

    public var symbol: String {
        switch self {
        case .sharpRise: "arrow.up"
        case .rise: "arrow.up.right"
        case .flat: "arrow.right"
        case .fall: "arrow.down.right"
        case .sharpFall: "arrow.down"
        case .unknown: "minus"
        }
    }

    public var label: String {
        switch self {
        case .sharpRise: "Rising sharply"
        case .rise: "Rising"
        case .flat: "Steady"
        case .fall: "Falling"
        case .sharpFall: "Falling sharply"
        case .unknown: "No trend"
        }
    }
}

public enum RangeCategory: Sendable {
    case low, inRange, high

    /// Standard CGM thresholds: low < 70 mg/dL, high > 180 mg/dL.
    public init(mgdl: Double) {
        if mgdl < 70 { self = .low }
        else if mgdl > 180 { self = .high }
        else { self = .inRange }
    }
}

public struct GlucoseSample: Identifiable, Equatable, Sendable {
    public let date: Date
    public let mgdl: Double
    public var id: Date { date }

    public init(date: Date, mgdl: Double) {
        self.date = date
        self.mgdl = mgdl
    }
}
