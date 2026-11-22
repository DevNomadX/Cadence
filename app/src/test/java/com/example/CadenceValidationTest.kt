package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudioFocusBehavior
import com.example.data.AudioSettings
import com.example.data.LibraryCategory
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.PreferencesRepository
import com.example.data.RepeatMode
import com.example.data.SavedFolderItem
import com.example.data.SleepTimerOption
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.db.TrackEntity
import com.example.data.library.ArtworkCacheManager
import com.example.data.library.LibraryRepository
import com.example.data.library.ScanState
import com.example.data.toStableList
import com.example.playback.CadencePlaybackService
import com.example.playback.MediaItemMapper
import com.example.playback.PlaybackProgress
import com.example.ui.FullScreenScanningView
import com.example.ui.player.LibraryDrillDown
import com.example.ui.player.LibraryUiState
import com.example.ui.player.NowPlayingUiState
import com.example.ui.player.SleepTimerState
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Comprehensive validation suite covering recomposition isolation, Compose UI interactions,
 * SettingsScreen controls, PreferencesRepository round-trip persistence, ArtworkCacheManager safety,
 * MediaItemMapper, Room cascade integrity, back navigation, and Manifest/Service registration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CadenceValidationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTracks = listOf(
        Track(
            id = "local_1",
            title = "Amber Solstice",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Ambient",
            durationMs = 180_000L,
            bpm = 84,
            localUriString = "content://media/external/audio/media/1",
            folderName = "Music",
            folderPath = "Music",
            isUserImported = true
        ),
        Track(
            id = "local_2",
            title = "Velvet Nocturne",
            artist = "Miro & Sol",
            album = "Afterglow",
            genre = "Electronic",
            durationMs = 210_000L,
            bpm = 102,
            localUriString = "content://media/external/audio/media/2",
            folderName = "Electronic",
            folderPath = "Music/Electronic",
            isUserImported = true
        )
    )

    // --- 1. Manifest & MediaSessionService Registration ---

    @Test
    fun `manifest declares CadencePlaybackService with MediaSessionService action and mediaPlayback foreground type`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager

        val component = ComponentName(context, CadencePlaybackService::class.java)
        val serviceInfo = pm.getServiceInfo(component, 0)
        assertTrue("Service should be exported for MediaController/SystemUI", serviceInfo.exported)
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        val resolved = pm.queryIntentServices(
            Intent("androidx.media3.session.MediaSessionService").setPackage(context.packageName),
            0
        )
        assertTrue(
            "CadencePlaybackService must respond to MediaSessionService intent",
            resolved.any { it.serviceInfo.name == CadencePlaybackService::class.java.name }
        )
    }

    // --- 2. Performance Validation: Recomposition Isolation ---

    @Test
    fun `progress ticks and sleep timer countdown do not recompose parent NowPlaying container`() {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 0L, durationMs = 180_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState(SleepTimerOption.FIFTEEN_MIN, remainingMs = 900_000L))
        val uiState = NowPlayingUiState(
            currentTrack = sampleTracks.first(),
            queueTracks = sampleTracks.toStableList(),
            currentQueueIndex = 0,
            isPlaying = false
        )
        var parentRecompositionCount = 0

        composeRule.setContent {
            SideEffect { parentRecompositionCount++ }
            CadenceTheme(
                palette = ExpressivePalette.TERRACOTTA,
                appearanceMode = AppearanceMode.LIGHT
            ) {
                NowPlayingScreen(
                    uiState = uiState,
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.TERRACOTTA,
                        appearanceMode = AppearanceMode.LIGHT
                    ),
                    progressFlow = progressFlow,
                    sleepTimerFlow = sleepTimerFlow,
                    playlists = StableList(),
                    onTogglePlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onCycleSpeed = {},
                    onMatchMood = {},
                    onOpenSettings = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    onSelectQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    onRemoveQueueItem = {},
                    onClearQueue = {},
                    onSaveQueueAsPlaylist = {},
                    onAddCurrentToPlaylist = {},
                    onCreatePlaylistWithCurrent = {},
                    onPlayNextCurrent = {},
                    onAddCurrentToQueue = {},
                    onSelectSleepTimer = {}
                )
            }
        }

        composeRule.waitForIdle()
        val baselineRecompositions = parentRecompositionCount
        assertEquals(1, baselineRecompositions)

        // Emit 10 high-frequency playback progress ticks (simulating 250ms polling)
        for (tick in 1..10) {
            progressFlow.value = PlaybackProgress(positionMs = tick * 250L, durationMs = 180_000L)
        }
        // Emit 5 sleep timer 1-second countdown ticks
        for (sec in 1..5) {
            sleepTimerFlow.value = SleepTimerState(
                option = SleepTimerOption.FIFTEEN_MIN,
                remainingMs = 900_000L - sec * 1_000L
            )
        }
        composeRule.waitForIdle()

        assertEquals(
            "Parent composable must NOT recompose when progressFlow or sleepTimerFlow ticks",
            baselineRecompositions,
            parentRecompositionCount
        )
        composeRule.onNodeWithTag("timer_pill").assertTextContains("14:55")
    }

    // --- 3. Compose UI Critical Flows ---

    @Test
    fun `NowPlayingScreen empty state renders scan prompts and settings trigger`() {
        var settingsOpened = false
        var folderAdded = false
        var deviceScanned = false

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                NowPlayingScreen(
                    uiState = NowPlayingUiState(currentTrack = null, queueTracks = StableList()),
                    themePrefs = ThemePreferences(),
                    progressFlow = MutableStateFlow(PlaybackProgress()),
                    sleepTimerFlow = MutableStateFlow(SleepTimerState()),
                    playlists = StableList(),
                    onTogglePlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onCycleSpeed = {},
                    onMatchMood = {},
                    onOpenSettings = { settingsOpened = true },
                    onAddFolder = { folderAdded = true },
                    onScanDevice = { deviceScanned = true },
                    onSelectQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    onRemoveQueueItem = {},
                    onClearQueue = {},
                    onSaveQueueAsPlaylist = {},
                    onAddCurrentToPlaylist = {},
                    onCreatePlaylistWithCurrent = {},
                    onPlayNextCurrent = {},
                    onAddCurrentToQueue = {},
                    onSelectSleepTimer = {}
                )
            }
        }

        composeRule.onNodeWithTag("empty_now_playing_art").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("empty_scan_folder_button").performClick()
        composeRule.onNodeWithTag("empty_scan_device_button").performClick()

        assertTrue(settingsOpened)
        assertTrue(folderAdded)
        assertTrue(deviceScanned)
    }

    @Test
    fun `NowPlayingScreen transport controls sleep timer sheet and queue sheet work`() {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 12_000L, durationMs = 180_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())
        var playToggled = false
        var nextClicked = false
        var prevClicked = false
        var shuffleClicked = false
        var repeatClicked = false
        var speedClicked = false
        var favoriteClicked = false
        var settingsClicked = false
        var selectedTimer: SleepTimerOption? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                NowPlayingScreen(
                    uiState = NowPlayingUiState(
                        currentTrack = sampleTracks.first(),
                        queueTracks = sampleTracks.toStableList(),
                        currentQueueIndex = 0,
                        isPlaying = false,
                        playbackSpeed = 1.0f,
                        shuffleEnabled = false,
                        repeatMode = RepeatMode.ALL
                    ),
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.TERRACOTTA,
                        appearanceMode = AppearanceMode.LIGHT
                    ),
                    progressFlow = progressFlow,
                    sleepTimerFlow = sleepTimerFlow,
                    playlists = StableList(),
                    onTogglePlayPause = { playToggled = true },
                    onNext = { nextClicked = true },
                    onPrevious = { prevClicked = true },
                    onSeek = {},
                    onToggleShuffle = { shuffleClicked = true },
                    onCycleRepeat = { repeatClicked = true },
                    onToggleFavorite = { favoriteClicked = true },
                    onCycleSpeed = { speedClicked = true },
                    onMatchMood = {},
                    onOpenSettings = { settingsClicked = true },
                    onAddFolder = {},
                    onScanDevice = {},
                    onSelectQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    onRemoveQueueItem = {},
                    onClearQueue = {},
                    onSaveQueueAsPlaylist = {},
                    onAddCurrentToPlaylist = {},
                    onCreatePlaylistWithCurrent = {},
                    onPlayNextCurrent = {},
                    onAddCurrentToQueue = {},
                    onSelectSleepTimer = { selectedTimer = it }
                )
            }
        }

        composeRule.onNodeWithTag("now_playing_title").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("play_pause_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("next_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("previous_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("shuffle_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("repeat_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("favorite_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("speed_pill").performScrollTo().performClick()

        assertTrue(settingsClicked)
        assertTrue(playToggled)
        assertTrue(nextClicked)
        assertTrue(prevClicked)
        assertTrue(shuffleClicked)
        assertTrue(repeatClicked)
        assertTrue(favoriteClicked)
        assertTrue(speedClicked)

        // Open Sleep Timer sheet and select End of Track
        composeRule.onNodeWithTag("timer_pill").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("End of track").performClick()
        assertEquals(SleepTimerOption.END_OF_TRACK, selectedTimer)

        // Open Up Next Queue sheet and verify queue items render
        composeRule.onNodeWithTag("queue_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("queue_item_0").assertIsDisplayed()
    }

    @Test
    fun `LibraryScreen search category filter settings button and drilldown emit expected events`() {
        var latestQuery = ""
        var latestCategory: LibraryCategory? = null
        var drillClosed = false
        var settingsOpened = false
        val tracks = sampleTracks
        val samplePlaylists = listOf(
            PlaylistSummary(id = 1L, name = "Focus Mix", trackCount = 2, coverTrack = tracks.first())
        )

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = tracks.toStableList(),
                        displayedTracks = tracks.toStableList(),
                        albums = LibraryRepository.groupAlbums(tracks).toStableList(),
                        artists = LibraryRepository.groupArtists(tracks).toStableList(),
                        folders = LibraryRepository.groupFolders(tracks).toStableList(),
                        genres = LibraryRepository.groupGenres(tracks).toStableList(),
                        playlists = samplePlaylists.toStableList(),
                        category = LibraryCategory.SONGS
                    ),
                    currentTrackId = tracks.first().id,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SQUIRCLE,
                    onSelectTrack = { _, _ -> },
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onSearchChange = { latestQuery = it },
                    onCategoryChange = { latestCategory = it },
                    onSortChange = {},
                    onOpenDrillDown = {},
                    onCloseDrillDown = { drillClosed = true },
                    onImportFiles = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    onRescanFolders = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddTrackToPlaylist = { _, _ -> },
                    onRemoveTrackFromPlaylist = { _, _ -> },
                    onCreatePlaylist = { _, _ -> },
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onPlayPlaylist = { _, _ -> },
                    onRemoveTrack = {},
                    onOpenSettings = { settingsOpened = true }
                )
            }
        }

        composeRule.onNodeWithTag("library_settings_button").performClick()
        assertTrue(settingsOpened)

        composeRule.onNodeWithTag("library_search_input").performTextInput("Solstice")
        assertEquals("Solstice", latestQuery)

        composeRule.onNodeWithTag("filter_albums").performClick()
        assertEquals(LibraryCategory.ALBUMS, latestCategory)

        composeRule.onNodeWithTag("track_row_${tracks.first().id}").assertIsDisplayed()
        assertFalse(drillClosed)
    }

    @Test
    fun `SettingsScreen theme library audio and performance controls invoke callbacks`() {
        var selectedPalette: ExpressivePalette? = null
        var selectedAppearance: AppearanceMode? = null
        var selectedShape: ExpressiveArtworkShape? = null
        var wavyChanged: Boolean? = null
        var auraChanged: Boolean? = null
        var perfChanged: Boolean? = null
        var addFolderClicked = false
        var removedFolderUri: String? = null
        var rescanClicked = false
        var minDurationPicked: Int? = null
        var gaplessChanged: Boolean? = null
        var headphoneChanged: Boolean? = null
        var audioFocusPicked: AudioFocusBehavior? = null
        var backClicked = false

        val folders = listOf(
            SavedFolderItem(treeUri = "content://com.android.externalstorage/tree/primary:Music", displayName = "Music")
        ).toStableList()

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.SYSTEM) {
                SettingsScreen(
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.TERRACOTTA,
                        appearanceMode = AppearanceMode.SYSTEM,
                        artworkShape = ExpressiveArtworkShape.SQUIRCLE,
                        wavyScrubberEnabled = true,
                        ambientAuraEnabled = true,
                        performanceModeEnabled = false
                    ),
                    audioSettings = AudioSettings(
                        gaplessPlaybackEnabled = true,
                        crossfadeDurationSec = 0,
                        pauseOnHeadphoneDisconnect = true,
                        audioFocusBehavior = AudioFocusBehavior.DUCK
                    ),
                    savedFolders = folders,
                    minTrackDurationSec = 0,
                    currentTrack = sampleTracks.first(),
                    isPlaying = false,
                    onBack = { backClicked = true },
                    onSelectPalette = { selectedPalette = it },
                    onSelectAppearance = { selectedAppearance = it },
                    onSelectArtworkShape = { selectedShape = it },
                    onToggleWavyScrubber = { wavyChanged = it },
                    onToggleAmbientAura = { auraChanged = it },
                    onTogglePerformanceMode = { perfChanged = it },
                    onAddFolder = { addFolderClicked = true },
                    onRemoveFolder = { removedFolderUri = it },
                    onRescanFolders = { rescanClicked = true },
                    onSelectMinTrackDurationSec = { minDurationPicked = it },
                    onToggleGaplessPlayback = { gaplessChanged = it },
                    onChangeCrossfadeSec = {},
                    onTogglePauseOnHeadphoneDisconnect = { headphoneChanged = it },
                    onSelectAudioFocusBehavior = { audioFocusPicked = it }
                )
            }
        }

        // Theme Studio controls
        composeRule.onNodeWithTag("settings_back_button").performClick()
        assertTrue(backClicked)

        composeRule.onNodeWithTag("appearance_${AppearanceMode.DARK.id}").performClick()
        assertEquals(AppearanceMode.DARK, selectedAppearance)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("palette_${ExpressivePalette.COBALT.id}"))
        composeRule.onNodeWithTag("palette_${ExpressivePalette.COBALT.id}").performClick()
        assertEquals(ExpressivePalette.COBALT, selectedPalette)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("shape_${ExpressiveArtworkShape.CLOVER.id}"))
        composeRule.onNodeWithTag("shape_${ExpressiveArtworkShape.CLOVER.id}").performClick()
        assertEquals(ExpressiveArtworkShape.CLOVER, selectedShape)

        // Library & Folders controls
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("remove_folder_0"))
        composeRule.onNodeWithTag("remove_folder_0").performClick()
        assertEquals("content://com.android.externalstorage/tree/primary:Music", removedFolderUri)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_add_folder_button"))
        composeRule.onNodeWithTag("settings_add_folder_button").performClick()
        assertTrue(addFolderClicked)

        composeRule.onNodeWithTag("settings_rescan_button").performClick()
        assertTrue(rescanClicked)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("min_duration_30"))
        composeRule.onNodeWithTag("min_duration_30").performClick()
        assertEquals(30, minDurationPicked)

        // Playback & Audio controls
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_gapless"))
        composeRule.onNodeWithTag("toggle_gapless").performClick()
        assertEquals(false, gaplessChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_headphone_pause"))
        composeRule.onNodeWithTag("toggle_headphone_pause").performClick()
        assertEquals(false, headphoneChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("audio_focus_${AudioFocusBehavior.PAUSE.id}"))
        composeRule.onNodeWithTag("audio_focus_${AudioFocusBehavior.PAUSE.id}").performClick()
        assertEquals(AudioFocusBehavior.PAUSE, audioFocusPicked)

        // Performance & Interface controls
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_performance_mode"))
        composeRule.onNodeWithTag("toggle_performance_mode").performClick()
        assertEquals(true, perfChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_wavy_scrubber"))
        composeRule.onNodeWithTag("toggle_wavy_scrubber").performClick()
        assertEquals(false, wavyChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_ambient_aura"))
        composeRule.onNodeWithTag("toggle_ambient_aura").performClick()
        assertEquals(false, auraChanged)
    }

    // --- 4. PreferencesRepository Round-Trip & Read-Only Migration Flag ---

    @Test
    fun `PreferencesRepository persists all theme audio library and performance settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)

        assertFalse(repo.isLegacyMigrationDone())
        assertFalse(repo.isLegacyMigrationDone())

        repo.markLegacyMigrationDone()
        assertTrue(repo.isLegacyMigrationDone())

        repo.setPalette(ExpressivePalette.TERRACOTTA)
        repo.setAppearanceMode(AppearanceMode.DARK)
        repo.setArtworkShape(ExpressiveArtworkShape.ARCH)
        repo.setWavyScrubberEnabled(false)
        repo.setAmbientAuraEnabled(false)
        repo.setPerformanceModeEnabled(true)

        val theme = repo.themePreferencesFlow.first()
        assertEquals(ExpressivePalette.TERRACOTTA, theme.palette)
        assertEquals(AppearanceMode.DARK, theme.appearanceMode)
        assertEquals(ExpressiveArtworkShape.ARCH, theme.artworkShape)
        assertFalse(theme.wavyScrubberEnabled)
        assertFalse(theme.ambientAuraEnabled)
        assertTrue(theme.performanceModeEnabled)
        assertFalse(theme.effectiveWavyScrubber)
        assertFalse(theme.effectiveAmbientAura)

        repo.setGaplessPlaybackEnabled(false)
        repo.setCrossfadeDurationSec(6)
        repo.setPauseOnHeadphoneDisconnect(false)
        repo.setAudioFocusBehavior(AudioFocusBehavior.PAUSE)

        val audio = repo.audioSettingsFlow.first()
        assertFalse(audio.gaplessPlaybackEnabled)
        assertEquals(6, audio.crossfadeDurationSec)
        assertFalse(audio.pauseOnHeadphoneDisconnect)
        assertEquals(AudioFocusBehavior.PAUSE, audio.audioFocusBehavior)

        assertEquals(60, repo.getMinTrackDurationSec())

        repo.setShuffleEnabled(true)
        repo.setRepeatMode(RepeatMode.ONE)
        repo.setPlaybackSpeed(1.25f)
        repo.setSortOrder(LibrarySortOrder.ARTIST_ASC)
        repo.setMinTrackDurationSec(30)
        repo.savePlaybackPosition("local_1", 42_500L)

        val playback = repo.playbackPreferencesFlow.first()
        assertTrue(playback.shuffleEnabled)
        assertEquals(RepeatMode.ONE, playback.repeatMode)
        assertEquals(1.25f, playback.playbackSpeed, 0.001f)
        assertEquals(LibrarySortOrder.ARTIST_ASC, playback.sortOrder)
        assertEquals(30, playback.minTrackDurationSec)
        assertEquals("local_1", playback.lastTrackId)
        assertEquals(42_500L, playback.lastPositionMs)
    }

    @Test
    fun `LibraryScreen track row cards maintain uniform compact height even with very long artist names`() {
        val shortTrack = Track(
            id = "short_row",
            title = "Amber Solstice",
            artist = "Klara Lind",
            album = "Warm Horizon",
            durationMs = 185_000L
        )
        val longTrack = Track(
            id = "long_row",
            title = "Tum Ho (From Rockstar Original Motion Picture Soundtrack)",
            artist = "Mohit Chauhan, Suzanne D'Mello, A. R. Rahman, Irshad Kamil",
            album = "Rockstar",
            durationMs = 316_000L
        )
        val tracks = listOf(shortTrack, longTrack)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = tracks.toStableList(),
                        displayedTracks = tracks.toStableList(),
                        category = LibraryCategory.SONGS
                    ),
                    currentTrackId = null,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onSelectTrack = { _, _ -> },
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onSearchChange = {},
                    onCategoryChange = {},
                    onSortChange = {},
                    onOpenDrillDown = {},
                    onCloseDrillDown = {},
                    onImportFiles = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    onRescanFolders = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddTrackToPlaylist = { _, _ -> },
                    onRemoveTrackFromPlaylist = { _, _ -> },
                    onCreatePlaylist = { _, _ -> },
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onPlayPlaylist = { _, _ -> },
                    onRemoveTrack = {}
                )
            }
        }

        val shortHeight = composeRule.onNodeWithTag("track_row_short_row").getUnclippedBoundsInRoot().height.value
        val longHeight = composeRule.onNodeWithTag("track_row_long_row").getUnclippedBoundsInRoot().height.value
        assertEquals(
            "All track rows must share an identical compact height regardless of artist/title length",
            shortHeight,
            longHeight,
            1.5f
        )
        assertTrue("Track row card height should stay compact (<= 80dp), got ${longHeight}dp", longHeight <= 80f)
    }

    // --- 5. ArtworkCacheManager Safety & Deduplication ---

    @Test
    fun `ArtworkCacheManager caches valid images deduplicates and rejects corrupt bytes safely`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ArtworkCacheManager(context)

        assertNull(manager.cacheArtworkBytes(null))
        assertNull(manager.cacheArtworkBytes(ByteArray(0)))
        assertNull(manager.cacheArtworkBytes("not-a-valid-image".toByteArray()))

        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        val pngBytes = baos.toByteArray()

        val paths = (1..4).map { async { manager.cacheArtworkBytes(pngBytes) } }.awaitAll()
        assertTrue(paths.all { it != null })
        assertEquals(1, paths.distinct().size)

        val cachedFile = File(paths.first()!!)
        assertTrue(cachedFile.exists())
        assertTrue(cachedFile.length() > 0L)
    }

    // --- 6. MediaItemMapper Metadata & Artwork Embedding ---

    @Test
    fun `MediaItemMapper builds MediaItem with metadata and embeds disk-cached artwork bytes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheManager = ArtworkCacheManager(context)
        val mapper = MediaItemMapper(context)

        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
        bmp.recycle()
        val cachedArtPath = cacheManager.cacheArtworkBytes(baos.toByteArray())
        assertNotNull(cachedArtPath)

        val localTrack = Track(
            id = "local_test_1",
            title = "Night Rain",
            artist = "Studio Trio",
            album = "After Hours",
            genre = "Jazz",
            durationMs = 195_000L,
            localUriString = "content://media/external/audio/media/99",
            artworkPath = cachedArtPath,
            isUserImported = true
        )

        val mediaItem = mapper.toMediaItem(localTrack)
        assertNotNull(mediaItem)
        assertEquals("local_test_1", mediaItem!!.mediaId)
        assertEquals("Night Rain", mediaItem.mediaMetadata.title?.toString())
        assertEquals("Studio Trio", mediaItem.mediaMetadata.artist?.toString())
        assertEquals("After Hours", mediaItem.mediaMetadata.albumTitle?.toString())
        assertNotNull(
            "Disk-cached artwork should be embedded as artworkData for SystemUI lock-screen/notifications",
            mediaItem.mediaMetadata.artworkData
        )
        assertTrue(mediaItem.mediaMetadata.artworkData!!.isNotEmpty())
    }

    // --- 7. Room Cascade & Grouping Edge Cases ---

    @Test
    fun `deleting a track cascades playlist memberships and blank metadata groups cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val trackDao = db.trackDao()
            val playlistDao = db.playlistDao()
            val entities = sampleTracks.map { TrackEntity.fromDomain(it) }
            trackDao.insertTracksIgnore(entities)

            val plId = playlistDao.insertPlaylist(PlaylistEntity(name = "Cascade Check"))
            val firstId = sampleTracks[0].id
            val secondId = sampleTracks[1].id
            playlistDao.insertPlaylistTrack(PlaylistTrackEntity(plId, firstId, 0))
            playlistDao.insertPlaylistTrack(PlaylistTrackEntity(plId, secondId, 1))

            trackDao.deleteTrackById(firstId)
            val remaining = playlistDao.getPlaylistTracksOnce(plId)
            assertEquals(listOf(secondId), remaining.map { it.id })

            val untagged = listOf(
                Track(
                    id = "u1",
                    title = "Untitled Tape",
                    artist = "",
                    album = "",
                    genre = "   ",
                    durationMs = 90_000L,
                    folderName = "Recordings",
                    folderPath = "Music/Recordings"
                )
            )
            assertEquals("Unknown Album", LibraryRepository.groupAlbums(untagged).first().title)
            assertEquals("Unknown Artist", LibraryRepository.groupArtists(untagged).first().title)
            assertTrue(LibraryRepository.groupGenres(untagged).isEmpty())
            assertEquals("Recordings", LibraryRepository.groupFolders(untagged).first().title)
        } finally {
            db.close()
        }
    }

    // --- 8. System Back & Predictive Back Behavior ---

    @Test
    fun `system back closes LibraryScreen drilldown and releases interception at root`() {
        val tracks = sampleTracks
        var drillDown by mutableStateOf<LibraryDrillDown?>(LibraryDrillDown.Album("Warm Horizon"))
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = tracks.toStableList(),
                        displayedTracks = tracks.toStableList(),
                        albums = LibraryRepository.groupAlbums(tracks).toStableList(),
                        drillDown = drillDown,
                        drillDownTracks = tracks.take(1).toStableList()
                    ),
                    currentTrackId = tracks.first().id,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SQUIRCLE,
                    onSelectTrack = { _, _ -> },
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onSearchChange = {},
                    onCategoryChange = {},
                    onSortChange = {},
                    onOpenDrillDown = { drillDown = it },
                    onCloseDrillDown = { drillDown = null },
                    onImportFiles = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    onRescanFolders = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddTrackToPlaylist = { _, _ -> },
                    onRemoveTrackFromPlaylist = { _, _ -> },
                    onCreatePlaylist = { _, _ -> },
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onPlayPlaylist = { _, _ -> },
                    onRemoveTrack = {}
                )
            }
        }

        composeRule.waitForIdle()
        assertNotNull(backDispatcher)
        assertTrue(
            "BackHandler must be enabled while Library drill-down is open",
            backDispatcher!!.hasEnabledCallbacks()
        )

        composeRule.runOnUiThread { backDispatcher!!.onBackPressed() }
        composeRule.waitForIdle()

        assertNull("System back must close the active Library drill-down", drillDown)
        assertFalse(
            "BackHandler must disable at root so predictive back-to-home can run",
            backDispatcher!!.hasEnabledCallbacks()
        )
    }

    // --- 9. Cold-Start Loading Guard, Header Compact Import Button & Full-Screen Scanner ---

    @Test
    fun `cold-start loading guard suppresses empty onboarding flash until Room emits`() {
        var loaded by mutableStateOf(false)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                NowPlayingScreen(
                    uiState = NowPlayingUiState(
                        currentTrack = null,
                        queueTracks = StableList(),
                        isInitialLibraryLoaded = loaded
                    ),
                    themePrefs = ThemePreferences(),
                    progressFlow = MutableStateFlow(PlaybackProgress()),
                    sleepTimerFlow = MutableStateFlow(SleepTimerState()),
                    playlists = StableList(),
                    onTogglePlayPause = {},
                    onNext = {},
                    onPrevious = {},
                    onSeek = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onCycleSpeed = {},
                    onMatchMood = {},
                    onOpenSettings = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    onSelectQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    onRemoveQueueItem = {},
                    onClearQueue = {},
                    onSaveQueueAsPlaylist = {},
                    onAddCurrentToPlaylist = {},
                    onCreatePlaylistWithCurrent = {},
                    onPlayNextCurrent = {},
                    onAddCurrentToQueue = {},
                    onSelectSleepTimer = {}
                )
            }
        }

        composeRule.onNodeWithTag("initial_loading_placeholder").assertExists()
        composeRule.onNodeWithTag("empty_now_playing_art").assertDoesNotExist()

        loaded = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("empty_now_playing_art").assertIsDisplayed()
    }

    @Test
    fun `LibraryScreen hides empty card before Room loads and uses compact circular import icon button`() {
        var loaded by mutableStateOf(false)
        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = StableList(),
                        displayedTracks = StableList(),
                        category = LibraryCategory.SONGS,
                        isInitialLibraryLoaded = loaded
                    ),
                    currentTrackId = null,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onSelectTrack = { _, _ -> },
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onSearchChange = {},
                    onCategoryChange = {},
                    onSortChange = {},
                    onOpenDrillDown = {},
                    onCloseDrillDown = {},
                    onImportFiles = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    onRescanFolders = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddTrackToPlaylist = { _, _ -> },
                    onRemoveTrackFromPlaylist = { _, _ -> },
                    onCreatePlaylist = { _, _ -> },
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onPlayPlaylist = { _, _ -> },
                    onRemoveTrack = {}
                )
            }
        }

        // Before initial Room load completes, no empty card flash
        composeRule.onNodeWithTag("library_empty_card").assertDoesNotExist()

        // Compact circular import icon button (48dp x 48dp) leaves ample width for "Your Sound"
        val importBounds = composeRule.onNodeWithTag("import_audio_button").getUnclippedBoundsInRoot()
        assertEquals(48f, importBounds.width.value, 2f)
        assertEquals(48f, importBounds.height.value, 2f)

        val titleBounds = composeRule.onNodeWithTag("library_header_title").getUnclippedBoundsInRoot()
        assertTrue(
            "Header title must have generous horizontal width (>= 140dp) so 'Your Sound' never truncates",
            titleBounds.width.value >= 140f
        )

        loaded = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_empty_card").assertIsDisplayed()
    }

    @Test
    fun `FullScreenScanningView displays folder badge progress bar and file counter`() {
        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                FullScreenScanningView(
                    scanState = ScanState(
                        isScanning = true,
                        currentFolderName = "FLAC Lossless",
                        currentFileName = "04 - Horizon Drift.flac",
                        scannedCount = 25,
                        totalCount = 100,
                        addedCount = 24,
                        statusLabel = "Scanning FLAC Lossless (25/100)…"
                    ),
                    artworkShape = ExpressiveArtworkShape.SCALLOP
                )
            }
        }

        composeRule.onNodeWithTag("full_screen_scanning_view").assertIsDisplayed()
        composeRule.onNodeWithTag("scanning_folder_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("scanning_progress_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("scanning_counter_text").assertTextContains("25 / 100 files · 24 added")
        composeRule.onNodeWithTag("scanning_detail_text").assertTextContains("04 - Horizon Drift.flac")
    }
}
