# ProGuard / R8 Rules for DualBoost Downloader
# Keep OkHttp & Coroutines
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*

