package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.StableList
import com.example.data.Track
import com.example.data.intelligence.DailyPicksCurator
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.DiscoverSection
import com.example.data.intelligence.DiscoverTuneConfig
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.MusicIntelligenceRequest
import com.example.data.intelligence.RoutineMixCurator
import com.example.data.intelligence.RoutineMixType
import com.example.data.intelligence.TrackIntelligenceCache
import com.example.data.toStableList
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.HomeUiState
import com.example.ui.player.SmartPlaylistUiState
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.resolveHomeHeroFormatBadge
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyPicksAndRoutineMixTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        TrackIntelligenceCache.clearForTesting()
    }

    private fun sampleRichLibrary(): List<Track> = listOf(
        Track("t1", "Midnight Rain Piano", "Nora Sol", "Quiet Hours", "Ambient", 240_000L, bpm = 74, playCount = 9, completedPlayCount = 8, isFavorite = true, mimeType = "audio/flac", bitDepth = 24, sampleRateHz = 96_000),
        Track("t2", "Cedar Acoustic Session", "Klara Lind", "Timber", "Acoustic", 215_000L, bpm = 86, playCount = 7, completedPlayCount = 6, isFavorite = true, mimeType = "audio/flac", bitDepth = 16, sampleRateHz = 44_100),
        Track("t3", "Focus Study Loop", "Studio Mono", "Deep Work", "Lo-Fi", 198_000L, bpm = 98, playCount = 5, completedPlayCount = 5, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t4", "Morning Jazz Instrumental", "Blue Note Trio", "Espresso", "Jazz", 230_000L, bpm = 104, playCount = 4, completedPlayCount = 4, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t5", "Golden Hour Groove", "Velvet Waves", "Sunlit", "Soul", 222_000L, bpm = 106, playCount = 11, completedPlayCount = 10, isFavorite = true, mimeType = "audio/mp4", bitrateKbps = 256),
        Track("t6", "Warm Afternoon Drive", "Sora Tan", "Coastline", "Indie Pop", 205_000L, bpm = 112, playCount = 6, completedPlayCount = 5, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t7", "Velvet Evening Ballad", "Amina Cole", "Dusk", "Neo-Soul", 248_000L, bpm = 88, playCount = 8, completedPlayCount = 7, isFavorite = true, mimeType = "audio/flac", bitDepth = 24, sampleRateHz = 48_000),
        Track("t8", "Saturday Pulse Club Edit", "Neon Kinetic", "Weekend", "Electronic", 196_000L, bpm = 128, playCount = 0, completedPlayCount = 0, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t9", "Festival Energy Rush", "Pulse Unit", "Overdrive", "Dance", 204_000L, bpm = 132, playCount = 1, completedPlayCount = 1, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t10", "Upbeat Funk Bounce", "Groove Lab", "Spark", "Funk", 212_000L, bpm = 122, playCount = 0, completedPlayCount = 0, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t11", "Starlight Nocturne", "Luma Drift", "Sleep", "Chill", 260_000L, bpm = 70, playCount = 3, completedPlayCount = 3, mimeType = "audio/ogg", bitrateKbps = 192),
        Track("t12", "Sunday Horizon", "Tide Collective", "Open Road", "Pop", 208_000L, bpm = 118, playCount = 2, completedPlayCount = 2, mimeType = "audio/mpeg", bitrateKbps = 320),
        Track("t13", "Minimal Strings Study", "Chamber Nine", "Focus", "Classical", 240_000L, bpm = 94, playCount = 3, completedPlayCount = 3, mimeType = "audio/flac", bitDepth = 24, sampleRateHz = 96_000),
        Track("t14", "Amber Soul Harmony", "Devon Brooks", "Warmth", "R&B", 218_000L, bpm = 96, playCount = 6, completedPlayCount = 6, isFavorite = true, mimeType = "audio/mpeg", bitrateKbps = 320)
    )

    @Test
    fun `DailyPicksCurator is deterministic for same LocalDate and rotates to fresh 5-6 songs on next calendar day`() {
        val library = sampleRichLibrary()
        val day1 = LocalDate.of(2026, 11, 25).toEpochDay()
        val day2 = LocalDate.of(2026, 11, 26).toEpochDay()
        val nowMs = 1_795_564_800_000L

        val picksDay1A = DailyPicksCurator.generateDailyPicks(library, epochDay = day1, nowMs = nowMs)
        val picksDay1B = DailyPicksCurator.generateDailyPicks(library, epochDay = day1, nowMs = nowMs)
        val picksDay2 = DailyPicksCurator.generateDailyPicks(library, epochDay = day2, nowMs = nowMs)

        assertTrue("Daily Picks must return 5-6 songs (was ${picksDay1A.size})", picksDay1A.size in 5..6)
        assertTrue("Next-day Daily Picks must return 5-6 songs (was ${picksDay2.size})", picksDay2.size in 5..6)
        assertEquals(
            "Same calendar LocalDate must produce identical ordered Daily Picks",
            picksDay1A.map { it.id },
            picksDay1B.map { it.id }
        )
        assertNotEquals(
            "Next calendar LocalDate must rotate to a fresh Daily Picks selection",
            picksDay1A.map { it.id },
            picksDay2.map { it.id }
        )
        assertEquals("Daily Picks must contain distinct tracks", picksDay1A.size, picksDay1A.distinctBy { it.id }.size)
    }

    @Test
    fun `RoutineMixCurator selects active context-aware routine first across morning afternoon evening late-night and weekend timestamps`() {
        val library = sampleRichLibrary()
        val nowMs = 1_795_564_800_000L

        // Weekday morning (Wed 09:00) -> Morning Focus first
        val morningMixes = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = library,
            nowMs = nowMs,
            localHourOfDay = 9,
            dayOfWeek = Calendar.WEDNESDAY
        )
        assertEquals(RoutineMixType.MORNING_FOCUS, morningMixes.first().type)
        assertTrue(morningMixes.first().isActiveNow)
        assertTrue("Must surface active mix + 2-3 complementary routine mixes", morningMixes.size in 3..4)
        assertTrue(morningMixes.drop(1).none { it.isActiveNow })

        // Weekday afternoon (Wed 14:00) -> Afternoon Momentum first
        val afternoonMixes = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = library,
            nowMs = nowMs,
            localHourOfDay = 14,
            dayOfWeek = Calendar.WEDNESDAY
        )
        assertEquals(RoutineMixType.AFTERNOON_MOMENTUM, afternoonMixes.first().type)
        assertTrue(afternoonMixes.first().isActiveNow)

        // Weekday evening (Wed 19:00) -> Evening Unwind first
        val eveningMixes = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = library,
            nowMs = nowMs,
            localHourOfDay = 19,
            dayOfWeek = Calendar.WEDNESDAY
        )
        assertEquals(RoutineMixType.EVENING_UNWIND, eveningMixes.first().type)
        assertTrue(eveningMixes.first().isActiveNow)

        // Late night (23:00 and 02:00) -> Late-Night Wind Down first, favoring calm low-BPM tracks
        val lateNight23 = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = library,
            nowMs = nowMs,
            localHourOfDay = 23,
            dayOfWeek = Calendar.WEDNESDAY
        )
        val lateNight02 = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = library,
            nowMs = nowMs,
            localHourOfDay = 2,
            dayOfWeek = Calendar.SATURDAY
        )
        assertEquals(RoutineMixType.LATE_NIGHT_WIND_DOWN, lateNight23.first().type)
        assertEquals(RoutineMixType.LATE_NIGHT_WIND_DOWN, lateNight02.first().type)
        assertTrue(
            "Late-Night Wind Down lead track should be calm/low-BPM (<= 100 BPM), was ${lateNight23.first().tracks.first().bpm}",
            lateNight23.first().tracks.first().bpm <= 100
        )

        // Weekend (Fri 18:00, Sat 11:00, Sun 15:00) -> Weekend Energy first, favoring upbeat high-energy tracks
        val friEve = RoutineMixCurator.buildRoutineMixes(library, nowMs, localHourOfDay = 18, dayOfWeek = Calendar.FRIDAY)
        val satMid = RoutineMixCurator.buildRoutineMixes(library, nowMs, localHourOfDay = 11, dayOfWeek = Calendar.SATURDAY)
        val sunAft = RoutineMixCurator.buildRoutineMixes(library, nowMs, localHourOfDay = 15, dayOfWeek = Calendar.SUNDAY)
        assertEquals(RoutineMixType.WEEKEND_ENERGY, friEve.first().type)
        assertEquals(RoutineMixType.WEEKEND_ENERGY, satMid.first().type)
        assertEquals(RoutineMixType.WEEKEND_ENERGY, sunAft.first().type)
        assertTrue(
            "Weekend Energy lead track should be upbeat (>= 112 BPM), was ${satMid.first().tracks.first().bpm}",
            satMid.first().tracks.first().bpm >= 112
        )
    }

    @Test
    fun `TrackIntelligenceCache memoizes vectors across Home Discover and TuneMix and invalidates on library or history mutations`() {
        val library = sampleRichLibrary()
        val engine = MusicIntelligenceEngine()
        val nowMs = 1_795_564_800_000L

        // First build triggers 1 cache miss
        DailyPicksCurator.generateDailyPicks(library, epochDay = 20_780L, nowMs = nowMs, engine = engine)
        val missesAfterFirst = TrackIntelligenceCache.cacheMisses
        assertEquals(1L, missesAfterFirst)

        // Subsequent calls across Routine Mixes, Mood Playlists, Discover Feed, and Tune Mix hit the memoized snapshot
        RoutineMixCurator.buildRoutineMixes(library, nowMs, localHourOfDay = 10, dayOfWeek = Calendar.TUESDAY, engine = engine)
        LocalMoodAndAffinityEngine.buildMoodSmartPlaylists(library, nowMs, localHourOfDay = 10)
        engine.generateDiscoverFeed(library, nowMs = nowMs, localHourOfDay = 10)
        engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(targetMood = "calm", maxTracks = 6, timeOfDay = "afternoon", nowMs = nowMs),
            history = engine.deriveListeningHistory(library),
            libraryTracks = library
        )

        assertEquals("Unchanged library must incur zero extra cache misses", missesAfterFirst, TrackIntelligenceCache.cacheMisses)
        assertTrue("Repeated intelligence calls must record cache hits", TrackIntelligenceCache.cacheHits >= 4L)

        // Mutating play history invalidates the cache immediately
        val afterPlay = library.mapIndexed { idx, track ->
            if (idx == 0) track.copy(playCount = track.playCount + 1, completedPlayCount = track.completedPlayCount + 1)
            else track
        }
        DailyPicksCurator.generateDailyPicks(afterPlay, epochDay = 20_780L, nowMs = nowMs, engine = engine)
        assertEquals("Play history change must invalidate versioned cache", missesAfterFirst + 1L, TrackIntelligenceCache.cacheMisses)
    }

    @Test
    fun `Local intelligence handles cold-start brand-new libraries with zero history zero BPM missing genre and fewer than 10 tracks`() {
        val sparseTinyLibrary = listOf(
            Track("cold_1", "Untitled Track 1", "Unknown Artist", "", genre = "", durationMs = 180_000L, bpm = 0, playCount = 0),
            Track("cold_2", "Untitled Track 2", "Unknown Artist", "", genre = "", durationMs = 205_000L, bpm = 0, playCount = 0),
            Track("cold_3", "Untitled Track 3", "Unknown Artist", "", genre = "", durationMs = 192_000L, bpm = 0, playCount = 0)
        )
        val engine = MusicIntelligenceEngine()
        val nowMs = 1_795_564_800_000L

        val picks = DailyPicksCurator.generateDailyPicks(sparseTinyLibrary, epochDay = 20_780L, nowMs = nowMs, engine = engine)
        assertEquals("Cold-start 3-track library must return all 3 tracks for Daily Picks", 3, picks.size)

        val routines = RoutineMixCurator.buildRoutineMixes(sparseTinyLibrary, nowMs, localHourOfDay = 22, dayOfWeek = Calendar.MONDAY, engine = engine)
        assertTrue("Routine mixes must not be empty on cold-start library", routines.isNotEmpty())
        assertTrue("Every routine mix must have non-empty tracks", routines.all { it.tracks.isNotEmpty() })

        val feed = engine.generateDiscoverFeed(sparseTinyLibrary, nowMs = nowMs, localHourOfDay = 22)
        assertTrue("Discover sections must not be empty on cold-start library", feed.sections.isNotEmpty())
        assertTrue("Discover routine mixes must not be empty on cold-start library", feed.routineMixes.isNotEmpty())

        val smart = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(maxTracks = 5, timeOfDay = "afternoon", nowMs = nowMs),
            history = engine.deriveListeningHistory(sparseTinyLibrary),
            libraryTracks = sparseTinyLibrary
        )
        assertTrue("Smart recommendation must never return empty on non-empty library", smart.recommendedTracks.isNotEmpty())
    }

    @Test
    fun `HomeScreen renders HI-FI badge above title without PAUSED label for MP3 track and places Daily Picks between Hero and Your Library without any refresh button`() {
        val mp3Track = Track(
            id = "mp3_1",
            title = "Midnight Skyline",
            artist = "Nora Sol",
            album = "Echoes",
            genre = "Electronic",
            durationMs = 210_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 320
        )
        val aacTrack = mp3Track.copy(id = "aac_1", mimeType = "audio/mp4", codec = "AAC", bitrateKbps = 256)
        assertEquals("HI-FI", resolveHomeHeroFormatBadge(mp3Track))
        assertEquals("AAC", resolveHomeHeroFormatBadge(aacTrack))
        assertEquals("LOSSLESS", resolveHomeHeroFormatBadge(sampleRichLibrary().first()))

        val dailyPicks = sampleRichLibrary().take(6)
        val losslessHeroTrack = dailyPicks.first()
        var selectedPickId: String? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = (listOf(mp3Track) + dailyPicks).toStableList(),
                            dailyPicks = dailyPicks.toStableList(),
                            recentlyPlayed = listOf(losslessHeroTrack).toStableList(),
                            recentlyAdded = dailyPicks.toStableList(),
                            likedTracks = listOf(losslessHeroTrack).toStableList(),
                            mostPlayedTracks = dailyPicks.toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = losslessHeroTrack,
                        isPlaying = false,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onExpandNowPlaying = {},
                        onTogglePlayPause = {},
                        onSelectTrack = { t, _ -> selectedPickId = t.id },
                        onPlayTracks = { _, _ -> },
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onAddFolder = {},
                        onScanDevice = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Compact HI-FI badge above song title for lossless/hi-res track, with PAUSED/NOW PLAYING/MP3 completely absent
        composeRule.onNodeWithTag("home_hero_status_badge", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PAUSED", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("NOW PLAYING", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("MP3", useUnmergedTree = true).assertDoesNotExist()

        val hifiBounds = composeRule.onNodeWithTag("home_hero_hifi_badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val titleBounds = composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("HI-FI", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("HI-FI badge must sit above song title", hifiBounds.bottom.value <= titleBounds.top.value + 1f)

        // 2. Daily Picks placed directly after Hero card and before Your Library
        val heroBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()
        composeRule.onNodeWithTag("home_daily_picks_header").assertIsDisplayed().assertTextContains("Daily Picks")
        composeRule.onNodeWithTag("home_daily_picks_subtitle").assertIsDisplayed().assertTextContains("Updated daily for your taste")
        val dailyHeaderBounds = composeRule.onNodeWithTag("home_daily_picks_header").getUnclippedBoundsInRoot()
        assertTrue("Daily Picks must sit below the Hero card", dailyHeaderBounds.top.value >= heroBounds.bottom.value)

        // Strictly NO manual refresh button in Daily Picks header
        composeRule.onNode(hasContentDescription("Refresh", substring = true, ignoreCase = true), useUnmergedTree = true)
            .assertDoesNotExist()

        // 136.dp rounded-20.dp Daily Pick artwork card & click playback
        val firstPick = dailyPicks.first()
        val pickArtBounds = composeRule.onNodeWithTag("home_daily_pick_art_${firstPick.id}", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals("Daily Pick artwork must be 136.dp wide", 136f, pickArtBounds.width.value, 1.5f)
        assertEquals("Daily Pick artwork must be 136.dp tall", 136f, pickArtBounds.height.value, 1.5f)

        composeRule.onNodeWithTag("home_daily_pick_tile_${firstPick.id}").performClick()
        assertEquals(firstPick.id, selectedPickId)

        // Scroll to Your Library and verify it comes AFTER Daily Picks
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_grid"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_your_library_header").assertIsDisplayed()
    }

    @Test
    fun `DiscoverScreen surfaces active Context-Aware Routine Mix first and wires Play Mix and Inspect in Tune Mix actions`() {
        val library = sampleRichLibrary()
        val routineMixes = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = library,
            nowMs = 1_795_564_800_000L,
            localHourOfDay = 23,
            dayOfWeek = Calendar.WEDNESDAY
        )
        val activeMix = routineMixes.first()
        assertEquals(RoutineMixType.LATE_NIGHT_WIND_DOWN, activeMix.type)

        val dummySection = DiscoverSection(
            category = DiscoverCategory.MADE_FOR_YOU,
            title = "Made for you",
            subtitle = "Daily local mix",
            badgeText = "Daily Mix",
            tracks = library.take(4).toStableList()
        )
        var playedTracks: List<Track> = emptyList()
        var tunedMood: String? = null
        var tunedEnergy: String? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    DiscoverScreen(
                        uiState = DiscoverUiState(
                            allTracks = library.toStableList(),
                            routineMixes = routineMixes,
                            sections = listOf(dummySection).toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrackId = library.first().id,
                        isPlaying = false,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectTrackFromSection = { _, _ -> },
                        onPlaySection = { tracks -> playedTracks = tracks },
                        onRefreshDiscover = {},
                        onOpenSettings = {},
                        onAddFolder = {},
                        onImportFiles = {},
                        onSaveMixAsPlaylist = { _, _ -> },
                        smartPlaylistFlow = MutableStateFlow(SmartPlaylistUiState()),
                        onGenerateTunedSmartPlaylist = { mood, _, energy, _ ->
                            tunedMood = mood
                            tunedEnergy = energy
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Discover header shows only the clean "Discover" title without the old subtitle text
        composeRule.onNodeWithTag("discover_header_title").assertIsDisplayed().assertTextContains("Discover")
        composeRule.onNodeWithText("Personalized mixes & hidden gems from your local library", useUnmergedTree = true)
            .assertDoesNotExist()

        composeRule.onNodeWithTag("discover_routine_mixes_header").assertIsDisplayed().assertTextContains("For This Moment")
        composeRule.onNodeWithTag("discover_routine_card_${activeMix.type.id}").assertIsDisplayed()
        composeRule.onNodeWithText("ACTIVE NOW", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Late-Night Wind Down", useUnmergedTree = true).assertIsDisplayed()

        // One-tap Play Mix
        composeRule.onNodeWithTag("discover_routine_play_${activeMix.type.id}").performClick()
        assertEquals(activeMix.tracks.size, playedTracks.size)

        // One-tap Inspect in Tune Mix opens sheet with routine's mood & energy preset
        composeRule.onNodeWithTag("discover_routine_tune_${activeMix.type.id}").performClick()
        composeRule.waitForIdle()
        assertEquals(activeMix.tuneConfig.selectedMood, tunedMood)
        assertEquals(activeMix.tuneConfig.energyFilter, tunedEnergy)
    }
}
