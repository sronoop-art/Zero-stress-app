// Top-level build file for the ZsStudio app.
// Same toolchain as the main ONLY TEAM-X app (Code On The Go friendly):
// Gradle 9.6.1 / AGP 9.3.1 / Kotlin 2.3.21 built into AGP.
// NO Firebase / Agora plugins here - this is a fully offline design tool.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
