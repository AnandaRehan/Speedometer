// Top-level build file. Plugin versions live in the version catalog.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false

    // Never applied: AGP 9 compiles Kotlin itself (built-in Kotlin).
    // Declared only to pin the Kotlin Gradle plugin to the same version as the
    // Compose compiler plugin above.
    alias(libs.plugins.kotlin.android) apply false
}
