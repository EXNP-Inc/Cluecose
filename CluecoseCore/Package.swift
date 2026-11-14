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
        // macOS is a TEST-only floor so the pure-Swift core (and its golden
        // fixture tests) can run with `swift test` on a developer Mac. The
        // shipped apps still build for iOS/watchOS; nothing runs on macOS.
        .macOS(.v13),
    ],
    products: [
        .library(name: "CluecoseCore", targets: ["CluecoseCore"]),
    ],
    targets: [
        .target(
            name: "CluecoseCore",
            resources: [
                // The food feature's shared content: the LLM prompt template
                // (prompt.txt) and the golden test fixtures (tests/*.json).
                // These live in the MAIN target so the bundled prompt is
                // available to the app AND so the fixtures are a single
                // canonical copy the test target reads.
                .copy("Resources/food"),
            ]
        ),
        .testTarget(
            name: "CluecoseCoreTests",
            dependencies: ["CluecoseCore"]
        ),
    ]
)
