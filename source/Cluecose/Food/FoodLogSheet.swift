//
//  FoodLogSheet.swift
//  Cluecose
//
//  The add/edit meal sheet (presented like the SettingsSheet). A segmented
//  control switches between Type (typed description) and Photo (up to 5
//  photos via the camera or the system PhotosPicker).
//
//  Saving ALWAYS analyzes: the description (+ photos, in photo mode) is sent
//  to the model endpoint (or a quick local estimate if the endpoint is
//  unavailable) and the eight nutrient rows are filled + scaled by the
//  portion slider. There is no separate "Estimate"/"Reanalyze" button —
//  any change to the meal (text, serving, context, source, portion) re-runs
//  the analysis on save, and hand-entered rows always win.
//
//  A meal with no data at all (no nutrient values AND no photos) cannot be
//  saved: the Save button is disabled until the entry has something in it.
//
//  Edit mode prefills from the existing entry; deleting a photo removes its
//  file on save. A share action writes the entry as a JSON file for the
//  system share sheet.
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

    // Photos (photo mode): library picker items + the actual data.
    @State private var photoItems: [PhotosPickerItem] = []
    @State private var photos: [Data] = []
    @State private var loadingPhotos = false
    // Camera capture (a freshly shot photo is prepended to `photos`).
    @State private var showCamera = false

    /// Portion slider: how much of the photographed/typed portion was eaten.
    /// 0.1…4.0 in 0.1 steps; default 1.0 (the whole portion). The estimate
    /// describes the full portion, and the stored values are estimate × this.
    @State private var portion: Double = 1.0

    // Nutrient rows (editable; the user's values win on save)
    @State private var kcalText = ""
    @State private var proteinText = ""
    @State private var carbsText = ""
    @State private var fatText = ""
    @State private var satFatText = ""
    @State private var sodiumText = ""
    @State private var caffeineText = ""
    @State private var alcoholText = ""
    /// The text the rows had when the sheet opened (edit mode: the stored
    /// values, unscaled; new meal: empty). A row whose text is UNCHANGED is
    /// not a hand edit — it is passed as nil so the analysis/estimate keeps
    /// ownership of it. (Re-saving an untouched entry must round-trip the
    /// stored values exactly, including the portion scaling.)
    @State private var rowOrigins = [String](repeating: "", count: 8)

    // Estimate results (informational — filling happens on save)
    @State private var modelDescription = ""
    @State private var citations: [SourceCitation] = []
    @State private var errorMessage: String?
    @State private var isSaving = false

    // Share (nice-to-have): a transient JSON file for the system share sheet.
    @State private var shareFileURL: URL?

    private var configEnabled: Bool { FoodLLMConfig.load().isEnabled }

    /// True when the entry carries data: a typed description (type mode),
    /// any photo (photo mode), or at least one nutrient row. Save is disabled
    /// until this is true — a meal can't be logged with nothing in it.
    private var hasAnyData: Bool {
        if !descriptionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
        if !photos.isEmpty { return true }
        let rows = [kcalText, proteinText, carbsText, fatText, satFatText,
                    sodiumText, caffeineText, alcoholText]
        return rows.contains { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
    }

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

                portionSection

                Section("Nutrition (editable — your values win)") {
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
                    if !hasAnyData {
                        Text("Add a description, a photo, or at least one nutrition value to save this meal.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
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
                        Button {
                            Task { await save() }
                        } label: {
                            if isSaving {
                                HStack(spacing: 8) {
                                    ProgressView()
                                    Text("Analyzing…")
                                        .frame(maxWidth: .infinity)
                                }
                            } else {
                                Text("Save")
                                    .font(.body.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 4)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(!hasAnyData || isSaving)
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
        .fullScreenCover(isPresented: $showCamera) {
            CameraCapture { data in
                guard photos.count < 5 else {
                    errorMessage = "Already at 5 photos — remove one to add this one."
                    return
                }
                photos.insert(data, at: 0)
            }
        }
    }

    // MARK: Photos section (camera + library)

    @ViewBuilder
    private var photosSection: some View {
        Section("Photos (up to 5)") {
            HStack(spacing: 12) {
                Button {
                    showCamera = true
                } label: {
                    Label("Take photo", systemImage: "camera.fill")
                        .font(.footnote.weight(.semibold))
                }
                .buttonStyle(.bordered)

                PhotosPicker(selection: $photoItems, maxSelectionCount: 5, matching: .images) {
                    Label(photoItems.isEmpty ? "Choose from library" : "Replace from library",
                          systemImage: "photo.on.rectangle.angled")
                        .font(.footnote.weight(.semibold))
                }
                .buttonStyle(.bordered)
            }
            .frame(maxWidth: .infinity)
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
                    .padding(.vertical, 2)
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

    // MARK: Portion section

    /// "How much of this did you eat?" — 0.1× to 4.0× in 0.1 steps, default
    /// 1.0. The estimate describes the full photographed/typed portion; the
    /// stored values are the estimate × the multiplier.
    @ViewBuilder
    private var portionSection: some View {
        Section {
            VStack(spacing: 6) {
                HStack {
                    Text("How much did you eat?")
                        .font(.footnote)
                        .foregroundStyle(.primary.opacity(0.8))
                    Spacer()
                    Text(String(format: "%.1f×", portion))
                        .font(.footnote.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                }
                Slider(value: $portion, in: 0.1...4.0, step: 0.1)
                HStack {
                    Text("¼")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("full portion")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text("4×")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: Save

    /// Save always analyzes: hand the current fields to the model, which runs
    /// the estimate (or local fallback), scales by the portion, keeps the
    /// user's explicit rows, and re-arms the blood-sugar insight.
    private func save() async {
        errorMessage = nil
        isSaving = true
        defer { isSaving = false }

        let values = FoodLogModel.MealValues(
            description: descriptionText,
            serving: servingText.isEmpty ? nil : servingText,
            context: contextText.isEmpty ? nil : contextText,
            source: mode == .photo ? .photo : .manual,
            // nil = "user didn't touch this row" → the analyzed value stays.
            caloriesKcal: rowValue(kcalText, origin: rowOrigins[0]),
            proteinG: rowValue(proteinText, origin: rowOrigins[1]),
            carbsG: rowValue(carbsText, origin: rowOrigins[2]),
            fatG: rowValue(fatText, origin: rowOrigins[3]),
            saturatedFatG: rowValue(satFatText, origin: rowOrigins[4]),
            sodiumMg: rowValue(sodiumText, origin: rowOrigins[5]),
            caffeineMg: rowValue(caffeineText, origin: rowOrigins[6]),
            alcoholG: rowValue(alcoholText, origin: rowOrigins[7]),
            citations: citations,
            portionMultiplier: portion
        )
        let photosToSend = mode == .photo ? photos : []
        let saved = await model.saveEntry(existing: existing,
                                          values: values,
                                          photos: photosToSend)
        // Surface the model's own description for photo entries it named.
        if saved.description != descriptionText {
            modelDescription = saved.description
        }
        // The analysis failed (endpoint unusable): the meal was saved with
        // BLANK values. Keep the sheet open so the user can fill the rows in
        // by hand right now — a re-save with the description unchanged will
        // not re-run the analysis, so the hand-entered values stick.
        let noValues = [saved.caloriesKcal, saved.proteinG, saved.carbsG, saved.fatG,
                        saved.saturatedFatG, saved.sodiumMg, saved.caffeineMg, saved.alcoholG]
            .allSatisfy { $0 == nil }
        if noValues && !photosToSend.isEmpty {
            errorMessage = "Saved — but the model couldn't analyze this photo. Enter the values below to fill it in."
            return
        }
        if noValues {
            errorMessage = "Saved — but the model endpoint is unavailable, so no nutrition was analyzed. Enter the values below, or tap Save again later to retry."
            return
        }
        dismiss()
    }

    private func parse(_ text: String) -> Double? {
        Double(text.trimmingCharacters(in: .whitespaces))
    }

    /// The parsed value of a nutrient row — or nil when the user left it
    /// exactly as pre-filled (so the saved/analyzed value owns it). A row
    /// that was pre-filled empty and is now filled is a hand edit; a
    /// pre-filled row set back to its original text is not.
    private func rowValue(_ text: String, origin: String) -> Double? {
        text.trimmingCharacters(in: .whitespaces) == origin
            ? nil
            : parse(text)
    }

    /// Format a stored value back into the row's text field. Uses a
    /// fixed-locale, group-free format so `parse` always reads it back.
    private func number(_ v: Double?) -> String {
        guard let v, v != 0 else { return "" }
        return v.formatted(.number.precision(.fractionLength(0...1))
            .grouping(.never)
            .locale(Locale(identifier: "en_US_POSIX")))
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
            portion = e.portionMultiplier
            // Show the UNSCALED base in the rows: the stored values are
            // base × portion, and the slider carries the portion — so the
            // rows are what the full portion is worth.
            let f: (Double?) -> Double? = { v in v.map { $0 / (e.portionMultiplier == 0 ? 1 : e.portionMultiplier) } }
            kcalText = number(f(e.caloriesKcal))
            proteinText = number(f(e.proteinG))
            carbsText = number(f(e.carbsG))
            fatText = number(f(e.fatG))
            satFatText = number(f(e.saturatedFatG))
            sodiumText = number(f(e.sodiumMg))
            caffeineText = number(f(e.caffeineMg))
            alcoholText = number(f(e.alcoholG))
            // Remember what "untouched" looks like, per row.
            rowOrigins = [kcalText, proteinText, carbsText, fatText,
                          satFatText, sodiumText, caffeineText, alcoholText]
            citations = e.citations ?? []
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

// MARK: - Camera capture (AVFoundation wrapper)

/// A minimal camera view that captures a single photo and hands its JPEG
/// data to `onCapture`. Presented in a `fullScreenCover` from the sheet.
/// (The PhotosPicker covers the library; this covers "take the photo now".)
struct CameraCapture: UIViewControllerRepresentable {
    /// Called with the JPEG data of the captured photo.
    var onCapture: (Data) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> CameraControllerVC {
        let vc = CameraControllerVC()
        vc.onCapture = { data in
            onCapture(data)
            dismiss()
        }
        vc.onCancel = { dismiss() }
        return vc
    }

    func updateUIViewController(_ uiViewController: CameraControllerVC, context: Context) {}
}

/// AVCaptureSession controller: back camera, photo output, torch-capable.
/// The session is started/stopped with the view lifecycle. If the camera
/// is unavailable (rare on a real device; never on the simulator), a message
/// is shown and the cancel button still works.
final class CameraControllerVC: UIViewController {
    var onCapture: ((Data) -> Void)?
    var onCancel: (() -> Void)?

    private let session = AVCaptureSession()
    private let photoOutput = AVCapturePhotoOutput()
    /// One serial queue for all session work (configure + stop).
    private let sessionQueue = DispatchQueue(label: "cluecose.camera")
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var configured = false
    private var captionLabel: UILabel?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        configureUI()
        configureSession()
    }

    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    private func configureUI() {
        let bar = UIView(frame: CGRect(x: 0, y: view.bounds.height - 96, width: view.bounds.width, height: 96))
        bar.backgroundColor = UIColor.black.withAlphaComponent(0.35)
        view.addSubview(bar)

        let cancel = UIButton(type: .system)
        cancel.setTitle("Cancel", for: .normal)
        cancel.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        cancel.setTitleColor(.white, for: .normal)
        cancel.translatesAutoresizingMaskIntoConstraints = false
        cancel.addTarget(self, action: #selector(tappedCancel), for: .touchUpInside)
        bar.addSubview(cancel)

        let shutter = UIButton(type: .system)
        shutter.setImage(UIImage(systemName: "circle.fill"), for: .normal)
        shutter.tintColor = .white
        shutter.translatesAutoresizingMaskIntoConstraints = false
        shutter.addTarget(self, action: #selector(tappedShutter), for: .touchUpInside)
        bar.addSubview(shutter)

        let caption = UILabel()
        caption.text = "Point at your meal, then tap the shutter."
        caption.textColor = .white.withAlphaComponent(0.8)
        caption.font = .systemFont(ofSize: 13)
        caption.textAlignment = .center
        caption.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(caption)
        self.captionLabel = caption

        NSLayoutConstraint.activate([
            cancel.leadingAnchor.constraint(equalTo: bar.leadingAnchor, constant: 24),
            cancel.centerYAnchor.constraint(equalTo: bar.centerYAnchor),
            shutter.centerXAnchor.constraint(equalTo: bar.centerXAnchor),
            shutter.centerYAnchor.constraint(equalTo: bar.centerYAnchor),
            shutter.widthAnchor.constraint(equalToConstant: 64),
            shutter.heightAnchor.constraint(equalToConstant: 64),
            caption.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            caption.bottomAnchor.constraint(equalTo: bar.topAnchor, constant: -8),
        ])
    }

    private func configureSession() {
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.session.beginConfiguration()
            self.session.sessionPreset = .photo
            let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back)
            var hasCamera = device != nil
            if let device {
                do {
                    let input = try AVCaptureDeviceInput(device: device)
                    if self.session.canAddInput(input) { self.session.addInput(input) }
                } catch { hasCamera = false }
            }
            if self.session.canAddOutput(self.photoOutput) {
                self.session.addOutput(self.photoOutput)
                self.configured = hasCamera
            } else {
                self.configured = false
            }
            self.session.commitConfiguration()
            guard self.configured else {
                // No camera hardware (simulator, or permission/availability
                // issue): say so instead of a dead black screen. The library
                // picker in the sheet still works, and so does typing.
                DispatchQueue.main.async { [weak self] in
                    self?.captionLabel?.text = "No camera on this simulator — take the photo on your phone, or choose from the library in the sheet."
                }
                return
            }
            self.session.startRunning()

            let layer = AVCaptureVideoPreviewLayer(session: self.session)
            layer.videoGravity = .resizeAspectFill
            layer.frame = self.view.bounds
            self.view.layer.addSublayer(layer)
            self.previewLayer = layer
        }
    }

    @objc private func tappedCancel() {
        stopSession()
        onCancel?()
    }

    @objc private func tappedShutter() {
        guard configured else { return }
        let settings = AVCapturePhotoSettings()
        settings.photoQualityPrioritization = .quality
        photoOutput.capturePhoto(with: settings, delegate: self)
    }

    private func stopSession() {
        sessionQueue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }
}

extension CameraControllerVC: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput,
                     didFinishProcessingPhoto photo: AVCapturePhoto,
                     error: Error?) {
        guard error == nil, let data = photo.fileDataRepresentation() else { return }
        // Hand the capture to the sheet (main thread for state mutation).
        DispatchQueue.main.async { [weak self] in
            self?.onCapture?(data)
        }
    }
}
