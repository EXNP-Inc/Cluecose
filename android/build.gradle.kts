plugins {
    id("com.android.application") version "8.5.2" apply false
    id("com.android.library") version "8.5.2" apply false
    kotlin("android") version "2.0.20" apply false
    kotlin("jvm") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    // JSON (de)serialization for the food storage contract (see :core FoodJson).
    // Version must match the Kotlin plugin version (2.0.20).
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
}
