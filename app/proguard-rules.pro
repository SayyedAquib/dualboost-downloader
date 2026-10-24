# ProGuard / R8 Rules for DualBoost Downloader
# Hardened Production Rules with Symbol Obfuscation & Resource Shrinking

# General attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn com.google.errorprone.annotations.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Preserve Data Models and Enums for safe state serialization
-keep class com.dualboost.downloader.data.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# SEC-AUDIT: Strip verbose and debug log calls in release builds
# Addresses: M-1 (URL leakage in logcat) + L-4 (ProGuard hardening)
# ============================================================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Obfuscate class names for additional tamper resistance
-repackageclasses 'db'
-allowaccessmodification

