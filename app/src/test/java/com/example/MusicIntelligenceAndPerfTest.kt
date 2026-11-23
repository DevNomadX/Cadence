package com.example

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.TrackEntity
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.ListeningHistoryInput
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.MusicIntelligenceRequest
import com.example.data.library.ScanState
import com.example.data.toStableList
import com.example.ui.CadenceTab
import com.example.ui.FloatingExpressiveNavDock
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.LibraryUiState
import com.example.ui.player.SmartPlaylistUiState
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
                palette = ExpressivePalette.TERRACOTTA,
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
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
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

        // Sparkles/AI trigger has moved out of Library top bar into the Discover nav tab
        composeRule.onNodeWithTag("smart_mix_button").assertDoesNotExist()
        composeRule.onNodeWithTag("library_settings_button").assertIsDisplayed()
        composeRule.onNodeWithTag("import_audio_button").assertIsDisplayed()
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
    fun `FloatingExpressiveNavDock expands only active tab and collapses inactive tabs into icons`() {
        var activeTab by mutableStateOf(CadenceTab.NOW_PLAYING)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
                FloatingExpressiveNavDock(
                    activeTab = activeTab,
                    onSelectTab = { activeTab = it }
                )
            }
        }

        composeRule.waitForIdle()
        // Only Now Playing label visible initially
        composeRule.onNodeWithTag("nav_label_now_playing", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("nav_label_discover", useUnmergedTree = true).assertDoesNotExist()

        // Tap Discover -> Discover expands with label, Now Playing collapses
        composeRule.onNodeWithTag("nav_discover").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.DISCOVER, activeTab)
        composeRule.onNodeWithTag("nav_label_discover", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_now_playing", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertDoesNotExist()

        // Tap Library -> Library expands with label, Discover collapses
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        assertEquals(CadenceTab.LIBRARY, activeTab)
        composeRule.onNodeWithTag("nav_label_library", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("nav_label_discover", useUnmergedTree = true).assertDoesNotExist()
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
        composeRule.onNodeWithTag("discover_refresh_button").performClick()
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

        // Open Smart Mix tuner sheet, inspect JSON, and save playlist
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_tune_smart_mix_button"))
        composeRule.onNodeWithTag("discover_tune_smart_mix_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_mix_result_title").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_toggle_json_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_mix_raw_json").assertIsDisplayed()
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
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.LIGHT) {
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

        var activeTab by mutableStateOf(CadenceTab.NOW_PLAYING)
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

        // 3. Trigger Refresh on Discover
        composeRule.onNodeWithTag("discover_refresh_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, refreshNonce)
        composeRule.onNodeWithTag("discover_section_made_for_you").assertIsDisplayed()
    }
}
