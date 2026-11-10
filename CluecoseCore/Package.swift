// swift-tools-version: 5.10
//
// CluecoseCore — the pure, platform-independent domain + analytics layer.
// Shared by the iPhone app and the Apple Watch app so the numbers are
// identical on both. No HealthKit, no Bluetooth, no UI: just samples and
// the math on top of them.
//

import PackageDescription

let package = Package(
    name: "CluecoseCore",
    // Floor only — the Xcode project's deployment settings (iOS 26 / watchOS
    // 26) take the max and are what actually apply.
    platforms: [
        .iOS(.v17),
        .watchOS(.v10),
    ],
    products: [
        .library(name: "CluecoseCore", targets: ["CluecoseCore"]),
    ],
    targets: [
        .target(name: "CluecoseCore"),
    ]
)
