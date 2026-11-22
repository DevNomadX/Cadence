package com.example

import android.content.Context
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.TrackEntity
import com.example.data.intelligence.ListeningHistoryInput
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.MusicIntelligenceRequest
import com.example.data.library.ScanState
import com.example.data.toStableList
import com.example.ui.player.LibraryUiState
import com.example.ui.player.SmartPlaylistUiState
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
    fun `LibraryScreen isolates scan progress recompositions and opens Smart Mix sheet cleanly`() {
        val tracks = sampleLibraryTracks
        val engine = MusicIntelligenceEngine()
        val scanFlow = MutableStateFlow(ScanState(isScanning = false))
        val smartFlow = MutableStateFlow(SmartPlaylistUiState())
        var parentRecompositions = 0
        var savedName: String? = null
        var savedIds: List<String> = emptyList()

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
                    scanStateFlow = scanFlow,
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
                    },
                    onSaveSmartPlaylist = { name, ids ->
                        savedName = name
                        savedIds = ids
                    }
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

        composeRule.onNodeWithTag("smart_mix_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_mix_result_title").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_toggle_json_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_mix_raw_json").assertIsDisplayed()
        composeRule.onNodeWithTag("smart_mix_save_button").performClick()
        composeRule.waitForIdle()

        assertTrue(!savedName.isNullOrBlank())
        assertEquals(tracks.size, savedIds.size)
    }
}
