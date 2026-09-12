# Zero Stress — R8 / ProGuard rules
# Release builds run R8 with proguard-android-optimize.txt; these rules keep
# the reflection-sensitive parts of the app and Firebase working.

# ---- Firebase Firestore data models (reflection-based serialization) ----
-keep class com.zerostress.manager.models.** { *; }
-keep class com.zerostress.manager.repository.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.IgnoreExtraProperties *;
}

# ---- Firestore / Firebase ----
# Firestore uses reflection over its internal transport and annotation classes.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ---- Agora RTC (JNI: native code calls back into these classes by name) ----
-keep class io.agora.** { *; }
-dontwarn io.agora.**

# ---- Kotlin coroutines ----
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ---- Jetpack Compose runtime ----
-dontwarn androidx.compose.**

# ---- OkHttp / Okio (pulled in transitively by Firestore) ----
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- javax annotations used by generated code ----
-dontwarn javax.annotation.**

# ---- Keep source file + line numbers for readable crash stack traces ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
