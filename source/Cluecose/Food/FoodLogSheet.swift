//
//  FoodLogSheet.swift
//  Cluecose
//
//  The add/edit meal sheet (presented like the SettingsSheet). A segmented
//  control switches between Type (typed description) and Photo (up to 5
//  photos via the system PhotosPicker — no permission prompt needed).
//
//  "Estimate" sends the description (and photos, in photo mode) to the
//  configured model endpoint and fills the eight editable nutrient rows plus
//  the model's own description and any citations (each a tappable Link).
//  If the endpoint fails, an "enter values by hand" fallback banner shows and
//  every row stays editable — manual entry always works.
//
//  Edit mode prefills from the existing entry; deleting a photo removes its
//  file; saving an edit flips isEstimated=false. A share action writes the
//  entry as a JSON file for the system share sheet.
//

import SwiftUI
import PhotosUI
import CluecoseCore

struct FoodLogSheet: View {
    @ObservedObject var model: FoodLogModel
    /// The entry being edited, or nil for a new meal.
    var existing: FoodEntry?
    @Environment(\.dismiss) private var dismiss

    // MARK: Mode

    private enum Mode: String, CaseIterable, Identifiable {
        case type = "Type"
        case photo = "Photo"
        var id: String { rawValue }
    }
    @State private var mode: Mode = .type

    // MARK: Fields

    @State private var descriptionText = ""
    @State private var servingText = ""
    @State private var contextText = ""

    // Photos (photo mode)
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var photos: [Data] = []
    @State private var loadingPhotos = false

    // Nutrient rows (editable; prefilled from an estimate or an edit)
    @State private var kcalText = ""
    @State private var proteinText = ""
    @State private var carbsText = ""
    @State private var fatText = ""
    @State private var satFatText = ""
    @State private var sodiumText = ""
    @State private var caffeineText = ""
    @State private var alcoholText = ""

    // Estimate results
    @State private var modelDescription = ""
    @State private var citations: [SourceCitation] = []
    @State private var hasEstimated = false
    @State private var showFallback = false
    @State private var errorMessage: String?
    @State private var isEstimating = false

    // Share (nice-to-have): a transient JSON file for the system share sheet.
    @State private var shareFileURL: URL?

    private var configEnabled: Bool { FoodLLMConfig.load().isEnabled }

