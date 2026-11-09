plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Release signing is read from environment variables (CI) or ~/.gradle/gradle.properties:
//   RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
// Set all four or none. With none, the release APK is signed with the debug key, so it is
// always signed and installable, but Google Play will not accept it.
val signingKeys = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
)
val releaseSigning: Map<String, String> = signingKeys.mapNotNull { name ->
    (providers.environmentVariable(name).orNull ?: providers.gradleProperty(name).orNull ?: System.getenv(name) ?: null)
        ?.takeIf { it.isNotBlank() }
        ?.let { name to it }
}.toMap()

check(releaseSigning.isEmpty() || releaseSigning.size == signingKeys.size) {
    "Incomplete release signing config. Missing: ${signingKeys - releaseSigning.keys}"
}
if (releaseSigning.isEmpty() &&
    gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
) {
    logger.warn("No RELEASE_* signing config found: signing release with the debug key (not Play-publishable).")
}

android {
    namespace = "com.ehan.speedometer"
    // Compose 1.12.x (BOM 2026.09.00) requires compileSdk 37.
    // targetSdk stays at 36, so runtime behavior is unchanged.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ehan.speedometer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        getByName("debug") {
          val localDebugKs = file("${rootDir}/debug.keystore")
          if (localDebugKs.exists()) {
            storeFile = localDebugKs
          }
        }
        if (releaseSigning.isNotEmpty()) {
            create("release") {
                storeFile = file(releaseSigning.getValue("RELEASE_STORE_FILE"))
                storePassword = releaseSigning.getValue("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning.getValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Real key when configured, otherwise the debug key: never an unsigned APK.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
          signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest and resources.
            isIncludeAndroidResources = true
        }
    }
}

roborazzi {
    // Screenshots are written here instead of build/outputs/roborazzi.
    // Recording on plain `testDebugUnitTest` is switched on in gradle.properties.
    outputDir.set(file("src/test/screenshots"))
}

tasks.withType<Test>().configureEach {
    // Gradle's default 512 MB test heap is tight for Robolectric + Compose.
    maxHeapSize = "2g"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.play.services.location)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // implementation(libs.androidx.compose.material.icons.core)
    // implementation(libs.androidx.compose.material.icons.extended)

    // implementation(libs.androidx.media3.ui)
    // implementation(libs.androidx.media3.ui.compose)
    // implementation(libs.androidx.media3.ui.compose.material3)
    // implementation(libs.androidx.media3.exoplayer)

    debugImplementation(libs.androidx.compose.ui.tooling)
    // Registers ComponentActivity in the manifest so Compose tests can launch it.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
