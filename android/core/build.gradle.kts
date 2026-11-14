plugins {
    kotlin("jvm")
    // JSON (de)serialization for the food domain types / storage contract.
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // JSON (de)serialization for the food domain types / storage contract.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(kotlin("test"))
}

// The food golden fixtures (parse-*/insight-*/patterns-*.json) and the shared
// prompt template (prompt.txt) live ONCE in the Swift package resources, so the
// Kotlin tests and the Swift tests run the exact same files — the cross-platform
// parity mechanism. Pointing the test-resources srcDir at the shared `Resources`
// directory puts them on the test classpath under the same layout the Swift
// bundle uses (`food/prompt.txt`, `food/tests/*.json`), mirroring how the
// existing RuntimeTables .bin files are already shared (core/src/test/resources/
// RuntimeTables is a copy of the same shared tree).
sourceSets {
    test {
        // `android/core` → two up → repo root `Cluecose` → shared resources.
        resources.srcDir("../../CluecoseCore/Sources/CluecoseCore/Resources")
    }
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