    // MARK: Body

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Entry type", selection: $mode) {
                        ForEach(Mode.allCases) { m in
                            Text(m.rawValue).tag(m)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("About this meal") {
                    TextField("What did you eat?", text: $descriptionText)
                        .autocorrectionDisabled(false)
                    TextField("Serving / portion (optional)", text: $servingText)
                        .autocorrectionDisabled(false)
                    TextField("Context (optional)", text: $contextText)
                        .autocorrectionDisabled(false)
                    Text("What the camera can't know: portion, brand, preparation, hidden ingredients.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if mode == .photo {
                    photosSection
                }

                estimateSection

                Section("Nutrition (editable)") {
                    nutrientRow("Calories", "kcal", $kcalText)
                    nutrientRow("Protein", "g", $proteinText)
                    nutrientRow("Carbs", "g", $carbsText)
                    nutrientRow("Fat", "g", $fatText)
                    nutrientRow("Saturated fat", "g", $satFatText)
                    nutrientRow("Sodium", "mg", $sodiumText)
                    nutrientRow("Caffeine", "mg", $caffeineText)
                    nutrientRow("Alcohol", "g", $alcoholText)
                }

                if !modelDescription.isEmpty {
                    Section("Identified") {
                        Text(modelDescription)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

                if !citations.isEmpty {
                    Section("Sources") {
                        ForEach(Array(citations.enumerated()), id: \.offset) { _, c in
                            Link(
                                destination: URL(string: c.url) ?? URL(string: "https://www.example.com")!
                            ) {
                                HStack {
                                    Image(systemName: "link")
                                        .font(.caption)
                                    Text(c.title.isEmpty ? c.url : c.title)
                                        .font(.footnote)
                                    Spacer()
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle(existing == nil ? "Add meal" : "Edit meal")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                if let existing {
                    ToolbarItem(placement: .confirmationAction) {
                        ShareLink(item: shareURL(for: existing)) {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                }
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 8) {
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.caption)
                            .foregroundStyle(.orange)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                    HStack(spacing: 10) {
                        if existing != nil {
                            Button(role: .destructive) {
                                deleteExisting()
                            } label: {
                                Label("Delete", systemImage: "trash")
                                    .frame(minWidth: 80)
                            }
                            .buttonStyle(.bordered)
                        }
                        saveButton
                    }
                }
                .padding()
                .background(.ultraThinMaterial)
            }
        }
        .preferredColorScheme(.dark)
        .onAppear(perform: prefill)
        .onChange(of: photoItems) { _, items in
            Task { await loadPhotos(items) }
        }
    }

    // MARK: Photos section

    @ViewBuilder
    private var photosSection: some View {
        Section("Photos (up to 5)") {
            PhotosPicker(selection: $photoItems, maxSelectionCount: 5, matching: .images) {
                Label(photoItems.isEmpty ? "Add photos" : "Replace photos",
                      systemImage: "photo.on.rectangle.angled")
            }
            if loadingPhotos {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Loading…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            if !photos.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(photos.enumerated()), id: \.offset) { index, data in
                            photoCell(data: data) {
                                removePhoto(at: index)
                            }
                        }
                    }
                }
            }
        }
    }

    private func photoCell(data: Data, onRemove: @escaping () -> Void) -> some View {
        ZStack(alignment: .topTrailing) {
            if let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            } else {
                RoundedRectangle(cornerRadius: 10)
                    .fill(.white.opacity(0.1))
                    .frame(width: 64, height: 64)
            }
            Button(action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 16))
                    .foregroundStyle(.white, .black.opacity(0.6))
            }
            .buttonStyle(.plain)
            .padding(3)
        }
    }

    private func loadPhotos(_ items: [PhotosPickerItem]) async {
        guard !items.isEmpty else {
            photos = []
            return
        }
        loadingPhotos = true
        var loaded: [Data] = []
        for item in items {
            if let data = try? await item.loadTransferable(type: Data.self) {
                loaded.append(data)
            }
        }
        photos = loaded
        loadingPhotos = false
    }

    /// Remove a photo from the sheet's working set. (The STORED file for a
    /// pre-existing entry is removed when the sheet saves the edit —
    /// `FoodLogModel.saveEntry` rewrites the photo set.)
    private func removePhoto(at index: Int) {
        guard photos.indices.contains(index) else { return }
        photos.remove(at: index)
    }

    // MARK: Estimate section

