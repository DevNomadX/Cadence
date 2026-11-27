package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.LibraryCategory
import com.example.data.StableList
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.TrackEntity
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.DiscoverSection
import com.example.data.intelligence.LibraryMoodFilter
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.SmartMoodTag
import com.example.data.toStableList
import com.example.playback.PlaybackController
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.LibraryUiState
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class LocalAiMoodAndAffinityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val baseNowMs = 1_760_000_000_000L

    private fun sampleTrack(
        id: String,
        title: String,
        artist: String = "Artist $id",
        album: String = "Album $id",
        genre: String = "",
        bpm: Int = 90,
        codec: String = "MP3",
        mimeType: String = "audio/mpeg",
        bitDepth: Int = 16,
        sampleRateHz: Int = 44_100,
        bitrateKbps: Int = 320,
        playCount: Int = 0,
        completedPlayCount: Int = 0,
        skipCount: Int = 0,
        lastPlayedAt: Long = 0L,
        isFavorite: Boolean = false,
        artworkColorArgb: Int = 0,
        moodTags: List<SmartMoodTag> = emptyList()
    ): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = 210_000L,
        bpm = bpm,
        codec = codec,
        mimeType = mimeType,
        bitDepth = bitDepth,
        sampleRateHz = sampleRateHz,
        bitrateKbps = bitrateKbps,
        playCount = playCount,
        completedPlayCount = completedPlayCount,
        skipCount = skipCount,
        lastPlayedAt = lastPlayedAt,
        isFavorite = isFavorite,
        artworkColorArgb = artworkColorArgb,
        moodTags = moodTags.toStableList()
    )

    @Test
    fun `deterministic mood classifier tags Late Night Acoustic Soulful Upbeat and Lossless Hi-Fi across signals`() {
        // 1. Multi-label: Late Night + Acoustic + Lossless Hi-Fi (slow BPM, keywords, 24-bit FLAC, deep indigo art)
        val midnightAcousticFlac = sampleTrack(
            id = "m1",
            title = "Midnight Rain (Acoustic Piano)",
            album = "After Hours Sessions",
            genre = "Ambient Folk",
            bpm = 76,
            codec = "FLAC",
            mimeType = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 96_000,
            artworkColorArgb = 0xFF1A2258.toInt() // Deep indigo
        )
        val m1Tags = LocalMoodAndAffinityEngine.classifyTrackMoods(midnightAcousticFlac)
        assertTrue("Expected LATE_NIGHT in $m1Tags", SmartMoodTag.LATE_NIGHT in m1Tags)
        assertTrue("Expected ACOUSTIC in $m1Tags", SmartMoodTag.ACOUSTIC in m1Tags)
        assertTrue("Expected LOSSLESS_HIFI in $m1Tags", SmartMoodTag.LOSSLESS_HIFI in m1Tags)
        assertFalse("Did not expect UPBEAT in $m1Tags", SmartMoodTag.UPBEAT in m1Tags)

        // 2. Soulful via genre + warm amber/terracotta artwork color
        val warmSoul = sampleTrack(
            id = "m2",
            title = "Golden Velvet",
            album = "Warm Echoes",
            genre = "Neo-Soul",
            bpm = 84,
            codec = "MP3",
            bitDepth = 16,
            sampleRateHz = 44_100,
            artworkColorArgb = 0xFFD97236.toInt() // Warm terracotta/amber
        )
        val m2Tags = LocalMoodAndAffinityEngine.classifyTrackMoods(warmSoul)
        assertTrue("Expected SOULFUL in $m2Tags", SmartMoodTag.SOULFUL in m2Tags)
        assertFalse("MP3 44.1kHz 16-bit should not be LOSSLESS_HIFI", SmartMoodTag.LOSSLESS_HIFI in m2Tags)

        // 3. Upbeat via high BPM + club remix keyword + vibrant saturated color
        val clubRemix = sampleTrack(
            id = "m3",
            title = "Electric Horizon (Club Remix)",
            album = "Neon Rush",
            genre = "House",
            bpm = 126,
            codec = "WAV",
            mimeType = "audio/wav",
            bitDepth = 16,
            sampleRateHz = 48_000,
            artworkColorArgb = 0xFFFF2288.toInt() // Vibrant magenta
        )
        val m3Tags = LocalMoodAndAffinityEngine.classifyTrackMoods(clubRemix)
        assertTrue("Expected UPBEAT in $m3Tags", SmartMoodTag.UPBEAT in m3Tags)
        assertTrue("WAV should tag LOSSLESS_HIFI", SmartMoodTag.LOSSLESS_HIFI in m3Tags)
        assertFalse("Upbeat club remix should not be LATE_NIGHT", SmartMoodTag.LATE_NIGHT in m3Tags)
    }

    @Test
    fun `multi-signal local affinity rewards completions penalizes early skips respects 24h cooldown 21d rediscovery and time-of-day`() {
        // Verify PlaybackController thresholds match 80% completion & 25% early skip
        assertEquals(0.80, PlaybackController.COMPLETION_THRESHOLD_RATIO, 0.001)
        assertEquals(0.25, PlaybackController.EARLY_SKIP_MAX_RATIO, 0.001)

        val completedFav = sampleTrack(
            id = "a1",
            title = "Completed Favorite",
            genre = "Soul",
            bpm = 86,
            playCount = 6,
            completedPlayCount = 6,
            skipCount = 0,
            lastPlayedAt = baseNowMs - (5L * LocalMoodAndAffinityEngine.ONE_DAY_MS),
            isFavorite = true
        )
        val heavilySkipped = sampleTrack(
            id = "a2",
            title = "Skipped Track",
            genre = "Soul",
            bpm = 86,
            playCount = 6,
            completedPlayCount = 0,
            skipCount = 5,
            lastPlayedAt = baseNowMs - (5L * LocalMoodAndAffinityEngine.ONE_DAY_MS),
            isFavorite = false
        )
        val completedScore = LocalMoodAndAffinityEngine.computeLocalAffinity(completedFav, baseNowMs, localHourOfDay = 14)
        val skippedScore = LocalMoodAndAffinityEngine.computeLocalAffinity(heavilySkipped, baseNowMs, localHourOfDay = 14)
        assertTrue("Affinity must be normalized 0..1", completedScore.score in 0f..1f && skippedScore.score in 0f..1f)
        assertTrue("Completed favorite (${completedScore.score}) must outscore skipped (${skippedScore.score})", completedScore.score > skippedScore.score + 0.30f)

        // 24-hour cooldown vs 21+ days rediscovery boost
        val playedTwoHoursAgo = completedFav.copy(
            id = "a3",
            lastPlayedAt = baseNowMs - (2L * LocalMoodAndAffinityEngine.ONE_HOUR_MS)
        )
        val forgottenGem = completedFav.copy(
            id = "a4",
            lastPlayedAt = baseNowMs - (28L * LocalMoodAndAffinityEngine.ONE_DAY_MS)
        )
        val cooldownBreakdown = LocalMoodAndAffinityEngine.computeLocalAffinity(playedTwoHoursAgo, baseNowMs, localHourOfDay = 14)
        val rediscoveryBreakdown = LocalMoodAndAffinityEngine.computeLocalAffinity(forgottenGem, baseNowMs, localHourOfDay = 14)
        assertTrue("24h cooldown must apply negative recency adjustment", cooldownBreakdown.recencyAdjustment < 0f)
        assertTrue("21+ day untouched high-affinity track must receive positive rediscovery boost", rediscoveryBreakdown.recencyAdjustment > 0f)
        assertTrue("Forgotten gem must outscore recently played twin", rediscoveryBreakdown.score > cooldownBreakdown.score)

        // Time-of-day context: 23:00 (21:00-04:59) boosts Late Night/Acoustic/Soulful, 12:00 (06:00-20:59) boosts Upbeat
        val calmNight = sampleTrack(
            id = "t_night",
            title = "Midnight Nocturne",
            genre = "Ambient",
            bpm = 74,
            moodTags = listOf(SmartMoodTag.LATE_NIGHT, SmartMoodTag.ACOUSTIC)
        )
        val upbeatDay = sampleTrack(
            id = "t_day",
            title = "Sunlit Dance Club",
            genre = "Dance Pop",
            bpm = 128,
            moodTags = listOf(SmartMoodTag.UPBEAT)
        )
        val nightLateScore = LocalMoodAndAffinityEngine.computeLocalAffinity(calmNight, baseNowMs, localHourOfDay = 23)
        val dayLateScore = LocalMoodAndAffinityEngine.computeLocalAffinity(calmNight, baseNowMs, localHourOfDay = 12)
        val nightUpbeatScore = LocalMoodAndAffinityEngine.computeLocalAffinity(upbeatDay, baseNowMs, localHourOfDay = 23)
        val dayUpbeatScore = LocalMoodAndAffinityEngine.computeLocalAffinity(upbeatDay, baseNowMs, localHourOfDay = 12)

        assertTrue("Late night track should score higher at 23:00 than 12:00", nightLateScore.score > dayLateScore.score)
        assertTrue("Upbeat track should score higher at 12:00 than 23:00", dayUpbeatScore.score > nightUpbeatScore.score)
    }

    @Test
    fun `balanced 60 25 15 mix enforces strict max 2 per artist and handles 45+ tracks TimSort-safely`() {
        val library = ArrayList<Track>()
        // 15 high-affinity favorites (including 6 by the same artist with mixed casing to verify strict cap!)
        for (i in 1..15) {
            val artist = if (i <= 6) (if (i % 2 == 0) "Nova Echo" else "nova echo") else "Fav Artist $i"
            library += sampleTrack(
                id = "fav_$i",
                title = "Favorite Track $i",
                artist = artist,
                genre = "Soul",
                bpm = 92,
                playCount = 8,
                completedPlayCount = 7,
                skipCount = 0,
                lastPlayedAt = baseNowMs - (4L * LocalMoodAndAffinityEngine.ONE_DAY_MS),
                isFavorite = true
            )
        }
        // 15 forgotten gems (untouched for 30+ days with high completion)
        for (i in 1..15) {
            library += sampleTrack(
                id = "gem_$i",
                title = "Forgotten Gem $i",
                artist = "Gem Artist $i",
                genre = "Acoustic Folk",
                bpm = 84,
                playCount = 6,
                completedPlayCount = 5,
                skipCount = 0,
                lastPlayedAt = baseNowMs - (35L * LocalMoodAndAffinityEngine.ONE_DAY_MS),
                isFavorite = true
            )
        }
        // 15 under-played tracks (playCount <= 1, low skip count)
        for (i in 1..15) {
            library += sampleTrack(
                id = "new_$i",
                title = "Fresh Cut $i",
                artist = "Fresh Artist $i",
                genre = "Upbeat Pop",
                bpm = 122,
                playCount = i % 2, // 0 or 1
                completedPlayCount = 0,
                skipCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false
            )
        }

        val mix = LocalMoodAndAffinityEngine.buildBalancedMix(
            tracks = library,
            targetSize = 12,
            nowMs = baseNowMs,
            localHourOfDay = 15,
            seed = 42
        )

        assertEquals(12, mix.tracks.size)
        // Check 60/25/15 composition on targetSize=12 (approx 7 / 3 / 2)
        assertTrue("Expected ~60% favorites (got ${mix.favoritesCount})", mix.favoritesCount in 6..8)
        assertTrue("Expected ~25% forgotten gems (got ${mix.forgottenGemsCount})", mix.forgottenGemsCount in 2..4)
        assertTrue("Expected ~15% under-played (got ${mix.underPlayedCount})", mix.underPlayedCount in 1..3)

        // Strict max 2 songs per normalized artist
        val artistCounts = mix.tracks.groupingBy { LocalMoodAndAffinityEngine.normalizedArtistKey(it.artist) }.eachCount()
        assertTrue(
            "Every artist must appear at most 2 times, got $artistCounts",
            artistCounts.values.all { it <= 2 }
        )

        // Verify full DiscoverFeedGenerator builds mood playlists & sections cleanly on 45-track library
        val feed = MusicIntelligenceEngine().generateDiscoverFeed(
            libraryTracks = library,
            nowMs = baseNowMs,
            localHourOfDay = 22
        )
        assertTrue("Discover feed should generate mood playlists", feed.moodPlaylists.isNotEmpty())
        feed.moodPlaylists.forEach { moodPl ->
            val moodArtistCounts = moodPl.tracks
                .groupingBy { LocalMoodAndAffinityEngine.normalizedArtistKey(it.artist) }
                .eachCount()
            assertTrue(
                "Mood playlist ${moodPl.title} exceeded max 2 per artist: $moodArtistCounts",
                moodArtistCounts.values.all { it <= 2 }
            )
        }
    }

    @Test
    fun `Room v4 schema persists affinityScore moodTags and artworkColorArgb cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.trackDao()
            val track = sampleTrack(
                id = "room_t1",
                title = "Midnight Acoustic",
                genre = "Acoustic",
                bpm = 78,
                codec = "FLAC",
                bitDepth = 24,
                sampleRateHz = 96_000,
                artworkColorArgb = 0xFF223366.toInt(),
                moodTags = listOf(SmartMoodTag.LATE_NIGHT, SmartMoodTag.ACOUSTIC, SmartMoodTag.LOSSLESS_HIFI)
            ).copy(affinityScore = 0.87f)

            dao.upsertTracks(listOf(TrackEntity.fromDomain(track)))
            val loaded = dao.getTrackById("room_t1")!!.toDomain()
            assertEquals(0.87f, loaded.affinityScore, 0.001f)
            assertEquals(0xFF223366.toInt(), loaded.artworkColorArgb)
            assertEquals(
                listOf(SmartMoodTag.LATE_NIGHT, SmartMoodTag.ACOUSTIC, SmartMoodTag.LOSSLESS_HIFI),
                loaded.moodTags.toList()
            )

            dao.updateAffinityScore("room_t1", 0.93f)
            val updated = dao.getTrackById("room_t1")!!.toDomain()
            assertEquals(0.93f, updated.affinityScore, 0.001f)
        } finally {
            db.close()
        }
    }

    @Test
    fun `LibraryScreen mood filter pills filter tracks instantaneously in memory alongside search`() {
        val lateNightFlac = sampleTrack(
            id = "lib_night",
            title = "Midnight Drift",
            artist = "Nocturne",
            genre = "Ambient",
            bpm = 74,
            codec = "FLAC",
            bitDepth = 24,
            sampleRateHz = 96_000,
            moodTags = listOf(SmartMoodTag.LATE_NIGHT, SmartMoodTag.LOSSLESS_HIFI)
        )
        val acousticMp3 = sampleTrack(
            id = "lib_acoustic",
            title = "Porch Guitar Unplugged",
            artist = "Cedar Folk",
            genre = "Acoustic Folk",
            bpm = 82,
            codec = "MP3",
            bitDepth = 16,
            sampleRateHz = 44_100,
            moodTags = listOf(SmartMoodTag.ACOUSTIC)
        )
        val upbeatMp3 = sampleTrack(
            id = "lib_upbeat",
            title = "Midnight Club Remix",
            artist = "Pulse",
            genre = "Dance",
            bpm = 128,
            codec = "MP3",
            bitDepth = 16,
            sampleRateHz = 44_100,
            moodTags = listOf(SmartMoodTag.UPBEAT)
        )
        val all = listOf(lateNightFlac, acousticMp3, upbeatMp3).toStableList()

        composeRule.setContent {
            CadenceTheme(
                palette = ExpressivePalette.MATCHA,
                appearanceMode = AppearanceMode.DARK
            ) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        allTracks = all,
                        displayedTracks = all,
                        category = LibraryCategory.SONGS,
                        isInitialLibraryLoaded = true
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

        // All 3 tracks visible initially
        composeRule.onNodeWithTag("library_mood_filter_row").assertIsDisplayed()
        composeRule.onNodeWithTag("track_row_lib_night").assertIsDisplayed()
        composeRule.onNodeWithTag("track_row_lib_acoustic").assertIsDisplayed()
        composeRule.onNodeWithTag("track_row_lib_upbeat").assertIsDisplayed()

        // Tap "Late Night" pill -> only lateNightFlac remains
        composeRule.onNodeWithTag("library_mood_pill_${LibraryMoodFilter.LATE_NIGHT.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("track_row_lib_night").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("track_row_lib_acoustic").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("track_row_lib_upbeat").fetchSemanticsNodes().size)

        // Scroll pills and tap "Lossless" -> only lateNightFlac (24-bit FLAC) remains
        composeRule.onNodeWithTag("library_mood_filter_row")
            .performScrollToNode(hasTestTag("library_mood_pill_${LibraryMoodFilter.LOSSLESS.id}"))
        composeRule.onNodeWithTag("library_mood_pill_${LibraryMoodFilter.LOSSLESS.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("track_row_lib_night").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("track_row_lib_upbeat").fetchSemanticsNodes().size)

        // Switch back to "All" and combine with search "Midnight" + "Upbeat" pill
        composeRule.onNodeWithTag("library_mood_filter_row")
            .performScrollToNode(hasTestTag("library_mood_pill_${LibraryMoodFilter.ALL.id}"))
        composeRule.onNodeWithTag("library_mood_pill_${LibraryMoodFilter.ALL.id}").performClick()
        composeRule.onNodeWithTag("library_search_input").performTextInput("Midnight")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("track_row_lib_night").assertIsDisplayed()
        composeRule.onNodeWithTag("track_row_lib_upbeat").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("track_row_lib_acoustic").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("library_mood_filter_row")
            .performScrollToNode(hasTestTag("library_mood_pill_${LibraryMoodFilter.UPBEAT.id}"))
        composeRule.onNodeWithTag("library_mood_pill_${LibraryMoodFilter.UPBEAT.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("track_row_lib_upbeat").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("track_row_lib_night").fetchSemanticsNodes().size)
    }

    @Test
    fun `DiscoverScreen renders Mood Playlists shelf hides empty moods and queues playlist on tap`() {
        val lateNightTracks = listOf(
            sampleTrack("dn1", "Midnight Echo", "Artist A", genre = "Ambient", bpm = 72, moodTags = listOf(SmartMoodTag.LATE_NIGHT)),
            sampleTrack("dn2", "Velvet Dusk", "Artist B", genre = "Lo-Fi", bpm = 78, moodTags = listOf(SmartMoodTag.LATE_NIGHT))
        )
        val upbeatTracks = listOf(
            sampleTrack("du1", "Neon Sprint", "Artist C", genre = "Dance", bpm = 126, moodTags = listOf(SmartMoodTag.UPBEAT))
        )
        val allTracks = (lateNightTracks + upbeatTracks).toStableList()
        val moodPlaylists = LocalMoodAndAffinityEngine.buildMoodSmartPlaylists(allTracks, baseNowMs, 22).toStableList()
        // Acoustic and Soulful have 0 matching tracks so they must be omitted cleanly
        assertTrue(moodPlaylists.any { it.mood == SmartMoodTag.LATE_NIGHT })
        assertTrue(moodPlaylists.any { it.mood == SmartMoodTag.UPBEAT })
        assertFalse(moodPlaylists.any { it.mood == SmartMoodTag.ACOUSTIC })
        assertFalse(moodPlaylists.any { it.mood == SmartMoodTag.SOULFUL })

        var playedQueue: List<Track> = emptyList()
        val section = DiscoverSection(
            category = DiscoverCategory.MADE_FOR_YOU,
            title = "Made for you",
            subtitle = "Daily mix",
            badgeText = "Daily Mix",
            tracks = allTracks
        )

        composeRule.setContent {
            CadenceTheme(
                palette = ExpressivePalette.MATCHA,
                appearanceMode = AppearanceMode.DARK
            ) {
                DiscoverScreen(
                    uiState = DiscoverUiState(
                        allTracks = allTracks,
                        sections = StableList(listOf(section)),
                        moodPlaylists = moodPlaylists,
                        isInitialLibraryLoaded = true
                    ),
                    currentTrackId = null,
                    isPlaying = false,
                    artworkShape = ExpressiveArtworkShape.SCALLOP,
                    onSelectTrackFromSection = { _, _ -> },
                    onPlaySection = { tracks -> playedQueue = tracks.toList() },
                    onRefreshDiscover = {},
                    onOpenSettings = {},
                    onAddFolder = {},
                    onImportFiles = {},
                    onSaveMixAsPlaylist = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("discover_mood_playlists_section").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_mood_card_${SmartMoodTag.LATE_NIGHT.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_mood_card_${SmartMoodTag.UPBEAT.id}").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("discover_mood_card_${SmartMoodTag.ACOUSTIC.id}").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("discover_mood_card_${SmartMoodTag.SOULFUL.id}").fetchSemanticsNodes().size)

        // One-tap queues and plays the Late Night smart playlist
        composeRule.onNodeWithTag("discover_mood_card_${SmartMoodTag.LATE_NIGHT.id}").performClick()
        composeRule.waitForIdle()
        assertEquals(2, playedQueue.size)
        assertTrue(playedQueue.map { it.id }.containsAll(listOf("dn1", "dn2")))
    }
}
