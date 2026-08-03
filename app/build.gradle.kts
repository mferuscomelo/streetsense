plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.streetsense.app"
    // NOTE: compileSdk/targetSdk are pinned to whatever was current when this
    // was written (Android Studio 2026.x era). Bump both to match whatever
    // your installed Android Studio ships, per the plan ("Android SDK is
    // yours") — this repo doesn't pin a specific Studio version.
    compileSdk = 36

    defaultConfig {
        applicationId = "io.streetsense.app"
        minSdk = 31 // Android 12+ only — skips the entire pre-S Bluetooth permission branch
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // Android's build toolchain caps source/target compatibility at 17 —
        // see developer.android.com/build/jdks. Java 26 is claimed on the
        // backend only; this app deliberately targets 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.appcompat)
    testImplementation(libs.junit)
}
