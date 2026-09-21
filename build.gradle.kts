// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// AGP 9.x toolchain (Code On The Go Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.3.21 upgrade):
// - Kotlin is now BUILT INTO AGP. The org.jetbrains.kotlin.android plugin is gone.
// - The buildscript classpath below upgrades AGP's runtime Kotlin Gradle plugin to
//   Kotlin 2.3.21 (AGP 9 only requires 2.2.10+). This must stay ABOVE plugins {}.
// - AGP 9 requires Gradle 9.x and JDK 17 (gradle-wrapper.properties is pinned to 9.6.1).

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        // Required since Kotlin 2.0: enabling buildFeatures.compose needs the
        // Compose Compiler Gradle plugin on the toolchain classpath.
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("com.google.gms.google-services") version "4.5.0" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
