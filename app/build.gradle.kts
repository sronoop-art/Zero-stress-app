plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Compose compiler matched to Kotlin 1.9.24 (classic composeOptions setup —
    // the kotlin.plugin.compose helper only exists for Kotlin 2.x)
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
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
    implementation("com.google.firebase:firebase-storage")

    // Firebase App Check (debug provider — replace with a Play Integrity /
    // DeviceCheck provider before releasing to production)
    implementation("com.google.firebase:firebase-appcheck")
    implementation("com.google.firebase:firebase-appcheck-debug")

    // OkHttp (used for signaling helpers)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // SwipeRefreshLayout (kept for pull-to-refresh where needed)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}