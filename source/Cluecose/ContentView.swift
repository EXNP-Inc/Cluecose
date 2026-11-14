import SwiftUI
import Charts
import CluecoseCore

struct ContentView: View {
    @EnvironmentObject var model: GlucoseModel
    @State private var showSettings = false
    @FocusState private var accountIDFocused: Bool

    var body: some View {
        ZStack {
            CluecoseTheme.backdrop().ignoresSafeArea()
            AuroraBlobs().ignoresSafeArea()
                .opacity(0.55)
                .blendMode(.screen)

            ScrollView(showsIndicators: false) {
                VStack(spacing: 18) {
                    header

                    if !model.hasPairedSensor {
                        pairCard
                    }

                    if let reading = liveReading {
                        heroCard(reading)
                        statsCard
                        BurnModeCard()
                        StatsGridCard()
                        historyCard
                        TipsCard()
                    } else if model.hasPairedSensor {
                        listeningCard
                    }

                    if case .error(let message) = model.phase {
                        errorCard(message)
                    }

                    footer
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 32)
                // iPhone Duo unfolded inner display (7.58", ~669 pt wide, iPad-mini
                // class): cap the content to a readable column and center it over the
                // full-bleed backdrop so cards don't stretch edge-to-edge. On the
                // 5.36" cover display and regular iPhones this is a no-op (they're
                // narrower than the cap).
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsSheet()
                .environmentObject(model)
        }
    }

    /// The reading to put on the hero card. `model.latest` alone is not safe
    /// after a reset/restore: the Health-app read-back (`refreshExternal`)
    /// refills `allSamples` with samples from BEFORE the reset, so a 14 h-old
    /// value renders as "Now" while the app is still linking to the sensor —
    /// the glucose card and the linking card both showing for a while.
    ///
    /// The hero is presented only for data we can trust:
    ///   • a fresh reading (≤ 25 min) — whatever its source, or
    ///   • stale data, but ONLY while the direct link is up and we hold
    ///     direct-BLE samples (a genuinely stale reading, "Last reading 40 min
    ///     ago"). Health-app history alone never dresses up as a reading.
    /// Otherwise the linking/listening card stands alone.
    private var liveReading: GlucoseSample? {
        guard let latest = model.latest else { return nil }
        if model.isFresh { return latest }
        guard model.linkActive, !model.samples.isEmpty else { return nil }
        return latest
    }

    // MARK: Header

    private var header: some View {
        HStack {
            HStack(spacing: 10) {
                Image("Logo")
                    .resizable()
                    .interpolation(.high)
                    .scaledToFit()
                    .frame(width: 38, height: 38)
                VStack(alignment: .leading, spacing: 1) {
                    Text("Cluecose")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                    Text("Bluetooth")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.55))
                }
            }
            Spacer()
            unitToggle
            Button {
                showSettings = true
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(.white.opacity(0.75))
                    .frame(width: 34, height: 34)
                    .background(.white.opacity(0.12), in: Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 6)
    }

    private var unitToggle: some View {
        HStack(spacing: 0) {
            ForEach(GlucoseUnit.allCases, id: \.rawValue) { u in
                Button {
                    withAnimation(.snappy) { model.unit = u }
                } label: {
                    Text(u.rawValue)
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(model.unit == u ? .black : .white.opacity(0.7))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background {
                            if model.unit == u {
                                Capsule().fill(.white.opacity(0.92))
                            }
                        }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(.ultraThinMaterial, in: Capsule())
    }

    // MARK: Pairing card (first run / takeover)

    private var pairCard: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle().stroke(.white.opacity(0.15), lineWidth: 1.5)
                    .frame(width: 74, height: 74)
                Image(systemName: "wave.3.forward")
                    .font(.system(size: 30, weight: .light))
                    .foregroundStyle(Color(red: 0.35, green: 0.92, blue: 0.7))
            }
            Text(model.needsReceiverID ? "Sensor is bound to another app" : "Pair your sensor — one-time NFC tap")
                .font(.system(size: 17, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            Text(pairingSubtitle)
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.6))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            // The LibreView Account ID is only relevant when a tap is rejected
            // because the sensor was activated by the Libre app (NFC 0xB1).
            // For a Cluecose-activated sensor the pairing flow never needs it,
            // so the field stays hidden and the screen stays Abbott/LibreView-free.
            if model.needsReceiverID {
                accountIDField
            }

            if model.nfcAwaiting {
                nfcWaitingBanner
            }

            Button {
                accountIDFocused = false
                Task { await model.pair() }
            } label: {
                Label(model.pairingInProgress ? "Reading sensor…" : "Pair sensor",
                      systemImage: model.pairingInProgress ? "arrow.triangle.2.circlepath" : "dot.radiowaves.left.and.right")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(.black)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color(red: 0.35, green: 0.95, blue: 0.65), in: RoundedRectangle(cornerRadius: 16))
            }
            .buttonStyle(.plain)
            .disabled(model.pairingInProgress)

            if !model.status.isEmpty {
                Text(model.status)
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.55))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 22)
        .glassPanel(cornerRadius: 34)
    }

    private var pairingSubtitle: String {
        if model.needsReceiverID {
            return "To read this sensor Cluecose needs the same account identity the sensor's activation app used. Open that app (or its website) and copy your Account ID — the UUID in your account page or report link — into the field below. It's just an identifier — it never leaves your phone."
        }
        return "Hold the top edge of your phone against your sensor for a second. This links Cluecose directly to it over Bluetooth so readings update the moment the sensor broadcasts — no account, no cloud."
    }

    private var accountIDField: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Activation app Account ID (UUID)")
                .font(.footnote.weight(.medium))
                .foregroundStyle(.white.opacity(0.7))
            TextField("e.g. 2977dec2-492a-11ea-9702-0242ac110002",
                      text: $model.libreViewAccountID)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.asciiCapable)
                .focused($accountIDFocused)
                .textFieldStyle(.plain)
                .padding(12)
                .background(.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
                .foregroundStyle(.white)
            Text("Stored only on this phone and used to compute a receiver ID. Never sent anywhere.")
                .font(.caption2)
                .foregroundStyle(.white.opacity(0.4))
        }
        .frame(maxWidth: .infinity)
    }

    /// Big, hard-to-miss prompt shown while the system NFC sheet is up and we
    /// are waiting for the tag tap. Replaces the "nothing happened" ambiguity.
    private var nfcWaitingBanner: some View {
        HStack(spacing: 12) {
            Image(systemName: "iphone.radiowaves.top.forward.filled")
                .font(.system(size: 28))
                .foregroundStyle(Color(red: 0.35, green: 0.92, blue: 0.7))
                .symbolEffect(.pulse, isActive: true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Hold the TOP of the phone to the sensor")
                    .font(.footnote.weight(.bold))
                    .foregroundStyle(.white)
                Text("The blue NFC sheet slides up from the bottom. Keep the top edge on the sensor for a couple of seconds.")
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.55))
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color(red: 0.35, green: 0.92, blue: 0.7).opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(red: 0.35, green: 0.92, blue: 0.7).opacity(0.35), lineWidth: 1))
    }

    // MARK: Hero reading card

    private func heroCard(_ reading: GlucoseSample) -> some View {
        let mgdl = reading.mgdl
        let color = CluecoseTheme.color(for: mgdl)

        return VStack(spacing: 14) {
            HStack {
                Label("Now", systemImage: "scope")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white.opacity(0.6))
                Spacer()
                connectionPill
                HStack(spacing: 6) {
                    Circle().fill(model.isFresh ? color : .orange)
                        .frame(width: 8, height: 8)
                    Text(model.isFresh ? "Live" : "Last reading \(model.readingAgeText ?? "")")
                        .font(.footnote.weight(.medium))
                        .foregroundStyle(model.isFresh ? .white.opacity(0.75) : .orange)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(.white.opacity(0.12), in: Capsule())
            }

            HStack(alignment: .firstTextBaseline, spacing: 14) {
                Text(model.unit.formatted(fromMgdl: mgdl, decimals: model.unit == .mmol ? 1 : 0))
                    .font(.system(size: 88, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .foregroundStyle(
                        LinearGradient(colors: [.white, .white.opacity(0.82)],
                                       startPoint: .top, endPoint: .bottom)
                    )
                    .shadow(color: color.opacity(0.55), radius: 26, y: 0)
                VStack(alignment: .leading, spacing: 3) {
                    Text(model.unit.rawValue)
                        .font(.system(size: 18, weight: .semibold, design: .rounded))
                        .foregroundStyle(.white.opacity(0.7))
                    HStack(spacing: 5) {
                        Image(systemName: model.trend.symbol)
                            .font(.footnote.weight(.bold))
                        Text(model.trend.label)
                            .font(.footnote.weight(.medium))
                    }
                    .foregroundStyle(color)
                    if let age = model.sensorAgeText {
                        HStack(spacing: 5) {
                            Image(systemName: "sensor.tag.radiowaves.forward.fill")
                                .font(.caption2)
                            Text("Sensor on \(age)")
                                .font(.caption2)
                        }
                        .foregroundStyle(.white.opacity(0.4))
                    }
                }
            }

            RangeGauge(mgdl: mgdl, color: color)
                .padding(.top, 2)
        }
        .glassPanel(cornerRadius: 34)
    }

    private var connectionPill: some View {
        HStack(spacing: 5) {
            Image(systemName: model.linkActive ? "bolt.fill" : "bolt.slash.fill")
                .font(.system(size: 9, weight: .bold))
            Text(model.linkActive ? "Direct link" : "Reconnecting")
                .font(.system(size: 10, weight: .semibold))
        }
        .foregroundStyle(model.linkActive ? Color(red: 0.4, green: 0.95, blue: 0.7) : .white.opacity(0.65))
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(.white.opacity(0.1), in: Capsule())
    }

    // MARK: Stats card (24h summary)

    private var statsCard: some View {
        let stats = model.stats24h
        return HStack(spacing: 0) {
            stat("Low 24h", model.unit.format(fromMgdl: stats.min), "arrow.down.to.line.compact",
                 Color(red: 0.45, green: 0.78, blue: 1.0))
            divider
            stat("High 24h", model.unit.format(fromMgdl: stats.max), "arrow.up.to.line.compact",
                 Color(red: 1.0, green: 0.62, blue: 0.35))
            divider
            stat("In range", String(format: "%.0f%%", stats.inRangePct), "checkmark.seal.fill",
                 Color(red: 0.30, green: 0.92, blue: 0.60))
        }
        .frame(height: 92)
        .glassPanel(cornerRadius: 28)
    }

    private var divider: some View {
        Rectangle()
            .fill(.white.opacity(0.12))
            .frame(width: 1)
            .padding(.vertical, 14)
    }

    private func stat(_ title: String, _ value: String, _ icon: String, _ tint: Color) -> some View {
        VStack(spacing: 6) {
            HStack(spacing: 5) {
                Image(systemName: icon)
                    .font(.caption2)
                Text(title)
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(.white.opacity(0.55))
            Text(value)
                .font(.system(size: 26, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(.white)
                .shadow(color: tint.opacity(0.5), radius: 10)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: History chart

    private var historyCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Last 24 hours")
                    .font(.system(size: 15, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white)
                Spacer()
                if !model.samples.isEmpty {
                    Text("\(model.stats24h.count) readings")
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.5))
                }
            }
            chart
                .frame(height: 190)
        }
        .glassPanel()
    }

    @ViewBuilder
    private var chart: some View {
        let samples = model.allSamples
        let stats = model.stats24h
        let lo: Double = max(0, min(60, stats.min) - 5)
        let hi: Double = max(200, stats.max) + 5
        if samples.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "waveform.path.ecg")
                    .font(.system(size: 30))
                    .foregroundStyle(.white.opacity(0.4))
                Text("No readings yet")
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.5))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            Chart {
                ForEach(samples) { sample in
                    AreaMark(x: .value("Time", sample.date), y: .value("Glucose", sample.mgdl))
                        .foregroundStyle(
                            LinearGradient(colors: [CluecoseTheme.glow(for: .inRange).opacity(0.35),
                                                    .clear],
                                           startPoint: .top, endPoint: .bottom)
                        )
                        .interpolationMethod(.catmullRom)

                    LineMark(x: .value("Time", sample.date), y: .value("Glucose", sample.mgdl))
                        .foregroundStyle(
                            LinearGradient(colors: [Color(red: 0.45, green: 0.95, blue: 0.75),
                                                    Color(red: 0.35, green: 0.8, blue: 0.95)],
                                           startPoint: .leading, endPoint: .trailing)
                        )
                        .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round))
                        .interpolationMethod(.catmullRom)
                }

                RectangleMark(yStart: .value("Target low", 70),
                              yEnd: .value("Target high", 180))
                    .foregroundStyle(.white.opacity(0.05))

                if let latest = model.latest {
                    PointMark(x: .value("Time", latest.date), y: .value("Glucose", latest.mgdl))
                        .symbolSize(70)
                        .foregroundStyle(CluecoseTheme.color(for: latest.mgdl))
                        .annotation(position: .top, spacing: 6) {
                            Text(model.unit.format(fromMgdl: latest.mgdl))
                                .font(.caption2.weight(.bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(.white.opacity(0.15), in: Capsule())
                        }
                }
            }
            .chartYScale(domain: lo...hi)
            .chartXAxis {
                AxisMarks(values: .stride(by: .hour, count: 6)) { _ in
                    AxisGridLine().foregroundStyle(.white.opacity(0.08))
                    AxisValueLabel(format: .dateTime.hour())
                        .foregroundStyle(.white.opacity(0.45))
                }
            }
            .chartYAxis {
                AxisMarks(position: .trailing, values: [lo, (lo + hi) / 2, hi]) { value in
                    AxisGridLine().foregroundStyle(.white.opacity(0.08))
                    AxisValueLabel {
                        if let v = value.as(Double.self) {
                            Text(model.unit.format(fromMgdl: v))
                                .foregroundStyle(.white.opacity(0.45))
                        }
                    }
                }
            }
        }
    }

    // MARK: Listening / error states

    private var listeningCard: some View {
        let title: String
        let detail: String
        switch model.phase {
        case .waitingBLE:
            title = "Linking to your sensor…"
            detail = "Waiting for your sensor's Bluetooth window — it opens a connection window about every minute. This can take up to a minute."
        case .listening:
            title = "Listening to your sensor"
            detail = model.isWarmingUp
                ? "Your sensor is still warming up — the first readings can take up to 24 h after applying it."
                : "First readings should appear within a minute or two."
        default:
            title = "Working…"
            detail = ""
        }

        return VStack(spacing: 14) {
            ZStack {
                Circle().stroke(.white.opacity(0.15), lineWidth: 1.5)
                    .frame(width: 84, height: 84)
                Circle()
                    .trim(from: 0, to: 0.7)
                    .stroke(
                        AngularGradient(colors: [CluecoseTheme.glow(for: .inRange),
                                                 .clear, CluecoseTheme.glow(for: .inRange)],
                                        center: .center),
                        style: StrokeStyle(lineWidth: 4, lineCap: .round)
                    )
                    .frame(width: 84, height: 84)
                    .rotationEffect(.degrees(-90))
                Image(systemName: "waveform.path.ecg")
                    .font(.system(size: 32, weight: .light))
                    .foregroundStyle(.white.opacity(0.9))
            }
            Text(title)
                .font(.system(size: 17, weight: .semibold, design: .rounded))
                .foregroundStyle(.white)
            if !detail.isEmpty {
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !model.status.isEmpty {
                Text(model.status)
                    .font(.caption2)
                    .foregroundStyle(.white.opacity(0.4))
                    .multilineTextAlignment(.center)
            }
            Button {
                model.reconnectNow()
            } label: {
                Text("Try again now")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 9)
                    .background(Color(red: 0.35, green: 0.95, blue: 0.65), in: Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 2)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 26)
        .glassPanel(cornerRadius: 34)
    }

    private func errorCard(_ message: String) -> some View {
        VStack(spacing: 14) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 30))
                .foregroundStyle(Color(red: 1.0, green: 0.62, blue: 0.35))
            Text(message)
                .font(.footnote)
                .foregroundStyle(.white.opacity(0.75))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                model.reconnectNow()
            } label: {
                Text("Try again")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.black)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 9)
                    .background(Color(red: 0.35, green: 0.95, blue: 0.65), in: Capsule())
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 22)
        .glassPanel(cornerRadius: 34)
    }

    private var footer: some View {
        Text("Not a medical device.")
            .font(.caption2)
            .foregroundStyle(.white.opacity(0.35))
            .multilineTextAlignment(.center)
    }
}

