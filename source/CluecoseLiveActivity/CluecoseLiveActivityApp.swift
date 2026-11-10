//
//  CluecoseLiveActivityApp.swift
//  CluecoseLiveActivity
//
//  Live Activity extension for Cluecose. The host app (com.dsikic.cluecose)
//  starts and updates activities; this extension (a WidgetKit extension) only
//  renders them on the Dynamic Island and Lock Screen. The paired Apple Watch
//  inherits the same activity automatically.
//

import SwiftUI
import WidgetKit

@main
struct CluecoseLiveActivityApp: WidgetBundle {
    var body: some Widget {
        GlucoseActivity()
    }
}