    @ViewBuilder
    private var estimateSection: some View {
        Section {
            if showFallback {
                Label("Enter values by hand — the estimate failed, but you can still log this meal.",
                      systemImage: "pencil.circle")
                    .font(.footnote)
                    .foregroundStyle(.orange)
            }
            if isEstimating {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Estimating…")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            Button {
                Task { await runEstimate() }
            } label: {
                Label(existing == nil && !hasEstimated ? "Estimate" : "Reanalyze",
                      systemImage: "wand.and.stars")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isEstimating || !configEnabled)
            if !configEnabled {
                Text("Set an API key in Settings › Nutrition analysis to enable estimation.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private func runEstimate() async {
        errorMessage = nil
        isEstimating = true
        defer { isEstimating = false }
        do {
            let request = NutritionPrompt.Request(
                description: descriptionText.isEmpty ? "meal photo" : descriptionText,
                serving: servingText.isEmpty ? nil : servingText,
                context: contextText.isEmpty ? nil : contextText,
                mode: mode == .photo ? .photo : .manual,
                photoCount: mode == .photo ? photos.count : 0
            )
            let photosToSend = mode == .photo ? photos : []
            let estimate = try await model.runEstimate(request: request, photos: photosToSend)
            // Fill the rows.
            kcalText = number(estimate.caloriesKcal)
            proteinText = number(estimate.proteinG)
            carbsText = number(estimate.carbsG)
            fatText = number(estimate.fatG)
            satFatText = number(estimate.saturatedFatG)
            sodiumText = number(estimate.sodiumMg)
            caffeineText = number(estimate.caffeineMg)
            alcoholText = number(estimate.alcoholG)
            if let d = estimate.description, !d.isEmpty {
                modelDescription = d
                // For a photo entry with no user text, use the model's name.
                if descriptionText.isEmpty { descriptionText = d }
            }
            citations = estimate.citations
            hasEstimated = true
            showFallback = false
        } catch {
            errorMessage = error.localizedDescription
            showFallback = true
        }
    }

    private func number(_ v: Double?) -> String {
        v.map { String(format: "%.1f", $0) } ?? ""
    }

    // MARK: Save

    private var saveButton: some View {
        Button {
            save()
        } label: {
            Text("Save")
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 4)
        }
        .buttonStyle(.borderedProminent)
        .disabled(descriptionText.isEmpty && (mode == .type))
    }

    private func save() {
        let values = FoodLogModel.MealValues(
            description: descriptionText,
            serving: servingText.isEmpty ? nil : servingText,
            context: contextText.isEmpty ? nil : contextText,
            source: mode == .photo ? .photo : .manual,
            caloriesKcal: parse(kcalText),
            proteinG: parse(proteinText),
            carbsG: parse(carbsText),
            fatG: parse(fatText),
            saturatedFatG: parse(satFatText),
            sodiumMg: parse(sodiumText),
            caffeineMg: parse(caffeineText),
            alcoholG: parse(alcoholText),
            citations: citations
        )
        // New entries keep isEstimated=true only when they were estimated;
        // edits always flip to false (the values are now the user's).
        let isEstimated: Bool
        if let existing {
            _ = existing
            isEstimated = false
        } else {
            isEstimated = hasEstimated
        }
        let photosToSend = mode == .photo ? photos : []
        model.saveEntry(existing: existing,
                        values: values,
                        isEstimated: isEstimated,
                        photos: photosToSend)
        dismiss()
    }

    private func parse(_ text: String) -> Double? {
        Double(text.trimmingCharacters(in: .whitespaces))
    }

    private func deleteExisting() {
        if let existing {
            model.delete(existing)
            dismiss()
        }
    }

    // MARK: Share (nice-to-have)

    /// Write the entry to a transient JSON file and hand the URL to the
    /// system share sheet.
    private func shareURL(for entry: FoodEntry) -> URL {
        if let url = shareFileURL { return url }
        let dir = FileManager.default.temporaryDirectory
        let url = dir.appendingPathComponent("\(entry.id).json")
        if let data = try? JSONEncoder().encode(entry) {
            try? data.write(to: url)
            shareFileURL = url
        }
        return url
    }

    // MARK: Prefill (edit mode)

    private func prefill() {
        if let e = existing {
            mode = e.source == .photo ? .photo : .type
            descriptionText = e.description
            servingText = e.serving ?? ""
            contextText = e.context ?? ""
            kcalText = number(e.caloriesKcal)
            proteinText = number(e.proteinG)
            carbsText = number(e.carbsG)
            fatText = number(e.fatG)
            satFatText = number(e.saturatedFatG)
            sodiumText = number(e.sodiumMg)
            caffeineText = number(e.caffeineMg)
            alcoholText = number(e.alcoholG)
            citations = e.citations ?? []
            if !citations.isEmpty { hasEstimated = true }
            if e.source == .photo {
                photos = model.store.photoData(for: e)
            }
        }
    }
}

// MARK: - Reusable nutrient row

private func nutrientRow(_ title: String, _ unit: String, _ text: Binding<String>) -> some View {
    HStack(spacing: 8) {
        Text(title)
            .font(.footnote)
            .foregroundStyle(.primary.opacity(0.8))
        Spacer()
        TextField("–", text: text)
            .keyboardType(.decimalPad)
            .multilineTextAlignment(.trailing)
            .frame(width: 96)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 10))
        Text(unit)
            .font(.footnote)
            .foregroundStyle(.secondary)
            .frame(width: 34, alignment: .leading)
    }
}
