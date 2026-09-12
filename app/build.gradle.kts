plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// ---- Agora credentials ------------------------------------------------------
// Read from gradle.properties (project or ~/.gradle) or environment variables.
// Do NOT hardcode real secrets in this file — it is committed to git.
//
//   gradle.properties :  AGORA_APP_ID=f8159b2c...
//   gradle.properties :  AGORA_APP_CERTIFICATE=9032a220...
//   (or set the same names as environment variables)
val agoraAppId: String =
    (project.findProperty("AGORA_APP_ID") as String? ?: System.getenv("AGORA_APP_ID") ?: "")
val agoraAppCertificate: String =
    (project.findProperty("AGORA_APP_CERTIFICATE") as String? ?: System.getenv("AGORA_APP_CERTIFICATE") ?: "")

if (agoraAppId.isBlank()) {
    logger.warn("⚠️  AGORA_APP_ID is missing — voice join will fail immediately. Add it to gradle.properties.")
}
if (agoraAppId.isNotBlank() && agoraAppCertificate.isBlank()) {
    logger.warn("⚠️  AGORA_APP_CERTIFICATE is missing — if the Agora console has the App Certificate")
    logger.warn("    enabled, voice joins will fail with Agora error 110 (invalid token). Add the")
    logger.warn("    certificate locally to gradle.properties (never commit it).")
}

android {
    namespace = "com.zerostress.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zerostress.manager"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Available in Kotlin as BuildConfig.AGORA_APP_ID / BuildConfig.AGORA_APP_CERTIFICATE.
        // Empty string = token auth disabled in Agora (fine for the App-Certificate-less
        // test mode; with a certificate enabled you must use token mode below).
        buildConfigField("String", "AGORA_APP_ID", "\"$agoraAppId\"")
        buildConfigField("String", "AGORA_APP_CERTIFICATE", "\"$agoraAppCertificate\"")

        // Smaller APK: only ship native .so libs for devices people actually use.
        // (Agora ships armeabi-v7a, arm64-v8a, x86, x86_64 — most phones are ARM.)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        // Optional: used only when building a signed release APK.
        // Place app/zerostress.jks next to this file (see README).
        create("release") {
            storeFile = file("zerostress.jks")
            storePassword = "zerostress123"
            keyAlias = "zerostress"
            keyPassword = "zerostress123"
        }
    }

    buildTypes {
        release {
            // Comment out the line below if you build release APKs without app/zerostress.jks
            signingConfig = signingConfigs.getByName("release")

            // ---- APK SIZE: R8 code shrinking + resource shrinking + obfuscation ----
            isMinifyEnabled = true
            isShrinkResources = true
            // Crash logs stay readable: upload app/build/outputs/mapping/release/mapping.txt
            // to the Play Console (or keep it safe) before publishing.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Keep a copy of the obfuscation map so crashes can be de-obfuscated.
            debugSymbolLevel = "SYMBOL_TABLE"
        }
        debug {
            // Debug builds stay unshrunk for fast iteration.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compose compiler matched to Kotlin 1.9.24 (classic composeOptions setup —
    // the kotlin.plugin.compose helper only exists for Kotlin 2.x)
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Trim native-lib metadata that ends up duplicated in the APK.
            excludes += "/META-INF/*.version"
        }
        // Compress native libraries into the APK (smaller download, a bit more RAM at runtime).
        jniLibs {
            useLegacyPackaging = false
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Firebase BOM — 33.1.2 is the newest line whose artifacts are compiled with
    // Kotlin 1.8/1.9 metadata (readable by the Kotlin 1.9.24 compiler AndroidIDE uses).
    // Do NOT jump to BOM 34.x: those artifacts (firebase-auth 24.x, play-services
    // measurement 23.x) carry Kotlin 2.2+ metadata and fail on-device.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")

    // Firebase App Check (debug provider — replace with a Play Integrity /
    // DeviceCheck provider before releasing to production)
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // Agora RTC 4.x — real-time voice for the Discord-style channels.
    // NOTE: the Maven Central artifact is "full-sdk" (io.agora.rtc:full-sdk),
    // NOT "agora-rtc-sdk" — that one only exists on Agora's own maven repo.
    implementation("io.agora.rtc:full-sdk:4.1.0")

    // Agora official token builder — lets the app generate RTC tokens on-device
    // from AGORA_APP_CERTIFICATE (required when the App Certificate is enabled).
    implementation("io.agora:authentication:2.1.3")
}
