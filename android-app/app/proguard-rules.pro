-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.mimochat.data.** { *; }
-keep class com.mimochat.service.VoiceCallConfig { *; }
-keep class com.mimochat.service.VoiceCallState { *; }
-keep class com.mimochat.service.VoiceCallState$* { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class androidx.camera.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
