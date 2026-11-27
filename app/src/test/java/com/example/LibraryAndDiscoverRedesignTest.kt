package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.LibraryCategory
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.Track
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.intelligence.SmartMoodTag
import com.example.data.library.LibraryRepository
import com.example.data.toStableList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.ui.components.ExpressiveArtworkFrame
import com.example.ui.components.TrackArtwork
import com.example.ui.components.ambientAuraBackdrop
import com.example.ui.components.resolveArtworkFallbackStyle
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.LibraryUiState
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.MatchaDarkScheme
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
class LibraryAndDiscoverRedesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testTracks = listOf(
        Track(
            id = "rd_1",
            title = "Midnight Glass",
            artist = "Kiasmos",
            album = "Afterglow",
            genre = "Ambient",
            durationMs = 248_000L,
            isFavorite = true,
            playCount = 18,
            completedPlayCount = 16,
            lastPlayedAt = 1_760_000_000_000L,
            dateAdded = 1_759_900_000_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 1411,
            sampleRateHz = 96_000,
            bitDepth = 24,
            moodTags = listOf(SmartMoodTag.LATE_NIGHT, SmartMoodTag.LOSSLESS_HIFI).toStableList()
        ),
        Track(
            id = "rd_2",
            title = "Cedar & Rain",
            artist = "Nils Frahm",
            album = "Quiet Rooms",
            genre = "Acoustic",
            durationMs = 212_000L,
            isFavorite = true,
            playCount = 12,
            completedPlayCount = 11,
            lastPlayedAt = 1_759_950_000_000L,
            dateAdded = 1_759_800_000_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 980,
            sampleRateHz = 44_100,
            bitDepth = 16,
            moodTags = listOf(SmartMoodTag.ACOUSTIC, SmartMoodTag.LOSSLESS_HIFI).toStableList()
        ),
        Track(
            id = "rd_3",
            title = "Velvet Horizon",
            artist = "Bonobo",
            album = "Migration",
            genre = "Downtempo",
            durationMs = 264_000L,
            isFavorite = false,
            playCount = 9,
            completedPlayCount = 8,
            lastPlayedAt = 1_759_920_000_000L,
            dateAdded = 1_759_700_000_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            moodTags = listOf(SmartMoodTag.SOULFUL, SmartMoodTag.LATE_NIGHT).toStableList()
        ),
        Track(
            id = "rd_4",
            title = "Solar Pulse",
            artist = "Tycho",
            album = "Awake",
            genre = "Synthwave",
            durationMs = 230_000L,
            isFavorite = false,
            playCount = 6,
            completedPlayCount = 5,
            lastPlayedAt = 1_759_850_000_000L,
            dateAdded = 1_759_600_000_000L,
            mimeType = "audio/mpeg",
            codec = "MP3",
            bitrateKbps = 320,
            sampleRateHz = 44_100,
            moodTags = listOf(SmartMoodTag.UPBEAT).toStableList()
        ),
        Track(
            id = "rd_5",
            title = "Amber Echoes",
            artist = "Kiasmos",
            album = "Afterglow",
            genre = "Ambient",
            durationMs = 256_000L,
            isFavorite = true,
            playCount = 14,
            completedPlayCount = 13,
            lastPlayedAt = 1_759_990_000_000L,
            dateAdded = 1_759_950_000_000L,
            mimeType = "audio/flac",
            codec = "FLAC",
            bitrateKbps = 1520,
            sampleRateHz = 48_000,
            bitDepth = 24,
            moodTags = listOf(SmartMoodTag.LATE_NIGHT, SmartMoodTag.SOULFUL, SmartMoodTag.LOSSLESS_HIFI).toStableList()
        )
    )

    private val testPlaylists = listOf(
        PlaylistSummary(
            id = 101L,
            name = "Late Night Lo-Fi",
            trackCount = 3,
            coverTrack = testTracks.first()
        ),
        PlaylistSummary(
            id = 102L,
            name = "Morning Acoustics",
            trackCount = 2,
            coverTrack = testTracks[1]
        )
    )

    @Test
    fun `LibraryScreen renders Bento Hub and Immediate Songs Feed edge-to-edge with sub-views and live search`() {
        var currentCategory by mutableStateOf(LibraryCategory.OVERVIEW)
        var currentSort by mutableStateOf(LibrarySortOrder.DATE_ADDED_DESC)
        var settingsOpened = false
        var shuffledCount = 0
        var openedDrill: com.example.ui.player.LibraryDrillDown? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .ambientAuraBackdrop(enabled = true, isPlaying = true)
                        .testTag("shared_ambient_backdrop")
                ) {
                    LibraryScreen(
                        uiState = LibraryUiState(
                            allTracks = testTracks.toStableList(),
                            displayedTracks = testTracks.toStableList(),
                            albums = LibraryRepository.groupAlbums(testTracks).toStableList(),
                            artists = LibraryRepository.groupArtists(testTracks).toStableList(),
                            folders = LibraryRepository.groupFolders(testTracks).toStableList(),
                            genres = LibraryRepository.groupGenres(testTracks).toStableList(),
                            playlists = testPlaylists.toStableList(),
                            category = currentCategory,
                            sortOrder = currentSort,
                            isInitialLibraryLoaded = true
                        ),
                        currentTrackId = testTracks.first().id,
                        isPlaying = true,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectTrack = { _, _ -> },
                        onPlayTracks = { list, shuffled ->
                            if (shuffled) shuffledCount = list.size
                        },
                        onToggleFavorite = {},
                        onSearchChange = {},
                        onCategoryChange = { currentCategory = it },
                        onSortChange = { currentSort = it },
                        onOpenDrillDown = { openedDrill = it },
                        onCloseDrillDown = { openedDrill = null },
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
        }

        // Edge-to-edge: library_root spans from top 0dp over shared ambient backdrop
        val backdropBounds = composeRule.onNodeWithTag("shared_ambient_backdrop").getUnclippedBoundsInRoot()
        val libraryBounds = composeRule.onNodeWithTag("library_root").getUnclippedBoundsInRoot()
        assertEquals(0f, backdropBounds.top.value, 1f)
        assertEquals(0f, libraryBounds.top.value, 1f)
        assertEquals(backdropBounds.height.value, libraryBounds.height.value, 2f)

        // Header: "Your Music" + unclipped subtitle + single 48dp Settings button (zero duplicate search button)
        composeRule.onNodeWithTag("library_header_title")
            .assertIsDisplayed()
            .assertTextContains("Your Music")
        composeRule.onNodeWithTag("library_header_subtitle")
            .assertIsDisplayed()
            .assertTextContains("Your library, your vibe.")
        assertTextNotEllipsized("library_header_subtitle")
        composeRule.onNodeWithTag("library_search_button").assertDoesNotExist()

        val settingsBtnBounds = composeRule.onNodeWithTag("library_settings_button").getUnclippedBoundsInRoot()
        assertEquals(48f, settingsBtnBounds.width.value, 2f)
        composeRule.onNodeWithTag("library_search_input").assertIsDisplayed()

        // Old cramped 8-tab horizontal category pill row is gone
        composeRule.onNodeWithTag("library_category_row").assertDoesNotExist()

        // 2x3 Bento Hub Grid with all 6 live category cards
        composeRule.onNodeWithTag("library_bento_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("library_bento_favorites").assertIsDisplayed()
        composeRule.onNodeWithTag("library_bento_subtitle_favorites", useUnmergedTree = true)
            .assertTextContains("3 liked")
        composeRule.onNodeWithTag("library_bento_playlists").assertIsDisplayed()
        composeRule.onNodeWithTag("library_bento_subtitle_playlists", useUnmergedTree = true)
            .assertTextContains("2 playlists")
        composeRule.onNodeWithTag("library_bento_albums").assertIsDisplayed()
        composeRule.onNodeWithTag("library_bento_artists").assertIsDisplayed()
        composeRule.onNodeWithTag("library_bento_folders").assertIsDisplayed()
        composeRule.onNodeWithTag("library_bento_recent").assertIsDisplayed()

        // Mood Filter Chips + Unified Songs Action Bar + Immediate Songs Feed on main canvas
        composeRule.onNodeWithTag("library_mood_filter_row").assertIsDisplayed()
        composeRule.onNodeWithTag("library_mood_pill_all").assertIsDisplayed()
        composeRule.onNodeWithTag("library_songs_action_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("library_songs_count_pill", useUnmergedTree = true)
            .assertTextContains("5")

        // Shuffle, New Playlist dialog, and Sort menu work cleanly
        composeRule.onNodeWithTag("library_shuffle_button").performClick()
        assertEquals(5, shuffledCount)

        composeRule.onNodeWithTag("sort_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Title A–Z").performClick()
        composeRule.waitForIdle()
        assertEquals(LibrarySortOrder.TITLE_ASC, currentSort)

        // Immediate Songs Feed displays track rows right on the main Library canvas
        composeRule.onNodeWithTag("library_results_list")
            .performScrollToNode(hasTestTag("track_row_rd_1"))
        composeRule.onNodeWithTag("track_row_rd_1").assertIsDisplayed()

        // Real-time search filters both collections (Playlists/Albums/Artists) and Songs
        composeRule.onNodeWithTag("library_search_input").performTextInput("Late Night")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_bento_grid").assertDoesNotExist()
        composeRule.onNodeWithTag("library_search_collections_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("library_search_collection_pl_101").performClick()
        assertEquals(com.example.ui.player.LibraryDrillDown.Playlist(101L, "Late Night Lo-Fi"), openedDrill)
        composeRule.onNodeWithTag("library_search_clear_button").performClick()
        composeRule.waitForIdle()

        // Tapping a Bento card opens dedicated Sub-View and Back returns cleanly to Main Hub
        composeRule.onNodeWithTag("library_bento_playlists").performClick()
        composeRule.waitForIdle()
        assertEquals(LibraryCategory.PLAYLISTS, currentCategory)
        composeRule.onNodeWithTag("library_subview_title")
            .assertIsDisplayed()
            .assertTextContains("Playlists")
        composeRule.onNodeWithTag("library_subview_back_button").performClick()
        composeRule.waitForIdle()
        assertEquals(LibraryCategory.OVERVIEW, currentCategory)

        // Settings button works
        composeRule.onNodeWithTag("library_settings_button").performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun `DiscoverScreen renders all editorial sections edge-to-edge with unclipped For This Moment subtitle and Insights sheet`() {
        val engine = MusicIntelligenceEngine()
        val feed = engine.generateDiscoverFeed(
            libraryTracks = testTracks,
            currentTrackId = testTracks.first().id
        )
        var searchOpened = false
        var settingsOpened = false
        var playedTracksCount = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .ambientAuraBackdrop(enabled = true, isPlaying = true)
                        .testTag("shared_ambient_backdrop")
                ) {
                    DiscoverScreen(
                        uiState = DiscoverUiState(
                            allTracks = testTracks.toStableList(),
                            sections = feed.sections,
                            routineMixes = feed.routineMixes,
                            moodPlaylists = feed.moodPlaylists,
                            anchorTrackTitle = feed.anchorTrackTitle,
                            insights = feed.insights,
                            isInitialLibraryLoaded = true
                        ),
                        currentTrackId = testTracks.first().id,
                        isPlaying = true,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectTrackFromSection = { _, list -> playedTracksCount = list.size },
                        onPlaySection = { list -> playedTracksCount = list.size },
                        onRefreshDiscover = {},
                        onOpenSettings = { settingsOpened = true },
                        onAddFolder = {},
                        onImportFiles = {},
                        onSaveMixAsPlaylist = { _, _ -> },
                        onOpenSearch = { searchOpened = true }
                    )
                }
            }
        }

        // Edge-to-edge: discover_root spans from top 0dp over shared ambient backdrop
        val backdropBounds = composeRule.onNodeWithTag("shared_ambient_backdrop").getUnclippedBoundsInRoot()
        val discoverBounds = composeRule.onNodeWithTag("discover_root").getUnclippedBoundsInRoot()
        assertEquals(0f, backdropBounds.top.value, 1f)
        assertEquals(0f, discoverBounds.top.value, 1f)
        assertEquals(backdropBounds.height.value, discoverBounds.height.value, 2f)

        // Clean Header: "Discover" title with zero subtitle + 44dp Search & Settings buttons
        composeRule.onNodeWithTag("discover_header_title")
            .assertIsDisplayed()
            .assertTextContains("Discover")
        composeRule.onNodeWithTag("discover_header_subtitle").assertDoesNotExist()
        val searchBounds = composeRule.onNodeWithTag("discover_search_button").getUnclippedBoundsInRoot()
        val settingsBounds = composeRule.onNodeWithTag("discover_settings_button").getUnclippedBoundsInRoot()
        assertEquals(44f, searchBounds.width.value, 2f)
        assertEquals(44f, settingsBounds.width.value, 2f)

        composeRule.onNodeWithTag("discover_search_button").performClick()
        assertTrue(searchOpened)
        composeRule.onNodeWithTag("discover_settings_button").performClick()
        assertTrue(settingsOpened)

        // Section 1: For This Moment with unclipped 1-line subtitle at 360dp
        composeRule.onNodeWithTag("discover_routine_mixes_header")
            .assertIsDisplayed()
            .assertTextContains("For This Moment")
        composeRule.onNodeWithTag("discover_routine_mixes_subtitle")
            .assertIsDisplayed()
            .assertTextContains("Tuned to your time of day & listening rhythm")
        assertTextNotEllipsized("discover_routine_mixes_subtitle")

        val activeRoutine = feed.routineMixes.first { it.tracks.isNotEmpty() }
        val routineCardBounds = composeRule
            .onNodeWithTag("discover_routine_card_${activeRoutine.type.id}")
            .getUnclippedBoundsInRoot()
        assertEquals(312f, routineCardBounds.width.value, 3f)
        val routineArtBounds = composeRule
            .onNodeWithTag("discover_routine_art_${activeRoutine.type.id}")
            .getUnclippedBoundsInRoot()
        assertEquals(76f, routineArtBounds.width.value, 2f)
        composeRule.onNodeWithTag("discover_routine_play_${activeRoutine.type.id}").performClick()
        assertTrue(playedTracksCount > 0)

        // Section 2: Mood Playlists (132dp x 132dp tonal mood tiles)
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_mood_playlists_section"))
        composeRule.onNodeWithTag("discover_mood_playlists_section").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_mood_playlists_see_all").assertIsDisplayed()
        val firstMood = feed.moodPlaylists.first { it.tracks.isNotEmpty() }
        val moodTileBounds = composeRule
            .onNodeWithTag("discover_mood_card_${firstMood.mood.id}")
            .getUnclippedBoundsInRoot()
        assertEquals(132f, moodTileBounds.width.value, 2f)
        assertEquals(132f, moodTileBounds.height.value, 2f)

        // Section 3: Made for you (Featured Daily Mix card with 104dp cover, pill, shaped-by text, Tune Mix, 3-dot menu)
        composeRule.onNodeWithTag("discover_list")
            .performScrollToKey("section_made_for_you")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discover_daily_mix_card").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_daily_mix_pill", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("discover_daily_mix_subtitle", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Daily mix shaped by", substring = true)
        val dailyArtBounds = composeRule
            .onNodeWithTag("discover_daily_mix_art", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(104f, dailyArtBounds.width.value, 2f)
        composeRule.onNodeWithTag("discover_tune_daily_mix_button").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_daily_mix_menu").assertIsDisplayed()

        // Section 4: Smart Mix (250dp wide compact cards + Tune & Play All pills)
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_smart_mix_row"))
        composeRule.onNodeWithTag("discover_smart_mix_header").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_tune_smart_mix_button").assertIsDisplayed()
        val smartCardBounds = composeRule.onNodeWithTag("discover_smart_mix_card_0").getUnclippedBoundsInRoot()
        assertEquals(250f, smartCardBounds.width.value, 3f)

        // Section 5: Listening Insights 2x2 Bento Grid + detail modal sheet
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_listening_insights_card"))
        composeRule.onNodeWithTag("discover_listening_insights_card").assertIsDisplayed()
        composeRule.onNodeWithTag("insight_top_mood").assertIsDisplayed()
        composeRule.onNodeWithTag("insight_top_artist").assertIsDisplayed()
        composeRule.onNodeWithTag("insight_listening_time").assertIsDisplayed()
        composeRule.onNodeWithTag("insight_hifi_percent").assertIsDisplayed()

        composeRule.onNodeWithTag("discover_insights_expand_button").performClick()
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Listening Summary").assertIsDisplayed()
    }

    @Test
    fun `TrackArtwork and ExpressiveArtworkFrame render cohesive M3 Expressive music emblem fallback on null and broken URIs`() {
        // Verify deterministic 4-variant hash distribution across distinct seeds
        val variants = (0 until 32).map { idx ->
            resolveArtworkFallbackStyle("album_seed_$idx", MatchaDarkScheme).variantIndex
        }.toSet()
        assertEquals(setOf(0, 1, 2, 3), variants)

        val nullArtTrack = testTracks.first().copy(id = "fallback_null", artworkPath = null, artworkResId = null)
        val brokenArtTrack = testTracks[1].copy(
            id = "fallback_broken",
            artworkPath = "/nonexistent/path/missing_cover_art.jpg",
            artworkResId = null
        )

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Column {
                    // 48dp compact thumbnail (null artwork)
                    ExpressiveArtworkFrame(
                        track = nullArtTrack,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        isPlaying = false,
                        animateScale = false,
                        shapeOverride = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp)
                    )
                    // 118dp medium cover (broken/missing file URI)
                    ExpressiveArtworkFrame(
                        track = brokenArtTrack,
                        artworkShape = ExpressiveArtworkShape.SQUIRCLE,
                        isPlaying = false,
                        animateScale = false,
                        shapeOverride = RoundedCornerShape(20.dp),
                        modifier = Modifier.size(118.dp)
                    )
                    // Direct TrackArtwork call with null URI
                    TrackArtwork(
                        artworkUri = null,
                        seed = "Direct Album",
                        contentDescription = "Direct album cover",
                        shape = RoundedCornerShape(18.dp),
                        trackId = "direct_null",
                        modifier = Modifier.size(100.dp)
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Null URI renders styled fallback container + ~44% emblem badge (~21.1dp for 48dp container)
        composeRule.onNodeWithTag("artwork_fallback_fallback_null", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("artwork_fallback_emblem_fallback_null", useUnmergedTree = true).assertIsDisplayed()
        val thumbEmblem = composeRule
            .onNodeWithTag("artwork_fallback_emblem_fallback_null", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(48f * 0.44f, thumbEmblem.width.value, 2f)

        // Broken URI falls back to styled M3 Expressive emblem (~51.9dp for 118dp container) instead of blank dark box
        composeRule.onNodeWithTag("artwork_fallback_fallback_broken", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("artwork_fallback_emblem_fallback_broken", useUnmergedTree = true).assertIsDisplayed()
        val coverEmblem = composeRule
            .onNodeWithTag("artwork_fallback_emblem_fallback_broken", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(118f * 0.44f, coverEmblem.width.value, 2f)

        // Direct TrackArtwork renders ~44dp emblem inside 100dp container
        composeRule.onNodeWithTag("artwork_fallback_direct_null", useUnmergedTree = true).assertIsDisplayed()
        val directEmblem = composeRule
            .onNodeWithTag("artwork_fallback_emblem_direct_null", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(44f, directEmblem.width.value, 2f)
    }

    @Test
    fun `LibraryScreen track rows at 360dp give generous artist width alongside duration, HI-FI badge, and active playing indicator`() {
        val longTitleTracks = listOf(
            testTracks[0].copy(
                id = "clip_1",
                title = "Extremely Long Ambient Track Title For Overflow Verification",
                artist = "Very Long Collaborative Ensemble Artist Name",
                artworkPath = null,
                mimeType = "audio/flac",
                bitrateKbps = 1411,
                dateAdded = 1_760_000_000_000L
            ),
            testTracks[1].copy(
                id = "clip_2",
                title = "Second Song Title",
                artist = "Second Song Artist",
                artworkPath = null,
                dateAdded = 1_759_900_000_000L
            )
        )

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.size(width = 360.dp, height = 800.dp)) {
                    LibraryScreen(
                        uiState = LibraryUiState(
                            allTracks = longTitleTracks.toStableList(),
                            displayedTracks = longTitleTracks.toStableList(),
                            category = LibraryCategory.OVERVIEW,
                            isInitialLibraryLoaded = true
                        ),
                        currentTrackId = "clip_1",
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
                }
            }
        }

        composeRule.onNodeWithTag("library_results_list")
            .performScrollToNode(hasTestTag("track_row_clip_1"))
        composeRule.waitForIdle()

        val rowBounds = composeRule.onNodeWithTag("track_row_clip_1").getUnclippedBoundsInRoot()
        val titleBounds = composeRule
            .onNodeWithTag("track_title_clip_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val artistBounds = composeRule
            .onNodeWithTag("track_artist_clip_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val hifiBounds = composeRule
            .onNodeWithTag("track_hifi_badge_clip_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        // Artist name must have generous readable width (>= 85dp) even on a 360dp phone with active pulse bars + duration + HI-FI badge
        assertTrue(
            "Artist width (${artistBounds.width.value}dp) must be >= 85dp on 360dp screen",
            artistBounds.width.value >= 85f
        )
        assertTrue(
            "Title right (${titleBounds.right.value}) must stay inside row bounds (${rowBounds.right.value})",
            titleBounds.right.value <= rowBounds.right.value
        )
        assertTrue(
            "HI-FI badge right (${hifiBounds.right.value}) must stay inside row bounds (${rowBounds.right.value})",
            hifiBounds.right.value <= rowBounds.right.value
        )
        // Fallback artwork emblem renders inside the 48dp active-shape artwork frame
        composeRule
            .onAllNodesWithTag("artwork_fallback_emblem_clip_1", useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }

    private fun assertTextNotEllipsized(testTag: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithTag(testTag).fetchSemanticsNode()
        val getTextLayoutResult = node.config[SemanticsActions.GetTextLayoutResult].action
        getTextLayoutResult?.invoke(layouts)
        assertTrue("Expected text layout result for $testTag", layouts.isNotEmpty())
        val layout = layouts.first()
        assertFalse(
            "Text '$testTag' must not overflow or ellipsize at 360dp width",
            layout.didOverflowWidth || layout.isLineEllipsized(0)
        )
    }
}
