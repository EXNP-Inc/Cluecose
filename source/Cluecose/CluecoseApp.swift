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
                // Re-assert on foreground (idempotent: the reader no-ops if the
                // link is already live). This is also the fast path after the
                // app was suspended — the background loop will usually already
                // have the link up, so this is a no-op.
                model.start()
            default:
                // .background / .inactive: do NOT tear the link down. We run
                // with the `bluetooth-central` background mode, so the reader
                // keeps the connection (or its own reconnect loop) alive while
                // the app is backgrounded. Tearing it down on lock — the old
                // behaviour — forced a cold ~75 s scan + handshake on every
                // unlock, which is exactly the "takes so long to reconnect".
                break
            }
        }
    }
}
