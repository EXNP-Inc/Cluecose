//
//  WatchHomeView.swift
//  Cluecose Watch
//

import SwiftUI
import CluecoseCore

struct WatchHomeView: View {
    @EnvironmentObject var model: WatchGlucoseModel

    private var tint: Color {
        guard let latest = model.latest else { return .white.opacity(0.4) }
        return switch RangeCategory(mgdl: latest.mgdl) {
        case .low: Color(red: 0.45, green: 0.78, blue: 1.0)
        case .inRange: Color(red: 0.30, green: 0.92, blue: 0.60)
        case .high: Color(red: 1.0, green: 0.62, blue: 0.35)
        }
    }

    var body: some View {
        ZStack {
            LinearGradient(
                stops: [
                    .init(color: Color(red: 0.02, green: 0.09, blue: 0.13), location: 0),
                    .init(color: Color(red: 0.05, green: 0.24, blue: 0.26), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 10) {
                if let latest = model.latest {
                    hero(latest)
                    metricsRow
                } else {
                    statusBlock
                }
                Text(model.status)
                    .font(.system(size: 9, weight: .medium))
                    .foregroundStyle(.white.opacity(0.5))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }
            .padding(.horizontal, 12)
        }
    }

    private func hero(_ latest: GlucoseSample) -> some View {
        HStack(spacing: 8) {
            Image(systemName: model.trend.symbol)
                .font(.system(size: 18, weight: .bold))
                .foregroundStyle(tint)
            // Tap the number to cycle mg/dL <-> mmol/L.
            Text(model.unit.format(fromMgdl: latest.mgdl))
                .font(.system(size: 52, weight: .heavy, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(.white)
                .shadow(color: tint.opacity(0.6), radius: 10)
                .contentShape(Rectangle())
                .onTapGesture { withAnimation(.snappy) { model.toggleUnit() } }
            Spacer(minLength: 0)
            VStack(alignment: .trailing, spacing: 2) {
                Text(model.unit.rawValue)
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.5))
                freshnessPill
            }
        }
    }

    private var freshnessPill: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(model.isFresh ? Color(red: 0.30, green: 0.92, blue: 0.60) : .orange)
                .frame(width: 6, height: 6)
            Text(model.isFresh ? "Live" : "Stale")
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(model.isFresh ? .white.opacity(0.8) : .orange)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 3)
        .background(.white.opacity(0.1), in: Capsule())
    }

    private var metricsRow: some View {
        HStack(spacing: 8) {
            if let tir = model.tir24h {
                VStack(spacing: 3) {
                    Text("TIR 24h")
                        .font(.system(size: 8, weight: .medium))
                        .foregroundStyle(.white.opacity(0.5))
                    Text("\(Int((tir * 100).rounded()))%")
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(CluecoseTint.green)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
            }
            VStack(spacing: 3) {
                HStack(spacing: 3) {
                    Image(systemName: model.burnMode.symbol)
                        .font(.system(size: 8, weight: .bold))
                    Text("State")
                        .font(.system(size: 8, weight: .medium))
                }
                .foregroundStyle(.white.opacity(0.5))
                Text(model.burnMode.label)
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(burnTint)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
        }
    }

    private var burnTint: Color {
        switch model.burnMode {
        case .storing: Color(red: 1.0, green: 0.62, blue: 0.35)
        case .balanced: Color(red: 0.30, green: 0.92, blue: 0.60)
        case .burning: Color(red: 0.45, green: 0.78, blue: 1.0)
        }
    }

    private var statusBlock: some View {
        VStack(spacing: 8) {
            Image(systemName: "applewatch.radiowaves.left.and.right")
                .font(.system(size: 26))
                .foregroundStyle(.white.opacity(0.5))
            Text("Waiting for glucose")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(.white.opacity(0.8))
        }
    }
}

enum CluecoseTint {
    static let green = Color(red: 0.30, green: 0.92, blue: 0.60)
}

#Preview {
    WatchHomeView()
        .environmentObject(WatchGlucoseModel())
}
