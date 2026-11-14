//
//  FoodLogCard.swift
//  Cluecose
//
//  The home-screen food card: the day's totals (kcal, protein, carbs, fat,
//  sodium, caffeine in the main line; alcohol + saturated fat as a secondary
//  line), the day's entries (description, a small nutrient line, a photo
//  thumb when there is one, and the per-meal insight in a colored callout
//  when present — spike = orange, reactive low = blue, nothing when nil),
//  and the "Add meal" button.
//
//  A tap on an entry opens the add/edit sheet in edit mode; a tap on the
//  sheet's delete removes it. Styled with the shared glass theme.
//

import SwiftUI
import CluecoseCore

struct FoodLogCard: View {
    @ObservedObject var model: FoodLogModel
    /// Opens the add sheet (new entry) — set by the parent (ContentView).
    var onAdd: () -> Void
    /// Opens the add/edit sheet for an existing entry — set by the parent.
    var onEdit: (FoodEntry) -> Void

    /// In-app meal search box: filters the day's entries by description,
    /// serving or context. Small and dependency-free (no Spotlight / CSSearchableItem
    /// plumbing needed), so the "find what I ate" case is covered on-device.
    @State private var query: String = ""

    /// The day's entries, narrowed by the current search query (case- and
    /// diacritic-insensitive). Empty query -> the full day list.
    private var visibleEntries: [FoodEntry] {
        let entries = model.dayEntries
        let q = query
        guard !q.isEmpty else { return entries }
        let needle = q.lowercased()
        return entries.filter { e in
            e.description.lowercased().contains(needle)
            || (e.serving ?? "").lowercased().contains(needle)
            || (e.context ?? "").lowercased().contains(needle)
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            totals
            searchBox
            if model.dayEntries.isEmpty {
                emptyState
            } else if visibleEntries.isEmpty {
                noMatchState
            } else {
                ForEach(visibleEntries) { entry in
                    entryRow(entry)
                }
            }
            addButton
        }
        .glassPanel()
    }

    // MARK: Search box

    private var searchBox: some View {
        HStack(spacing: 6) {
            Image(systemName: "magnifyingglass")
                .font(.caption)
                .foregroundStyle(.white.opacity(0.4))
            TextField("Search meals", text: $query)
                .font(.footnote)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.4))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
    }

    private var noMatchState: some View {
        Text("No meals match “\(query)” today.")
            .font(.footnote)
            .foregroundStyle(.white.opacity(0.5))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
    }

    // MARK: Header

    private var header: some View {
        HStack {
            Label("Food", systemImage: "fork.knife")
                .font(.system(size: 15, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            Spacer()
            Text(model.selectedDay, format: .dateTime.weekday(.abbreviated).day())
                .font(.caption)
                .foregroundStyle(.white.opacity(0.5))
        }
    }

    // MARK: Totals

    private var totals: some View {
        let t = model.todayTotals
        return VStack(spacing: 6) {
            if t.entryCount == 0 {
                Text("No meals logged today.")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.5))
            } else {
                // Main line: kcal, protein, carbs, fat, sodium, caffeine.
                HStack(spacing: 10) {
                    totalPill("kcal", value: t.kcal, unit: "")
                    totalPill("P", value: t.proteinG, unit: "g")
                    totalPill("C", value: t.carbsG, unit: "g")
                    totalPill("F", value: t.fatG, unit: "g")
                    totalPill("Na", value: t.sodiumMg, unit: "mg")
                    totalPill("Caf", value: t.caffeineMg, unit: "mg")
                }
                // Secondary line: alcohol + saturated fat.
                HStack(spacing: 10) {
                    if let alc = t.alcoholG, alc > 0 {
                        totalPill("Alc", value: alc, unit: "g")
                    }
                    if let sf = t.saturatedFatG, sf > 0 {
                        totalPill("Sat", value: sf, unit: "g")
                    }
                    Spacer()
                }
            }
        }
    }

    private func totalPill(_ label: String, value: Double?, unit: String) -> some View {
        let text = value.map { String(format: "%.0f", $0) } ?? "–"
        return HStack(spacing: 3) {
            Text(label)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.white.opacity(0.55))
            Text(text + unit)
                .font(.caption2.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.white.opacity(0.08), in: Capsule())
    }

    // MARK: Empty state

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "fork.knife.circle")
                .font(.system(size: 28))
                .foregroundStyle(.white.opacity(0.4))
            Text("Log what you eat to see it here.")
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.5))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
    }

    // MARK: Entry row

    private func entryRow(_ entry: FoodEntry) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top, spacing: 10) {
                photoThumb(entry)
                VStack(alignment: .leading, spacing: 3) {
                    Text(entry.description)
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                    if !entry.photoFileNames.isEmpty {
                        Text("\(entry.photoFileNames.count) photo\(entry.photoFileNames.count == 1 ? "" : "s")")
                            .font(.caption2)
                            .foregroundStyle(.white.opacity(0.4))
                    }
                    nutrientLine(entry)
                }
                Spacer(minLength: 0)
                Text(entry.date, format: .dateTime.hour().minute())
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.4))
            }
            if let insight = entry.insight {
                insightCallout(insight)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onEdit(entry) }
    }

    @ViewBuilder
    private func photoThumb(_ entry: FoodEntry) -> some View {
        if let data = model.store.photoData(for: entry).first,
           let image = UIImage(data: data) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 40, height: 40)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }

    /// A compact single line of the entry's nutrient values (skips nils).
    private func nutrientLine(_ entry: FoodEntry) -> some View {
        var parts: [String] = []
        if let v = entry.caloriesKcal { parts.append(String(format: "%.0f kcal", v)) }
        if let v = entry.proteinG { parts.append(String(format: "P %.0f", v)) }
        if let v = entry.carbsG { parts.append(String(format: "C %.0f", v)) }
        if let v = entry.fatG { parts.append(String(format: "F %.0f", v)) }
        if let v = entry.sodiumMg { parts.append(String(format: "Na %.0f", v)) }
        if let v = entry.caffeineMg { parts.append(String(format: "Caf %.0f", v)) }
        return Text(parts.joined(separator: "  ·  "))
            .font(.caption2)
            .foregroundStyle(.white.opacity(0.5))
            .lineLimit(1)
    }

    /// The per-meal insight callout: orange for a spike, blue for a reactive
    /// low. Nothing when there is no insight (the engine returned nil).
    private func insightCallout(_ insight: MealInsight) -> some View {
        let color: Color = insight.kind == .spike
            ? Color(red: 1.0, green: 0.62, blue: 0.35)
            : Color(red: 0.45, green: 0.78, blue: 1.0)
        let symbol = insight.kind == .spike ? "arrow.up.right" : "arrow.down"
        return HStack(alignment: .top, spacing: 7) {
            Image(systemName: symbol)
                .font(.caption2.weight(.bold))
                .foregroundStyle(color)
            Text(insight.text)
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.75))
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.3), lineWidth: 1))
    }

    // MARK: Add button

    private var addButton: some View {
        Button(action: onAdd) {
            Label("Add meal", systemImage: "plus.circle.fill")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.black)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color(red: 0.35, green: 0.95, blue: 0.65), in: Capsule())
        }
        .buttonStyle(.plain)
    }
}
