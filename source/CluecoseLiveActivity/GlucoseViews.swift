//
//  GlucoseViews.swift
//  CluecoseLiveActivity
//
//  Lock Screen + Dynamic Island views for the glucose Live Activity.
//

import ActivityKit
import SwiftUI
import WidgetKit
import CluecoseCore

private func glucoseTint(mgdl: Double) -> Color {
    switch RangeCategory(mgdl: mgdl) {
    case .low: Color(red: 0.45, green: 0.78, blue: 1.0)
    case .inRange: Color(red: 0.30, green: 0.92, blue: 0.60)
    case .high: Color(red: 1.0, green: 0.62, blue: 0.35)
    }
}

/// Lock Screen (full) presentation.
struct GlucoseLockView: View {
    var attributes: CluecoseGlucoseAttributes
    var state: CluecoseGlucoseAttributes.ContentState

    private var unit: GlucoseUnit { GlucoseUnit(rawValue: attributes.unit) ?? .mgdl }
    private var trend: Trend { Trend(wireValue: state.trend) }

    var body: some View {
        HStack(spacing: 14) {
            Image("Logo")
                .resizable()
                .interpolation(.high)
                .scaledToFit()
                .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(unit.format(fromMgdl: state.mgdl))
                        .font(.system(size: 34, weight: .heavy, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                    Text(unit.rawValue)
                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white.opacity(0.6))
                }
                HStack(spacing: 5) {
                    Image(systemName: trend.symbol)
                        .font(.caption.weight(.bold))
                    Text(trend.label)
                        .font(.caption.weight(.medium))
                    if !state.isFresh {
                        Text("· stale")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
                .foregroundStyle(state.isFresh ? glucoseTint(mgdl: state.mgdl) : .orange)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 3) {
                Text(state.date, style: .time)
                    .font(.caption2.weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(.white.opacity(0.5))
                Text(state.isFresh ? "Live" : "Stale")
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background((state.isFresh ? glucoseTint(mgdl: state.mgdl).opacity(0.25) : Color.orange.opacity(0.25)),
                                in: Capsule())
                    .foregroundStyle(state.isFresh ? .white.opacity(0.85) : .orange)
            }
        }
    }
}

/// Dynamic Island, leading capsule (app icon slot).
struct GlucoseCompactLeading: View {
    var attributes: CluecoseGlucoseAttributes
    var state: CluecoseGlucoseAttributes.ContentState

    private var unit: GlucoseUnit { GlucoseUnit(rawValue: attributes.unit) ?? .mgdl }

    var body: some View {
        // Value only, filling the whole capsule. On the iPhone Duo's cover
        // display the compact capsule is narrower than on a standard
        // iPhone — an extra glyph in front of the value pushed "6.9" into
        // "6…". The trend arrow now lives in the trailing capsule.
        Text(unit.format(fromMgdl: state.mgdl))
            .font(.system(size: 13, weight: .heavy, design: .rounded))
            .monospacedDigit()
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .foregroundStyle(state.isFresh ? glucoseTint(mgdl: state.mgdl) : .orange)
    }
}

/// Dynamic Island, trailing capsule.
struct GlucoseCompactTrailing: View {
    var attributes: CluecoseGlucoseAttributes
    var state: CluecoseGlucoseAttributes.ContentState

    var body: some View {
        // The trend arrow: one small glyph that always fits, even in the
        // narrower iPhone Duo compact capsule.
        Image(systemName: Trend(wireValue: state.trend).symbol)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(state.isFresh ? glucoseTint(mgdl: state.mgdl) : .orange)
    }
}

struct GlucoseActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: CluecoseGlucoseAttributes.self) { context in
            GlucoseLockView(attributes: context.attributes,
                            state: context.state)
                .activityBackgroundTint(.black)
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded island (tucked open above the lock screen).
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: Trend(wireValue: context.state.trend).symbol)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(color(for: context.state))
                }
                DynamicIslandExpandedRegion(.center) {
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(unit(for: context.attributes).format(fromMgdl: context.state.mgdl))
                            .font(.system(size: 46, weight: .heavy, design: .rounded))
                            .monospacedDigit()
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                        Text(unit(for: context.attributes).rawValue)
                            .font(.system(size: 16, weight: .semibold, design: .rounded))
                            .foregroundStyle(.white.opacity(0.6))
                    }
                    .foregroundStyle(color(for: context.state))
                }
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 3) {
                        Text(Trend(wireValue: context.state.trend).label)
                            .font(.system(size: 13, weight: .medium, design: .rounded))
                            .foregroundStyle(context.state.isFresh ? .white.opacity(0.7) : .orange)
                        Text(context.state.date, style: .time)
                            .font(.system(size: 12, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(.white.opacity(0.5))
                    }
                }
            } compactLeading: {
                GlucoseCompactLeading(attributes: context.attributes, state: context.state)
            } compactTrailing: {
                GlucoseCompactTrailing(attributes: context.attributes, state: context.state)
            } minimal: {
                Circle()
                    .fill(color(for: context.state))
                    .frame(width: 6, height: 6)
            }
        }
    }

    private func unit(for attributes: CluecoseGlucoseAttributes) -> GlucoseUnit {
        GlucoseUnit(rawValue: attributes.unit) ?? .mgdl
    }

    private func color(for state: CluecoseGlucoseAttributes.ContentState) -> Color {
        state.isFresh ? glucoseTint(mgdl: state.mgdl) : .orange
    }
}
