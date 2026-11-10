# Cluecose

Liquid-glass CGM companion that displays your FreeStyle Libre 3 glucose
reading — **directly from the sensor over Bluetooth**. No Abbott account, no
LibreLink cloud. Optional Apple Health sync is **on-device only** (HealthKit
is Apple's local store) — nothing ever leaves the phone.

## How it gets your readings

Everything is local. Cluecose pairs with the sensor over NFC, then reads the
encrypted Libre 3 data plane over CoreBluetooth and decrypts it on-device:

1. **One-time NFC tap** — hold the top of the phone to the sensor for ~2 s.
   Cluecose reads the sensor's ISO 15693 tag to activate it (or switch the
   receiver to Cluecose) and learn its BLE address, 4-byte BLE PIN, serial,
   and activation time. No Abbott login involved.
2. **BLE authorize** — Cluecose connects to the sensor's `0898…` GATT service
   and runs the full command-gated security handshake (Phases 1–6). The
   Phase-5 session key is derived **on-device** from Abbott's own runtime
   tables bundled with the library — it's a whitebox key schedule, not ECDH.
3. **Live data plane** — Cluecose subscribes to the encrypted characteristics
   (realtime, clinical, patch status, history), decrypts each frame with
   AES-CCM, and decodes the glucose. It pulls ~24 h of history once so the
   chart is filled immediately, then streams ~1 reading/minute.
4. **Reconnect, no tap** — the BLE PIN and the Phase-5 key are persisted
   locally, so relaunching (and auto-reconnect after a dropped link)
   re-authorizes without another NFC tap.

### Background behavior (stays connected, heals on its own)

The app runs with the `bluetooth-central` background mode and **does not tear
the link down when the phone locks**. If the link drops while backgrounded,
iOS wakes the app when the sensor is observed again (CoreBluetooth connection
events, restoration id `com.dsikic.cluecose.central`) and Cluecose jumps
straight to the cached handshake; otherwise its reconnect loop ramps
2 → 4 → 8 → 16 s and then retries every 30 s, forever, until the link is
back. Opening the app after a lock therefore shows a live reading instead of
a cold ~75 s scan.

