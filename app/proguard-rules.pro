# Cadence R8 / ProGuard rules

# Preserve line numbers for clean crash stack traces
-keepattributes SourceFile,LineNumberTable

# Room entities, DAOs, and generated database implementation
-keep class com.example.data.db.** { *; }

# Media3 ExoPlayer & MediaSessionService components
-keep class com.example.playback.CadencePlaybackService { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# DataStore & Kotlin Coroutines
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-dontwarn kotlinx.coroutines.**

# Local Music Intelligence Engine data models
-keep class com.example.data.intelligence.** { *; }

# ViewModel reflection instantiation (AndroidViewModelFactory)
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>();
}
