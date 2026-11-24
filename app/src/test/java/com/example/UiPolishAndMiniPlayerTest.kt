package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.data.Track
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.MusicIntelligenceEngine
import com.example.data.toStableList
import com.example.playback.PlaybackProgress
import com.example.ui.CadenceTab
import com.example.ui.FloatingBottomDockOverlay
import com.example.ui.player.DiscoverUiState
import com.example.ui.player.HomeUiState
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiPolishAndMiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleTracks = listOf(
        Track(
            id = "p1",
            title = "Midnight Skyline",
            artist = "Klara Lind",
            album = "Nocturne",
            durationMs = 214_000L,
            genre = "Ambient",
            isFavorite = true,
            playCount = 12,
            completedPlayCount = 10
        ),
        Track(
            id = "p2",
            title = "Velvet Horizon",
            artist = "Nils Rivera",
            album = "Drift",
            durationMs = 198_000L,
            genre = "Electronic",
            isFavorite = true,
            playCount = 9,
            completedPlayCount = 8
        ),
        Track(
            id = "p3",
            title = "Amber Rain",
            artist = "Sora Takahashi",
            album = "Quiet Hours",
            durationMs = 242_000L,
            genre = "Indie",
            isFavorite = false,
            playCount = 6,
            completedPlayCount = 5
        ),
        Track(
            id = "p4",
            title = "Glass Harbor",
            artist = "Elena Brooks",
            album = "Harbor",
            durationMs = 185_000L,
            genre = "Ambient",
            isFavorite = false,
            playCount = 4,
            completedPlayCount = 3
        )
    )

    @Test
    fun `HomeScreen 2x2 shortcut cards render concise labels without truncation on 360dp portrait width`() {
        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(800.dp)
                ) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = sampleTracks.toStableList(),
                            recentlyPlayed = sampleTracks.take(3).toStableList(),
                            recentlyAdded = sampleTracks.toStableList(),
                            likedTracks = sampleTracks.take(2).toStableList(),
                            mostPlayedTracks = sampleTracks.toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = sampleTracks.first(),
                        isPlaying = true,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onExpandNowPlaying = {},
                        onTogglePlayPause = {},
                        onSelectTrack = { _, _ -> },
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

        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_shortcuts_grid"))
        composeRule.waitForIdle()

        val expectedShortcuts = listOf(
            "home_shortcut_liked" to "Favorites",
            "home_shortcut_most_played" to "Top Rotation",
            "home_shortcut_recent_added" to "New Adds",
            "home_shortcut_shuffle_all" to "Shuffle All"
        )
        expectedShortcuts.forEach { (cardTag, label) ->
            val cardBounds = composeRule.onNodeWithTag(cardTag).getUnclippedBoundsInRoot()
            val node = composeRule.onNodeWithText(label, useUnmergedTree = true)
            node.assertIsDisplayed()
            val textBounds = node.getUnclippedBoundsInRoot()
            val semanticsNode = node.fetchSemanticsNode()
            val layoutResults = mutableListOf<TextLayoutResult>()
            val getLayout = semanticsNode.config[SemanticsActions.GetTextLayoutResult]
            assertTrue("Expected text layout result for '$label'", getLayout.action?.invoke(layoutResults) == true)
            val result = layoutResults.first()
            assertEquals("'$label' must render on a single line", 1, result.lineCount)
            assertFalse("'$label' must not ellipsize on 360dp portrait width", result.isLineEllipsized(0))
            assertTrue(
                "'$label' glyph width (${result.getLineRight(0)}px) must fit within layout width (${result.size.width}px)",
                result.getLineRight(0) <= result.size.width + 0.5f
            )
            assertTrue(
                "'$label' right bound (${textBounds.right}) must fit cleanly inside card right bound (${cardBounds.right})",
                textBounds.right <= cardBounds.right - 10.dp
            )
        }
    }

    @Test
    fun `DiscoverScreen renders Forgotten Gems and Similar Vibe cleanly alongside action buttons at 360dp width`() {
        val engine = MusicIntelligenceEngine()
        val nowMs = 1_760_000_000_000L
        val library = (1..18).map { idx ->
            sampleTracks[idx % sampleTracks.size].copy(
                id = "disc_polish_$idx",
                title = "Track $idx",
                isFavorite = idx in 1..6,
                playCount = if (idx in 1..6) 12 else 2,
                completedPlayCount = if (idx in 1..6) 10 else 1,
                lastPlayedAt = if (idx in 1..6) nowMs - (30L * 24L * 3600L * 1000L) else nowMs - (idx * 3600_000L)
            )
        }
        val feed = engine.generateDiscoverFeed(libraryTracks = library, nowMs = nowMs)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(800.dp)
                ) {
                    DiscoverScreen(
                        uiState = DiscoverUiState(
                            allTracks = library.toStableList(),
                            sections = feed.sections,
                            anchorTrackTitle = feed.anchorTrackTitle,
                            insights = feed.insights,
                            isInitialLibraryLoaded = true
                        ),
                        currentTrackId = library.last().id,
                        isPlaying = false,
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
        composeRule.waitForIdle()

        // Scroll to Forgotten Gems and assert title fits cleanly alongside Save + Play All buttons
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_section_${DiscoverCategory.FORGOTTEN_FAVORITES.id}"))
        composeRule.waitForIdle()

        val forgottenTitleNode = composeRule.onNodeWithText("Forgotten Gems")
        forgottenTitleNode.assertIsDisplayed()
        composeRule.onNodeWithTag("discover_save_${DiscoverCategory.FORGOTTEN_FAVORITES.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_play_all_${DiscoverCategory.FORGOTTEN_FAVORITES.id}").assertIsDisplayed()
        val forgottenLayouts = mutableListOf<TextLayoutResult>()
        forgottenTitleNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(forgottenLayouts)
        assertEquals(1, forgottenLayouts.first().lineCount)
        assertFalse("Forgotten Gems must not overflow next to action buttons", forgottenLayouts.first().hasVisualOverflow)

        // Scroll to Similar Vibe and assert title fits cleanly alongside Save + Play All buttons
        composeRule.onNodeWithTag("discover_list")
            .performScrollToNode(hasTestTag("discover_section_${DiscoverCategory.CONTINUE_THE_VIBE.id}"))
        composeRule.waitForIdle()

        val vibeTitleNode = composeRule.onNodeWithText("Similar Vibe")
        vibeTitleNode.assertIsDisplayed()
        composeRule.onNodeWithTag("discover_save_${DiscoverCategory.CONTINUE_THE_VIBE.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("discover_play_all_${DiscoverCategory.CONTINUE_THE_VIBE.id}").assertIsDisplayed()
        val vibeLayouts = mutableListOf<TextLayoutResult>()
        vibeTitleNode.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(vibeLayouts)
        assertEquals(1, vibeLayouts.first().lineCount)
        assertFalse("Similar Vibe must not overflow next to action buttons", vibeLayouts.first().hasVisualOverflow)
    }

    @Test
    fun `FloatingMiniPlayer renders flush bottom hairline progress and supports swipe skip swipe up expand and button taps`() {
        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 60_000L, durationMs = 180_000L))
        var expandCount = 0
        var playPauseCount = 0
        var nextCount = 0
        var previousCount = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.DARK) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(800.dp)
                ) {
                    FloatingBottomDockOverlay(
                        activeTab = CadenceTab.HOME,
                        currentTrack = sampleTracks.first(),
                        isPlaying = true,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onSelectTab = {},
                        onExpandMiniPlayer = { expandCount++ },
                        onTogglePlayPause = { playPauseCount++ },
                        onNext = { nextCount++ },
                        onPrevious = { previousCount++ },
                        progressFlow = progressFlow,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Hairline 2dp progress bar sits flush along the bottom edge of mini_player_pill
        val pillBounds = composeRule.onNodeWithTag("mini_player_pill").getUnclippedBoundsInRoot()
        val progressNode = composeRule.onNodeWithTag("mini_player_progress_bar", useUnmergedTree = true)
        progressNode.assertIsDisplayed()
        val progressBounds = progressNode.getUnclippedBoundsInRoot()
        assertEquals(2f, (progressBounds.bottom - progressBounds.top).value, 0.5f)
        assertEquals(pillBounds.bottom.value, progressBounds.bottom.value, 1.5f)

        // 2. Horizontal swipe left on body -> Next (without triggering expand)
        composeRule.onNodeWithTag("mini_player_gesture_body", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals("Swipe left should trigger Next", 1, nextCount)
        assertEquals("Swipe left must not trigger Previous", 0, previousCount)
        assertEquals("Swipe left must not expand Now Playing", 0, expandCount)

        // 3. Horizontal swipe right on body -> Previous (without triggering expand)
        composeRule.onNodeWithTag("mini_player_gesture_body", useUnmergedTree = true)
            .performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals("Swipe right should trigger Previous", 1, previousCount)
        assertEquals("Swipe right must not expand Now Playing", 0, expandCount)

        // 4. Upward swipe on body -> Expand Now Playing
        composeRule.onNodeWithTag("mini_player_gesture_body", useUnmergedTree = true)
            .performTouchInput {
                swipe(
                    start = center,
                    end = Offset(center.x, center.y - 180f),
                    durationMillis = 150L
                )
            }
        composeRule.waitForIdle()
        assertEquals("Swipe up should expand Now Playing", 1, expandCount)

        // 5. Independent 48dp Play/Pause and Next buttons remain unobstructed
        composeRule.onNodeWithTag("mini_play_pause_button").performClick()
        composeRule.onNodeWithTag("mini_next_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, playPauseCount)
        assertEquals(2, nextCount)

        // 6. Single-tap on Mini-Player body still expands Now Playing seamlessly
        composeRule.onNodeWithTag("mini_player_pill").performClick()
        composeRule.waitForIdle()
        assertEquals(2, expandCount)
    }
}
