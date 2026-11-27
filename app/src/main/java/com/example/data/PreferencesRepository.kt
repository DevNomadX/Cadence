package com.example.data

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cadenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cadence_preferences"
)

@Keep
@Immutable
data class UserProfile(
    val hasCompletedOnboarding: Boolean = false
)

@Immutable
data class ThemePreferences(
    val palette: ExpressivePalette = ExpressivePalette.DEFAULT,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val artworkShape: ExpressiveArtworkShape = ExpressiveArtworkShape.SCALLOP,
    val wavyScrubberEnabled: Boolean = true,
    val ambientAuraEnabled: Boolean = true,
    val performanceModeEnabled: Boolean = false,
    val isAutoPerformanceMode: Boolean = false
) {
    val effectiveWavyScrubber: Boolean get() = wavyScrubberEnabled && !performanceModeEnabled
    val effectiveAmbientAura: Boolean get() = ambientAuraEnabled && !performanceModeEnabled
}

@Keep
@Immutable
data class AudioSettings(
    val gaplessPlaybackEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 0,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.DUCK,
    val acousticEnhanceEnabled: Boolean = false
)

@Immutable
data class PlaybackPreferences(
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playbackSpeed: Float = 1.0f,
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_ADDED_DESC,
    val minTrackDurationSec: Int = PreferencesRepository.DEFAULT_MIN_TRACK_DURATION_SEC
)

@Immutable
data class UserPreferences(
    val palette: ExpressivePalette = ExpressivePalette.DEFAULT,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val artworkShape: ExpressiveArtworkShape = ExpressiveArtworkShape.SCALLOP,
    val wavyScrubberEnabled: Boolean = true,
    val ambientAuraEnabled: Boolean = true,
    val performanceModeEnabled: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playbackSpeed: Float = 1.0f,
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_ADDED_DESC,
    val minTrackDurationSec: Int = PreferencesRepository.DEFAULT_MIN_TRACK_DURATION_SEC,
    val gaplessPlaybackEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 0,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.DUCK,
    val acousticEnhanceEnabled: Boolean = false,
    val legacyFavoriteTrackIds: Set<String> = emptySet(),
    val legacyImportedTrackUris: Set<String> = emptySet()
)

class PreferencesRepository(private val context: Context) {

