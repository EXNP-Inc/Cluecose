//
//  CluecoseWatchApp.swift
//  Cluecose Watch
//
//  Watch companion: shows the current Cluecose reading + trend + 24 h TIR.
//  Data path: the iPhone app writes Libre 3 readings into the Health app;
//  the watch reads them back. The watch never talks to the sensor itself.
//

import SwiftUI
import WatchKit

@main
struct CluecoseWatchApp: App {
    @StateObject private var model = WatchGlucoseModel()

    var body: some Scene {
        WindowGroup {
            WatchHomeView()
                .environmentObject(model)
                .preferredColorScheme(.dark)
                .task { await model.start() }
        }
    }
}
