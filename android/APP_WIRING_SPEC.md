# Real GlucoseRepository wiring (replace StubGlucoseRepository)

Goal: make the Android app actually read a Libre 3 sensor — mirroring iOS
`source/Cluecose/Libre/LibreDirectReader.swift` (1144 L) — using the already
-ported `:core` building blocks. **Demo mode / stub must be removed as the
default** (user requirement: "turn off the demo mode so I can actually use the
nfc pairing").

## Core building blocks ALREADY ported (do not re-port)
- `core.pairing.PairingFlow(transport: CommandPairingTransport, eventLogger)`
  - `runCachedReconnectHandshake(tail4, phase5RawKey, tables, r2Provider)` → cached 0x11 path (NO VM needed). ✅ offline-proven.
  - `runCachedReconnectPreamble()`.
  - Full first-pair path lands with SessionKey (VM) — wire after VM port.
- `core.pairing.PairingTransport` / `CommandPairingTransport` interfaces + `BleCharRef { CERT_HANDSHAKE, CHALLENGE }`. The app implements these over a real GATT session.
- `core.ble.BleFraming` (write offset-prefix / notify seq-prefix fragmentation).
- `core.dataplane.*` (DataFrame, DataPlaneDecoder, RealtimeGlucoseReading, PatchStatus, HistoricalBackfill, SensorLifecycle, etc.).
- `core.nfc.NFCActivationCommand` (0xA0 activate / 0xA8 switch, metcrc + abbottCrc16), `NFCActivationModels`, `Libre3ReceiverID`.
- `core.crypto.LibAES.phase5BlockEncryptor(rawKey, tables)`, `AesCcm`, `P256ScalarMultiplier`.
- `core.tables.{TablesSource, SourceTables, RuntimeTable}`; `app.tables.AssetsTablesSource(context.assets)`.
- (After VM port) `core.crypto.FirstPairConfig` + top-level VM fns; `core.pairing.SessionKey`, `PhoneCert`, `SensorCert`, `EphemeralExchange`, `ChallengeMessage`.

## GATT UUIDs (from iOS LibreSensorGATT.swift)
- Data service `089810CC-EF89-11E9-81B4-2A2AE2DBCCE4` (scan filter)
- Security service `0898203A-...`, Debug service `08982400-...`
- Data-service characteristics (base `08981xxx-...`):
  - glucoseData `0898177A`, patchStatus `08981482`, patchControl `08981338`,
    historicData `0898195A`, eventLog `08981BEE`, clinicalData `08981AB8`,
    factoryData `08981D24`.
- CERT_HANDSHAKE = handle 0x002d characteristic; CHALLENGE = handle 0x002a.
  Map these to the data-service characteristic whose observed handle is 0x002d /
  0x002a (resolve at runtime from the discovered handle table, not hardcoded UUID).

## New app-layer files (under `app/src/main/kotlin/com/dsikic/cluecose/`)
1. `ble/SensorScanner.kt` — wraps `BluetoothLeScanner`, scans for the data
   service UUID, returns best DiscoveredSensor (name + MAC `AA:BB:CC:DD:EE:FF`).
   Permissions: BLUETOOTH_SCAN/CONNECT (SDK 31+), BLUETOOTH_ADMIN/LOCATION (≤30).
   Emulator caveat: no real BLE on the emulator — verify scan behavior on the
   Samsung `R58N62WKXPT` and the sensor on the Sony; emulator only shows the
   "listening → no sensor → prompt" flow.
2. `ble/GattSession.kt` — wraps `BluetoothGatt`: connect, service discovery,
   read/write/notify per characteristic. Implements
   `core.pairing.CommandPairingTransport` (write/awaitNotify/writeCommand/
   awaitCommandResponse) synchronously on a background thread, honoring
   `BleFraming`. This is the transport the PairingFlow drives.
3. `ble/DataPlaneListener.kt` — subscribes to glucoseData notify, reassembles
   frames via `BleFraming`, feeds `DataPlaneDecoder`, emits
   `GlucoseSample`s; handles PatchStatus / SensorLifecycle events.
4. `libre/RealGlucoseRepository.kt` — implements `GlucoseRepository`. Mirrors
   `LibreDirectReader`:
   - `startListening()` → restore persisted state → if paired, `reconnect()`
     (cached path); else `firstLibreDiscovery()` + scan.
   - `nfcPair()` → NFC reader-mode ISO 15693 (NfcV) 0xA0/0xA8 → obtain
     `receiverIDLEHex`, `blePIN`(4B), `activationTimeSeconds`, `phase5RawKey`;
     then `runFirstPairHandshake` (VM) or, for a re-bind, cached path.
   - `stopListening()` / `forgetSensor()` → clear persisted state.
   - Persistence (parity with iOS keys): `cluecose.libre.directState`
     (serial, bleAddress, blePIN, receiverIDLEHex, receiverIDSource,
     activationTimeSeconds, warmup/wear durations, lastGlucoseLifeCount,
     lastGlucoseMgDL, pairedAt, phase5RawKey) and `cluecose.libre.directSamples`.
     Store in SharedPreferences (base64 of a small encoded struct).
   - Receiver ID: `accountlessReceiverID()` — persist a UUID under a pref,
     fold like iOS `Libre3ReceiverID.accountlessValue`.
5. `nfc/NfcActivationReader.kt` — Android NfcV reader-mode port of iOS
   `CoreNFCActivationReader` (ISO 15693 0xA1 read-patch-info, then
   0xA0/0xA8 activate/switch with metcrc). Uses `NfcAdapter`/`NfcV` +
   `transceive`, request flag high-data-rate. Alert strings mirror iOS.
6. `LinkKeeperService.kt` (replace current stub) — foreground service that
   holds the GATT link alive; mirrors iOS connection-watcher/reconnect.

## ViewModel / UI changes
- `GlucoseViewModel` constructor: `provided ?: RealGlucoseRepository(context, scope)`.
  **Remove `StubGlucoseRepository` as the default.** Keep the class + a
  `BuildConfig.DEBUG`-gated demo toggle ONLY if the user wants it; default OFF.
  (Decision: remove the "demo data" badge from the UI and do NOT instantiate
  the stub by default. The stub may remain in the source tree, unused, or be
  deleted — prefer deleting to avoid confusion.)
- `ListeningScreen` / `MainScreen`: show real sensor name + age (from
  `SensorMeta`), real status line from `Event.Status`. Remove any
  "demo data" text (found in `SettingsSheet.kt`, `MainScreen.kt`).
- Settings: show real sensor name / activation age / receiver source.

## Verification plan
- Emulator `emulator-5554` (AVD `cluecose`): no real BLE → verify launch →
  "listening" → no sensor found → pairing prompt (NFC) appears. Confirm
  `topResumedActivity` and NO "demo data" badge.
- Samsung `R58N62WKXPT`: real BLE adapter → verify scan discovers the sensor
  by name, connect, cached reconnect if a paired state exists, live samples.
- Sony `RQ30060B26`: NFC first-pair (the VM-dependent path) — the decisive test.

## Blocked-on
- Full first-pair (03 03) needs the VM port (`FirstPairVM.cNN.kt` bodies) +
  `SessionKey` port. The cached 0x11 reconnect path is NOT blocked and can be
  wired + tested independently.