    private val syncThemePrefs by lazy(LazyThreadSafetyMode.NONE) {
        context.applicationContext.getSharedPreferences(SYNC_THEME_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val deviceIsLowRam: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        isDeviceLowRam(context)
    }

    private object Keys {
        val PALETTE = stringPreferencesKey("expressive_palette")
        val APPEARANCE = stringPreferencesKey("appearance_mode")
        val ARTWORK_SHAPE = stringPreferencesKey("artwork_shape")
        val WAVY_SCRUBBER = booleanPreferencesKey("wavy_scrubber_enabled")
        val AMBIENT_AURA = booleanPreferencesKey("ambient_aura_enabled")
        val PERFORMANCE_MODE = booleanPreferencesKey("performance_mode_enabled")
        val SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val REPEAT = stringPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val SORT_ORDER = stringPreferencesKey("library_sort_order")
        val MIN_TRACK_DURATION_SEC = intPreferencesKey("min_track_duration_sec")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback_enabled")
        val CROSSFADE_SEC = intPreferencesKey("crossfade_duration_sec")
        val PAUSE_ON_HEADPHONE_DISCONNECT = booleanPreferencesKey("pause_on_headphone_disconnect")
        val AUDIO_FOCUS_BEHAVIOR = stringPreferencesKey("audio_focus_behavior")
        val ACOUSTIC_ENHANCE = booleanPreferencesKey("acoustic_enhance_enabled")
        val LEGACY_FAVORITES = stringSetPreferencesKey("favorite_track_ids")
        val LEGACY_IMPORTED_URIS = stringSetPreferencesKey("imported_track_uris")
        val LEGACY_MIGRATED = booleanPreferencesKey("room_migration_v1_done")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SCAN_ALL_FOLDERS = booleanPreferencesKey("scan_all_folders_enabled")
    }

    val userProfileFlow: Flow<UserProfile> = context.cadenceDataStore.data.map { prefs ->
        val rawDone = prefs[Keys.ONBOARDING_COMPLETED]
        val resolvedDone = rawDone ?: syncThemePrefs.getBoolean(SYNC_KEY_ONBOARDING_COMPLETED, false)
        if (rawDone != null && !syncThemePrefs.contains(SYNC_KEY_ONBOARDING_COMPLETED)) {
            syncThemePrefs.edit()
                .putBoolean(SYNC_KEY_ONBOARDING_COMPLETED, resolvedDone)
                .apply()
        }
        UserProfile(hasCompletedOnboarding = resolvedDone)
    }.distinctUntilChanged()

    val hasCompletedOnboarding: Flow<Boolean> = userProfileFlow
        .map { it.hasCompletedOnboarding }
        .distinctUntilChanged()

    val themePreferencesFlow: Flow<ThemePreferences> = context.cadenceDataStore.data.map { prefs ->
        val manualPerf = prefs[Keys.PERFORMANCE_MODE]
        val perfEnabled = manualPerf ?: deviceIsLowRam
        val resolved = ThemePreferences(
            palette = ExpressivePalette.fromId(prefs[Keys.PALETTE]),
            appearanceMode = AppearanceMode.fromId(prefs[Keys.APPEARANCE]),
            artworkShape = ExpressiveArtworkShape.fromId(prefs[Keys.ARTWORK_SHAPE]),
            wavyScrubberEnabled = prefs[Keys.WAVY_SCRUBBER] ?: true,
            ambientAuraEnabled = prefs[Keys.AMBIENT_AURA] ?: true,
            performanceModeEnabled = perfEnabled,
            isAutoPerformanceMode = manualPerf == null && deviceIsLowRam
        )
        // Backfill synchronous startup mirror if DataStore has values not yet mirrored
        if (!syncThemePrefs.contains(SYNC_KEY_APPEARANCE) || !syncThemePrefs.contains(SYNC_KEY_PALETTE)) {
            syncThemePrefs.edit()
                .putString(SYNC_KEY_PALETTE, resolved.palette.id)
                .putString(SYNC_KEY_APPEARANCE, resolved.appearanceMode.id)
                .putString(SYNC_KEY_ARTWORK_SHAPE, resolved.artworkShape.id)
                .putBoolean(SYNC_KEY_WAVY_SCRUBBER, resolved.wavyScrubberEnabled)
                .putBoolean(SYNC_KEY_AMBIENT_AURA, resolved.ambientAuraEnabled)
                .apply {
                    if (manualPerf != null) {
                        putBoolean(SYNC_KEY_HAS_MANUAL_PERF, true)
                        putBoolean(SYNC_KEY_PERFORMANCE_MODE, manualPerf)
                    }
                }
                .apply()
        }
        resolved
    }.distinctUntilChanged()

    val audioSettingsFlow: Flow<AudioSettings> = context.cadenceDataStore.data.map { prefs ->
        AudioSettings(
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            crossfadeDurationSec = (prefs[Keys.CROSSFADE_SEC] ?: 0).coerceIn(0, 12),
            pauseOnHeadphoneDisconnect = prefs[Keys.PAUSE_ON_HEADPHONE_DISCONNECT] ?: true,
            audioFocusBehavior = AudioFocusBehavior.fromId(prefs[Keys.AUDIO_FOCUS_BEHAVIOR]),
            acousticEnhanceEnabled = prefs[Keys.ACOUSTIC_ENHANCE] ?: false
        )
    }.distinctUntilChanged()

    val playbackPreferencesFlow: Flow<PlaybackPreferences> = context.cadenceDataStore.data.map { prefs ->
        PlaybackPreferences(
            shuffleEnabled = prefs[Keys.SHUFFLE] ?: false,
            repeatMode = RepeatMode.fromId(prefs[Keys.REPEAT]),
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            lastTrackId = prefs[Keys.LAST_TRACK_ID],
            lastPositionMs = (prefs[Keys.LAST_POSITION_MS] ?: 0L).coerceAtLeast(0L),
            sortOrder = LibrarySortOrder.fromId(prefs[Keys.SORT_ORDER]),
            minTrackDurationSec = (prefs[Keys.MIN_TRACK_DURATION_SEC] ?: DEFAULT_MIN_TRACK_DURATION_SEC).coerceIn(0, 300)
        )
    }.distinctUntilChanged()

    val preferencesFlow: Flow<UserPreferences> = context.cadenceDataStore.data.map { prefs ->
        UserPreferences(
            palette = ExpressivePalette.fromId(prefs[Keys.PALETTE]),
            appearanceMode = AppearanceMode.fromId(prefs[Keys.APPEARANCE]),
            artworkShape = ExpressiveArtworkShape.fromId(prefs[Keys.ARTWORK_SHAPE]),
            wavyScrubberEnabled = prefs[Keys.WAVY_SCRUBBER] ?: true,
            ambientAuraEnabled = prefs[Keys.AMBIENT_AURA] ?: true,
            performanceModeEnabled = prefs[Keys.PERFORMANCE_MODE] ?: deviceIsLowRam,
            shuffleEnabled = prefs[Keys.SHUFFLE] ?: false,
            repeatMode = RepeatMode.fromId(prefs[Keys.REPEAT]),
            playbackSpeed = (prefs[Keys.PLAYBACK_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            lastTrackId = prefs[Keys.LAST_TRACK_ID],
            lastPositionMs = (prefs[Keys.LAST_POSITION_MS] ?: 0L).coerceAtLeast(0L),
            sortOrder = LibrarySortOrder.fromId(prefs[Keys.SORT_ORDER]),
            minTrackDurationSec = (prefs[Keys.MIN_TRACK_DURATION_SEC] ?: DEFAULT_MIN_TRACK_DURATION_SEC).coerceIn(0, 300),
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            crossfadeDurationSec = (prefs[Keys.CROSSFADE_SEC] ?: 0).coerceIn(0, 12),
            pauseOnHeadphoneDisconnect = prefs[Keys.PAUSE_ON_HEADPHONE_DISCONNECT] ?: true,
            audioFocusBehavior = AudioFocusBehavior.fromId(prefs[Keys.AUDIO_FOCUS_BEHAVIOR]),
            acousticEnhanceEnabled = prefs[Keys.ACOUSTIC_ENHANCE] ?: false,
            legacyFavoriteTrackIds = prefs[Keys.LEGACY_FAVORITES] ?: emptySet(),
            legacyImportedTrackUris = prefs[Keys.LEGACY_IMPORTED_URIS] ?: emptySet()
        )
    }.distinctUntilChanged()

    val scanAllFoldersFlow: Flow<Boolean> = context.cadenceDataStore.data
        .map { prefs -> prefs[Keys.SCAN_ALL_FOLDERS] ?: false }
        .distinctUntilChanged()

    suspend fun isScanAllFoldersEnabled(): Boolean =
        context.cadenceDataStore.data.first()[Keys.SCAN_ALL_FOLDERS] ?: false

    suspend fun setScanAllFoldersEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.SCAN_ALL_FOLDERS] = enabled }
    }