The pairing + crypto stack is **LibreCRKit** (MIT, clean-room; fork of
`airedev326/LibreCRKit` now at
[pov-it/LibreCRKit](https://github.com/pov-it/LibreCRKit), branch `trio`).
It bundles the phone certificate and all Abbott runtime tables as resources
and derives the key on-device. Attribution is kept in the source headers.

> **Single-active-session rule**: a Libre 3 serves one connected receiver at
> a time. Quit the Libre / FreeStyle Libre app while Cluecose holds the link.

### Receiver ID (and the 0xB1 error)

The Libre 3 stores the *receiver ID* that activated it and answers a later
activation with a **different** ID using NFC error `0xB1`. Cluecose handles
this with a small candidate ladder:

- **Fresh / Cluecose-activated sensor** — accepts Cluecose's own stable
  accountless receiver ID (a per-install value, no Abbott material). This is
  the default and needs nothing from you.
- **Sensor already bound to the Libre / FreeStyle Libre app** — the app
  derived the stored ID from your **LibreView Account ID** (the account
  *UUID*, not your login). If Cluecose's accountless ID is rejected with
  `0xB1`, the app asks you to paste that UUID once. Cluecose folds it exactly
  the way the Abbott apps do (two documented derivations) and retries. The
  UUID is stored only on this phone and is **never transmitted anywhere**.

### Sensor clock (timeRaw)

The sensor's history frames carry a 4-byte `timeRaw` (the activation epoch).
It is **little-endian** and is the *activation* time, not the reading time —
the reading time is `timeRaw + lifeCount · 60 s`. Cluecose parses it that
way, with a plausibility guard, so backfilled history lands on correct dates.

## First run

1. Launch Cluecose.
2. **Tap the sensor** — hold the top edge of the iPhone to it. (Keep the
   sensor active; it opens a Bluetooth connection window about every minute,
   so the first connect can take up to ~90 s.)
3. If Cluecose says the sensor is bound to a different receiver, paste your
   LibreView **Account ID** (the UUID in your LibreView account page / report
   link — open the Libre app or log in at libreview.com) and tap again.
4. The reading appears in the hero card; the pill shows **Direct link** (BLE
   attached) or **Reconnecting**.

## Health app sync (optional, on-device)

Cluecose can mirror glucose through the Apple Health app. It is a side
channel — the direct BLE link is always the source of truth — and it works
in both directions:

- **Write**: every reading Cluecose gets from the sensor is saved as a blood
  glucose sample (mg/dL) attributed to this app. Writes are incremental
  (only samples newer than the last saved one), so relaunches never create
  duplicates.
- **Read**: glucose from any other source in Health (manual entries, an
  older CGM, another app) is pulled in every 60 s and merged into the chart
  and analytics, deduped against direct readings by the minute (direct wins).

Toggle in Settings → **Health app sync** (default on). First use shows the
system permission dialog. The Watch app reads through this channel.

## Analytics

Computed on-device over the last 24 h of the merged series (direct +
external), via `GlucoseAnalytics` in the shared `CluecoseCore` package —
the same code runs on the phone and the watch, so the numbers match:

- **TIR / TAR / TBR** — time in range 70–180 mg/dL (TAR above 180, TBR
  below 70, with sub-54 tracked separately as TBR2). Time-weighted: each
  sample counts until the next one; gaps > 15 min are skipped.
- **Mean, CV, SD, GMI** — coefficient of variation (SD/mean · 100) and
  estimated GMI (`3.31 + 0.0239 · mean mg/dL`) in mmol units.
- **AUC above 140** — trapezoidal area under the curve above 140 mg/dL
  (mg/dL·min). Cluecose calls this **storage pressure**: it's a correlate of
  insulin-mediated storage. Against a 24 h threshold of 2 000 mg/dL·min
  (scaled to the hours actually covered) the app reports a
  **Storing / Balanced / Burning** verdict — an *estimate of insulin
  activity*, not a direct fat-oxidation measurement.
- **Tips** — short, informational notes when a stat leaves a reasonable
  band (low/high time, variability, storage pressure, or "steady is good").
  General information, **not medical advice**.

## Apple Watch app

A single-target watchOS app (Xcode 27 deprecates separate WatchKit
extensions) that reads glucose from Health — the channel the iPhone app
writes — and shows the same hero value, trend arrow, 24 h TIR, and
Storing/Balanced/Burning state on the wrist. It polls Health every 30 s
(the watch has no push from iPhone HealthKit writes).

- Bundle id: `com.dsikic.cluecose.watch`, companion app
  `com.dsikic.cluecose`.
- Build for a watch simulator with the `CluecoseWatch` scheme, e.g.:

  ```bash
  DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild \
    -project Cluecose.xcodeproj -scheme CluecoseWatch -configuration Debug \
    -destination "platform=watchOS Simulator,name=Apple Watch Series 12 (46mm)" \
    -derivedDataPath build/dd-watch build
  xcrun simctl install <WATCH_SIM_UDID> build/dd-watch/Build/Products/Debug-watchsimulator/CluecoseWatch.app
  xcrun simctl launch <WATCH_SIM_UDID> com.dsikic.cluecose.watch
  ```

  (The phone build embeds `CluecoseWatch.app` under `Cluecose.app/Watch/`,
  so a real paired watch gets it automatically when the iPhone app is
  installed.)

## Build & run

```bash
scripts/install-launch-duo.sh            # build, install + launch on "DS iPhone Duo"
scripts/install-launch-duo.sh <UDID>     # other device (use the UDID, e.g. 00008160-...)
scripts/build-run-ios.sh                 # generic build (no device)
```

The device destination must be the **UDID** (from
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcrun devicectl
list devices`), not the friendly name. Scripts set
`DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`.

The project is generated by **XcodeGen**: after changing sources or
`project.yml`, run `tools/xcodegen/bin/xcodegen generate` (a vendored
xcodegen binary lives in `tools/xcodegen/`).

## Project

- XcodeGen spec: `project.yml` — iOS 26.0 / watchOS 26.0, team `Q53D65KB4X`,
  automatic signing.
- Targets:
  - `Cluecose` (iOS app, `com.dsikic.cluecose`) — depends on LibreCRKit and
    CluecoseCore, embeds the watch app.
  - `CluecoseWatch` (single-target watchOS app, `com.dsikic.cluecose.watch`)
    — depends on CluecoseCore.
  - `CluecoseCore` (local SwiftPM package) — the pure, platform-independent
    domain + analytics layer shared by both apps: `GlucoseSample`,
    `GlucoseStats`, `GlucoseAnalytics` (stats, AUC, burn mode, tips, trend).
    No HealthKit, no Bluetooth, no UI.
- Entitlements:
  - iPhone: NFC TAG reader + `com.apple.developer.healthkit` (+ empty
    `.access`). The HealthKit entitlement requires a freshly generated
    provisioning profile — build with `-allowProvisioningUpdates`.
  - Watch: `com.apple.developer.healthkit` (+ empty `.access`).
- Info.plist: `NSBluetoothAlwaysUsageDescription`,
  `NFCReaderUsageDescription`, `NSHealthShareUsageDescription`,
  `NSHealthUpdateUsageDescription`, `UIBackgroundModes = [bluetooth-central]`.
- SPM: `LibreCRKit` (pov-it, branch `trio`) — MIT.
- Sources:
  - `CluecoseCore/Sources/CluecoseCore/` — `GlucoseTypes.swift`,
    `GlucoseAnalytics.swift`
  - `source/Cluecose/`
    - `CluecoseApp.swift` — entry point; foreground/background hooks
    - `GlucoseModel.swift` — app model: phase state machine, receiver-ID
      ladder, external-sample merge, analytics recompute
    - `ContentView.swift` — liquid-glass UI (pair card, hero reading, range
      gauge, 24 h stats, Swift Charts history, settings sheet)
    - `Analytics/AnalyticsView.swift` — storage-vs-burning card, stat grid,
      tips
    - `HealthKit/HealthKitSync.swift` — HealthKit write/read bridge
    - `CluecoseTheme.swift` — glass panels, aurora backdrop, range colors
    - `Libre/LibreDirectReader.swift` — the whole direct-BLE path: NFC tap,
      BLE connect, Phase 1–6 authorize, data-plane listener, 24 h backfill,
      reconnect, persistence
  - `source/CluecoseWatch/` + `source/CluecoseWatchExtension/` — watch UI
    (`CluecoseWatchApp.swift`, `WatchHomeView.swift`, `WatchGlucoseModel.swift`)

## Storage

- UserDefaults only (nothing in the Keychain, nothing on a server):
  - `cluecose.libre.directState` — BLE PIN, BLE address, receiver ID + source,
    activation time, Phase-5 key, last life count / glucose
  - `cluecose.libre.directSamples` — 48 h rolling glucose samples
  - `cluecose.libre.accountlessID` — this install's stable accountless ID
  - `cluecose.libre.accountID` — (only if you pasted one) your LibreView
    Account ID
  - `cluecose.unit` — mg/dL or mmol/L
  - `cluecose.healthkit.enabled` — Health app sync on/off (default on)
  - `cluecose.healthkit.lastWritten` — date of the last HK write (dedupe)

## Legal / medical

Not a medical device; for personal use with your own data. The cryptographic
key material is Abbott's own tables/certificates, redistributed under
LibreCRKit's MIT license. Do not copy GPL code from Juggluco/DiaBLE — they
are reference only. The storage/burning verdict is an estimate of insulin
activity derived from the AUC above 140 mg/dL; it is not a direct
fat-oxidation measurement. Tips and stats are general information, not
medical advice — for persistent lows, highs, or symptoms, talk to a
clinician.

## Roadmap

- ~~Time-in-range history, glucose variability (CV/SD)~~ ✅ (24 h stats,
  AUC>140, Storing/Balanced/Burning, tips)
- ~~Optional write-back of samples to Health~~ ✅ (two-way, on-device)
- ~~Apple Watch app~~ ✅ (reads HealthKit)
- Low/high alerting via local notifications
- Today-screen widget
- Longer windows (7/14/30 d) and pattern charts
