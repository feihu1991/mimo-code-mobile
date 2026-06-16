# MiMo Chat - ProGuard Rules

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }

# ---- Gson ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.mimochat.MiMoConfig { *; }
-keep class com.mimochat.Message { *; }
-keep class com.mimochat.Session { *; }
-keep class com.mimochat.Part { *; }
-keep class com.mimochat.PartInput { *; }
-keep class com.mimochat.CreateSessionResponse { *; }
-keep class com.mimochat.SendMessageRequest { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- CameraX ----
-keep class androidx.camera.** { *; }

# ---- Media3/ExoPlayer ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- AndroidX Security Crypto ----
-keep class androidx.security.crypto.** { *; }
