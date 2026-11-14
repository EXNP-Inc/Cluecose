//
//  PatternsCard.swift
//  Cluecose
//
//  The "Learn" surface: 7-day averages from the shared `PatternsEngine`
//  (kcal, protein, carbs, fat, sodium, caffeine, alcohol, saturated fat, and
//  the saturated-fat share of total fat) plus the deterministic coaching
//  follow-up questions. When HealthKit sleep data is available, the
//  nutrition-sleep association lines are appended at the bottom — always
//  phrased as an association, never causation.
//

import SwiftUI
import CluecoseCore

struct PatternsCard: View {
    @ObservedObject var model: FoodLogModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Learn", systemImage: "chart.line.uptrend.xyaxis")
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)

            if model.patterns.entryCount == 0 {
                Text("Log meals for a few days and 7-day patterns appear here.")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.5))
            } else {
                averagesGrid
                if !model.coaching.isEmpty {
                    coachingSection
                }
            }

            if !model.sleepInsights.isEmpty {
                sleepSection
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassPanel()
    }

    // MARK: 7-day averages

    private var averagesGrid: some View {
        let p = model.patterns
        return VStack(spacing: 6) {
            gridRow(
                average("Calories", p.avgKcalPerDay, "kcal/day"),
                average("Protein", p.avgProteinPerDay, "g/day"),
                average("Carbs", p.avgCarbsPerDay, "g/day")
            )
            gridRow(
                average("Fat", p.avgFatPerDay, "g/day"),
                average("Sodium", p.avgSodiumPerDay, "mg/day"),
                average("Caffeine", p.avgCaffeinePerDay, "mg/day")
            )
            gridRow(
                average("Alcohol", p.avgAlcoholPerDay, "g/day"),
                average("Sat. fat", p.avgSaturatedFatPerDay, "g/day"),
                satFatShare
            )
        }
    }

    private func gridRow(_ cells: GridCell...) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(cells.enumerated()), id: \.offset) { i, cell in
                if i > 0 {
                    Rectangle().fill(.white.opacity(0.1))
                        .frame(width: 1)
                }
                cell.view.frame(maxWidth: .infinity)
            }
        }
    }

    private struct GridCell {
        let view: AnyView
        init(_ view: AnyView) { self.view = view }
    }

    private func average(_ title: String, _ value: Double?, _ unit: String) -> GridCell {
        let text: String
        if let v = value {
            text = String(format: "%.0f", v)
        } else {
            text = "–"
        }
        let view = VStack(spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.5))
            Text(text)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(.white)
            Text(unit)
                .font(.system(size: 9))
                .foregroundStyle(.white.opacity(0.4))
        }
        return GridCell(AnyView(view))
    }

    private var satFatShare: GridCell {
        let p = model.patterns
        let text = p.saturatedFatToFatRatio.map { String(format: "%.0f%%", $0 * 100) } ?? "–"
        let view = VStack(spacing: 2) {
            Text("Sat. of fat")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.5))
            Text(text)
                .font(.system(size: 15, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(.white)
            Text("share")
                .font(.system(size: 9))
                .foregroundStyle(.white.opacity(0.4))
        }
        return GridCell(AnyView(view))
    }

    // MARK: Coaching

    private var coachingSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Coaching")
                .font(.caption.weight(.medium))
                .foregroundStyle(.white.opacity(0.6))
            ForEach(Array(model.coaching.enumerated()), id: \.offset) { _, q in
                HStack(alignment: .top, spacing: 7) {
                    Image(systemName: "questionmark.circle")
                        .font(.caption2)
                        .foregroundStyle(Color(red: 0.30, green: 0.92, blue: 0.60))
                    Text(q)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
    }

    // MARK: Nutrition-sleep (association only)

    private var sleepSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Food & sleep (last 7 nights)", systemImage: "moon.zzz.fill")
                .font(.caption.weight(.medium))
                .foregroundStyle(Color(red: 0.45, green: 0.78, blue: 1.0))
            ForEach(Array(model.sleepInsights.enumerated()), id: \.offset) { _, s in
                Text(s)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.8))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Text("Association, not causation.")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.4))
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 0.45, green: 0.78, blue: 1.0).opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
    }
}