    suspend fun getMinTrackDurationSec(): Int =
        (context.cadenceDataStore.data.first()[Keys.MIN_TRACK_DURATION_SEC] ?: DEFAULT_MIN_TRACK_DURATION_SEC).coerceIn(0, 300)

    suspend fun isLegacyMigrationDone(): Boolean =
        context.cadenceDataStore.data.first()[Keys.LEGACY_MIGRATED] ?: false

    suspend fun markLegacyMigrationDone() {
        context.cadenceDataStore.edit { it[Keys.LEGACY_MIGRATED] = true }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        syncThemePrefs.edit()
            .putBoolean(SYNC_KEY_ONBOARDING_COMPLETED, completed)
            .apply()
        context.cadenceDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun completeOnboarding() {
        setOnboardingCompleted(true)
    }

    suspend fun setPalette(palette: ExpressivePalette) {
        syncThemePrefs.edit().putString(SYNC_KEY_PALETTE, palette.id).apply()
        context.cadenceDataStore.edit { it[Keys.PALETTE] = palette.id }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        syncThemePrefs.edit().putString(SYNC_KEY_APPEARANCE, mode.id).apply()
        context.cadenceDataStore.edit { it[Keys.APPEARANCE] = mode.id }
    }

    suspend fun setArtworkShape(shape: ExpressiveArtworkShape) {
        syncThemePrefs.edit().putString(SYNC_KEY_ARTWORK_SHAPE, shape.id).apply()
        context.cadenceDataStore.edit { it[Keys.ARTWORK_SHAPE] = shape.id }
    }

    suspend fun setWavyScrubberEnabled(enabled: Boolean) {
        syncThemePrefs.edit().putBoolean(SYNC_KEY_WAVY_SCRUBBER, enabled).apply()
        context.cadenceDataStore.edit { it[Keys.WAVY_SCRUBBER] = enabled }
    }

    suspend fun setAmbientAuraEnabled(enabled: Boolean) {
        syncThemePrefs.edit().putBoolean(SYNC_KEY_AMBIENT_AURA, enabled).apply()
        context.cadenceDataStore.edit { it[Keys.AMBIENT_AURA] = enabled }
    }

    suspend fun setPerformanceModeEnabled(enabled: Boolean) {
        syncThemePrefs.edit()
            .putBoolean(SYNC_KEY_HAS_MANUAL_PERF, true)
            .putBoolean(SYNC_KEY_PERFORMANCE_MODE, enabled)
            .apply()
        context.cadenceDataStore.edit { it[Keys.PERFORMANCE_MODE] = enabled }
    }

    suspend fun setMinTrackDurationSec(seconds: Int) {
        context.cadenceDataStore.edit { it[Keys.MIN_TRACK_DURATION_SEC] = seconds.coerceIn(0, 300) }
    }

    suspend fun setGaplessPlaybackEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setCrossfadeDurationSec(seconds: Int) {
        context.cadenceDataStore.edit { it[Keys.CROSSFADE_SEC] = seconds.coerceIn(0, 12) }
    }

    suspend fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.PAUSE_ON_HEADPHONE_DISCONNECT] = enabled }
    }

    suspend fun setAudioFocusBehavior(behavior: AudioFocusBehavior) {
        context.cadenceDataStore.edit { it[Keys.AUDIO_FOCUS_BEHAVIOR] = behavior.id }
    }

    suspend fun setAcousticEnhanceEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.ACOUSTIC_ENHANCE] = enabled }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        context.cadenceDataStore.edit { it[Keys.REPEAT] = mode.id }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.cadenceDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    suspend fun setSortOrder(order: LibrarySortOrder) {
        context.cadenceDataStore.edit { it[Keys.SORT_ORDER] = order.id }
    }

    suspend fun savePlaybackPosition(trackId: String, positionMs: Long) {
        context.cadenceDataStore.edit { prefs ->
            prefs[Keys.LAST_TRACK_ID] = trackId
            prefs[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }

    companion object {
        const val DEFAULT_MIN_TRACK_DURATION_SEC = 60
        const val SYNC_THEME_PREFS_NAME = "cadence_theme_sync_prefs"
        internal const val SYNC_KEY_PALETTE = "expressive_palette"
        internal const val SYNC_KEY_APPEARANCE = "appearance_mode"
        internal const val SYNC_KEY_ARTWORK_SHAPE = "artwork_shape"
        internal const val SYNC_KEY_WAVY_SCRUBBER = "wavy_scrubber_enabled"
        internal const val SYNC_KEY_AMBIENT_AURA = "ambient_aura_enabled"
        internal const val SYNC_KEY_HAS_MANUAL_PERF = "has_manual_performance_mode"
        internal const val SYNC_KEY_PERFORMANCE_MODE = "performance_mode_enabled"
        internal const val SYNC_KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        private fun isDeviceLowRam(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return am?.isLowRamDevice == true
        }

        /**
         * Reads the mirrored onboarding state synchronously from SharedPreferences so
         * cold start resolves the onboarding gate with zero frame-1 flicker.
         */
        fun readStartupUserProfileSnapshot(context: Context): UserProfile {
            val prefs = context.applicationContext.getSharedPreferences(
                SYNC_THEME_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            return UserProfile(
                hasCompletedOnboarding = prefs.getBoolean(SYNC_KEY_ONBOARDING_COMPLETED, false)
            )
        }

        /**
         * Reads the mirrored theme settings synchronously from SharedPreferences so
         * MainActivity.onCreate() and Compose frame 1 resolve the exact user theme
         * (Light/Dark/Auto + palette) immediately with zero dark-to-light startup flash.
         */
        fun readStartupThemeSnapshot(context: Context): ThemePreferences {
            val prefs = context.applicationContext.getSharedPreferences(
                SYNC_THEME_PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val lowRam = isDeviceLowRam(context)
            val hasManualPerf = prefs.getBoolean(SYNC_KEY_HAS_MANUAL_PERF, false)
            val perfEnabled = if (hasManualPerf) {
                prefs.getBoolean(SYNC_KEY_PERFORMANCE_MODE, lowRam)
            } else {
                lowRam
            }
            return ThemePreferences(
                palette = ExpressivePalette.fromId(prefs.getString(SYNC_KEY_PALETTE, null)),
                appearanceMode = AppearanceMode.fromId(prefs.getString(SYNC_KEY_APPEARANCE, null)),
                artworkShape = ExpressiveArtworkShape.fromId(prefs.getString(SYNC_KEY_ARTWORK_SHAPE, null)),
                wavyScrubberEnabled = prefs.getBoolean(SYNC_KEY_WAVY_SCRUBBER, true),
                ambientAuraEnabled = prefs.getBoolean(SYNC_KEY_AMBIENT_AURA, true),
                performanceModeEnabled = perfEnabled,
                isAutoPerformanceMode = !hasManualPerf && lowRam
            )
        }
    }
}