// MARK: - Settings sheet

struct SettingsSheet: View {
    @EnvironmentObject var model: GlucoseModel
    @Environment(\.dismiss) private var dismiss
    @State private var showClearConfirm = false

    var body: some View {
        NavigationStack {
            List {
                Section("Sensor") {
                    LabeledContent("Paired sensor", value: model.sensorName ?? "Not paired")
                    LabeledContent("Sensor age", value: model.sensorAgeText ?? "—")
                    Button("Re-pair with NFC tap") {
                        dismiss()
                        Task { await model.pair() }
                    }
                    .disabled(model.pairingInProgress)
                }

                Section("Data") {
                    LabeledContent("Direct Bluetooth link",
                                   value: model.linkActive ? "Connected" : "Searching")
                    if model.healthKit.available {
                        Toggle(isOn: $model.healthKitEnabled) {
                            VStack(alignment: .leading, spacing: 1) {
                                Text("Health app sync")
                                Text(model.healthKitEnabled ? model.healthKitStatus : "Off")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    } else {
                        LabeledContent("Health app", value: "unavailable")
                    }
                }

                Section("Display") {
                    Toggle(isOn: $model.liveActivityEnabled) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Live Activity")
                            Text(model.liveActivityEnabled
                                 ? "Glucose on the Dynamic Island, Lock Screen and Apple Watch"
                                 : "Off")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button(role: .destructive) {
                    showClearConfirm = true
                } label: {
                    Text("Forget sensor")
                        .frame(maxWidth: .infinity)
                }
                .padding()
                .background(.ultraThinMaterial)
                .confirmationDialog("Forget this sensor?",
                                    isPresented: $showClearConfirm, titleVisibility: .visible) {
                    Button("Forget sensor", role: .destructive) {
                        model.clearSensor()
                        dismiss()
                    }
                    Button("Cancel", role: .cancel) {}
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

// MARK: - Range gauge (70–180 band with current-value marker)

struct RangeGauge: View {
    let mgdl: Double
    let color: Color

    private let lo: Double = 40
    private let hi: Double = 300

    private var fraction: CGFloat {
        let f = (mgdl - lo) / (hi - lo)
        return CGFloat(min(max(f, 0), 1))
    }

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let bandStart = CGFloat((70 - lo) / (hi - lo)) * w
            let bandWidth = CGFloat((180 - 70) / (hi - lo)) * w
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(.white.opacity(0.12))
                    .frame(height: 8)
                HStack(spacing: 0) {
                    Spacer().frame(width: bandStart)
                    Capsule()
                        .fill(LinearGradient(colors: [CluecoseTheme.glow(for: .low).opacity(0.5),
                                                      CluecoseTheme.glow(for: .inRange).opacity(0.85),
                                                      CluecoseTheme.glow(for: .high).opacity(0.5)],
                                             startPoint: .leading, endPoint: .trailing))
                        .frame(width: bandWidth, height: 8)
                    Spacer(minLength: 0)
                }
                Circle()
                    .fill(.white)
                    .frame(width: 16, height: 16)
                    .shadow(color: color.opacity(0.9), radius: 6)
                    .overlay(Circle().fill(color).frame(width: 7, height: 7))
                    .offset(x: fraction * w - 8, y: -4)
            }
            .frame(height: 16)
        }
        .frame(height: 20)
    }
}

// MARK: - Aurora blobs background

struct AuroraBlobs: View {
    @State private var animate = false

    var body: some View {
        ZStack {
            blob(Color(red: 0.15, green: 0.85, blue: 0.65), size: 320, y: -80, x: -100)
                .rotationEffect(.degrees(animate ? 360 : 0), anchor: .topTrailing)
            blob(Color(red: 0.2, green: 0.45, blue: 0.95), size: 380, y: 260, x: 120)
                .rotationEffect(.degrees(animate ? -360 : 0), anchor: .bottomLeading)
            blob(Color(red: 0.4, green: 0.9, blue: 0.8), size: 260, y: 560, x: -60)
                .rotationEffect(.degrees(animate ? 360 : 0), anchor: .center)
        }
        .onAppear {
            withAnimation(.linear(duration: 40).repeatForever(autoreverses: false)) {
                animate = true
            }
        }
    }

    private func blob(_ color: Color, size: CGFloat, y: CGFloat, x: CGFloat) -> some View {
        Circle()
            .fill(color.opacity(0.4))
            .frame(width: size, height: size)
            .blur(radius: 90)
            .offset(x: x, y: y)
    }
}

#Preview {
    ContentView()
        .environmentObject(GlucoseModel())
        .preferredColorScheme(.dark)
}
