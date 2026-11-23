# Cadence R8 / ProGuard rules

# Preserve line numbers for clean crash stack traces
-keepattributes SourceFile,LineNumberTable

# Room entities, DAOs, and generated database implementation
-keep class com.example.data.db.** { *; }

# Media3 ExoPlayer & MediaSessionService components
-keep class com.example.playback.CadencePlaybackService { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# DataStore, @Keep annotations & Kotlin Coroutines
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keepclassmembers enum com.example.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn kotlinx.coroutines.**

# Local Music Intelligence Engine, Discover & Track data models
-keep class com.example.data.intelligence.** { *; }
-keep class com.example.data.Track { *; }
-keep class com.example.data.StableList { *; }
-keep class com.example.data.StableMap { *; }
-keep class com.example.ui.CadenceTab { *; }
-keep class com.example.ui.player.DiscoverUiState { *; }
-keep class com.example.ui.player.SmartPlaylistUiState { *; }

# ViewModel reflection instantiation (AndroidViewModelFactory)
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>();
}

# Jetpack Compose Runtime & Stacktrace / Group Mapping compatibility with R8
-keep class androidx.compose.runtime.tooling.** { *; }
-keep class androidx.compose.runtime.Composer { *; }
-keep class androidx.compose.runtime.ComposerImpl { *; }
-dontwarn androidx.compose.runtime.tooling.**
-dontwarn androidx.compose.ui.tooling.**
