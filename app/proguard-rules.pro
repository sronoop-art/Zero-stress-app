# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.zerostress.manager.models.** { *; }
-keep class com.zerostress.manager.repository.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.IgnoreExtraProperties *;
}
