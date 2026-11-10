import SwiftUI

@main
struct CluecoseApp: App {
    @StateObject private var model = GlucoseModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .task { model.start() }
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                // Resume the BLE listener on foreground (start() is idempotent).
                model.start()
            case .background:
                // Keep the Bluetooth-central link alive in the background;
                // the reader reconnects on its own schedule.
                model.stop()
            default:
                break
            }
        }
    }
}
