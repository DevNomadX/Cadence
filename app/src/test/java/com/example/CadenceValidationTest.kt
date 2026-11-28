package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
import com.example.data.library.LibrarySearchEngine
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
import com.example.ui.theme.colorScheme
import com.example.ui.theme.resolveCadenceColorScheme
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
                palette = ExpressivePalette.MATCHA,
                appearanceMode = AppearanceMode.LIGHT
            ) {
                NowPlayingScreen(
                    uiState = uiState,
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.MATCHA,
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
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
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
    fun `NowPlayingScreen transport controls and audio output fallback sheet work`() {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 12_000L, durationMs = 180_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())
        val outputState = com.example.playback.AudioOutputState(
            activeRoute = com.example.playback.AudioOutputRouteType.BLUETOOTH,
            activeDeviceName = "Studio Buds Pro",
            devices = listOf(
                com.example.playback.AudioOutputDevice(
                    id = 10,
                    name = "Studio Buds Pro",
                    routeType = com.example.playback.AudioOutputRouteType.BLUETOOTH,
                    isActive = true
                ),
                com.example.playback.AudioOutputDevice(
                    id = -1,
                    name = "Phone Speaker",
                    routeType = com.example.playback.AudioOutputRouteType.SPEAKER,
                    isActive = false
                )
            ).toStableList()
        )
        var playToggled = false
        var nextClicked = false
        var prevClicked = false
        var shuffleClicked = false
        var repeatClicked = false
        var favoriteClicked = false
        var collapseClicked = false

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
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
                        palette = ExpressivePalette.MATCHA,
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
                    onMatchMood = {},
                    onCollapse = { collapseClicked = true },
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
                    onSelectSleepTimer = {},
                    audioOutputStateOverride = outputState
                )
            }
        }

        composeRule.onNodeWithTag("now_playing_title").assertIsDisplayed()
        composeRule.onNodeWithTag("collapse_now_playing_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("next_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("previous_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("shuffle_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("repeat_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("favorite_button").assertIsDisplayed().performClick()

        assertTrue(collapseClicked)
        assertTrue(playToggled)
        assertTrue(nextClicked)
        assertTrue(prevClicked)
        assertTrue(shuffleClicked)
        assertTrue(repeatClicked)
        assertTrue(favoriteClicked)

        // Tap Audio Output Switcher -> opens M3 fallback sheet when system dialog is unavailable
        composeRule.onNodeWithTag("audio_output_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_output_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_output_device_0_bluetooth").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_output_device_1_speaker").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_output_system_settings_button").assertIsDisplayed()
    }

    @Test
    fun `NowPlayingScreen overflow menu exposes playback speed selector`() {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 12_000L, durationMs = 180_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())
        var selectedSpeed = 1.0f

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                NowPlayingScreen(
                    uiState = NowPlayingUiState(
                        currentTrack = sampleTracks.first(),
                        queueTracks = sampleTracks.toStableList(),
                        currentQueueIndex = 0,
                        isPlaying = false,
                        playbackSpeed = selectedSpeed
                    ),
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.MATCHA,
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
                    onSelectPlaybackSpeed = { selectedSpeed = it },
                    onMatchMood = {}
                )
            }
        }

        composeRule.onNodeWithTag("now_playing_more_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overflow_playback_speed_section").assertIsDisplayed()
        composeRule.onNodeWithTag("overflow_speed_1.5x").performClick()
        assertEquals(1.5f, selectedSpeed, 0.01f)
    }

    @Test
    fun `NowPlayingScreen opens Lyrics Sleep Timer and Queue sheets`() {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 12_000L, durationMs = 180_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())
        val lyricsFlow = MutableStateFlow<com.example.data.lyrics.LyricsUiState>(
            com.example.data.lyrics.LyricsUiState.Empty(
                trackId = sampleTracks.first().id,
                trackTitle = sampleTracks.first().title
            )
        )
        var lyricsRequested = false
        var selectedTimer: SleepTimerOption? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                NowPlayingScreen(
                    uiState = NowPlayingUiState(
                        currentTrack = sampleTracks.first(),
                        queueTracks = sampleTracks.toStableList(),
                        currentQueueIndex = 0,
                        isPlaying = false
                    ),
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.MATCHA,
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
                    onMatchMood = {},
                    onSelectSleepTimer = { selectedTimer = it },
                    lyricsStateFlow = lyricsFlow,
                    onRequestLyrics = { lyricsRequested = true }
                )
            }
        }

        // Open Sleep Timer sheet and select End of Track (auto-dismisses sheet on selection)
        composeRule.onNodeWithTag("timer_pill").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("End of track").performClick()
        composeRule.waitForIdle()
        assertEquals(SleepTimerOption.END_OF_TRACK, selectedTimer)

        // Open Lyrics sheet and verify fallback state renders
        composeRule.onNodeWithTag("lyrics_pill").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(lyricsRequested)
        composeRule.onNodeWithTag("lyrics_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_empty_state").assertIsDisplayed()
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
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertEquals("Solstice", latestQuery)

        composeRule.onNodeWithTag("library_search_clear_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_bento_albums").performClick()
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
        var crossfadePicked: Int? = null
        var headphoneChanged: Boolean? = null
        var audioFocusPicked: AudioFocusBehavior? = null
        var backClicked = false

        val folders = listOf(
            SavedFolderItem(treeUri = "content://com.android.externalstorage/tree/primary:Music", displayName = "Music")
        ).toStableList()

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.SYSTEM) {
                SettingsScreen(
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.MATCHA,
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
                    onChangeCrossfadeSec = { crossfadePicked = it },
                    onTogglePauseOnHeadphoneDisconnect = { headphoneChanged = it },
                    onSelectAudioFocusBehavior = { audioFocusPicked = it }
                )
            }
        }

        // Pinned top bar & back action
        composeRule.onNodeWithTag("settings_header_title")
            .assertIsDisplayed()
            .assertTextContains("Settings")
        composeRule.onNodeWithTag("settings_back_button").performClick()
        assertTrue(backClicked)

        // Appearance & Theme summary card 3-column pills -> open Appearance sub-screen
        composeRule.onNodeWithTag("appearance_summary_palette", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_summary_mode", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_summary_shape", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_appearance_card").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_active_style_card").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance_${AppearanceMode.DARK.id}").performClick()
        assertEquals(AppearanceMode.DARK, selectedAppearance)

        composeRule.onNodeWithTag("appearance_subscreen_list")
            .performScrollToNode(hasTestTag("palette_${ExpressivePalette.COBALT.id}"))
        composeRule.onNodeWithTag("palette_${ExpressivePalette.COBALT.id}").performClick()
        assertEquals(ExpressivePalette.COBALT, selectedPalette)

        composeRule.onNodeWithTag("appearance_subscreen_list")
            .performScrollToNode(hasTestTag("shape_${ExpressiveArtworkShape.CLOVER.id}"))
        composeRule.onNodeWithTag("shape_${ExpressiveArtworkShape.CLOVER.id}").performClick()
        assertEquals(ExpressiveArtworkShape.CLOVER, selectedShape)

        // Return to main Settings Bento screen
        composeRule.onNodeWithTag("appearance_back_button").performClick()
        composeRule.waitForIdle()

        // Audio & Playback controls
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_gapless"))
        composeRule.onNodeWithTag("toggle_gapless").performClick()
        assertEquals(false, gaplessChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("crossfade_selector_pill"))
        composeRule.onNodeWithTag("crossfade_selector_pill").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crossfade_preset_9").performClick()
        assertEquals(9, crossfadePicked)
        composeRule.onNodeWithTag("crossfade_sheet_close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_headphone_pause"))
        composeRule.onNodeWithTag("toggle_headphone_pause").performClick()
        assertEquals(false, headphoneChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("audio_focus_row"))
        composeRule.onNodeWithTag("audio_focus_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_focus_${AudioFocusBehavior.PAUSE.id}").performClick()
        composeRule.waitForIdle()
        assertEquals(AudioFocusBehavior.PAUSE, audioFocusPicked)

        // Library & Storage controls
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("scanned_folders_row"))
        composeRule.onNodeWithTag("scanned_folders_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("remove_folder_0").performClick()
        assertEquals("content://com.android.externalstorage/tree/primary:Music", removedFolderUri)
        composeRule.onNodeWithTag("folders_sheet_close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_add_folder_button"))
        composeRule.onNodeWithTag("settings_add_folder_button").performClick()
        assertTrue(addFolderClicked)

        composeRule.onNodeWithTag("settings_rescan_button").performClick()
        assertTrue(rescanClicked)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("min_duration_30"))
        composeRule.onNodeWithTag("min_duration_30").performClick()
        assertEquals(30, minDurationPicked)

        // Interface & Performance controls
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_performance_mode"))
        composeRule.onNodeWithTag("toggle_performance_mode").performClick()
        assertEquals(true, perfChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_wavy_scrubber"))
        composeRule.onNodeWithTag("toggle_wavy_scrubber").performClick()
        assertEquals(false, wavyChanged)

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("toggle_ambient_aura"))
        composeRule.onNodeWithTag("toggle_ambient_aura").performClick()
        assertEquals(false, auraChanged)

        // Cardless About entry row -> opens dedicated full-page About sub-screen
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about_row"))
        composeRule.onNodeWithTag("settings_about_row").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("about_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("about_emblem").assertIsDisplayed()
        composeRule.onNodeWithTag("about_version_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("about_subscreen_list").performScrollToNode(hasTestTag("about_privacy_row"))
        composeRule.onNodeWithTag("about_privacy_row").assertIsDisplayed()
        composeRule.onNodeWithTag("about_engine_row").assertIsDisplayed()
        composeRule.onNodeWithTag("about_source_code_row").assertIsDisplayed()
        composeRule.onNodeWithTag("about_licenses_row").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_licenses_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("about_licenses_close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("about_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_top_bar").assertIsDisplayed()
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

        repo.setPalette(ExpressivePalette.MATCHA)
        repo.setAppearanceMode(AppearanceMode.DARK)
        repo.setArtworkShape(ExpressiveArtworkShape.ARCH)
        repo.setWavyScrubberEnabled(false)
        repo.setAmbientAuraEnabled(false)
        repo.setPerformanceModeEnabled(true)

        val theme = repo.themePreferencesFlow.first()
        assertEquals(ExpressivePalette.MATCHA, theme.palette)
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
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
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
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
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
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
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
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
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

        // Single circular Settings button (48dp) + persistent stadium Search pill (no duplicate search icon button)
        composeRule.onNodeWithTag("library_search_button").assertDoesNotExist()
        composeRule.onNodeWithTag("library_search_input").assertIsDisplayed()
        val settingsBounds = composeRule.onNodeWithTag("library_settings_button").getUnclippedBoundsInRoot()
        assertEquals(48f, settingsBounds.width.value, 2f)
        assertEquals(48f, settingsBounds.height.value, 2f)

        composeRule.onNodeWithTag("library_header_title")
            .assertIsDisplayed()
            .assertTextContains("Your Music")
        composeRule.onNodeWithTag("library_header_subtitle")
            .assertIsDisplayed()
            .assertTextContains("Your library, your vibe.")
        val titleBounds = composeRule.onNodeWithTag("library_header_title").getUnclippedBoundsInRoot()
        assertTrue(
            "Header title must have generous horizontal width (>= 140dp) so 'Your Music' never truncates",
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

    // --- 10. Library Search Engine Accuracy, Ranking & Responsive Search UX ---

    @Test
    fun `LibrarySearchEngine handles diacritics multi-token cross-field filenames and exact-prefix ranking`() {
        val tracks = listOf(
            Track(
                id = "t_cross",
                title = "Tum Hi Ho",
                artist = "Arijit Singh",
                album = "Aashiqui 2",
                genre = "Bollywood",
                durationMs = 262_000L,
                localUriString = "content://media/external/audio/media/10",
                folderName = "Hindi Hits"
            ),
            Track(
                id = "t_diacritic",
                title = "Café Del Mar",
                artist = "Ümit Aksoy",
                album = "Ibiza Sunset",
                genre = "Chillout",
                durationMs = 240_000L,
                localUriString = "content://com.android.externalstorage/document/primary%3AMusic%2F07_Summer_Breeze_Mix.flac",
                folderName = "Lounge"
            ),
            Track(
                id = "t_substring",
                title = "Midnight Rain",
                artist = "Nova",
                album = "After Dark",
                genre = "Synth",
                durationMs = 200_000L
            ),
            Track(
                id = "t_prefix",
                title = "Rainy Days",
                artist = "Echo",
                album = "Cloudburst",
                genre = "Indie",
                durationMs = 190_000L
            ),
            Track(
                id = "t_exact",
                title = "Rain",
                artist = "Sora",
                album = "Monsoon",
                genre = "Ambient",
                durationMs = 175_000L
            )
        )
        val indexed = LibrarySearchEngine.buildIndex(tracks)

        // Blank query returns null so callers can keep user sort order untouched
        assertNull(LibrarySearchEngine.searchIndexed(indexed, "   "))

        // Out-of-order multi-token cross-field query ("arijit tum" -> "Tum Hi Ho" by "Arijit Singh")
        val crossMatches = LibrarySearchEngine.searchIndexed(indexed, "  arijit   tum  ")
        assertEquals(listOf("t_cross"), crossMatches?.map { it.id })

        // Diacritic-insensitive matching across title & artist
        assertEquals(listOf("t_diacritic"), LibrarySearchEngine.searchIndexed(indexed, "cafe umit")?.map { it.id })

        // Filename matching from encoded content URI (separators normalized, extension stripped)
        assertEquals(listOf("t_diacritic"), LibrarySearchEngine.searchIndexed(indexed, "summer breeze")?.map { it.id })

        // Relevance ordering: exact title ("Rain") > prefix title ("Rainy Days") > substring title ("Midnight Rain")
        val ranked = LibrarySearchEngine.searchIndexed(indexed, "rain")
        assertEquals(listOf("t_exact", "t_prefix", "t_substring"), ranked?.map { it.id })

        // Playlist multi-token filtering
        val playlists = listOf(
            PlaylistSummary(id = 1L, name = "Café Chillout Sessions", trackCount = 4),
            PlaylistSummary(id = 2L, name = "Morning Run", trackCount = 8)
        )
        assertEquals(listOf(1L), LibrarySearchEngine.filterPlaylists(playlists, "sessions cafe").map { it.id })
    }

    @Test
    fun `LibraryScreen search debounces clears via trailing X and shows clearable empty state`() {
        var activeQuery by mutableStateOf("")
        var dispatchCount = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                val filtered = rememberFilteredTestTracks(sampleTracks, activeQuery)
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = sampleTracks.toStableList(),
                        displayedTracks = filtered.toStableList(),
                        category = LibraryCategory.SONGS,
                        searchQuery = activeQuery
                    ),
                    currentTrackId = null,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SQUIRCLE,
                    onSelectTrack = { _, _ -> },
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onSearchChange = {
                        dispatchCount++
                        activeQuery = it
                    },
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

        // Clear button hidden while query is empty
        composeRule.onNodeWithTag("library_search_clear_button").assertDoesNotExist()

        // Typing updates text immediately and reveals trailing X before debounce fires
        composeRule.onNodeWithTag("library_search_input").performTextInput("zzzz_missing")
        composeRule.onNodeWithTag("library_search_clear_button").assertIsDisplayed()
        assertEquals("Debounce should defer ViewModel dispatch during rapid typing", 0, dispatchCount)

        // After 150ms debounce window, empty search state renders with one-tap Clear Search CTA
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertEquals("zzzz_missing", activeQuery)
        composeRule.onNodeWithText("No songs match \"zzzz_missing\"").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_search_clear_button").performClick()
        composeRule.waitForIdle()

        assertEquals("", activeQuery)
        composeRule.onNodeWithTag("library_search_clear_button").assertDoesNotExist()
        composeRule.onNodeWithTag("track_row_${sampleTracks.first().id}").assertIsDisplayed()

        // Trailing X clears search in a single tap as well
        composeRule.onNodeWithTag("library_search_input").performTextInput("Solstice")
        composeRule.mainClock.advanceTimeBy(200L)
        composeRule.waitForIdle()
        assertEquals("Solstice", activeQuery)

        composeRule.onNodeWithTag("library_search_clear_button").performClick()
        composeRule.waitForIdle()
        assertEquals("", activeQuery)
        composeRule.onNodeWithTag("library_search_clear_button").assertDoesNotExist()
    }

    private fun rememberFilteredTestTracks(tracks: List<Track>, query: String): List<Track> =
        LibrarySearchEngine.searchTracks(tracks, query)

    // --- 11. Hi-Fi Audio Quality Heuristics, Room Migration & Expressive Badges ---

    @Test
    fun `Track Hi-Fi heuristics classify Hi-Res Lossless High-Bitrate and Standard tracks deterministically`() {
        val hiResFlac = Track(
            id = "hr_1",
            title = "Studio Master",
            artist = "Nordic Quartet",
            album = "Sessions",
            durationMs = 245_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 2840,
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        assertTrue(hiResFlac.isHiFi)
        assertTrue(hiResFlac.isLosslessFormat)
        assertTrue(hiResFlac.isHiRes)
        assertEquals("HI-RES", hiResFlac.hiFiLabel)
        assertEquals("24-bit / 96 kHz FLAC", hiResFlac.hiFiSpecSummary)

        val cdLosslessFlac = Track(
            id = "ls_1",
            title = "Warm Tape",
            artist = "Klara Lind",
            album = "Horizon",
            durationMs = 210_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 920,
            sampleRateHz = 44_100,
            bitDepth = 16
        )
        assertTrue(cdLosslessFlac.isHiFi)
        assertTrue(cdLosslessFlac.isLosslessFormat)
        assertFalse(cdLosslessFlac.isHiRes)
        assertEquals("LOSSLESS", cdLosslessFlac.hiFiLabel)
        assertEquals("16-bit / 44.1 kHz FLAC", cdLosslessFlac.hiFiSpecSummary)

        val alacTrack = Track(
            id = "alac_1",
            title = "Apple Lossless",
            artist = "Studio",
            album = "Master",
            durationMs = 190_000L,
            mimeType = "audio/alac",
            codec = "ALAC",
            sampleRateHz = 48_000,
            bitDepth = 16
        )
        assertTrue(alacTrack.isHiFi)
        assertEquals("LOSSLESS", alacTrack.hiFiLabel)
        assertEquals("16-bit / 48 kHz ALAC", alacTrack.hiFiSpecSummary)

        val highBitrateMp3 = Track(
            id = "mp3_320",
            title = "Night Drive",
            artist = "Miro",
            album = "Afterglow",
            durationMs = 180_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            bitDepth = 0
        )
        assertTrue(highBitrateMp3.isHiFi)
        assertFalse(highBitrateMp3.isLosslessFormat)
        assertEquals("HI-FI", highBitrateMp3.hiFiLabel)
        assertEquals("320 kbps MP3", highBitrateMp3.hiFiSpecSummary)

        val standardMp3 = Track(
            id = "mp3_128",
            title = "Radio Edit",
            artist = "Echo",
            album = "Singles",
            durationMs = 175_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 128,
            sampleRateHz = 44_100,
            bitDepth = 0
        )
        assertFalse(standardMp3.isHiFi)
        assertEquals("", standardMp3.hiFiLabel)
        assertEquals("", standardMp3.hiFiSpecSummary)

        val mediumAac = Track(
            id = "aac_256",
            title = "Stream Cut",
            artist = "Echo",
            album = "Singles",
            durationMs = 175_000L,
            mimeType = "audio/aac",
            codec = "AAC",
            bitrateKbps = 256,
            sampleRateHz = 48_000,
            bitDepth = 0
        )
        assertFalse(mediumAac.isHiFi)
    }

    @Test
    fun `Room persists codec and bitDepth and MIGRATION_1_2 upgrades v1 tracks cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val hiResTrack = Track(
                id = "room_hires_1",
                title = "Analog Rain",
                artist = "Studio Trio",
                album = "Masters",
                durationMs = 240_000L,
                mimeType = "audio/flac",
                codec = "FLAC",
                bitrateKbps = 2840,
                sampleRateHz = 96_000,
                bitDepth = 24
            )
            db.trackDao().upsertTracks(listOf(TrackEntity.fromDomain(hiResTrack)))
            val loaded = db.trackDao().getTrackById("room_hires_1")?.toDomain()
            assertNotNull(loaded)
            assertEquals("FLAC", loaded!!.codec)
            assertEquals(2840, loaded.bitrateKbps)
            assertEquals(96_000, loaded.sampleRateHz)
            assertEquals(24, loaded.bitDepth)
            assertTrue(loaded.isHiFi)
            assertEquals("HI-RES", loaded.hiFiLabel)
        } finally {
            db.close()
        }

        // Verify MIGRATION_1_2 on a real SQLite v1 table schema preserves rows with safe defaults
        val sqliteDb = android.database.sqlite.SQLiteDatabase.create(null)
        try {
            sqliteDb.execSQL(
                """
                CREATE TABLE tracks (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    bitrateKbps INTEGER NOT NULL,
                    sampleRateHz INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqliteDb.execSQL(
                "INSERT INTO tracks (id, title, mimeType, bitrateKbps, sampleRateHz) VALUES ('v1_t', 'Legacy', 'audio/flac', 900, 44100)"
            )
            sqliteDb.execSQL("ALTER TABLE tracks ADD COLUMN codec TEXT NOT NULL DEFAULT ''")
            sqliteDb.execSQL("ALTER TABLE tracks ADD COLUMN bitDepth INTEGER NOT NULL DEFAULT 0")
            sqliteDb.rawQuery("SELECT id, codec, bitDepth FROM tracks WHERE id = 'v1_t'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v1_t", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
        } finally {
            sqliteDb.close()
        }
    }

    @Test
    fun `LibraryScreen renders inline HI-FI badge strictly on Hi-Fi tracks without stretching row height`() {
        val hiFiTrack = Track(
            id = "row_hifi",
            title = "Tum Ho (Lossless Master)",
            artist = "Mohit Chauhan, Suzanne D'Mello, A. R. Rahman",
            album = "Rockstar",
            durationMs = 316_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 1040,
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        val stdTrack = Track(
            id = "row_std",
            title = "Amber Solstice",
            artist = "Klara Lind",
            album = "Warm Horizon",
            durationMs = 185_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 128,
            sampleRateHz = 44_100
        )
        val tracks = listOf(hiFiTrack, stdTrack)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = tracks.toStableList(),
                        displayedTracks = tracks.toStableList(),
                        category = LibraryCategory.SONGS
                    ),
                    currentTrackId = null,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SQUIRCLE,
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

        composeRule.onNodeWithTag("track_hifi_badge_row_hifi")
            .assertIsDisplayed()
            .assertTextContains("HI-FI")
        composeRule.onNodeWithTag("track_hifi_badge_row_std").assertDoesNotExist()

        val hiFiHeight = composeRule.onNodeWithTag("track_row_row_hifi").getUnclippedBoundsInRoot().height.value
        val stdHeight = composeRule.onNodeWithTag("track_row_row_std").getUnclippedBoundsInRoot().height.value
        assertEquals(
            "Inline HI-FI badge must never stretch track row height",
            stdHeight,
            hiFiHeight,
            1.5f
        )
        assertTrue("Track row height must remain compact (<= 80dp), got ${hiFiHeight}dp", hiFiHeight <= 80f)
    }

    @Test
    fun `NowPlayingScreen displays expandable real-metadata audio quality badge across tiers and omits unavailable specs`() {
        val hiResTrack = Track(
            id = "np_hires",
            title = "Midnight Vinyl",
            artist = "Studio Trio",
            album = "After Hours",
            genre = "Jazz",
            durationMs = 240_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 2840,
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        val stdTrack = Track(
            id = "np_std",
            title = "Radio Cut",
            artist = "Echo",
            album = "Singles",
            genre = "Pop",
            durationMs = 180_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 128,
            sampleRateHz = 44_100
        )
        val unknownTrack = Track(
            id = "np_unknown",
            title = "Mystery Tape",
            artist = "Unknown",
            album = "Vault",
            genre = "Ambient",
            durationMs = 150_000L,
            mimeType = "audio/*",
            codec = "",
            bitrateKbps = 0,
            sampleRateHz = 0,
            bitDepth = 0
        )
        var activeTrack by mutableStateOf(hiResTrack)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.BERRY, appearanceMode = AppearanceMode.LIGHT) {
                NowPlayingScreen(
                    uiState = NowPlayingUiState(
                        currentTrack = activeTrack,
                        queueTracks = listOf(hiResTrack, stdTrack, unknownTrack).toStableList(),
                        currentQueueIndex = 0,
                        isPlaying = false
                    ),
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.BERRY,
                        appearanceMode = AppearanceMode.LIGHT
                    ),
                    progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 0L, durationMs = 240_000L)),
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

        // 1. Hi-Res FLAC collapsed pill shows tier + headline spec, expanded reveals all 4 real specs
        composeRule.onNodeWithTag("now_playing_hifi_badge")
            .assertIsDisplayed()
            .assertTextContains("HI-RES • 24-BIT")
        composeRule.onNodeWithTag("now_playing_audio_specs_panel").assertDoesNotExist()

        composeRule.onNodeWithTag("now_playing_hifi_badge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_audio_specs_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_spec_format").assertTextContains("FLAC")
        composeRule.onNodeWithTag("audio_spec_bit_depth").assertTextContains("24-bit")
        composeRule.onNodeWithTag("audio_spec_sample_rate").assertTextContains("96 kHz")
        composeRule.onNodeWithTag("audio_spec_bitrate").assertTextContains("2,840 kbps")

        // Tap again to collapse
        composeRule.onNodeWithTag("now_playing_hifi_badge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_audio_specs_panel").assertDoesNotExist()

        // 2. Standard lossy MP3 shows STANDARD • MP3 and omits Bit Depth when expanded
        activeTrack = stdTrack
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_hifi_badge")
            .assertIsDisplayed()
            .assertTextContains("STANDARD • MP3")
        composeRule.onNodeWithTag("now_playing_hifi_badge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audio_spec_format").assertTextContains("MP3")
        composeRule.onNodeWithTag("audio_spec_bit_depth").assertDoesNotExist()
        composeRule.onNodeWithTag("audio_spec_sample_rate").assertTextContains("44.1 kHz")
        composeRule.onNodeWithTag("audio_spec_bitrate").assertTextContains("128 kbps")

        // 3. Track with zero extracted metadata hides badge completely (never fabricates specs)
        activeTrack = unknownTrack
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_hifi_badge").assertDoesNotExist()
    }

    // --- 12. Android 13+ Monochrome Themed App Icon & System Dynamic Color Palette ---

    @Test
    fun `adaptive launcher icons include Android 13 monochrome layer and monochrome drawable loads cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val monochromeDrawable = context.getDrawable(R.drawable.ic_launcher_monochrome)
        assertNotNull("ic_launcher_monochrome drawable must exist and inflate cleanly", monochromeDrawable)

        val launcherIcon = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(
            "ic_launcher must resolve to AdaptiveIconDrawable on API 26+",
            launcherIcon is android.graphics.drawable.AdaptiveIconDrawable
        )
        val adaptiveLauncher = launcherIcon as android.graphics.drawable.AdaptiveIconDrawable
        assertNotNull("ic_launcher must include background layer", adaptiveLauncher.background)
        assertNotNull("ic_launcher must include foreground layer", adaptiveLauncher.foreground)
        assertNotNull("ic_launcher must include Android 13+ monochrome layer", adaptiveLauncher.monochrome)

        val roundIcon = context.getDrawable(R.mipmap.ic_launcher_round)
        assertTrue(
            "ic_launcher_round must resolve to AdaptiveIconDrawable on API 26+",
            roundIcon is android.graphics.drawable.AdaptiveIconDrawable
        )
        val adaptiveRound = roundIcon as android.graphics.drawable.AdaptiveIconDrawable
        assertNotNull("ic_launcher_round must include Android 13+ monochrome layer", adaptiveRound.monochrome)
    }

    @Test
    fun `System Material You palette renders in Theme Studio resolves dynamically and adapts HiFi badge`() {
        assertEquals(ExpressivePalette.DYNAMIC, ExpressivePalette.fromId("dynamic"))
        assertEquals(ExpressivePalette.DYNAMIC, ExpressivePalette.fromId("system"))
        assertEquals("System", ExpressivePalette.DYNAMIC.displayName)

        var selectedPalette: ExpressivePalette? = null
        val hiFiTrack = Track(
            id = "dyn_hifi",
            title = "Glass Echo",
            artist = "Nordic Quartet",
            album = "Masters",
            durationMs = 210_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            sampleRateHz = 96_000,
            bitDepth = 24
        )

        composeRule.setContent {
            CadenceTheme(
                palette = ExpressivePalette.DYNAMIC,
                appearanceMode = AppearanceMode.DARK,
                performanceMode = true
            ) {
                SettingsScreen(
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.MATCHA,
                        appearanceMode = AppearanceMode.DARK,
                        artworkShape = ExpressiveArtworkShape.SQUIRCLE
                    ),
                    audioSettings = AudioSettings(),
                    savedFolders = StableList(),
                    minTrackDurationSec = 60,
                    currentTrack = hiFiTrack,
                    isPlaying = false,
                    onBack = {},
                    onSelectPalette = { selectedPalette = it },
                    onSelectAppearance = {},
                    onSelectArtworkShape = {},
                    onToggleWavyScrubber = {},
                    onToggleAmbientAura = {},
                    onTogglePerformanceMode = {},
                    onAddFolder = {},
                    onRemoveFolder = {},
                    onRescanFolders = {},
                    onSelectMinTrackDurationSec = {},
                    onToggleGaplessPlayback = {},
                    onChangeCrossfadeSec = {},
                    onTogglePauseOnHeadphoneDisconnect = {},
                    onSelectAudioFocusBehavior = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_appearance_card").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance_subscreen_list")
            .performScrollToNode(hasTestTag("palette_${ExpressivePalette.DYNAMIC.id}"))
        composeRule.onNodeWithTag("palette_${ExpressivePalette.DYNAMIC.id}")
            .assertIsDisplayed()
            .assertTextContains("System")
            .performClick()
        assertEquals(ExpressivePalette.DYNAMIC, selectedPalette)
    }

    @Test
    fun `All 10 distinct palettes resolve hue-matched Light and Dark schemes migrate legacy IDs safely sync Bento summary and render unclipped at 360dp`() {
        val expectedNames = listOf(
            "System",
            "Berry Velvet",
            "Matcha Grove",
            "Cobalt Pulse",
            "Citrus Pop",
            "Arctic Cyan",
            "Peach Blossom",
            "Wisteria Mist",
            "Spearmint Fizz",
            "Obsidian Mono"
        )
        assertEquals("Must have exactly 10 palettes (5 symmetrical rows of 2)", 10, ExpressivePalette.entries.size)
        assertEquals(expectedNames, ExpressivePalette.entries.map { it.displayName })

        // Verify all removed legacy & repetitive palettes are gone and migrate safely to Matcha Grove
        val removedIds = listOf(
            "abyssal_teal", "crimson_ember", "cyber_violet", "desert_copper",
            "ABYSSAL_TEAL", "CRIMSON_EMBER",
            "sakura_coral", "amethyst_dusk", "glacier_mint",
            "nordic_slate", "solar_amber", "terracotta", "terracotta_sun",
            "TERRACOTTA", "unknown_corrupted_id"
        )
        removedIds.forEach { legacyId ->
            assertTrue("Removed palette '$legacyId' must not exist", ExpressivePalette.entries.none { it.id == legacyId.lowercase() })
            assertEquals(
                "Removed palette '$legacyId' must safely fall back to Matcha Grove",
                ExpressivePalette.MATCHA,
                ExpressivePalette.fromId(legacyId)
            )
        }
        assertEquals(ExpressivePalette.BERRY, ExpressivePalette.fromId(null))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val syncPrefs = context.getSharedPreferences(PreferencesRepository.SYNC_THEME_PREFS_NAME, Context.MODE_PRIVATE)
        syncPrefs.edit().putString(PreferencesRepository.SYNC_KEY_PALETTE, "abyssal_teal").commit()
        val upgradedSnapshot = PreferencesRepository.readStartupThemeSnapshot(context)
        assertEquals(ExpressivePalette.MATCHA, upgradedSnapshot.palette)

        // Every curated palette provides distinct Light & Dark surfaces and accents
        val curatedPalettes = ExpressivePalette.entries.filter { it != ExpressivePalette.DYNAMIC }
        val distinctLightBackgrounds = curatedPalettes.map { it.colorScheme(darkTheme = false).background }.toSet()
        val distinctDarkBackgrounds = curatedPalettes.map { it.colorScheme(darkTheme = true).background }.toSet()
        val distinctLightPrimaries = curatedPalettes.map { it.colorScheme(darkTheme = false).primary }.toSet()
        val distinctDarkPrimaries = curatedPalettes.map { it.colorScheme(darkTheme = true).primary }.toSet()
        assertEquals("All 9 curated light themes must have unique hue-tinted/monochrome backgrounds", 9, distinctLightBackgrounds.size)
        assertEquals("All 9 curated dark themes must have unique hue-tinted/monochrome backgrounds", 9, distinctDarkBackgrounds.size)
        assertEquals("All 9 curated light themes must have unique primary colors", 9, distinctLightPrimaries.size)
        assertEquals("All 9 curated dark themes must have unique primary colors", 9, distinctDarkPrimaries.size)

        // Verify key anchors of the 4 new dual-accent palettes & 3 refreshed palettes
        assertEquals(Color(0xFF67E8F9), ExpressivePalette.ARCTIC_CYAN.colorScheme(darkTheme = true).primary)
        assertEquals(Color(0xFF006874), ExpressivePalette.ARCTIC_CYAN.colorScheme(darkTheme = false).primary)
        assertEquals(Color(0xFFFF9E80), ExpressivePalette.PEACH_BLOSSOM.colorScheme(darkTheme = true).primary)
        assertEquals(Color(0xFFB94324), ExpressivePalette.PEACH_BLOSSOM.colorScheme(darkTheme = false).primary)
        assertEquals(Color(0xFFD8B4FE), ExpressivePalette.WISTERIA_MIST.colorScheme(darkTheme = true).primary)
        assertEquals(Color(0xFF6D42B5), ExpressivePalette.WISTERIA_MIST.colorScheme(darkTheme = false).primary)
        assertEquals(Color(0xFF5EEAD4), ExpressivePalette.SPEARMINT_FIZZ.colorScheme(darkTheme = true).primary)
        assertEquals(Color(0xFF0F766E), ExpressivePalette.SPEARMINT_FIZZ.colorScheme(darkTheme = false).primary)
        assertEquals(Color(0xFF5CD685), ExpressivePalette.MATCHA.colorScheme(darkTheme = true).primary)
        assertEquals(Color(0xFF15803D), ExpressivePalette.MATCHA.colorScheme(darkTheme = false).primary)

        // Verify Obsidian Mono stays crisp low-chroma onyx & platinum across Light and Dark modes
        val monoDark = ExpressivePalette.OBSIDIAN_MONO.colorScheme(darkTheme = true)
        val monoLight = ExpressivePalette.OBSIDIAN_MONO.colorScheme(darkTheme = false)
        assertEquals(Color(0xFFF1F3F5), monoDark.primary)
        assertEquals(Color(0xFF0C0D10), monoDark.surface)
        assertEquals(Color(0xFF14161A), monoLight.primary)
        assertEquals(Color(0xFFF8F9FA), monoLight.surface)
        listOf(
            monoDark.background, monoDark.surface, monoDark.surfaceContainerLow,
            monoDark.surfaceContainerHigh, monoDark.primary, monoDark.secondary,
            monoLight.background, monoLight.surface, monoLight.surfaceContainerLow,
            monoLight.surfaceContainerHigh, monoLight.primary, monoLight.secondary
        ).forEach { c ->
            val maxChannel = maxOf(c.red, c.green, c.blue)
            val minChannel = minOf(c.red, c.green, c.blue)
            assertTrue(
                "Obsidian Mono must remain crisp low-chroma onyx/platinum (spread <= 0.10f), was $c",
                (maxChannel - minChannel) <= 0.10f
            )
        }

        // Verify Cobalt Pulse is crisp cobalt & aurora blue (blue channel strictly dominant over red)
        val cobaltLight = ExpressivePalette.COBALT.colorScheme(darkTheme = false)
        val cobaltDark = ExpressivePalette.COBALT.colorScheme(darkTheme = true)
        assertEquals(Color(0xFF1D4ED8), cobaltLight.primary)
        assertEquals(Color(0xFF60A5FA), cobaltDark.primary)
        assertTrue("Cobalt Pulse light primary must be pure blue", cobaltLight.primary.blue > cobaltLight.primary.red * 3f)
        assertTrue("Cobalt Pulse dark primary must be blue-dominant", cobaltDark.primary.blue > cobaltDark.primary.red)

        ExpressivePalette.entries.forEach { palette ->
            val light = palette.colorScheme(darkTheme = false)
            val dark = palette.colorScheme(darkTheme = true)
            assertTrue("${palette.displayName} light/dark backgrounds must differ", light.background != dark.background)
            assertTrue("${palette.displayName} light primary vs onPrimary contrast", light.primary != light.onPrimary)
            assertTrue("${palette.displayName} dark primary vs onPrimary contrast", dark.primary != dark.onPrimary)
            assertTrue("${palette.displayName} light primaryContainer contrast", light.primaryContainer != light.onPrimaryContainer)
            assertTrue("${palette.displayName} dark primaryContainer contrast", dark.primaryContainer != dark.onPrimaryContainer)
            assertTrue(
                "${palette.displayName} preview swatches must be 3 distinct colors",
                setOf(palette.previewPrimary, palette.previewSecondary, palette.previewTertiary).size == 3
            )
            assertNotNull(resolveCadenceColorScheme(palette, darkTheme = false, context = context))
            assertNotNull(resolveCadenceColorScheme(palette, darkTheme = true, context = context))
        }

        // Render SettingsScreen at 360dp width with reactive palette state and verify Bento cards, search, pills, and live sync
        var activePalette by mutableStateOf(ExpressivePalette.ARCTIC_CYAN)
        composeRule.setContent {
            CadenceTheme(palette = activePalette, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    SettingsScreen(
                        themePrefs = ThemePreferences(
                            palette = activePalette,
                            appearanceMode = AppearanceMode.DARK,
                            artworkShape = ExpressiveArtworkShape.SCALLOP
                        ),
                        audioSettings = AudioSettings(),
                        savedFolders = StableList(),
                        minTrackDurationSec = 60,
                        currentTrack = sampleTracks.first(),
                        isPlaying = true,
                        onBack = {},
                        onSelectPalette = { activePalette = it },
                        onSelectAppearance = {},
                        onSelectArtworkShape = {},
                        onToggleWavyScrubber = {},
                        onToggleAmbientAura = {},
                        onTogglePerformanceMode = {},
                        onAddFolder = {},
                        onRemoveFolder = {},
                        onRescanFolders = {},
                        onSelectMinTrackDurationSec = {},
                        onToggleGaplessPlayback = {},
                        onChangeCrossfadeSec = {},
                        onTogglePauseOnHeadphoneDisconnect = {},
                        onSelectAudioFocusBehavior = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Confirm Display Name is completely absent and summary card shows initial palette
        assertEquals(0, composeRule.onAllNodesWithTag("settings_display_name_row").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Display Name", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("settings_appearance_card")
            .assertTextContains("Arctic Cyan")

        // Verify pinned top bar sits cleanly above the scrolling list
        val topBarBounds = composeRule.onNodeWithTag("settings_top_bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        val listBounds = composeRule.onNodeWithTag("settings_list").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(
            "LazyColumn top (${listBounds.top}) must sit at or below pinned TopAppBar bottom (${topBarBounds.bottom})",
            listBounds.top >= topBarBounds.bottom - 1.dp
        )

        // Verify inline Settings search filters rows in real time and handles empty state
        composeRule.onNodeWithTag("settings_search_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search_input").performTextInput("crossfade")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("crossfade_selector_pill").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("settings_appearance_card").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("settings_search_input").performTextClearance()
        composeRule.onNodeWithTag("settings_search_input").performTextInput("zzzz_not_a_setting")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_search_clear").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search_button").performClick()
        composeRule.waitForIdle()

        // Verify 4 equal-weight short-clip pills stay on one single horizontal row at 360dp
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("min_duration_0"))
        val pill0Bounds = composeRule.onNodeWithTag("min_duration_0").assertIsDisplayed().getUnclippedBoundsInRoot()
        val pill60Bounds = composeRule.onNodeWithTag("min_duration_60").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals("Short-clip pills must align on a single horizontal row", pill0Bounds.top.value, pill60Bounds.top.value, 1f)

        // Scroll back to top, open Appearance & Theme sub-screen, and verify all 10 palette taglines fit on 1 line at 360dp
        composeRule.onNodeWithTag("settings_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("settings_appearance_card").performClick()
        composeRule.waitForIdle()

        ExpressivePalette.entries.forEach { palette ->
            composeRule.onNodeWithTag("appearance_subscreen_list")
                .performScrollToNode(hasTestTag("palette_${palette.id}"))
            val taglineNode = composeRule
                .onNodeWithTag("palette_tagline_${palette.id}", useUnmergedTree = true)
                .fetchSemanticsNode()
            val results = mutableListOf<TextLayoutResult>()
            taglineNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            assertTrue("Expected layout result for ${palette.displayName} tagline", results.isNotEmpty())
            val layout = results.first()
            assertEquals("Tagline for ${palette.displayName} must stay 1 line", 1, layout.lineCount)
            assertFalse(
                "Tagline '${palette.tagline}' for ${palette.displayName} must not clip/ellipsize at 360dp " +
                    "(constraints=${layout.layoutInput.constraints}, mp=${layout.multiParagraph.width}x${layout.multiParagraph.height}, size=${layout.size})",
                layout.isLineEllipsized(0) || layout.hasVisualOverflow
            )
        }

        // Verify tapping Obsidian Mono updates both Active Style live preview card and Bento summary pill
        composeRule.onNodeWithTag("appearance_subscreen_list")
            .performScrollToNode(hasTestTag("palette_${ExpressivePalette.OBSIDIAN_MONO.id}"))
        composeRule.onNodeWithTag("palette_${ExpressivePalette.OBSIDIAN_MONO.id}").performClick()
        composeRule.waitForIdle()
        assertEquals(ExpressivePalette.OBSIDIAN_MONO, activePalette)

        composeRule.onNodeWithTag("appearance_subscreen_list").performScrollToIndex(0)
        composeRule.onNodeWithTag("settings_active_style_card").assertIsDisplayed()
        assertTrue(
            "Active Style live preview card and palette grid should display Obsidian Mono",
            composeRule.onAllNodesWithText("Obsidian Mono").fetchSemanticsNodes().size >= 2
        )

        composeRule.onNodeWithTag("appearance_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_appearance_card")
            .assertTextContains("Obsidian Mono")

        // Verify About row is discoverable via search ("GitHub") and renders unclipped at 360dp
        composeRule.onNodeWithTag("settings_search_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_search_input").performTextInput("GitHub")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_about_row").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_search_button").performClick() // clear query
        composeRule.onNodeWithTag("settings_search_button").performClick() // close search
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about_row"))
        val aboutSubtitleNode = composeRule
            .onNodeWithTag("settings_about_subtitle", useUnmergedTree = true)
            .fetchSemanticsNode()
        val aboutSubtitleLayouts = mutableListOf<TextLayoutResult>()
        aboutSubtitleNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(aboutSubtitleLayouts)
        assertTrue("Expected layout result for About subtitle", aboutSubtitleLayouts.isNotEmpty())
        val aboutSubtitleLayout = aboutSubtitleLayouts.first()
        assertEquals("About subtitle must stay 1 line at 360dp", 1, aboutSubtitleLayout.lineCount)
        assertFalse(
            "About subtitle must not clip/ellipsize at 360dp",
            aboutSubtitleLayout.isLineEllipsized(0) || aboutSubtitleLayout.hasVisualOverflow
        )

        // Tap About row -> open dedicated full-page About screen and verify zero clipping at 360dp
        composeRule.onNodeWithTag("settings_about_row").performClick()
        composeRule.waitForIdle()

        val aboutTopBounds = composeRule.onNodeWithTag("about_top_bar").assertIsDisplayed().getUnclippedBoundsInRoot()
        val aboutListBounds = composeRule.onNodeWithTag("about_subscreen_list").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(
            "About list top (${aboutListBounds.top}) must sit at or below pinned About TopAppBar (${aboutTopBounds.bottom})",
            aboutListBounds.top >= aboutTopBounds.bottom - 1.dp
        )

        composeRule.onNodeWithTag("about_emblem").assertIsDisplayed()
        composeRule.onNodeWithTag("about_app_name").assertIsDisplayed().assertTextContains("Cadence")
        composeRule.onNodeWithTag("about_version_pill").assertIsDisplayed()

        val aboutTaglineNode = composeRule
            .onNodeWithTag("about_tagline", useUnmergedTree = true)
            .fetchSemanticsNode()
        val aboutTaglineLayouts = mutableListOf<TextLayoutResult>()
        aboutTaglineNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(aboutTaglineLayouts)
        assertTrue("Expected layout result for About tagline", aboutTaglineLayouts.isNotEmpty())
        val aboutTaglineLayout = aboutTaglineLayouts.first()
        assertEquals("About tagline must stay 1 line at 360dp", 1, aboutTaglineLayout.lineCount)
        assertFalse(
            "About tagline must not clip/ellipsize at 360dp",
            aboutTaglineLayout.isLineEllipsized(0) || aboutTaglineLayout.hasVisualOverflow
        )

        composeRule.onNodeWithTag("about_subscreen_list").performScrollToNode(hasTestTag("about_licenses_row"))
        composeRule.onNodeWithTag("about_privacy_row").assertIsDisplayed()
        composeRule.onNodeWithTag("about_engine_row").assertIsDisplayed()
        composeRule.onNodeWithTag("about_source_code_row").assertIsDisplayed()
        composeRule.onNodeWithTag("about_licenses_row").assertIsDisplayed()

        composeRule.onNodeWithTag("about_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_top_bar").assertIsDisplayed()
    }
}
