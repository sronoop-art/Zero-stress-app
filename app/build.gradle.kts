// App module build script.
// NOTE: kept intentionally conservative (no exotic DSL, ASCII only) because this
// project is built on-device with Code On The Go, whose embedded Kotlin script
// compiler is fragile with unusual constructs or non-ASCII characters.

// ---- Agora credentials ------------------------------------------------------
// Read from gradle.properties (project or ~/.gradle) or environment variables.
// Do NOT hardcode real secrets in this file - it is committed to git.
//
//   gradle.properties :  AGORA_APP_ID=f8159b2c...
//   gradle.properties :  AGORA_APP_CERTIFICATE=9032a220...
//   (or set the same names as environment variables)
val agoraAppId: String =
    (project.findProperty("AGORA_APP_ID") as String? ?: System.getenv("AGORA_APP_ID") ?: "")
val agoraAppCertificate: String =
    (project.findProperty("AGORA_APP_CERTIFICATE") as String? ?: System.getenv("AGORA_APP_CERTIFICATE") ?: "")

if (agoraAppId.isBlank()) {
    logger.warn("WARNING: AGORA_APP_ID is missing - voice join will fail immediately. Add it to gradle.properties.")
}
if (agoraAppId.isNotBlank() && agoraAppCertificate.isBlank()) {
    logger.warn("WARNING: AGORA_APP_CERTIFICATE is missing - if the Agora console has the App")
    logger.warn("    certificate enabled, voice joins will fail with Agora error 110 (invalid token).")
    logger.warn("    Add the certificate locally to gradle.properties (never commit it).")
}

plugins {
    // AGP 9.x: Kotlin is BUILT IN - do NOT re-add org.jetbrains.kotlin.android.
    // The Kotlin 2.3.21 compiler is provided via the buildscript classpath in the
    // root build.gradle.kts (Code On The Go Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.3.21 stack).
    id("com.android.application")
    // Required since Kotlin 2.0 whenever buildFeatures.compose = true.
    // Version comes from the root build.gradle.kts.
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.zerostress.manager"
    // Compose BOM 2026.08.00 artifacts (compose 1.12.x) require compileSdk 37+.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zerostress.manager"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Available in Kotlin as BuildConfig.AGORA_APP_ID / BuildConfig.AGORA_APP_CERTIFICATE.
        buildConfigField("String", "AGORA_APP_ID", "\"$agoraAppId\"")
        buildConfigField("String", "AGORA_APP_CERTIFICATE", "\"$agoraAppCertificate\"")

        // Smaller APK: only ship native .so libs for devices people actually use.
        // (Agora ships armeabi-v7a, arm64-v8a, x86, x86_64 - most phones are ARM.)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        // Optional: used only when building a signed release APK.
        // Place app/zerostress.jks next to this file (see README).
        // The keystore is NOT committed on purpose - when it is missing the
        // release build continues and just produces an unsigned APK instead of
        // failing on checkDebugAarMetadata-style file lookups.
        create("release") {
            val keystore = file("zerostress.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = "zerostress123"
                keyAlias = "zerostress"
                keyPassword = "zerostress123"
            }
        }
    }

    buildTypes {
        release {
            // Sign only when app/zerostress.jks exists next to this file.
            // This block runs in the configuration phase of EVERY build
            // invocation, so a keystore added later is picked up on the next
            // build without any sync. The loud warning replaces the silent
            // unsigned build that used to surprise people.
            val keystoreFile = file("zerostress.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
                logger.lifecycle("Release signing: app/zerostress.jks found")
            } else {
                logger.warn("==============================================================")
                logger.warn("  app/zerostress.jks NOT FOUND - release APK will be UNSIGNED")
                logger.warn("  Add the keystore and re-run (no sync needed) to sign it.")
                logger.warn("==============================================================")
            }

            // ---- APK SIZE: R8 code shrinking + resource shrinking + obfuscation ----
            isMinifyEnabled = true
            isShrinkResources = true
            // Crash logs stay readable: upload app/build/outputs/mapping/release/mapping.txt
            // to the Play Console (or keep it safe) before publishing.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // AGP 9 built-in Kotlin:
    // - composeOptions { kotlinCompilerExtensionVersion } is REMOVED - the Compose
    //   compiler now ships with the Kotlin toolchain (2.3.21) and is applied automatically.
    // - kotlinOptions { jvmTarget } is REMOVED - jvmTarget defaults to
    //   compileOptions.targetCompatibility (17) under built-in Kotlin.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Pack native libraries uncompressed-aligned (smaller install footprint,
        // no extraction step at install time). Works with useLegacyPackaging=false.
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.17.0")

    // Jetpack Compose - BOM pins every androidx.compose artifact. Tested against
    // Kotlin 2.3.21 built-in Kotlin (Compose compiler ships with the toolchain).
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.12.4")

    // Lifecycle / ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    // Firebase BOM 34.x - built for the modern (Kotlin 2.x) toolchain; safe now that
    // the project compiles with Kotlin 2.3.21. Do NOT go back to BOM 33.x.
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    // Remote Config - OTA switches and thresholds (min app version gate,
    // rank-title unlock scores, content-pack URL) without shipping a new APK.
    implementation("com.google.firebase:firebase-config")

    // Crash reporting - release crashes land in Firebase Console > Crashlytics
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")

    // App Check: Play Integrity in release, debug provider in debug builds
    // (selection happens in ZeroStressApp.kt via BuildConfig.DEBUG)
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Agora RTC 4.x - real-time voice for the Discord-style channels.
    // NOTE: the Maven Central artifact is "full-sdk" (io.agora.rtc:full-sdk),
    // NOT "agora-rtc-sdk" - that one only exists on Agora's own maven repo.
    implementation("io.agora.rtc:full-sdk:4.1.0")

    // Agora official token builder - lets the app generate RTC tokens on-device
    // from AGORA_APP_CERTIFICATE (required when the App Certificate is enabled).
    implementation("io.agora:authentication:2.1.3")

    // Unit tests (pure JVM - run with `gradlew :app:testDebugUnitTest`)
    testImplementation("junit:junit:4.13.2")
}
