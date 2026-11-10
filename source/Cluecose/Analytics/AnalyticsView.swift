//
//  AnalyticsView.swift
//  Cluecose
//
//  Insight cards: the storage-vs-burning verdict, the 24 h CGM stat grid,
//  and the tips list. All numbers come from `GlucoseAnalytics`.
//

import SwiftUI
import CluecoseCore

// MARK: - Storage vs burning verdict

struct BurnModeCard: View {
    @EnvironmentObject var model: GlucoseModel

    private var tint: Color {
        switch model.burnMode {
        case .storing:  Color(red: 1.0, green: 0.62, blue: 0.35)
        case .balanced: Color(red: 0.30, green: 0.92, blue: 0.60)
        case .burning:  Color(red: 0.45, green: 0.78, blue: 1.0)
        }
    }

    private var subtitle: String {
        switch model.burnMode {
        case .storing:
            return "Repeated time above 140 mg/dL — insulin is working overtime, and that's when glucose gets shunted into fat stores."
        case .balanced:
            return "Glucose is holding in range without much storage pressure. Neither storing hard nor in a clear burn."
        case .burning:
            return "Low storage pressure and falling glucose — the body is running on its own stores (glycogen, then fat)."
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: model.burnMode.symbol)
                    .font(.system(size: 22))
                    .foregroundStyle(tint)
                Text("Metabolic state")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                Spacer()
                Text("last 24 h")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.4))
            }

            HStack(alignment: .center, spacing: 14) {
                Text(model.burnMode.label.uppercased())
                    .font(.system(size: 34, weight: .heavy, design: .rounded))
                    .foregroundStyle(
                        LinearGradient(colors: [tint, tint.opacity(0.7)],
                                       startPoint: .top, endPoint: .bottom)
                    )
                    .shadow(color: tint.opacity(0.5), radius: 18)
                    // Never wrap on narrow layouts (iPhone Duo's outer screen):
                    // shrink to fit on one line instead of breaking mid-word.
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Spacer(minLength: 8)
                VStack(alignment: .trailing, spacing: 2) {
                    Text("AUC > 140")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.45))
                    Text("\(Int(model.analytics24h.aucAbove140))")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(.white.opacity(0.9))
                    Text("mg/dL·min of storage pressure")
                        .font(.caption2)
                        .foregroundStyle(.white.opacity(0.35))
                }
                .fixedSize(horizontal: false, vertical: true)
            }
            .layoutPriority(1)

            Text(subtitle)
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.6))
                .fixedSize(horizontal: false, vertical: true)

            Text("Estimate from glucose + trend — an index of insulin activity, not a direct fat-oxidation measurement. Not medical advice.")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.3))
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }
}

// MARK: - 24 h stat grid

struct StatsGridCard: View {
    @EnvironmentObject var model: GlucoseModel

    var body: some View {
        let stats = model.analytics24h
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("24 h metrics")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                Spacer()
                if stats.isMeaningful {
                    Text("\(stats.count) readings")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.4))
                }
            }

            if !stats.isMeaningful {
                Text("Need a few more readings to compute these — they fill in as the day goes on.")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.45))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
            } else {
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10),
                                    GridItem(.flexible(), spacing: 10),
                                    GridItem(.flexible(), spacing: 10)],
                          spacing: 10) {
                    cell("TIR 70–180", pct(stats.tir), "checkmark.seal.fill", CluecoseTheme.glow(for: .inRange),
                         warn: stats.tir < 0.70)
                    cell("Time high", pct(stats.tar), "arrow.up.to.line.compact", CluecoseTheme.glow(for: .high),
                         warn: stats.tar > 0.25)
                    cell("Time low", pct(stats.tbr), "arrow.down.to.line.compact", CluecoseTheme.glow(for: .low),
                         warn: stats.tbr > 0.04)
                    cell("Mean", model.unit.format(fromMgdl: stats.mean), "sum", .white.opacity(0.85))
                    cell("CV", "\(Int(stats.cv))%", "waveform.path.ecg", Color(red: 0.75, green: 0.6, blue: 1.0),
                         warn: stats.cv > 36)
                    cell("GMI", String(format: "%.1f%%", stats.gmi), "gauge.with.dots.needle.67percent",
                         Color(red: 0.95, green: 0.75, blue: 0.45), warn: stats.gmi > 7.0)
                }
            }
        }
        .glassPanel()
    }

    private func pct(_ v: Double) -> String { "\(Int((v * 100).rounded()))%" }

    private func cell(_ title: String, _ value: String, _ icon: String, _ tint: Color,
                      warn: Bool = false) -> some View {
        VStack(spacing: 5) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 9))
                Text(title)
                    .font(.system(size: 10, weight: .medium))
            }
            .foregroundStyle(warn ? Color(red: 1.0, green: 0.62, blue: 0.35) : .white.opacity(0.55))
            Text(value)
                .font(.system(size: 21, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(warn ? Color(red: 1.0, green: 0.62, blue: 0.35) : .white)
                .shadow(color: tint.opacity(warn ? 0 : 0.4), radius: 8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(.white.opacity(0.05), in: RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Tips

struct TipsCard: View {
    @EnvironmentObject var model: GlucoseModel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(Color(red: 0.95, green: 0.78, blue: 0.4))
                Text("Tips")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                Spacer()
            }

            if model.tips.isEmpty {
                Text("No tips right now — steady is good.")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.5))
            } else {
                ForEach(model.tips) { tip in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: tip.symbol)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(tint(for: tip.kind))
                            .frame(width: 20)
                            .padding(.top, 1)
                        Text(tip.text)
                            .font(.footnote)
                            .foregroundStyle(.white.opacity(0.72))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }

            Text("General information, not medical advice. For persistent lows, highs, or symptoms, talk to a clinician.")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.3))
                .padding(.top, 2)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    private func tint(for kind: GlucoseTip.Kind) -> Color {
        switch kind {
        case .low: return CluecoseTheme.glow(for: .low)
        case .high: return CluecoseTheme.glow(for: .high)
        case .variability, .range, .storage: return Color(red: 1.0, green: 0.62, blue: 0.35)
        case .good: return CluecoseTheme.glow(for: .inRange)
        }
    }
}
