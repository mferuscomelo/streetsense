plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.streetsense.app"
    // compileSdk must match a platform actually installed in ~/Android/Sdk —
    // 37 is what's there, and is also AGP 9.3.1's highest supported API.
    // targetSdk stays at 36 deliberately: it's just a manifest number needing
    // no installed platform, and holding it back avoids opting into Android
    // 17's newest behaviour changes. Cleartext is still restricted at 36, so
    // the network security config below is still required.
    compileSdk = 37

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
    // OpenStreetMap tiles for the session map — chosen over the Google Maps
    // SDK specifically to avoid an API key, a billing account, and
    // proprietary tile terms. See docs/attribution.md.
    implementation(libs.osmdroid)
    testImplementation(libs.junit)
}
