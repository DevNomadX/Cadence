package com.example.data

import android.app.ActivityManager
import android.content.Context
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

@Immutable
data class ThemePreferences(
    val palette: ExpressivePalette = ExpressivePalette.TERRACOTTA,
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

@Immutable
data class AudioSettings(
    val gaplessPlaybackEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 0,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.DUCK
)

@Immutable
data class PlaybackPreferences(
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playbackSpeed: Float = 1.0f,
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.DATE_ADDED_DESC,
    val minTrackDurationSec: Int = 0
)

@Immutable
data class UserPreferences(
    val palette: ExpressivePalette = ExpressivePalette.TERRACOTTA,
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
    val minTrackDurationSec: Int = 0,
    val gaplessPlaybackEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 0,
    val pauseOnHeadphoneDisconnect: Boolean = true,
    val audioFocusBehavior: AudioFocusBehavior = AudioFocusBehavior.DUCK,
    val legacyFavoriteTrackIds: Set<String> = emptySet(),
    val legacyImportedTrackUris: Set<String> = emptySet()
)

class PreferencesRepository(private val context: Context) {

    private val deviceIsLowRam: Boolean by lazy(LazyThreadSafetyMode.NONE) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
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
        val LEGACY_FAVORITES = stringSetPreferencesKey("favorite_track_ids")
        val LEGACY_IMPORTED_URIS = stringSetPreferencesKey("imported_track_uris")
        val LEGACY_MIGRATED = booleanPreferencesKey("room_migration_v1_done")
    }

    val themePreferencesFlow: Flow<ThemePreferences> = context.cadenceDataStore.data.map { prefs ->
        val manualPerf = prefs[Keys.PERFORMANCE_MODE]
        val perfEnabled = manualPerf ?: deviceIsLowRam
        ThemePreferences(
            palette = ExpressivePalette.fromId(prefs[Keys.PALETTE]),
            appearanceMode = AppearanceMode.fromId(prefs[Keys.APPEARANCE]),
            artworkShape = ExpressiveArtworkShape.fromId(prefs[Keys.ARTWORK_SHAPE]),
            wavyScrubberEnabled = prefs[Keys.WAVY_SCRUBBER] ?: true,
            ambientAuraEnabled = prefs[Keys.AMBIENT_AURA] ?: true,
            performanceModeEnabled = perfEnabled,
            isAutoPerformanceMode = manualPerf == null && deviceIsLowRam
        )
    }.distinctUntilChanged()

    val audioSettingsFlow: Flow<AudioSettings> = context.cadenceDataStore.data.map { prefs ->
        AudioSettings(
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            crossfadeDurationSec = (prefs[Keys.CROSSFADE_SEC] ?: 0).coerceIn(0, 10),
            pauseOnHeadphoneDisconnect = prefs[Keys.PAUSE_ON_HEADPHONE_DISCONNECT] ?: true,
            audioFocusBehavior = AudioFocusBehavior.fromId(prefs[Keys.AUDIO_FOCUS_BEHAVIOR])
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
            minTrackDurationSec = (prefs[Keys.MIN_TRACK_DURATION_SEC] ?: 0).coerceIn(0, 300)
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
            minTrackDurationSec = (prefs[Keys.MIN_TRACK_DURATION_SEC] ?: 0).coerceIn(0, 300),
            gaplessPlaybackEnabled = prefs[Keys.GAPLESS_PLAYBACK] ?: true,
            crossfadeDurationSec = (prefs[Keys.CROSSFADE_SEC] ?: 0).coerceIn(0, 10),
            pauseOnHeadphoneDisconnect = prefs[Keys.PAUSE_ON_HEADPHONE_DISCONNECT] ?: true,
            audioFocusBehavior = AudioFocusBehavior.fromId(prefs[Keys.AUDIO_FOCUS_BEHAVIOR]),
            legacyFavoriteTrackIds = prefs[Keys.LEGACY_FAVORITES] ?: emptySet(),
            legacyImportedTrackUris = prefs[Keys.LEGACY_IMPORTED_URIS] ?: emptySet()
        )
    }.distinctUntilChanged()

    suspend fun getMinTrackDurationSec(): Int =
        (context.cadenceDataStore.data.first()[Keys.MIN_TRACK_DURATION_SEC] ?: 0).coerceIn(0, 300)

    suspend fun isLegacyMigrationDone(): Boolean =
        context.cadenceDataStore.data.first()[Keys.LEGACY_MIGRATED] ?: false

    suspend fun markLegacyMigrationDone() {
        context.cadenceDataStore.edit { it[Keys.LEGACY_MIGRATED] = true }
    }

    suspend fun setPalette(palette: ExpressivePalette) {
        context.cadenceDataStore.edit { it[Keys.PALETTE] = palette.id }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.cadenceDataStore.edit { it[Keys.APPEARANCE] = mode.id }
    }

    suspend fun setArtworkShape(shape: ExpressiveArtworkShape) {
        context.cadenceDataStore.edit { it[Keys.ARTWORK_SHAPE] = shape.id }
    }

    suspend fun setWavyScrubberEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.WAVY_SCRUBBER] = enabled }
    }

    suspend fun setAmbientAuraEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.AMBIENT_AURA] = enabled }
    }

    suspend fun setPerformanceModeEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.PERFORMANCE_MODE] = enabled }
    }

    suspend fun setMinTrackDurationSec(seconds: Int) {
        context.cadenceDataStore.edit { it[Keys.MIN_TRACK_DURATION_SEC] = seconds.coerceIn(0, 300) }
    }

    suspend fun setGaplessPlaybackEnabled(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.GAPLESS_PLAYBACK] = enabled }
    }

    suspend fun setCrossfadeDurationSec(seconds: Int) {
        context.cadenceDataStore.edit { it[Keys.CROSSFADE_SEC] = seconds.coerceIn(0, 10) }
    }

    suspend fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        context.cadenceDataStore.edit { it[Keys.PAUSE_ON_HEADPHONE_DISCONNECT] = enabled }
    }

    suspend fun setAudioFocusBehavior(behavior: AudioFocusBehavior) {
        context.cadenceDataStore.edit { it[Keys.AUDIO_FOCUS_BEHAVIOR] = behavior.id }
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
}
