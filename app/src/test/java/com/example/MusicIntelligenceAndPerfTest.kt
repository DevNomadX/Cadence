package com.example

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.AudioSettings
import com.example.data.LibraryCategory
import com.example.data.PreferencesRepository
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.TrackEntity
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.DiscoverTuneConfig
import com.example.data.intelligence.ListeningHistoryInput
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.MusicIntelligenceRequest
import com.example.data.library.ScanState
import com.example.data.toStableList
import com.example.data.toStableMap
import android.media.AudioDeviceInfo
import com.example.data.lyrics.OfflineLyricsLoader
import com.example.playback.AudioOutputMonitor
import com.example.playback.AudioOutputRouteType
import com.example.playback.PlaybackProgress
import coil.size.Size as CoilSize
import coil.transition.Transition
import com.example.ui.CadenceTab
import com.example.ui.ExpandableNowPlayingOverlay
import com.example.ui.FloatingBottomDockOverlay
import com.example.ui.FloatingExpressiveNavDock
import com.example.ui.FloatingNavBarState
import com.example.ui.PersistentTabHost
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.SCRUBBER_BLOOM_BASE_RADIUS_DP
import com.example.ui.components.SCRUBBER_BLOOM_BREATH_DURATION_MS
import com.example.ui.components.SCRUBBER_BLOOM_PAUSED_PEAK_ALPHA
import com.example.ui.components.SCRUBBER_BLOOM_PAUSED_RADIUS_DP
import com.example.ui.components.SCRUBBER_BLOOM_PLAYING_MAX_RADIUS_DP
import com.example.ui.components.SCRUBBER_BLOOM_PLAYING_MIN_RADIUS_DP
import com.example.ui.components.SCRUBBER_BLOOM_STOP_0_ALPHA
import com.example.ui.components.SCRUBBER_BLOOM_STOP_1_ALPHA
import com.example.ui.components.SCRUBBER_BLOOM_STOP_2_ALPHA
import com.example.ui.components.SCRUBBER_BLOOM_STOP_3_ALPHA
import com.example.ui.components.SCRUBBER_THUMB_CORE_DP
import com.example.ui.components.SCRUBBER_THUMB_LOBE_COUNT
import com.example.ui.components.SCRUBBER_THUMB_ROTATION_DURATION_MS
import com.example.ui.components.SCRUBBER_TOUCH_HEIGHT_DP
import com.example.ui.components.SCRUBBER_UNPLAYED_TRACK_DP
import com.example.ui.components.buildScallopedThumbPath
import com.example.ui.components.SmartPlaylistSheetContent
import com.example.ui.components.buildArtworkImageRequest
import com.example.ui.components.formatSmartMixSubtitle
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.HomeUiState
import com.example.ui.player.LibraryUiState
import com.example.ui.player.NowPlayingUiState
import com.example.ui.player.SleepTimerState
import com.example.ui.player.SmartPlaylistUiState
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.formatHomeGreeting
import kotlinx.coroutines.flow.first
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.MatchaLightScheme
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.opaqueOver
import com.example.ui.theme.toAppCompatNightMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MusicIntelligenceAndPerfTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleLibraryTracks = listOf(
        Track(
            id = "local_nocturne",
            title = "Velvet Nocturne",
            artist = "Miro & Sol",
            album = "Afterglow",
            genre = "Ambient",
            durationMs = 212_000L,
            bpm = 78,
            playCount = 14,
            isFavorite = true
        ),
        Track(
            id = "local_solstice",
            title = "Amber Solstice",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Indie Folk",
            durationMs = 195_000L,
            bpm = 88,
            playCount = 9
        ),
        Track(
            id = "local_botanical",
            title = "Botanical Echo",
            artist = "Sora & Moss",
            album = "Glasshouse",
            genre = "Downtempo",
            durationMs = 224_000L,
            bpm = 92,
            playCount = 6
        ),
        Track(
            id = "local_chroma",
            title = "Chroma Drift",
            artist = "Neon Loom",
            album = "Prism",
            genre = "Synthwave",
            durationMs = 188_000L,
            bpm = 126,
            playCount = 2
        ),
        Track(
            id = "local_ceramic",
            title = "Ceramic Pulse",
            artist = "Aalto Trio",
            album = "Tactile",
            genre = "Nu Jazz",
            durationMs = 204_000L,
            bpm = 104,
            playCount = 8
        )
    )

    @Test
    fun `MusicIntelligenceEngine produces valid RFC 8259 JSON with moods tempo and verified ranked track IDs`() {
        val engine = MusicIntelligenceEngine()
        val tracks = sampleLibraryTracks
        val history = engine.deriveListeningHistory(
            libraryTracks = tracks,
            skippedTrackIds = listOf("local_chroma")
        )
        val request = MusicIntelligenceRequest(
            seedTrackId = "local_nocturne",
            targetMood = "late_night",
            maxTracks = 4,
            timeOfDay = "late_night"
        )

        val output = engine.analyzeAndRecommend(request, history, tracks)

        val json = JSONObject(output.rawJson)
        assertEquals("1.0", json.getString("schemaVersion"))
        assertTrue(json.has("playlistProfile"))
        assertTrue(json.has("moodClassifications"))
        assertTrue(json.has("tempoDescriptors"))
        assertTrue(json.has("recommendedTracks"))

        val validIds = tracks.map { it.id }.toSet()
        assertEquals(4, output.recommendedTracks.size)
        assertEquals("local_nocturne", output.recommendedTracks.first().trackId)
        assertTrue(output.recommendedTracks.all { it.trackId in validIds })
        assertTrue(output.moodClassifications.isNotEmpty())
        assertTrue(output.playlistProfile.cohesionScore in 0.35..1.0)
        assertFalse(output.tempoDescriptors.tempoInferred)
        assertTrue(MusicIntelligenceEngine.SYSTEM_PROMPT.contains("recommendedTracks"))
    }

    @Test
    fun `MusicIntelligenceEngine infers BPM when missing filters hallucinated IDs and handles empty library`() {
        val engine = MusicIntelligenceEngine()

        val emptyOut = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(maxTracks = 10),
            history = ListeningHistoryInput(),
            libraryTracks = emptyList()
        )
        assertTrue(emptyOut.recommendedTracks.isEmpty())
        assertEquals(0.0, emptyOut.playlistProfile.cohesionScore, 0.001)

        val unknownBpmTrack = Track(
            id = "local_no_bpm",
            title = "Midnight Rain Study",
            artist = "Indie Tape",
            album = "After Hours",
            genre = "Ambient",
            bpm = 0,
            durationMs = 210_000L
        )
        val inferredOut = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(targetMood = "calm", maxTracks = 5),
            history = ListeningHistoryInput(),
            libraryTracks = listOf(unknownBpmTrack)
        )
        assertTrue(inferredOut.tempoDescriptors.tempoInferred)
        assertEquals("largo_ambient", inferredOut.tempoDescriptors.descriptor)

        val injectedJson = """
            {
              "schemaVersion": "1.0",
              "playlistProfile": { "suggestedTitle": "Test", "suggestedPalette": "cobalt", "cohesionScore": 0.85 },
              "moodClassifications": [{ "mood": "calm", "confidence": 0.9, "energyLevel": "low", "valence": "neutral" }],
              "tempoDescriptors": { "dominantBpmRange": { "min": 70, "max": 90 }, "descriptor": "downtempo_chill", "pacingCurve": "steady", "tempoInferred": false },
              "recommendedTracks": [
                { "rank": 1, "trackId": "fake_hallucinated_id", "score": 0.99, "moodTag": "calm", "tempoDescriptor": "downtempo_chill", "reason": "Hallucinated" },
                { "rank": 2, "trackId": "local_no_bpm", "score": 0.88, "moodTag": "calm", "tempoDescriptor": "largo_ambient", "reason": "Valid local track" }
              ]
            }
        """.trimIndent()
        val parsed = engine.parseOutputJson(injectedJson, validTrackIds = setOf("local_no_bpm"))
        assertEquals(listOf("local_no_bpm"), parsed.recommendedTracks.map { it.trackId })
        assertEquals(1, parsed.recommendedTracks.first().rank)
    }

    @Test
    fun `TrackDao dedupe tuples and opaqueOver surface blending eliminate offscreen alpha layers`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val blended = Color(0xFFD96B43).opaqueOver(Color(0xFFFFF8F5), 0.72f)
        assertEquals("opaqueOver must always produce a fully opaque color (alpha == 1f)", 1.0f, blended.alpha, 0.001f)

        val db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val entities = sampleLibraryTracks.map { TrackEntity.fromDomain(it) }
            db.trackDao().insertTracksIgnore(entities)
            val tuples = db.trackDao().getTrackDedupeTuples()
            assertEquals(entities.size, tuples.size)
            assertTrue(tuples.all { it.dedupeKey.isNotBlank() })
        } finally {
            db.close()
        }
    }

    @Test
    fun `CadenceTheme propagates LocalPerformanceMode accurately`() {
        var observedPerf = false
        composeRule.setContent {
            CadenceTheme(
                palette = ExpressivePalette.MATCHA,
                appearanceMode = AppearanceMode.LIGHT,
                performanceMode = true
            ) {
                observedPerf = LocalPerformanceMode.current
            }
        }
        composeRule.waitForIdle()
        assertTrue(observedPerf)
    }

    @Test
    fun `ExpressiveShapes cache Outline instances per Size to prevent per-layout Path allocation`() {
        val shape = ScallopedCircleShape()
        val density = Density(2f)
        val thumbSize = Size(96f, 96f)
        val first = shape.createOutline(thumbSize, LayoutDirection.Ltr, density)
        val second = shape.createOutline(thumbSize, LayoutDirection.Ltr, density)
        assertSame("Repeated layouts of identical thumbnail size must reuse cached Outline", first, second)
    }

    @Test
    fun `LibraryScreen isolates scan progress recompositions and omits top-bar sparkles button`() {
        val tracks = sampleLibraryTracks
        val scanFlow = MutableStateFlow(ScanState(isScanning = false))
        var parentRecompositions = 0

        composeRule.setContent {
            SideEffect { parentRecompositions++ }
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = tracks.toStableList(),
                        displayedTracks = tracks.toStableList()
                    ),
                    currentTrackId = tracks.first().id,
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
                    onRemoveTrack = {},
                    scanStateFlow = scanFlow
                )
            }
        }

        composeRule.waitForIdle()
        val baseline = parentRecompositions

        for (i in 1..5) {
            scanFlow.value = ScanState(isScanning = true, scannedCount = i * 8, statusLabel = "Scanned ${i * 8} tracks…")
        }
        composeRule.waitForIdle()
        assertEquals(baseline, parentRecompositions)

        // Sparkles/AI trigger is in Discover; duplicate search icon button removed in favor of persistent search pill
        composeRule.onNodeWithTag("smart_mix_button").assertDoesNotExist()
        composeRule.onNodeWithTag("library_search_button").assertDoesNotExist()
        composeRule.onNodeWithTag("library_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("library_settings_button").assertIsDisplayed()
    }

    @Test
    fun `Discover feed generates all 6 distinct deduplicated categories with cold-start fallback`() {
        val engine = MusicIntelligenceEngine()
        val nowMs = 1_760_000_000_000L
        val oneDayMs = 24L * 60L * 60L * 1000L

        // Build 36 varied local tracks covering recent favorites, 45d unplayed, forgotten favorites, & genres
        val genres = listOf("Ambient", "Indie Folk", "Synthwave", "Nu Jazz", "Downtempo", "Classical")
        val richLibrary = (1..36).map { idx ->
            val genre = genres[idx % genres.size]
            val playCount = when {
                idx in 1..6 -> 18 - idx             // Top played recent
                idx in 7..12 -> 14                  // Forgotten favorites (high plays, 25 days ago)
                idx in 13..18 -> 0                  // Never played (Rediscover)
                idx in 19..24 -> 2                  // 45+ days ago (Rediscover)
                else -> 1                           // Deeper cuts (Explore)
            }
            val lastPlayedAt = when {
                idx in 1..6 -> nowMs - (idx * oneDayMs)
                idx in 7..12 -> nowMs - (25L * oneDayMs)
                idx in 13..18 -> 0L
                idx in 19..24 -> nowMs - (45L * oneDayMs)
                else -> nowMs - (10L * oneDayMs)
            }
            Track(
                id = "track_$idx",
                title = "Track $idx ($genre)",
                artist = "Artist ${(idx % 12) + 1}",
                album = "Album ${(idx % 9) + 1}",
                genre = genre,
                durationMs = 180_000L + idx * 3_000L,
                bpm = 72 + (idx % 6) * 10,
                dateAdded = nowMs - ((40 - idx) * oneDayMs),
                playCount = playCount,
                lastPlayedAt = lastPlayedAt,
                isFavorite = idx in listOf(1, 2, 7, 8)
            )
        }

        val feed = engine.generateDiscoverFeed(
            libraryTracks = richLibrary,
            currentTrackId = "track_1",
            nowMs = nowMs,
            maxTracksPerSection = 5
        )

        assertEquals(6, feed.sections.size)
        assertEquals(
            listOf(
                DiscoverCategory.MADE_FOR_YOU,
                DiscoverCategory.SMART_MIX,
                DiscoverCategory.REDISCOVER,
                DiscoverCategory.FORGOTTEN_FAVORITES,
                DiscoverCategory.CONTINUE_THE_VIBE,
                DiscoverCategory.EXPLORE_YOUR_LIBRARY
            ),
            feed.sections.map { it.category }
        )

        // All 6 sections must be non-empty and completely deduplicated across sections on a 36-track library
        assertTrue(feed.sections.all { it.tracks.isNotEmpty() })
        val allIds = feed.sections.flatMap { s -> s.tracks.map { it.id } }
        assertEquals("Tracks across the 6 Discover sections should avoid duplicates", allIds.size, allIds.toSet().size)

        // Rediscover tracks must be 0 plays or >= 30 days since last play
        val rediscoverTracks = feed.sections.first { it.category == DiscoverCategory.REDISCOVER }.tracks
        assertTrue(
            rediscoverTracks.all { it.playCount == 0 || (nowMs - it.lastPlayedAt) >= 30L * oneDayMs }
        )

        // Graceful fallback with zero listening history (brand new library)
        val freshLibrary = (1..10).map { idx ->
            Track(
                id = "fresh_$idx",
                title = "Fresh Track $idx",
                artist = "New Artist $idx",
                album = "Debut $idx",
                genre = genres[idx % genres.size],
                durationMs = 200_000L,
                bpm = 0,
                dateAdded = nowMs - (idx * oneDayMs),
                playCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false
            )
        }
        val coldFeed = engine.generateDiscoverFeed(libraryTracks = freshLibrary, nowMs = nowMs)
        assertEquals(6, coldFeed.sections.size)
        assertTrue("All 6 sections must gracefully populate even with zero play history", coldFeed.sections.all { it.tracks.isNotEmpty() })
    }

    @Test
    fun `FloatingExpressiveNavDock expands only active tab among Home Library Discover and collapses inactive tabs into icons`() {
        var activeTab by mutableStateOf(CadenceTab.HOME)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                FloatingExpressiveNavDock(
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it }
                )
            }
        }

        composeRule.waitForIdle()
        // Only Home label visible initially; Home pill is expanded while inactive tabs stay 50dp circles
        composeRule.onNodeWithTag("nav_label_home", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("nav_label_discover", useUnmergedTree = true).assertDoesNotExist()
        val homeActiveW = composeRule.onNodeWithTag("nav_home").getUnclippedBoundsInRoot().let { it.right - it.left }
        val libInactiveW = composeRule.onNodeWithTag("nav_library").getUnclippedBoundsInRoot().let { it.right - it.left }
        assertTrue("Active Home pill must expand wider than inactive circular tab ($homeActiveW vs $libInactiveW)", homeActiveW > libInactiveW + 20.dp)
        assertTrue("Inactive circular tab width must stay ~50dp, was $libInactiveW", kotlin.math.abs((libInactiveW - 50.dp).value) <= 2f)

        // Tap Discover -> Discover expands with label, Home collapses cleanly to 50dp circle
        composeRule.onNodeWithTag("nav_discover").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.DISCOVER, activeTab)
        composeRule.onNodeWithTag("nav_label_discover", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_home", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertDoesNotExist()
        val homeCollapsedW = composeRule.onNodeWithTag("nav_home").getUnclippedBoundsInRoot().let { it.right - it.left }
        val discoverActiveW = composeRule.onNodeWithTag("nav_discover").getUnclippedBoundsInRoot().let { it.right - it.left }
        assertTrue("Collapsed Home tab must return to ~50dp circle, was $homeCollapsedW", kotlin.math.abs((homeCollapsedW - 50.dp).value) <= 2f)
        assertTrue("Expanded Discover tab must be wider than collapsed Home tab", discoverActiveW > homeCollapsedW + 20.dp)

        // Tap Library -> Library expands with label, Discover collapses cleanly
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.LIBRARY, activeTab)
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_discover", useUnmergedTree = true).assertDoesNotExist()

        // Tap Home again -> smooth round-trip back to Home
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.HOME, activeTab)
        composeRule.onNodeWithTag("nav_label_home", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertDoesNotExist()

        // Route restoration defaults to Home
        assertEquals(CadenceTab.HOME, CadenceTab.fromRoute("home"))
        assertEquals(CadenceTab.HOME, CadenceTab.fromRoute("unknown_route"))
    }

    @Test
    fun `DiscoverScreen renders all 6 sections plays mixes and opens Smart Mix tuner`() {
        val engine = MusicIntelligenceEngine()
        val tracks = sampleLibraryTracks
        val feed = engine.generateDiscoverFeed(libraryTracks = tracks, currentTrackId = tracks.first().id)
        val smartFlow = MutableStateFlow(SmartPlaylistUiState())
        var playedCount = 0
        var savedName: String? = null
        var refreshed = false

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                DiscoverScreen(
                    uiState = DiscoverUiState(
                        allTracks = tracks.toStableList(),
                        sections = feed.sections,
                        anchorTrackTitle = feed.anchorTrackTitle,
                        isInitialLibraryLoaded = true
                    ),
                    currentTrackId = tracks.first().id,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onSelectTrackFromSection = { _, list -> playedCount = list.size },
                    onPlaySection = { list -> playedCount = list.size },
                    onRefreshDiscover = { refreshed = true },
                    onOpenSettings = {},
                    onAddFolder = {},
                    onImportFiles = {},
                    onSaveMixAsPlaylist = { name, _ -> savedName = name },
                    smartPlaylistFlow = smartFlow,
                    currentTrackTitle = tracks.first().title,
                    onGenerateSmartPlaylist = { mood, seed ->
                        val out = engine.analyzeAndRecommend(
                            MusicIntelligenceRequest(
                                seedTrackId = if (seed) tracks.first().id else null,
                                targetMood = mood,
                                maxTracks = 5
                            ),
                            engine.deriveListeningHistory(tracks),
                            tracks
                        )
                        smartFlow.value = SmartPlaylistUiState(
                            selectedMood = mood,
                            seedFromCurrent = seed,
                            isGenerating = false,
                            output = out
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("discover_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_search_button").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_settings_button").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_daily_mix_menu"))
        composeRule.onNodeWithTag("discover_daily_mix_menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_daily_mix_refresh").performClick()
        assertTrue(refreshed)

        // Play All on Made for you
        composeRule.onNodeWithTag("discover_play_all_made_for_you").performClick()
        assertTrue(playedCount > 0)

        // Scroll through all 6 sections to confirm they exist
        DiscoverCategory.entries.forEach { category ->
            composeRule.onNodeWithTag("discover_list")
                .performScrollToNode(hasTestTag("discover_section_${category.id}"))
            composeRule.onNodeWithTag("discover_section_${category.id}").assertIsDisplayed()
        }

        // Open Tune Mix sheet, verify clean header (no JSON debug UI), and save playlist
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_tune_smart_mix_button"))
        composeRule.onNodeWithTag("discover_tune_smart_mix_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_mix_result_title").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_mix_result_subtitle").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_toggle_json_button").assertDoesNotExist()
        composeRule.onNodeWithTag("smart_mix_raw_json").assertDoesNotExist()
        composeRule.onNodeWithTag("smart_mix_save_button").performClick()
        composeRule.waitForIdle()

        assertTrue(!savedName.isNullOrBlank())
    }

    @Test
    fun `DiscoverFeedGenerator handles large 120-track cold-start library and edge-case metadata without TimSort or math crashes`() {
        val engine = MusicIntelligenceEngine()
        val nowMs = 1_760_000_000_000L

        // 120 freshly-scanned tracks with identical playCount=0, lastPlayedAt=0, dateAdded=0
        // (triggers TimSort merge paths >= 32 items where non-deterministic Comparators crash!)
        val largeColdStartLibrary = (1..120).map { idx ->
            Track(
                id = "cold_$idx",
                title = if (idx % 15 == 0) "" else "Song $idx",
                artist = if (idx % 10 == 0) "" else "Artist ${idx % 18}",
                album = if (idx % 12 == 0) "" else "Album ${idx % 14}",
                genre = if (idx % 8 == 0) "" else "Genre ${idx % 6}",
                durationMs = if (idx % 20 == 0) 0L else 180_000L,
                bpm = if (idx % 5 == 0) 0 else 90,
                dateAdded = 0L,
                playCount = if (idx == 1) -3 else 0,
                lastPlayedAt = 0L,
                isFavorite = false
            )
        } + listOf(
            // Duplicate ID & blank ID edge cases
            Track(id = "cold_1", title = "Dup 1", artist = "Dup", album = "", durationMs = 180_000L),
            Track(id = "   ", title = "Blank ID", artist = "", album = "", durationMs = 180_000L)
        )

        // Test multiple refresh nonces including boundary values (0, 1, 7, Int.MAX_VALUE)
        for (nonce in listOf(0, 1, 7, Int.MAX_VALUE)) {
            val feed = engine.generateDiscoverFeed(
                libraryTracks = largeColdStartLibrary,
                currentTrackId = "missing_id",
                skippedTrackIds = listOf("cold_2", "nonexistent"),
                nowMs = nowMs,
                shuffleNonce = nonce
            )
            assertEquals(6, feed.sections.size)
            feed.sections.forEach { section ->
                assertTrue("Section ${section.category} should be populated", section.tracks.isNotEmpty())
                assertEquals(
                    "Section ${section.category} must not contain duplicate track IDs",
                    section.tracks.size,
                    section.tracks.map { it.id }.toSet().size
                )
            }
        }

        // Empty library & single-track library
        val emptyFeed = engine.generateDiscoverFeed(libraryTracks = emptyList(), nowMs = nowMs)
        assertTrue(emptyFeed.sections.isEmpty())

        val singleTrackFeed = engine.generateDiscoverFeed(
            libraryTracks = listOf(
                Track(id = "solo_1", title = "", artist = "", album = "", genre = "", durationMs = 0L, bpm = 0)
            ),
            nowMs = nowMs
        )
        assertEquals(6, singleTrackFeed.sections.size)
        assertTrue(singleTrackFeed.sections.all { it.tracks.isNotEmpty() })
    }

    @Test
    fun `DiscoverScreen handles duplicate Lazy keys empty library and error fallback without crashing`() {
        val dupTrack = sampleLibraryTracks.first()
        val duplicateTracksSection = com.example.data.intelligence.DiscoverSection(
            category = DiscoverCategory.MADE_FOR_YOU,
            title = "Made for you",
            subtitle = "Stress test duplicate tracks",
            badgeText = "Daily Mix",
            tracks = listOf(dupTrack, dupTrack, sampleLibraryTracks[1]).toStableList()
        )
        val duplicateSmartMixSection = com.example.data.intelligence.DiscoverSection(
            category = DiscoverCategory.SMART_MIX,
            title = "Smart Mix",
            subtitle = "Stress test duplicate smart mix tracks",
            badgeText = "90 BPM",
            tracks = listOf(dupTrack, dupTrack).toStableList()
        )

        var currentUiState by mutableStateOf(
            DiscoverUiState(
                allTracks = sampleLibraryTracks.toStableList(),
                sections = listOf(duplicateTracksSection, duplicateSmartMixSection, duplicateTracksSection).toStableList(),
                isInitialLibraryLoaded = true
            )
        )
        var retryClicks = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                DiscoverScreen(
                    uiState = currentUiState,
                    currentTrackId = dupTrack.id,
                    isPlaying = true,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onSelectTrackFromSection = { _, _ -> },
                    onPlaySection = {},
                    onRefreshDiscover = { retryClicks++ },
                    onOpenSettings = {},
                    onAddFolder = {},
                    onImportFiles = {},
                    onSaveMixAsPlaylist = { _, _ -> }
                )
            }
        }

        // Duplicate keys across and within sections must render cleanly without IllegalArgumentException
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_header_title").assertIsDisplayed()

        // Transition to Empty state
        currentUiState = DiscoverUiState(
            allTracks = emptyList<Track>().toStableList(),
            sections = emptyList<com.example.data.intelligence.DiscoverSection>().toStableList(),
            isInitialLibraryLoaded = true
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_empty_card").assertIsDisplayed()

        // Transition to Error fallback state and tap Retry
        currentUiState = DiscoverUiState(
            allTracks = sampleLibraryTracks.toStableList(),
            sections = emptyList<com.example.data.intelligence.DiscoverSection>().toStableList(),
            isInitialLibraryLoaded = true,
            errorMessage = "Simulated generation failure"
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_retry_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, retryClicks)
    }

    @Test
    fun `Switching to Discover tab via FloatingExpressiveNavDock renders cleanly for both empty and large libraries`() {
        val engine = MusicIntelligenceEngine()
        val largeLibrary = (1..80).map { idx ->
            Track(
                id = "nav_track_$idx",
                title = "Local Track $idx",
                artist = "Artist ${idx % 10}",
                album = "Album ${idx % 8}",
                genre = if (idx % 2 == 0) "Ambient" else "Indie Folk",
                durationMs = 195_000L,
                bpm = 0,
                playCount = 0,
                lastPlayedAt = 0L
            )
        }

        var activeTab by mutableStateOf(CadenceTab.HOME)
        var usePopulatedLibrary by mutableStateOf(false)
        var refreshNonce by mutableStateOf(0)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.DYNAMIC, appearanceMode = AppearanceMode.SYSTEM) {
                androidx.compose.material3.Scaffold(
                    bottomBar = {
                        FloatingExpressiveNavDock(
                            activeTab = activeTab,
                            onSelectTab = { activeTab = it }
                        )
                    }
                ) { padding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.padding(padding)
                    ) {
                        when (activeTab) {
                            CadenceTab.DISCOVER -> {
                                val tracks = if (usePopulatedLibrary) largeLibrary else emptyList()
                                val feed = engine.generateDiscoverFeed(
                                    libraryTracks = tracks,
                                    shuffleNonce = refreshNonce
                                )
                                DiscoverScreen(
                                    uiState = DiscoverUiState(
                                        allTracks = tracks.toStableList(),
                                        sections = feed.sections,
                                        anchorTrackTitle = feed.anchorTrackTitle,
                                        isInitialLibraryLoaded = true,
                                        refreshCount = refreshNonce
                                    ),
                                    currentTrackId = null,
                                    isPlaying = false,
                                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                                    onSelectTrackFromSection = { _, _ -> },
                                    onPlaySection = {},
                                    onRefreshDiscover = { refreshNonce++ },
                                    onOpenSettings = {},
                                    onAddFolder = {},
                                    onImportFiles = {},
                                    onSaveMixAsPlaylist = { _, _ -> }
                                )
                            }
                            else -> {
                                androidx.compose.material3.Text(text = activeTab.route)
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // 1. Navigate to Discover with empty library
        composeRule.onNodeWithTag("nav_discover").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.DISCOVER, activeTab)
        composeRule.onNodeWithTag("discover_empty_card").assertIsDisplayed()

        // 2. Populate 80-track freshly-scanned library and switch away & back to Discover
        usePopulatedLibrary = true
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_discover").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_section_made_for_you").assertIsDisplayed()

        // 3. Trigger Refresh on Discover via Made for you 3-dot menu
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_daily_mix_menu"))
        composeRule.onNodeWithTag("discover_daily_mix_menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_daily_mix_refresh").performClick()
        composeRule.waitForIdle()
        assertEquals(1, refreshNonce)
        composeRule.onNodeWithTag("discover_section_made_for_you").assertIsDisplayed()
    }

    @Test
    fun `PreferencesRepository mirrors theme synchronously and MainActivity opens directly into Light mode with zero flash`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)

        // Persist Light mode + Matcha palette and verify synchronous SharedPreferences snapshot
        repo.setAppearanceMode(AppearanceMode.LIGHT)
        repo.setPalette(ExpressivePalette.MATCHA)
        repo.setArtworkShape(ExpressiveArtworkShape.SQUIRCLE)

        val snapshot = PreferencesRepository.readStartupThemeSnapshot(context)
        assertEquals(AppearanceMode.LIGHT, snapshot.appearanceMode)
        assertEquals(ExpressivePalette.MATCHA, snapshot.palette)
        assertEquals(ExpressiveArtworkShape.SQUIRCLE, snapshot.artworkShape)

        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppearanceMode.LIGHT.toAppCompatNightMode())
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppearanceMode.DARK.toAppCompatNightMode())
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppearanceMode.SYSTEM.toAppCompatNightMode())

        // Verify MainActivity.onCreate applies AppCompat night mode & Light Matcha window background synchronously
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.getDefaultNightMode())
            val bgDrawable = activity.window.decorView.background as? ColorDrawable
            assertEquals(
                "Window background must immediately match Light Matcha surface color on cold start",
                MatchaLightScheme.background.toArgb(),
                bgDrawable?.color
            )
        } finally {
            controller.destroy()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @Test
    fun `FloatingBottomDockOverlay floats over full-bleed scrolling Library content without hard-clipping list bounds`() {
        val manyTracks = (1..20).map { idx ->
            sampleLibraryTracks[idx % sampleLibraryTracks.size].copy(
                id = "float_track_$idx",
                title = "Floating Track $idx"
            )
        }
        var activeTab by mutableStateOf(CadenceTab.LIBRARY)
        var expandedMiniPlayer = false

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LibraryScreen(
                        uiState = LibraryUiState(
                            allTracks = manyTracks.toStableList(),
                            displayedTracks = manyTracks.toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrackId = manyTracks.first().id,
                        isPlaying = true,
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

                    FloatingBottomDockOverlay(
                        activeTab = activeTab,
                        currentTrack = manyTracks.first(),
                        isPlaying = true,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectTab = { activeTab = it },
                        onExpandMiniPlayer = { expandedMiniPlayer = true },
                        onTogglePlayPause = {},
                        onNext = {},
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("floating_bottom_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("floating_nav_dock").assertIsDisplayed()

        // Verify the scrolling list extends all the way behind the floating mini-player & nav dock
        val listBounds = composeRule.onNodeWithTag("library_results_list").getUnclippedBoundsInRoot()
        val miniPlayerBounds = composeRule.onNodeWithTag("mini_player_pill").getUnclippedBoundsInRoot()
        val dockBounds = composeRule.onNodeWithTag("floating_nav_dock").getUnclippedBoundsInRoot()

        assertTrue(
            "Library list bottom (${listBounds.bottom}) must extend behind floating mini-player top (${miniPlayerBounds.top})",
            listBounds.bottom > miniPlayerBounds.top
        )
        assertTrue(
            "Library list bottom (${listBounds.bottom}) must extend to the bottom edge behind the floating nav dock (${dockBounds.bottom})",
            listBounds.bottom >= dockBounds.bottom - 1.dp
        )

        // Verify clicking the floating mini-player still responds cleanly
        composeRule.onNodeWithTag("mini_player_pill").performClick()
        composeRule.waitForIdle()
        assertTrue(expandedMiniPlayer)
    }

    @Test
    fun `NowPlayingScreen fits drag handle 2-group 42dp glass header artwork 10dp transport row and 3 utility pills without scrolling`() {
        val hiFiTrack = sampleLibraryTracks.first().copy(
            id = "hifi_now_playing",
            title = "Velvet Nocturne (Master)",
            artist = "Miro & Sol",
            genre = "Ambient",
            durationMs = 248_000L,
            mimeType = "audio/flac",
            bitrateKbps = 1411,
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 64_000L, durationMs = 248_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())
        var lyricsClicked = false
        var moodClicked = false
        var collapsed = false
        var lastSeekFraction: Float? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                // Constrain to a compact standard portrait viewport (360dp x 640dp) to prove zero-scroll fit
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    NowPlayingScreen(
                        uiState = NowPlayingUiState(
                            currentTrack = hiFiTrack,
                            isPlaying = false,
                            isInitialLibraryLoaded = true,
                            queueTracks = listOf(hiFiTrack).toStableList(),
                            currentQueueIndex = 0
                        ),
                        themePrefs = ThemePreferences(
                            palette = ExpressivePalette.MATCHA,
                            appearanceMode = AppearanceMode.LIGHT,
                            artworkShape = ExpressiveArtworkShape.SCALLOP
                        ),
                        progressFlow = progressFlow,
                        sleepTimerFlow = sleepTimerFlow,
                        playlists = StableList(),
                        onTogglePlayPause = {},
                        onNext = {},
                        onPrevious = {},
                        onSeek = { lastSeekFraction = it },
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onToggleFavorite = {},
                        onMatchMood = { moodClicked = true },
                        onCollapse = { collapsed = true },
                        onRequestLyrics = { lyricsClicked = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Standard portrait layout must NOT require scrolling
        composeRule.onNodeWithTag("now_playing_portrait_container").assert(!hasScrollAction())

        // Every major control from drag handle + 4-element top bar down to 3-pill utility row must be simultaneously visible
        val expectedVisibleTags = listOf(
            "now_playing_drag_handle",
            "now_playing_drag_handle_pill",
            "now_playing_top_header",
            "collapse_now_playing_button",
            "now_playing_status_pill",
            "audio_output_button",
            "queue_button",
            "hero_album_artwork",
            "now_playing_title",
            "now_playing_hifi_badge",
            "playback_scrubber",
            "scrubber_elapsed_time",
            "scrubber_duration_time",
            "shuffle_button",
            "previous_button",
            "play_pause_button",
            "next_button",
            "repeat_button",
            "lyrics_pill",
            "timer_pill",
            "match_mood_pill"
        )
        expectedVisibleTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        // Verify centered 36dp x 4dp drag handle pill above 4-element header
        val containerBounds = composeRule.onNodeWithTag("now_playing_portrait_container").getUnclippedBoundsInRoot()
        val dragHandleBounds = composeRule.onNodeWithTag("now_playing_drag_handle").getUnclippedBoundsInRoot()
        val dragPillBounds = composeRule.onNodeWithTag("now_playing_drag_handle_pill").getUnclippedBoundsInRoot()
        assertEquals(36f, (dragPillBounds.right - dragPillBounds.left).value, 1f)
        assertEquals(4f, (dragPillBounds.bottom - dragPillBounds.top).value, 1f)
        val dragCenterX = (dragPillBounds.left.value + dragPillBounds.right.value) / 2f
        val containerCenterX = (containerBounds.left.value + containerBounds.right.value) / 2f
        assertEquals("Drag handle pill must be horizontally centered", containerCenterX, dragCenterX, 1.5f)

        // Verify all 4 separate 42dp header elements with 8dp gaps on left and right
        composeRule.onNodeWithText("PAUSED", useUnmergedTree = true).assertIsDisplayed()
        val minimizeBounds = composeRule.onNodeWithTag("collapse_now_playing_button").getUnclippedBoundsInRoot()
        val statusPillBounds = composeRule.onNodeWithTag("now_playing_status_pill").getUnclippedBoundsInRoot()
        val outputBounds = composeRule.onNodeWithTag("audio_output_button").getUnclippedBoundsInRoot()
        val queueBounds = composeRule.onNodeWithTag("queue_button").getUnclippedBoundsInRoot()

        assertTrue("Drag handle must sit above 4-element header", dragHandleBounds.bottom <= minimizeBounds.top + 1.dp)
        assertEquals("Minimize circle must be 42dp wide", 42f, (minimizeBounds.right - minimizeBounds.left).value, 1f)
        assertEquals("Minimize circle must be 42dp tall", 42f, (minimizeBounds.bottom - minimizeBounds.top).value, 1f)
        assertEquals("Status pill must be 42dp tall", 42f, (statusPillBounds.bottom - statusPillBounds.top).value, 1f)
        assertEquals("Left Minimize -> Status pill gap must be 8dp", 8f, (statusPillBounds.left - minimizeBounds.right).value, 1f)
        assertEquals("Audio output circle must be 42dp wide", 42f, (outputBounds.right - outputBounds.left).value, 1f)
        assertEquals("Audio output circle must be 42dp tall", 42f, (outputBounds.bottom - outputBounds.top).value, 1f)
        assertEquals("Queue circle must be 42dp wide", 42f, (queueBounds.right - queueBounds.left).value, 1f)
        assertEquals("Queue circle must be 42dp tall", 42f, (queueBounds.bottom - queueBounds.top).value, 1f)
        assertEquals("Right Output -> Queue gap must be 8dp", 8f, (queueBounds.left - outputBounds.right).value, 1f)
        assertTrue("Status pill must not overlap Audio output circle", statusPillBounds.right < outputBounds.left)

        // Verify 32dp Audio Quality Pill with 14dp 3-bar waveform icon and 14dp trailing chevron
        val badgeBounds = composeRule.onNodeWithTag("now_playing_hifi_badge").getUnclippedBoundsInRoot()
        assertEquals("Audio quality pill height must be 32dp", 32f, (badgeBounds.bottom - badgeBounds.top).value, 1f)
        val waveIconBounds = composeRule.onNodeWithTag("audio_badge_waveform_icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(14f, (waveIconBounds.right - waveIconBounds.left).value, 1f)
        assertEquals(14f, (waveIconBounds.bottom - waveIconBounds.top).value, 1f)
        val chevronBounds = composeRule.onNodeWithTag("audio_badge_chevron", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(14f, (chevronBounds.right - chevronBounds.left).value, 1f)
        assertEquals(14f, (chevronBounds.bottom - chevronBounds.top).value, 1f)

        // Verify 8-lobe rotating thumb + edgeless 5-stop light bloom scrubber specs, 48dp touch height, and drag seek
        assertEquals(3f, SCRUBBER_UNPLAYED_TRACK_DP, 0.01f)
        assertEquals(14f, SCRUBBER_THUMB_CORE_DP, 0.01f)
        assertEquals(28f, SCRUBBER_BLOOM_BASE_RADIUS_DP, 0.01f)
        assertEquals(22f, SCRUBBER_BLOOM_PAUSED_RADIUS_DP, 0.01f)
        assertEquals(8, SCRUBBER_THUMB_LOBE_COUNT)
        assertEquals(6000, SCRUBBER_THUMB_ROTATION_DURATION_MS)
        assertEquals(48f, SCRUBBER_TOUCH_HEIGHT_DP, 0.01f)
        val thumbBounds = buildScallopedThumbPath(outerRadiusPx = 20f).getBounds()
        assertFalse("8-lobe scalloped thumb path must be non-empty", thumbBounds.isEmpty)
        assertTrue("Scalloped thumb path must fit within outer radius", thumbBounds.width in 36f..40.5f && thumbBounds.height in 36f..40.5f)
        val scrubberBounds = composeRule.onNodeWithTag("playback_scrubber").getUnclippedBoundsInRoot()
        assertEquals("Scrubber touch target must be 48dp tall", 48f, (scrubberBounds.bottom - scrubberBounds.top).value, 1.5f)
        val elapsedBounds = composeRule.onNodeWithTag("scrubber_elapsed_time").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("scrubber_elapsed_time").assertTextContains("1:04")
        composeRule.onNodeWithTag("scrubber_duration_time").assertTextContains("4:08")
        assertTrue("Timestamps must sit ~8dp below scrubber", elapsedBounds.top - scrubberBounds.bottom >= 7.dp)
        composeRule.onNodeWithTag("playback_scrubber").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertNotNull("Dragging scalloped seek handle must emit seek fraction", lastSeekFraction)

        // Artwork must adapt cleanly and stay within 260dp cap
        val artBounds = composeRule.onNodeWithTag("hero_album_artwork").getUnclippedBoundsInRoot()
        val artHeight = artBounds.bottom - artBounds.top
        val artWidth = artBounds.right - artBounds.left
        assertTrue("Hero artwork must be visible (> 120dp), was $artHeight", artHeight >= 120.dp)
        assertTrue("Hero artwork must stay capped <= 260dp, was $artHeight", artHeight <= 261.dp)
        assertTrue("Hero artwork must remain square ($artWidth x $artHeight)", kotlin.math.abs((artWidth - artHeight).value) <= 2f)

        // Verify uniform ~10dp gaps between all 5 transport buttons (no touching/cramping)
        val shuffleBounds = composeRule.onNodeWithTag("shuffle_button").getUnclippedBoundsInRoot()
        val prevBounds = composeRule.onNodeWithTag("previous_button").getUnclippedBoundsInRoot()
        val playBounds = composeRule.onNodeWithTag("play_pause_button").getUnclippedBoundsInRoot()
        val nextBounds = composeRule.onNodeWithTag("next_button").getUnclippedBoundsInRoot()
        val repeatBounds = composeRule.onNodeWithTag("repeat_button").getUnclippedBoundsInRoot()

        assertTrue("Shuffle->Prev gap must be >= 9dp", prevBounds.left - shuffleBounds.right >= 9.dp)
        assertTrue("Prev->Play gap must be >= 9dp", playBounds.left - prevBounds.right >= 9.dp)
        assertTrue("Play->Next gap must be >= 9dp", nextBounds.left - playBounds.right >= 9.dp)
        assertTrue("Next->Repeat gap must be >= 9dp", repeatBounds.left - nextBounds.right >= 9.dp)

        // Verify 40dp bottom shortcut pills with 10dp spacing and generous 24dp bottom clearance
        val lyricsBounds = composeRule.onNodeWithTag("lyrics_pill").getUnclippedBoundsInRoot()
        val timerBounds = composeRule.onNodeWithTag("timer_pill").getUnclippedBoundsInRoot()
        val moodBounds = composeRule.onNodeWithTag("match_mood_pill").getUnclippedBoundsInRoot()
        assertEquals("Lyrics pill height must be 40dp", 40f, (lyricsBounds.bottom - lyricsBounds.top).value, 1f)
        assertEquals("Timer pill height must be 40dp", 40f, (timerBounds.bottom - timerBounds.top).value, 1f)
        assertEquals("Match Mood pill height must be 40dp", 40f, (moodBounds.bottom - moodBounds.top).value, 1f)
        assertEquals("Lyrics -> Timer gap must be 10dp", 10f, (timerBounds.left - lyricsBounds.right).value, 1f)
        assertEquals("Timer -> Match Mood gap must be 10dp", 10f, (moodBounds.left - timerBounds.right).value, 1f)
        assertTrue(
            "Bottom shortcut pills must have >= 24dp open clearance above bottom edge, was ${containerBounds.bottom - lyricsBounds.bottom}",
            containerBounds.bottom - lyricsBounds.bottom >= 23.dp
        )

        // Verify utility pills & standalone minimize circle respond directly without scrolling
        composeRule.onNodeWithTag("collapse_now_playing_button").performClick()
        composeRule.onNodeWithTag("match_mood_pill").performClick()
        composeRule.onNodeWithTag("lyrics_pill").performClick()
        composeRule.waitForIdle()
        assertTrue(collapsed)
        assertTrue(moodClicked)
        assertTrue(lyricsClicked)
    }

    @Test
    fun `NowPlaying WavyExpressiveSlider renders rotating thumb and luminous halo cleanly in both playing and paused states`() {
        var playing by mutableStateOf(true)
        var soughtFraction: Float? = null
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 90_000L, durationMs = 240_000L))
        val track = sampleLibraryTracks.first()

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(390.dp).height(844.dp)) {
                    NowPlayingScreen(
                        uiState = NowPlayingUiState(
                            currentTrack = track,
                            isPlaying = playing,
                            isInitialLibraryLoaded = true,
                            queueTracks = sampleLibraryTracks.toStableList(),
                            currentQueueIndex = 0
                        ),
                        themePrefs = ThemePreferences(
                            palette = ExpressivePalette.MATCHA,
                            wavyScrubberEnabled = true,
                            performanceModeEnabled = false
                        ),
                        progressFlow = progressFlow,
                        sleepTimerFlow = MutableStateFlow(SleepTimerState()),
                        playlists = StableList(),
                        onTogglePlayPause = { playing = !playing },
                        onNext = {},
                        onPrevious = {},
                        onSeek = { soughtFraction = it },
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onToggleFavorite = {},
                        onMatchMood = {},
                        onCollapse = {}
                    )
                }
            }
        }

        // Verify edgeless 5-stop exponential light bloom specs for both playing and paused states
        assertEquals(28f, SCRUBBER_BLOOM_BASE_RADIUS_DP, 0.01f)
        assertEquals(26f, SCRUBBER_BLOOM_PLAYING_MIN_RADIUS_DP, 0.01f)
        assertEquals(32f, SCRUBBER_BLOOM_PLAYING_MAX_RADIUS_DP, 0.01f)
        assertEquals(22f, SCRUBBER_BLOOM_PAUSED_RADIUS_DP, 0.01f)
        assertEquals(1800, SCRUBBER_BLOOM_BREATH_DURATION_MS)
        assertEquals(0.46f, SCRUBBER_BLOOM_STOP_0_ALPHA, 0.001f)
        assertEquals(0.24f, SCRUBBER_BLOOM_STOP_1_ALPHA, 0.001f)
        assertEquals(0.09f, SCRUBBER_BLOOM_STOP_2_ALPHA, 0.001f)
        assertEquals(0.02f, SCRUBBER_BLOOM_STOP_3_ALPHA, 0.001f)
        assertEquals(0.22f, SCRUBBER_BLOOM_PAUSED_PEAK_ALPHA, 0.001f)
        assertTrue(
            "5-stop exponential bloom alphas must strictly decrease toward transparent",
            SCRUBBER_BLOOM_STOP_0_ALPHA > SCRUBBER_BLOOM_STOP_1_ALPHA &&
                SCRUBBER_BLOOM_STOP_1_ALPHA > SCRUBBER_BLOOM_STOP_2_ALPHA &&
                SCRUBBER_BLOOM_STOP_2_ALPHA > SCRUBBER_BLOOM_STOP_3_ALPHA &&
                SCRUBBER_BLOOM_STOP_3_ALPHA > 0f
        )

        // Playing state: advance clock across rotation & 1800ms bloom breath frames cleanly
        composeRule.mainClock.advanceTimeBy(1800L)
        composeRule.onNodeWithTag("playback_scrubber").assertIsDisplayed()
        composeRule.onNodeWithTag("playback_scrubber_canvas").assertIsDisplayed()
        val playingBounds = composeRule.onNodeWithTag("playback_scrubber").getUnclippedBoundsInRoot()
        assertEquals(48f, (playingBounds.bottom - playingBounds.top).value, 1.5f)
        composeRule.onNodeWithTag("playback_scrubber").performTouchInput { swipeRight() }
        assertNotNull("Seek drag must work while playing with active rotation & edgeless light bloom", soughtFraction)

        // Transition to paused state: settles calmly to 22dp radius / 0.22f peak alpha bloom
        soughtFraction = null
        playing = false
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("playback_scrubber").assertIsDisplayed()
        composeRule.onNodeWithTag("playback_scrubber_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("playback_scrubber").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertNotNull("Seek drag must remain crisp while paused", soughtFraction)
    }

    @Test
    fun `NowPlayingScreen long title aligns cleanly without colliding with icons and expanding audio badge keeps utility pills visible`() {
        val longTitleHiResTrack = sampleLibraryTracks.first().copy(
            id = "long_title_hires",
            title = "Midnight Reverie Across the Quiet Coastal Harbor (Extended Analog Studio Master Edition)",
            artist = "The Velvet Horizon Orchestra",
            genre = "Ambient",
            durationMs = 312_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 45_000L, durationMs = 312_000L))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                    NowPlayingScreen(
                        uiState = NowPlayingUiState(
                            currentTrack = longTitleHiResTrack,
                            isPlaying = true,
                            isInitialLibraryLoaded = true,
                            queueTracks = listOf(longTitleHiResTrack).toStableList(),
                            currentQueueIndex = 0
                        ),
                        themePrefs = ThemePreferences(
                            palette = ExpressivePalette.MATCHA,
                            appearanceMode = AppearanceMode.DARK
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
                        onMatchMood = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // 3-tier hierarchy alignment: Title, Artist, and Audio Quality Badge share leading start edge
        val titleBounds = composeRule.onNodeWithTag("now_playing_title").getUnclippedBoundsInRoot()
        val artistBounds = composeRule.onNodeWithTag("now_playing_artist").getUnclippedBoundsInRoot()
        val badgeBounds = composeRule.onNodeWithTag("now_playing_hifi_badge").getUnclippedBoundsInRoot()
        val favBounds = composeRule.onNodeWithTag("favorite_button").getUnclippedBoundsInRoot()
        val moreBounds = composeRule.onNodeWithTag("now_playing_more_button").getUnclippedBoundsInRoot()

        assertTrue("Long title must never collide with Favorite icon (${titleBounds.right} vs ${favBounds.left})", titleBounds.right <= favBounds.left)
        assertTrue("Long title must never collide with More icon (${titleBounds.right} vs ${moreBounds.left})", titleBounds.right <= moreBounds.left)
        assertTrue("Artist must sit below Title", artistBounds.top >= titleBounds.bottom + 3.dp)
        assertTrue("Audio badge must sit ~10dp below Artist", badgeBounds.top >= artistBounds.bottom + 8.dp)
        assertTrue("Title and Artist must share leading start edge", kotlin.math.abs((titleBounds.left - artistBounds.left).value) <= 1.5f)
        assertTrue("Title and Audio Badge must share leading start edge", kotlin.math.abs((titleBounds.left - badgeBounds.left).value) <= 1.5f)

        // Tap audio quality badge -> expands real extracted metadata specs
        composeRule.onNodeWithTag("now_playing_hifi_badge")
            .assertTextContains("HI-RES • 24-BIT")
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("now_playing_audio_specs_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("audio_spec_format").assertTextContains("FLAC")
        composeRule.onNodeWithTag("audio_spec_bit_depth").assertTextContains("24-bit")
        composeRule.onNodeWithTag("audio_spec_sample_rate").assertTextContains("96 kHz")
        composeRule.onNodeWithTag("audio_spec_bitrate").assertTextContains("1,411 kbps")

        // Even with 2-line long title + expanded audio specs on 360x640 viewport, zero scrolling & all utility pills stay visible!
        composeRule.onNodeWithTag("now_playing_portrait_container").assert(!hasScrollAction())
        composeRule.onNodeWithTag("playback_scrubber").assertIsDisplayed()
        composeRule.onNodeWithTag("play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("lyrics_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("timer_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("match_mood_pill").assertIsDisplayed()

        // Tap again -> collapses smoothly
        composeRule.onNodeWithTag("now_playing_hifi_badge").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now_playing_audio_specs_panel").assertDoesNotExist()
    }

    @Test
    fun `MiniPlayer expands into full-screen NowPlaying sheet unmounts when minimized and collapses via button and swipe down`() {
        val track = sampleLibraryTracks.first()
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 15_000L, durationMs = track.durationMs))
        val sleepTimerFlow = MutableStateFlow(SleepTimerState())

        var activeTab by mutableStateOf(CadenceTab.HOME)
        var isExpanded by mutableStateOf(false)
        var isPlaying by mutableStateOf(false)
        var nextClicks = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                Box(modifier = Modifier.size(width = 390.dp, height = 780.dp)) {
                    when (activeTab) {
                        CadenceTab.HOME -> HomeScreen(
                            uiState = HomeUiState(
                                allTracks = sampleLibraryTracks.toStableList(),
                                recentlyPlayed = sampleLibraryTracks.toStableList(),
                                recentlyAdded = sampleLibraryTracks.toStableList(),
                                isInitialLibraryLoaded = true
                            ),
                            currentTrack = track,
                            isPlaying = isPlaying,
                            artworkShape = ExpressiveArtworkShape.SCALLOP,
                            onExpandNowPlaying = { isExpanded = true },
                            onTogglePlayPause = { isPlaying = !isPlaying },
                            onSelectTrack = { _, _ -> },
                            onPlayTracks = { _, _ -> },
                            onToggleFavorite = {},
                            onOpenSettings = {},
                            onAddFolder = {},
                            onScanDevice = {}
                        )

                        else -> androidx.compose.material3.Text(
                            text = "Tab ${activeTab.route}",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    FloatingBottomDockOverlay(
                        activeTab = activeTab,
                        currentTrack = track,
                        isPlaying = isPlaying,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectTab = { activeTab = it },
                        onExpandMiniPlayer = { isExpanded = true },
                        onTogglePlayPause = { isPlaying = !isPlaying },
                        onNext = { nextClicks++ },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    ExpandableNowPlayingOverlay(
                        isExpanded = isExpanded,
                        onCollapse = { isExpanded = false },
                        ambientAuraEnabled = false,
                        isPlaying = isPlaying
                    ) { dismissDragModifier ->
                        NowPlayingScreen(
                            uiState = NowPlayingUiState(
                                currentTrack = track,
                                queueTracks = sampleLibraryTracks.toStableList(),
                                isPlaying = isPlaying,
                                isInitialLibraryLoaded = true
                            ),
                            themePrefs = ThemePreferences(
                                palette = ExpressivePalette.MATCHA,
                                appearanceMode = AppearanceMode.LIGHT
                            ),
                            progressFlow = progressFlow,
                            sleepTimerFlow = sleepTimerFlow,
                            playlists = StableList(),
                            onTogglePlayPause = { isPlaying = !isPlaying },
                            onNext = { nextClicks++ },
                            onPrevious = {},
                            onSeek = {},
                            onToggleShuffle = {},
                            onCycleRepeat = {},
                            onToggleFavorite = {},
                            onMatchMood = {},
                            onCollapse = { isExpanded = false },
                            dismissDragModifier = dismissDragModifier
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // 1. Home is default, Mini-Player is docked, and full-screen Now Playing is NOT composed when minimized
        composeRule.onNodeWithTag("home_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("now_playing_sheet_overlay").assertDoesNotExist()

        // 2. Mini-Player Play/Pause & Next work independently without expanding
        composeRule.onNodeWithTag("mini_play_pause_button").performClick()
        composeRule.onNodeWithTag("mini_next_button").performClick()
        composeRule.waitForIdle()
        assertTrue(isPlaying)
        assertEquals(1, nextClicks)
        assertFalse(isExpanded)

        // 3. Switch to Library -> Mini-Player remains docked consistently
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.LIBRARY, activeTab)
        composeRule.onNodeWithTag("mini_player_pill").assertIsDisplayed()

        // 4. Tap Mini-Player body -> expands smoothly into full-screen Now Playing sheet
        composeRule.onNodeWithTag("mini_player_pill").performClick()
        composeRule.waitForIdle()
        assertTrue(isExpanded)
        composeRule.onNodeWithTag("now_playing_sheet_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("hero_album_artwork").assertIsDisplayed()

        // 5. Tap top-left collapse button -> minimizes back to Library & unmounts full-screen sheet
        composeRule.onNodeWithTag("collapse_now_playing_button").performClick()
        composeRule.waitForIdle()
        assertFalse(isExpanded)
        assertEquals(CadenceTab.LIBRARY, activeTab)
        composeRule.onNodeWithTag("now_playing_sheet_overlay").assertDoesNotExist()

        // 6. Re-expand and swipe down on top drag handle -> smoothly dismisses back to Library
        composeRule.onNodeWithTag("mini_player_pill").performClick()
        composeRule.waitForIdle()
        assertTrue(isExpanded)
        composeRule.onNodeWithTag("now_playing_drag_handle").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 180.dp.toPx(), durationMillis = 150)
        }
        composeRule.waitForIdle()
        assertFalse(isExpanded)
        assertEquals(CadenceTab.LIBRARY, activeTab)
        composeRule.onNodeWithTag("now_playing_sheet_overlay").assertDoesNotExist()

        // 7. Re-expand and swipe down on artwork area -> smoothly dismisses back to Library
        composeRule.onNodeWithTag("mini_player_pill").performClick()
        composeRule.waitForIdle()
        assertTrue(isExpanded)
        composeRule.onNodeWithTag("hero_album_artwork").performTouchInput {
            swipeDown(startY = top + 10f, endY = bottom + 220f, durationMillis = 150)
        }
        composeRule.waitForIdle()
        assertFalse(isExpanded)
        assertEquals(CadenceTab.LIBRARY, activeTab)
        composeRule.onNodeWithTag("now_playing_sheet_overlay").assertDoesNotExist()
    }

    @Test
    fun `OfflineLyricsLoader parses synced LRC and AudioOutputMonitor maps audio routes cleanly`() {
        val rawLrc = """
            [ti:Velvet Nocturne]
            [ar:Miro & Sol]
            [00:12.50]Soft amber lights across the bay
            [00:24.00][01:02.25]Drifting slow into the night
        """.trimIndent()
        val parsed = OfflineLyricsLoader.parseLyricsText(rawLrc, trackId = "t1", sourceLabel = "Sidecar .lrc")
        assertTrue(parsed.isSynced)
        assertEquals(3, parsed.lines.size)
        assertEquals(12_500L, parsed.lines[0].timestampMs)
        assertEquals("Soft amber lights across the bay", parsed.lines[0].text)
        assertEquals(24_000L, parsed.lines[1].timestampMs)
        assertEquals(62_250L, parsed.lines[2].timestampMs)

        // AudioOutputMonitor maps Android output device types and gracefully defaults to Phone Speaker
        assertEquals(AudioOutputRouteType.BLUETOOTH, AudioOutputMonitor.mapDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(AudioOutputRouteType.WIRED_HEADSET, AudioOutputMonitor.mapDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioOutputRouteType.USB_AUDIO, AudioOutputMonitor.mapDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(AudioOutputRouteType.EXTERNAL, AudioOutputMonitor.mapDeviceType(AudioDeviceInfo.TYPE_HDMI))
        assertEquals(AudioOutputRouteType.SPEAKER, AudioOutputMonitor.mapDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))

        val fallbackState = AudioOutputMonitor.resolveStateFromRawDevices(emptyArray())
        assertEquals(AudioOutputRouteType.SPEAKER, fallbackState.activeRoute)
        assertEquals("Phone Speaker", fallbackState.activeDeviceName)
        assertEquals(1, fallbackState.devices.size)
        assertTrue(fallbackState.devices.first().isActive)
    }

    @Test
    fun `OnboardingScreen omits display name prompt persists onboarding completion synchronously and completes 3-step flow`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesRepository(context)
        var startedListening = false
        var selectedScanAll: Boolean? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                OnboardingScreen(
                    themePrefs = ThemePreferences(
                        palette = ExpressivePalette.MATCHA,
                        appearanceMode = AppearanceMode.LIGHT
                    ),
                    onStartListening = { scanAll ->
                        startedListening = true
                        selectedScanAll = scanAll
                    }
                )
            }
        }

        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("onboarding_name_input").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("onboarding_name_input").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_start_listening_button").performClick()
        assertTrue(startedListening)
        assertEquals(true, selectedScanAll)

        // Verify DataStore + synchronous SharedPreferences startup snapshot persist onboarding cleanly
        prefs.setOnboardingCompleted(true)
        val syncSnapshot = PreferencesRepository.readStartupUserProfileSnapshot(context)
        assertTrue(syncSnapshot.hasCompletedOnboarding)
        assertTrue(prefs.hasCompletedOnboarding.first())
        assertTrue(prefs.userProfileFlow.first().hasCompletedOnboarding)
    }

    @Test
    fun `SettingsScreen completely removes Display Name and renders pinned header with 5 bento categories`() {
        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                SettingsScreen(
                    themePrefs = ThemePreferences(),
                    audioSettings = AudioSettings(),
                    savedFolders = StableList(),
                    minTrackDurationSec = 0,
                    currentTrack = sampleLibraryTracks.first(),
                    isPlaying = false,
                    onBack = {},
                    onSelectPalette = {},
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

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_search_button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_appearance_card").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("settings_display_name_row").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Display Name", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("settings_category_appearance", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_category_audio"))
        composeRule.onNodeWithTag("settings_category_audio").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_category_library"))
        composeRule.onNodeWithTag("settings_category_library").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_category_interface"))
        composeRule.onNodeWithTag("settings_category_interface").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_about_row"))
        composeRule.onNodeWithTag("settings_about_row").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen renders clean time-of-day greeting without display name Continue Listening progress and 2x2 shortcut grid`() {
        // Verify deterministic time-of-day greeting formatter across all 5 buckets (no user-name interpolation)
        assertEquals("Late night vibes", formatHomeGreeting(hourOfDay = 0))
        assertEquals("Late night vibes", formatHomeGreeting(hourOfDay = 2))
        assertEquals("Good morning!", formatHomeGreeting(hourOfDay = 8))
        assertEquals("Good afternoon!", formatHomeGreeting(hourOfDay = 14))
        assertEquals("Good evening!", formatHomeGreeting(hourOfDay = 19))
        assertEquals("Good night!", formatHomeGreeting(hourOfDay = 23))
        assertEquals("Good morning!", formatHomeGreeting(hourOfDay = 9))
        assertEquals("Good afternoon!", formatHomeGreeting(hourOfDay = 13))
        assertEquals("Good night!", formatHomeGreeting(hourOfDay = 21))

        val currentTrack = sampleLibraryTracks.first()
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 60_000L, durationMs = 212_000L))
        var searchOpened = false
        var playedShuffle = false
        var playedBatchCount = 0
        var resumeClicked = false
        var shortcutCategory: LibraryCategory? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.LIGHT) {
                HomeScreen(
                    uiState = HomeUiState(
                        allTracks = sampleLibraryTracks.toStableList(),
                        recentlyPlayed = sampleLibraryTracks.take(3).toStableList(),
                        recentlyAdded = sampleLibraryTracks.toStableList(),
                        likedTracks = sampleLibraryTracks.filter { it.isFavorite }.toStableList(),
                        mostPlayedTracks = sampleLibraryTracks.sortedByDescending { it.playCount }.toStableList(),
                        isInitialLibraryLoaded = true
                    ),
                    currentTrack = currentTrack,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onExpandNowPlaying = {},
                    onTogglePlayPause = { resumeClicked = true },
                    onSelectTrack = { _, _ -> },
                    onPlayTracks = { list, shuffle ->
                        playedBatchCount = list.size
                        playedShuffle = shuffle
                    },
                    onToggleFavorite = {},
                    onOpenSettings = {},
                    onAddFolder = {},
                    onScanDevice = {},
                    progressFlow = progressFlow,
                    onOpenSearch = { searchOpened = true },
                    onOpenLibraryShortcut = { shortcutCategory = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_header_title").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Display Name", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("home_header_subtitle").assertIsDisplayed()
        composeRule.onNodeWithTag("home_search_button").performClick()
        assertTrue(searchOpened)

        // Now Playing Hero card shows elapsed & total timestamps and single Play/Pause button
        composeRule.onNodeWithTag("home_continue_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_continue_elapsed", useUnmergedTree = true).assertTextContains("1:00")
        composeRule.onNodeWithTag("home_continue_duration", useUnmergedTree = true).assertTextContains("3:32")
        composeRule.onNodeWithTag("home_continue_play_button").performClick()
        assertTrue(resumeClicked)

        // 2x2 Your Library Grid cards all display and invoke immediate playback
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_grid"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_shortcuts_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("home_shortcut_liked").assertIsDisplayed().performClick()
        assertEquals(1, playedBatchCount)
        assertFalse(playedShuffle)

        composeRule.onNodeWithTag("home_shortcut_most_played").assertIsDisplayed().performClick()
        assertEquals(sampleLibraryTracks.size, playedBatchCount)

        composeRule.onNodeWithTag("home_shortcut_recent_added").assertIsDisplayed()
        composeRule.onNodeWithTag("home_shortcut_shuffle_all").assertIsDisplayed().performClick()
        assertTrue(playedShuffle)
        assertEquals(null, shortcutCategory)
    }

    @Test
    fun `MusicIntelligenceEngine and DiscoverFeedGenerator apply completion affinity skip penalty recency decay Tune Mix Deep Cuts and Listening Insights`() {
        val engine = MusicIntelligenceEngine()
        val nowMs = 1_760_000_000_000L
        val oneHourMs = 60L * 60L * 1000L
        val oneDayMs = 24L * oneHourMs

        val completedTrack = Track(
            id = "completed_gem",
            title = "Amber Dawn",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Ambient",
            durationMs = 240_000L,
            bpm = 82,
            playCount = 10,
            completedPlayCount = 9,
            skipCount = 0,
            lastPlayedAt = nowMs - (4L * oneDayMs),
            isFavorite = true,
            mimeType = "audio/flac",
            bitrateKbps = 1411,
            sampleRateHz = 96_000,
            bitDepth = 24
        )
        val heavilySkippedTrack = Track(
            id = "heavily_skipped",
            title = "Amber Static",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Ambient",
            durationMs = 240_000L,
            bpm = 82,
            playCount = 10,
            completedPlayCount = 1,
            skipCount = 9,
            lastPlayedAt = nowMs - (4L * oneDayMs),
            isFavorite = false
        )
        val justPlayedTrack = Track(
            id = "just_played_10m",
            title = "Amber Breeze",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Ambient",
            durationMs = 240_000L,
            bpm = 82,
            playCount = 10,
            completedPlayCount = 9,
            skipCount = 0,
            lastPlayedAt = nowMs - (10L * 60L * 1000L), // 10 minutes ago -> 6-hour anti-repetition decay
            isFavorite = true
        )
        val highEnergyTrack = Track(
            id = "high_energy_rush",
            title = "Neon Velocity",
            artist = "Pulse Unit",
            album = "Drive",
            genre = "Electronic",
            durationMs = 200_000L,
            bpm = 132,
            playCount = 4,
            completedPlayCount = 4,
            skipCount = 0,
            lastPlayedAt = nowMs - (3L * oneDayMs)
        )
        val deepCutUnplayed = Track(
            id = "deep_cut_unplayed",
            title = "Hidden Cassette",
            artist = "Rare Echo",
            album = "Vault",
            genre = "Indie Folk",
            durationMs = 210_000L,
            bpm = 96,
            playCount = 0,
            completedPlayCount = 0,
            skipCount = 0,
            lastPlayedAt = 0L
        )

        val tracks = listOf(completedTrack, heavilySkippedTrack, justPlayedTrack, highEnergyTrack, deepCutUnplayed)
        val history = engine.deriveListeningHistory(tracks)

        // 1. Completion affinity beats skip penalty, and 6-hour anti-repetition penalizes just-played track
        val ranked = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(targetMood = "calm", maxTracks = 5, nowMs = nowMs),
            history = history,
            libraryTracks = tracks
        )
        val scoreMap = ranked.recommendedTracks.associate { it.trackId to it.score }
        assertTrue(
            "High completion track (${scoreMap["completed_gem"]}) must outscore heavily skipped track (${scoreMap["heavily_skipped"]})",
            (scoreMap["completed_gem"] ?: 0.0) > (scoreMap["heavily_skipped"] ?: 0.0)
        )
        assertTrue(
            "Track played 4 days ago (${scoreMap["completed_gem"]}) must outscore identical track played 10 mins ago (${scoreMap["just_played_10m"]})",
            (scoreMap["completed_gem"] ?: 0.0) > (scoreMap["just_played_10m"] ?: 0.0)
        )

        // 2. Tune Mix: High energy filter boosts high-BPM track to #1
        val highEnergyOut = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(
                energyFilter = "high",
                maxTracks = 5,
                timeOfDay = "afternoon",
                nowMs = nowMs
            ),
            history = history,
            libraryTracks = tracks
        )
        assertEquals("high_energy_rush", highEnergyOut.recommendedTracks.first().trackId)

        // 3. Tune Mix: Fresh Rediscovery (1.0f) elevates unplayed deep cut over Familiar Favorites (0.0f)
        val familiarOut = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(discoveryBalance = 0.0f, maxTracks = 5, nowMs = nowMs),
            history = history,
            libraryTracks = tracks
        )
        val freshOut = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(discoveryBalance = 1.0f, maxTracks = 5, nowMs = nowMs),
            history = history,
            libraryTracks = tracks
        )
        val familiarDeepScore = familiarOut.recommendedTracks.first { it.trackId == "deep_cut_unplayed" }.score
        val freshDeepScore = freshOut.recommendedTracks.first { it.trackId == "deep_cut_unplayed" }.score
        assertTrue("Fresh balance must boost unplayed deep cut score ($freshDeepScore > $familiarDeepScore)", freshDeepScore > familiarDeepScore)

        // 4. DiscoverFeedGenerator: Deep Cuts & Unplayed Gems + Listening Insights
        val tunedFeed = engine.generateDiscoverFeed(
            libraryTracks = tracks,
            nowMs = nowMs,
            tuneConfig = DiscoverTuneConfig(selectedMood = "energetic", energyFilter = "high", discoveryBalance = 0.8f)
        )
        val deepCutsSection = tunedFeed.sections.first { it.category == DiscoverCategory.EXPLORE_YOUR_LIBRARY }
        assertTrue(deepCutsSection.title.contains("Deep Cuts"))
        assertTrue(deepCutsSection.tracks.any { it.id == "deep_cut_unplayed" })

        val insights = tunedFeed.insights
        assertEquals("Klara Lind", insights.topArtist)
        assertEquals("Ambient", insights.topGenre)
        assertEquals(5, insights.totalTrackCount)
        assertEquals(1, insights.hiFiTrackCount)
        assertEquals(20, insights.hiFiPercentage)
        assertEquals(23, insights.completedPlaysTotal)
        assertTrue(insights.totalListeningMs > 0L)
    }

    @Test
    fun `DiscoverScreen renders Tune Mix controls on Daily Mix and Listening Insights card`() {
        val engine = MusicIntelligenceEngine()
        val hiFiLibrary = sampleLibraryTracks.mapIndexed { idx, t ->
            if (idx == 0) t.copy(mimeType = "audio/flac", bitrateKbps = 1411, sampleRateHz = 96_000, bitDepth = 24, completedPlayCount = 12)
            else t.copy(completedPlayCount = 4)
        }
        val feed = engine.generateDiscoverFeed(libraryTracks = hiFiLibrary)
        val smartFlow = MutableStateFlow(SmartPlaylistUiState())
        var selectedMoodInSheet: String? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                DiscoverScreen(
                    uiState = DiscoverUiState(
                        allTracks = hiFiLibrary.toStableList(),
                        sections = feed.sections,
                        anchorTrackTitle = feed.anchorTrackTitle,
                        insights = feed.insights,
                        tuneConfig = DiscoverTuneConfig(),
                        isInitialLibraryLoaded = true
                    ),
                    currentTrackId = hiFiLibrary.first().id,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onSelectTrackFromSection = { _, _ -> },
                    onPlaySection = {},
                    onRefreshDiscover = {},
                    onOpenSettings = {},
                    onAddFolder = {},
                    onImportFiles = {},
                    onSaveMixAsPlaylist = { _, _ -> },
                    smartPlaylistFlow = smartFlow,
                    onGenerateTunedSmartPlaylist = { mood, seed, energy, balance ->
                        selectedMoodInSheet = mood
                        smartFlow.value = SmartPlaylistUiState(
                            selectedMood = mood,
                            energyFilter = energy,
                            discoveryBalance = balance,
                            seedFromCurrent = seed,
                            isGenerating = false,
                            output = engine.analyzeAndRecommend(
                                MusicIntelligenceRequest(
                                    targetMood = mood,
                                    energyFilter = energy,
                                    discoveryBalance = balance,
                                    maxTracks = 5
                                ),
                                engine.deriveListeningHistory(hiFiLibrary),
                                hiFiLibrary
                            )
                        )
                    }
                )
            }
        }

        // Daily Mix exposes Tune Mix control directly
        composeRule.onNodeWithTag("discover_tune_daily_mix_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_energy_high").assertDoesNotExist()
        composeRule.onNodeWithTag("smart_mood_row").performScrollToNode(hasTestTag("smart_mood_upbeat"))
        composeRule.onNodeWithTag("smart_mood_upbeat").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals("upbeat", selectedMoodInSheet)
        composeRule.onNodeWithTag("smart_balance_slider").assertIsDisplayed()

        // Dismiss sheet by pressing save & verify Listening Insights card scrolls into view cleanly
        composeRule.onNodeWithTag("smart_mix_save_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_listening_insights_card"))
        composeRule.onNodeWithTag("discover_listening_insights_card").assertIsDisplayed()
        composeRule.onNodeWithTag("insight_hifi_percent").assertIsDisplayed()
    }

    @Test
    fun `PersistentTabHost keeps Home Library Discover warm preserves scroll state and blocks inactive semantics`() {
        val manyTracks = (1..24).map { idx ->
            sampleLibraryTracks[idx % sampleLibraryTracks.size].copy(
                id = "warm_track_$idx",
                title = "Warm Track $idx"
            )
        }
        val engine = MusicIntelligenceEngine()
        val discoverFeed = engine.generateDiscoverFeed(libraryTracks = manyTracks)
        var homeDisposed = 0
        var libraryDisposed = 0
        var discoverDisposed = 0
        lateinit var homeListState: LazyListState
        var activeTab by mutableStateOf(CadenceTab.HOME)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PersistentTabHost(
                        activeTab = activeTab,
                        showSettings = false,
                        modifier = Modifier.fillMaxSize()
                    ) { tab, isActive ->
                        when (tab) {
                            CadenceTab.HOME -> {
                                DisposableEffect(Unit) { onDispose { homeDisposed++ } }
                                val listState = rememberLazyListState()
                                homeListState = listState
                                HomeScreen(
                                    uiState = HomeUiState(
                                        allTracks = manyTracks.toStableList(),
                                        dailyPicks = manyTracks.take(6).toStableList(),
                                        recentlyPlayed = manyTracks.take(6).toStableList(),
                                        recentlyAdded = manyTracks.toStableList(),
                                        isInitialLibraryLoaded = true
                                    ),
                                    currentTrack = manyTracks.first(),
                                    isPlaying = true,
                                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                                    onExpandNowPlaying = {},
                                    onTogglePlayPause = {},
                                    onSelectTrack = { _, _ -> },
                                    onPlayTracks = { _, _ -> },
                                    onToggleFavorite = {},
                                    onOpenSettings = {},
                                    onAddFolder = {},
                                    onScanDevice = {},
                                    isActiveTab = isActive,
                                    listState = listState
                                )
                            }

                            CadenceTab.LIBRARY -> {
                                DisposableEffect(Unit) { onDispose { libraryDisposed++ } }
                                LibraryScreen(
                                    uiState = LibraryUiState(
                                        allTracks = manyTracks.toStableList(),
                                        displayedTracks = manyTracks.toStableList(),
                                        isInitialLibraryLoaded = true
                                    ),
                                    currentTrackId = manyTracks.first().id,
                                    isPlaying = true,
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
                                    onRemoveTrack = {},
                                    isActiveTab = isActive
                                )
                            }

                            CadenceTab.DISCOVER -> {
                                DisposableEffect(Unit) { onDispose { discoverDisposed++ } }
                                DiscoverScreen(
                                    uiState = DiscoverUiState(
                                        allTracks = manyTracks.toStableList(),
                                        sections = discoverFeed.sections,
                                        anchorTrackTitle = discoverFeed.anchorTrackTitle,
                                        insights = discoverFeed.insights,
                                        isInitialLibraryLoaded = true
                                    ),
                                    currentTrackId = manyTracks.first().id,
                                    isPlaying = true,
                                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                                    onSelectTrackFromSection = { _, _ -> },
                                    onPlaySection = {},
                                    onRefreshDiscover = {},
                                    onOpenSettings = {},
                                    onAddFolder = {},
                                    onImportFiles = {},
                                    onSaveMixAsPlaylist = { _, _ -> }
                                )
                            }
                        }
                    }

                    FloatingExpressiveNavDock(
                        navState = remember(activeTab) { FloatingNavBarState(activeTab) },
                        onSelectTab = { activeTab = it },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        // Scroll Home down past header & hero card before switching tabs
        composeRule.onNodeWithTag("home_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_recent_row"))
        composeRule.waitForIdle()
        val scrolledIndexBeforeSwitch = homeListState.firstVisibleItemIndex
        val scrolledOffsetBeforeSwitch = homeListState.firstVisibleItemScrollOffset
        assertTrue(
            "Expected Home to scroll before switching tabs",
            scrolledIndexBeforeSwitch > 0 || scrolledOffsetBeforeSwitch > 0
        )

        // Switch to Library: Home stays warm (not disposed), inactive semantics cleared
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("home_header_title").assertDoesNotExist()
        assertEquals(0, homeDisposed)

        // Switch to Discover: Library stays warm (not disposed)
        composeRule.onNodeWithTag("nav_discover").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_header_title").assertIsDisplayed()
        composeRule.onNodeWithTag("library_header_title").assertDoesNotExist()
        assertEquals(0, homeDisposed)
        assertEquals(0, libraryDisposed)

        // Switch back to Home: exact scroll position preserved & zero teardown across all 3 tabs
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitForIdle()
        assertEquals(scrolledIndexBeforeSwitch, homeListState.firstVisibleItemIndex)
        assertEquals(scrolledOffsetBeforeSwitch, homeListState.firstVisibleItemScrollOffset)
        assertEquals(0, homeDisposed)
        assertEquals(0, libraryDisposed)
        assertEquals(0, discoverDisposed)
    }

    @Test
    fun `Playback progress ticks do not recompose nav bar or screens and Coil thumbnail config uses 160px hardware non-crossfade`() = runTest {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 10_000L, durationMs = 180_000L))
        val track = sampleLibraryTracks.first()
        var activeTab by mutableStateOf(CadenceTab.HOME)
        var homeCompositions = 0
        var libraryCompositions = 0
        var dockCompositions = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PersistentTabHost(
                        activeTab = activeTab,
                        showSettings = false,
                        modifier = Modifier.fillMaxSize()
                    ) { tab, isActive ->
                        when (tab) {
                            CadenceTab.HOME -> {
                                SideEffect { homeCompositions++ }
                                HomeScreen(
                                    uiState = HomeUiState(
                                        allTracks = sampleLibraryTracks.toStableList(),
                                        recentlyPlayed = sampleLibraryTracks.take(3).toStableList(),
                                        recentlyAdded = sampleLibraryTracks.toStableList(),
                                        isInitialLibraryLoaded = true
                                    ),
                                    currentTrack = track,
                                    isPlaying = true,
                                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                                    onExpandNowPlaying = {},
                                    onTogglePlayPause = {},
                                    onSelectTrack = { _, _ -> },
                                    onPlayTracks = { _, _ -> },
                                    onToggleFavorite = {},
                                    onOpenSettings = {},
                                    onAddFolder = {},
                                    onScanDevice = {},
                                    progressFlow = progressFlow,
                                    isActiveTab = isActive
                                )
                            }

                            CadenceTab.LIBRARY -> {
                                SideEffect { libraryCompositions++ }
                                LibraryScreen(
                                    uiState = LibraryUiState(
                                        allTracks = sampleLibraryTracks.toStableList(),
                                        displayedTracks = sampleLibraryTracks.toStableList(),
                                        isInitialLibraryLoaded = true
                                    ),
                                    currentTrackId = track.id,
                                    isPlaying = true,
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
                                    onRemoveTrack = {},
                                    isActiveTab = isActive
                                )
                            }

                            CadenceTab.DISCOVER -> Unit
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        SideEffect { dockCompositions++ }
                        FloatingBottomDockOverlay(
                            activeTab = activeTab,
                            currentTrack = track,
                            isPlaying = true,
                            artworkShape = ExpressiveArtworkShape.SCALLOP,
                            onSelectTab = { activeTab = it },
                            onExpandMiniPlayer = {},
                            onTogglePlayPause = {},
                            onNext = {},
                            progressFlow = progressFlow
                        )
                    }
                }
            }
        }

        // Switch to Library so Home is warm/inactive and Library + Dock + Mini-Player progress are active
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player_pill").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_progress_bar", useUnmergedTree = true).assertIsDisplayed()

        val homeBefore = homeCompositions
        val libraryBefore = libraryCompositions
        val dockBefore = dockCompositions

        // Emit 12 rapid playback progress ticks (simulating 3 seconds of 250ms ticks)
        repeat(12) { tick ->
            progressFlow.value = PlaybackProgress(
                positionMs = 12_000L + (tick * 2_500L),
                durationMs = 180_000L
            )
            composeRule.waitForIdle()
        }

        assertEquals("Inactive warm Home must not recompose on playback ticks", homeBefore, homeCompositions)
        assertEquals("Active Library must not recompose on playback ticks", libraryBefore, libraryCompositions)
        assertEquals("Floating dock & nav bar must not recompose on playback ticks", dockBefore, dockCompositions)

        // Verify Coil fast-scrolling thumbnail ImageRequest configuration
        val context = ApplicationProvider.getApplicationContext<Context>()
        val request = buildArtworkImageRequest(
            context = context,
            artworkPath = "/storage/emulated/0/Music/cover.jpg",
            decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
            enableCrossfade = false
        )
        assertEquals(CoilSize(160, 160), request.sizeResolver.size())
        assertTrue("Thumbnail request must enable hardware bitmaps", request.allowHardware)
        assertEquals("art_/storage/emulated/0/Music/cover.jpg_160", request.memoryCacheKey?.key)
        assertEquals("art_/storage/emulated/0/Music/cover.jpg_160", request.diskCacheKey)
        assertEquals(
            "Fast-scrolling list thumbnails must disable crossfade",
            Transition.Factory.NONE,
            request.transitionFactory
        )
    }

    @Test
    fun `Tune Mix sheet removes debug clutter renders Mood-only pills clean header preview rows and 52dp single-line bottom bar`() {
        val engine = MusicIntelligenceEngine()
        val output = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(
                seedTrackId = sampleLibraryTracks.first().id,
                maxTracks = sampleLibraryTracks.size
            ),
            history = engine.deriveListeningHistory(sampleLibraryTracks),
            libraryTracks = sampleLibraryTracks
        )
        val lookup = sampleLibraryTracks.associateBy { it.id }.toStableMap()
        val expectedSubtitle = formatSmartMixSubtitle(output, output.recommendedTracks.size)
        assertTrue(
            "Subtitle '$expectedSubtitle' must contain BPM, vibe descriptor, and track count",
            expectedSubtitle.contains("BPM") && expectedSubtitle.contains("•") && expectedSubtitle.endsWith("tracks")
        )

        var activeMood by mutableStateOf<String?>(null)
        var seedEnabled by mutableStateOf(true)
        var balance by mutableStateOf(0.5f)
        var playClickedCount = 0
        var saveClickedTitle: String? = null
        var refreshClicks = 0
        var resetClicks = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(760.dp)) {
                    SmartPlaylistSheetContent(
                        output = output,
                        isGenerating = false,
                        selectedMood = activeMood,
                        seedFromCurrent = seedEnabled,
                        currentTrackTitle = sampleLibraryTracks.first().title,
                        currentTrackArtist = sampleLibraryTracks.first().artist,
                        trackLookup = lookup,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectMood = { activeMood = it },
                        onToggleSeedFromCurrent = { seedEnabled = it },
                        onRegenerate = { refreshClicks++ },
                        onPlaySmartPlaylist = { playClickedCount = it.size },
                        onSaveSmartPlaylist = { title, _ -> saveClickedTitle = title },
                        discoveryBalance = balance,
                        onChangeDiscoveryBalance = { balance = it },
                        onResetTune = {
                            resetClicks++
                            activeMood = null
                            balance = 0.5f
                            seedEnabled = true
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. v1 Debug Clutter completely absent: no JSON button/preview, no Energy row, no #1 ranks, no cohesion/score badges
        composeRule.onNodeWithTag("smart_toggle_json_button").assertDoesNotExist()
        composeRule.onNodeWithTag("smart_mix_raw_json").assertDoesNotExist()
        composeRule.onNodeWithText("JSON", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("smart_energy_any").assertDoesNotExist()
        composeRule.onNodeWithTag("smart_energy_high").assertDoesNotExist()
        composeRule.onNodeWithText("Energy").assertDoesNotExist()
        composeRule.onNodeWithText("#1").assertDoesNotExist()
        composeRule.onNodeWithText("#2").assertDoesNotExist()
        composeRule.onNodeWithText("cohesion", substring = true, ignoreCase = true).assertDoesNotExist()
        val firstScoreText = ((output.recommendedTracks.first().score.coerceIn(0.0, 1.0)) * 100.0).toInt().toString()
        composeRule.onNodeWithText(firstScoreText).assertDoesNotExist()

        // 2. Top Sheet Header: "Tune Mix", "Reset", "Refresh" with >= 48.dp touch target height
        composeRule.onNodeWithTag("smart_mix_sheet_title").assertIsDisplayed().assertTextContains("Tune Mix")
        val resetBounds = composeRule.onNodeWithTag("smart_reset_tune_button").getUnclippedBoundsInRoot()
        val refreshBounds = composeRule.onNodeWithTag("smart_mix_regenerate_button").getUnclippedBoundsInRoot()
        assertTrue("Reset target height must be >= 48.dp", resetBounds.height.value >= 47.5f)
        assertTrue("Refresh target height must be >= 48.dp", refreshBounds.height.value >= 47.5f)
        composeRule.onNodeWithTag("smart_mix_regenerate_button").performClick()
        assertEquals(1, refreshClicks)

        // 3. Mood Filter Row Only: Auto, Calm, Focus, Warm, Upbeat
        listOf(
            "smart_mood_auto" to "Auto",
            "smart_mood_calm" to "Calm",
            "smart_mood_focused" to "Focus",
            "smart_mood_warm" to "Warm",
            "smart_mood_upbeat" to "Upbeat"
        ).forEach { (tag, label) ->
            composeRule.onNodeWithTag("smart_mood_row").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed().assertTextContains(label)
        }
        composeRule.onNodeWithTag("smart_mood_upbeat").performClick()
        composeRule.waitForIdle()
        assertEquals("upbeat", activeMood)

        // Reset restores defaults
        composeRule.onNodeWithTag("smart_reset_tune_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, resetClicks)
        assertEquals(null, activeMood)

        // 4. Mix Balance slider & labels
        composeRule.onNodeWithTag("smart_balance_slider").assertIsDisplayed()
        composeRule.onNodeWithText("Familiar Favorites").assertIsDisplayed()
        composeRule.onNodeWithText("Fresh Rediscovery").assertIsDisplayed()

        // 5. Seed Track Pill with trailing × button clears seed smoothly
        composeRule.onNodeWithTag("smart_seed_toggle").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_seed_clear_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertFalse("Tapping trailing × must clear active seed", seedEnabled)
        composeRule.onNodeWithTag("smart_seed_clear_button").assertDoesNotExist()
        // Tapping pill again restores seed
        composeRule.onNodeWithTag("smart_seed_toggle").performClick()
        composeRule.waitForIdle()
        assertTrue("Tapping cleared seed pill should restore seed", seedEnabled)

        // 6. Unified Mix Header Card + Clean Preview Track Row with 44.dp art
        composeRule.onNodeWithTag("smart_mix_list").performScrollToNode(hasTestTag("smart_mix_header_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_mix_result_title")
            .assertIsDisplayed()
            .assertTextContains(output.playlistProfile.suggestedTitle)
        composeRule.onNodeWithTag("smart_mix_result_subtitle")
            .assertIsDisplayed()
            .assertTextContains(expectedSubtitle)

        val firstTrackId = output.recommendedTracks.first().trackId
        composeRule.onNodeWithTag("smart_mix_list").performScrollToNode(hasTestTag("smart_track_$firstTrackId"))
        composeRule.waitForIdle()
        val artBounds = composeRule.onNodeWithTag("smart_track_art_$firstTrackId", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals("Preview track art must be 44.dp wide", 44f, artBounds.width.value, 1.5f)
        assertEquals("Preview track art must be 44.dp tall", 44f, artBounds.height.value, 1.5f)

        // 7. Sticky Bottom Action Bar: Save Playlist & Play Mix side-by-side at 52.dp height on 1 line without clipping
        val saveNode = composeRule.onNodeWithTag("smart_mix_save_button").assertIsDisplayed()
        val playNode = composeRule.onNodeWithTag("smart_mix_play_button").assertIsDisplayed()
        val saveBounds = saveNode.getUnclippedBoundsInRoot()
        val playBounds = playNode.getUnclippedBoundsInRoot()

        assertEquals("Save Playlist button must be 52.dp tall", 52f, saveBounds.height.value, 1.5f)
        assertEquals("Play Mix button must be 52.dp tall", 52f, playBounds.height.value, 1.5f)
        assertTrue("Save Playlist must sit to the left of Play Mix", saveBounds.right.value < playBounds.left.value)
        assertTrue("Bottom action buttons must fit inside 360.dp width", playBounds.right.value <= 360f)
        assertTrue("Bottom action bar must not clip past bottom of viewport", playBounds.bottom.value <= 760f)

        // Verify Save Playlist and Play Mix labels fit on a single line (no 2-line wrapping)
        val saveTextLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithText("Save Playlist", useUnmergedTree = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(saveTextLayouts)
        assertTrue("Save Playlist label must resolve text layout", saveTextLayouts.isNotEmpty())
        assertEquals("Save Playlist label must stay on 1 line", 1, saveTextLayouts.first().lineCount)

        val playTextLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        composeRule.onNodeWithText("Play Mix", useUnmergedTree = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(playTextLayouts)
        assertTrue("Play Mix label must resolve text layout", playTextLayouts.isNotEmpty())
        assertEquals("Play Mix label must stay on 1 line", 1, playTextLayouts.first().lineCount)

        saveNode.performClick()
        playNode.performClick()
        assertEquals(output.playlistProfile.suggestedTitle, saveClickedTitle)
        assertEquals(output.recommendedTracks.size, playClickedCount)
    }
}
