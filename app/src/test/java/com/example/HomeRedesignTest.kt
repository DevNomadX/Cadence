package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import com.example.data.Track
import com.example.data.intelligence.MoodSmartPlaylist
import com.example.data.intelligence.SmartMoodTag
import com.example.data.toStableList
import com.example.playback.PlaybackProgress
import com.example.ui.icons.CadenceIcons
import com.example.ui.player.HomeUiState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.example.ui.components.ambientAuraBackdrop
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.currentLocalHourOfDay
import com.example.ui.screens.formatHighlightedHomeGreeting
import com.example.ui.screens.formatHomeGreeting
import com.example.ui.screens.formatHomeSubtitle
import com.example.ui.screens.homeSubtitleCandidates
import com.example.ui.screens.resolveHomeHeroColors
import com.example.ui.screens.resolveHomeLibraryGridColors
import com.example.ui.screens.resolveHomeMoodDisplay
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.ScallopedCookieShape
import com.example.ui.theme.opaqueOver
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
class HomeRedesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hiResTrack = Track(
        id = "hr_1",
        title = "Velvet Horizon (Studio Master)",
        artist = "Klara Lind",
        album = "Nocturne",
        durationMs = 240_000L,
        genre = "Ambient",
        mimeType = "audio/flac",
        bitrateKbps = 2116,
        sampleRateHz = 96_000,
        bitDepth = 24,
        isFavorite = true,
        playCount = 14,
        completedPlayCount = 12,
        lastPlayedAt = 1_760_000_000_000L
    )

    private val acousticTrack = Track(
        id = "ac_2",
        title = "Cedar Room",
        artist = "Sora Tan",
        album = "Timber",
        durationMs = 195_000L,
        genre = "Acoustic",
        mimeType = "audio/mpeg",
        bitrateKbps = 320,
        isFavorite = false,
        playCount = 6,
        completedPlayCount = 5,
        lastPlayedAt = 1_759_900_000_000L
    )

    private val tracks = listOf(hiResTrack, acousticTrack)

    @Test
    fun `HomeScreen editorial header Jump Back In hero card Bento library grid and Gallery shelf match flagship M3 Expressive spec`() {
        assertEquals("Late night vibes", formatHomeGreeting(hourOfDay = 0))
        assertEquals("Late night vibes", formatHomeGreeting(hourOfDay = 1))
        assertEquals("Good morning!", formatHomeGreeting(hourOfDay = 7))
        assertEquals("Good afternoon!", formatHomeGreeting(hourOfDay = 15))
        assertEquals("Good evening!", formatHomeGreeting(hourOfDay = 20))
        assertEquals("Good night!", formatHomeGreeting(hourOfDay = 21))

        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 60_000L, durationMs = 240_000L))
        var expandedCount = 0
        var toggleCount = 0
        val soughtFractions = mutableListOf<Float>()

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(800.dp)
                        .ambientAuraBackdrop(enabled = true, isPlaying = true)
                ) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = tracks.toStableList(),
                            dailyPicks = tracks.toStableList(),
                            recentlyPlayed = tracks.toStableList(),
                            recentlyAdded = tracks.toStableList(),
                            likedTracks = listOf(hiResTrack).toStableList(),
                            mostPlayedTracks = tracks.toStableList(),
                            moodCollections = emptyList<MoodSmartPlaylist>().toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = hiResTrack,
                        isPlaying = true,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onExpandNowPlaying = { expandedCount++ },
                        onTogglePlayPause = { toggleCount++ },
                        onSelectTrack = { _, _ -> },
                        onPlayTracks = { _, _ -> },
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onAddFolder = {},
                        onScanDevice = {},
                        progressFlow = progressFlow,
                        onSeek = { soughtFractions.add(it) }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 0. Edge-to-edge root container extends seamlessly to y = 0.dp behind the status bar with clean header clearance
        val rootBounds = composeRule.onNodeWithTag("home_root").getUnclippedBoundsInRoot()
        assertEquals("HomeScreen root must start edge-to-edge at y = 0.dp", 0f, rootBounds.top.value, 0.5f)
        assertEquals("HomeScreen root must span full viewport height", 800f, rootBounds.bottom.value, 0.5f)
        val headerTitleBounds = composeRule.onNodeWithTag("home_header_title")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Home header must keep >= 8.dp breathing room below status bar top (was ${headerTitleBounds.top.value})",
            headerTitleBounds.top.value - rootBounds.top.value >= 8f
        )

        // 1. Editorial Header: Primary-accented greeting + non-truncating subtitle + 44.dp tonal Search & Settings buttons
        val subtitleNode = composeRule.onNodeWithTag("home_header_subtitle").assertIsDisplayed()
        val subtitleText = subtitleNode.fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .joinToString("") { it.text }
        val expectedPool = homeSubtitleCandidates(
            hourOfDay = currentLocalHourOfDay(),
            trackCount = tracks.size,
            hasLosslessTracks = true
        )
        assertTrue(
            "Session subtitle '$subtitleText' must come from time- and library-aware candidates $expectedPool",
            subtitleText in expectedPool
        )
        assertNotEquals("Pick up where you left off.", subtitleText)
        val subtitleLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        subtitleNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(subtitleLayouts)
        assertTrue("Header subtitle must resolve text layout", subtitleLayouts.isNotEmpty())
        assertFalse(
            "Header subtitle '$subtitleText' must never ellipsize on a 360.dp screen",
            subtitleLayouts.first().isLineEllipsized(0) || subtitleLayouts.first().didOverflowWidth
        )

        val searchBounds = composeRule.onNodeWithTag("home_search_button").getUnclippedBoundsInRoot()
        val settingsBounds = composeRule.onNodeWithTag("settings_button").getUnclippedBoundsInRoot()
        assertEquals(44f, searchBounds.width.value, 1.5f)
        assertEquals(44f, searchBounds.height.value, 1.5f)
        assertEquals(44f, settingsBounds.width.value, 1.5f)
        assertEquals(44f, settingsBounds.height.value, 1.5f)

        // 2. Split-Row Editorial Hero Card: compact height (~160-190dp) so Daily Picks stays visible below
        val heroBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()
        assertTrue(
            "Split-Row Hero card must stay compact (~160-195dp, was ${heroBounds.height.value})",
            heroBounds.height.value in 155f..195f
        )
        composeRule.onNodeWithTag("home_daily_picks_header").assertIsDisplayed()

        // Top Row: Title/Artist + HI-FI badge on top-left, 80.dp shaped artwork on top-right
        val artBounds = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals("Top-right shaped artwork must be 80.dp wide", 80f, artBounds.width.value, 1.5f)
        assertEquals("Top-right shaped artwork must be 80.dp tall", 80f, artBounds.height.value, 1.5f)

        composeRule.onNodeWithTag("home_hero_status_badge", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("NOW PLAYING", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PAUSED", useUnmergedTree = true).assertDoesNotExist()

        val hifiBadgeBounds = composeRule.onNodeWithTag("home_hero_hifi_badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertEquals("Compact HI-FI badge pill must be 22.dp tall", 22f, hifiBadgeBounds.height.value, 1.5f)
        composeRule.onNodeWithText("HI-FI", useUnmergedTree = true).assertIsDisplayed()

        val titleBounds = composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val artistBounds = composeRule.onNodeWithTag("home_hero_artist", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertTrue("HI-FI badge must sit above title", hifiBadgeBounds.bottom.value <= titleBounds.top.value + 1f)
        assertTrue("Title must sit above artist", titleBounds.bottom.value <= artistBounds.top.value + 1f)
        assertTrue(
            "Title/Artist must sit strictly to the left of top-right 80.dp artwork without overlap (titleRight=${titleBounds.right.value}, artLeft=${artBounds.left.value})",
            titleBounds.right.value <= artBounds.left.value - 8f && artistBounds.right.value <= artBounds.left.value - 8f
        )
        assertTrue(
            "Top-right artwork must be right-aligned inside hero card",
            heroBounds.right.value - artBounds.right.value <= 24f
        )

        // Bottom Row: Wavy scrubber + timestamps on bottom-left, ONLY 48.dp Play/Pause button on bottom-right
        val waveBounds = composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val playBounds = composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        // Favorite, Next, and Previous buttons must NOT exist on the Hero Card
        composeRule.onNodeWithTag("home_hero_favorite_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_next_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_previous_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_transport_cluster", useUnmergedTree = true).assertDoesNotExist()

        assertEquals("Wavy scrubber must be 24.dp tall", 24f, waveBounds.height.value, 1.5f)
        assertEquals("Play/Pause squircle button must be 48.dp wide", 48f, playBounds.width.value, 1.5f)
        assertEquals("Play/Pause squircle button must be 48.dp tall", 48f, playBounds.height.value, 1.5f)

        // Strict non-overlapping split-row geometry at 360.dp
        assertTrue(
            "Wavy scrubber must sit strictly to the left of Play/Pause button with 16.dp gap (waveRight=${waveBounds.right.value}, playLeft=${playBounds.left.value})",
            waveBounds.right.value <= playBounds.left.value - 12f
        )
        assertTrue(
            "Play/Pause button must sit below top-right artwork without overlap",
            playBounds.top.value >= artBounds.bottom.value + 6f
        )
        assertTrue(
            "Play/Pause button must align inside Hero card right edge",
            heroBounds.right.value - playBounds.right.value <= 24f
        )
        composeRule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertIsDisplayed()

        // Interactive tap-to-seek and drag-to-seek on wavy progress bar do NOT trigger card expand
        composeRule.onNodeWithTag("home_continue_elapsed", useUnmergedTree = true).assertTextContains("1:00")
        composeRule.onNodeWithTag("home_continue_duration", useUnmergedTree = true).assertTextContains("4:00")

        composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).performTouchInput {
            click(Offset(width * 0.75f, height / 2f))
        }
        composeRule.waitForIdle()
        assertEquals(1, soughtFractions.size)
        assertTrue("Tap seek should land near 0.75, was ${soughtFractions.last()}", soughtFractions.last() in 0.62f..0.88f)
        assertEquals("Tap on wavy scrubber must not trigger card expand", 0, expandedCount)

        composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).performTouchInput {
            swipe(
                start = Offset(width * 0.2f, height / 2f),
                end = Offset(width * 0.6f, height / 2f),
                durationMillis = 120
            )
        }
        composeRule.waitForIdle()
        assertEquals(2, soughtFractions.size)
        assertTrue("Drag seek should land near 0.60, was ${soughtFractions.last()}", soughtFractions.last() in 0.45f..0.75f)
        assertEquals("Drag on wavy scrubber must not trigger card expand", 0, expandedCount)

        // Play/Pause button fires toggle without expanding the Hero card
        composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).performClick()
        assertEquals(1, toggleCount)
        assertEquals(0, expandedCount)

        // Tapping Hero card header or shaped artwork opens full Now Playing sheet
        composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).performClick()
        assertEquals(1, expandedCount)
        composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true).performClick()
        assertEquals(2, expandedCount)

        // 3. Your Library 2x2 Bento Grid: 88.dp tiles + 44.dp expressive icon badges + unclipped labels at 360.dp
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_grid"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_your_library_header").assertIsDisplayed().assertTextContains("Your Library")

        listOf(
            "home_shortcut_liked" to "Favorites",
            "home_shortcut_most_played" to "Top Rotation",
            "home_shortcut_recent_added" to "New Adds",
            "home_shortcut_shuffle_all" to "Shuffle All"
        ).forEach { (tag, label) ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals("Bento tile $tag must be 88.dp tall", 88f, bounds.height.value, 2f)
            val iconBounds = composeRule.onNodeWithTag("${tag}_icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("Icon badge in $tag must be 44.dp", 44f, iconBounds.width.value, 1.5f)
            assertEquals("Icon badge in $tag must be 44.dp", 44f, iconBounds.height.value, 1.5f)

            val labelLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            composeRule.onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
                .action?.invoke(labelLayouts)
            assertTrue("Shortcut label '$label' must resolve text layout", labelLayouts.isNotEmpty())
            assertFalse(
                "Shortcut label '$label' must not ellipsize at 360.dp",
                labelLayouts.first().isLineEllipsized(0)
            )
        }

        // 4. Gallery-Grade Recently Played shelf: 140.dp borderless album artwork
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_recently_played_header"))
        composeRule.waitForIdle()
        val recentArtBounds = composeRule.onNodeWithTag("home_recent_art_${hiResTrack.id}", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(140f, recentArtBounds.width.value, 1.5f)
        assertEquals(140f, recentArtBounds.height.value, 1.5f)

        // 5. Made for Your Mood is completely omitted when moodCollections is empty
        composeRule.onNodeWithTag("home_mood_section_header").assertDoesNotExist()
    }

    @Test
    fun `HomeScreen renders Made for Your Mood cards when populated and plays mood collection on click`() {
        var playedMoodTracks: List<Track> = emptyList()
        var playedShuffle: Boolean? = null

        val nostalgicMood = MoodSmartPlaylist(
            mood = SmartMoodTag.LATE_NIGHT,
            title = SmartMoodTag.LATE_NIGHT.label,
            subtitle = "Nocturnal & ambient flow",
            tracks = tracks.toStableList(),
            totalMatchCount = tracks.size
        )

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = tracks.toStableList(),
                            recentlyPlayed = tracks.toStableList(),
                            recentlyAdded = tracks.toStableList(),
                            likedTracks = listOf(hiResTrack).toStableList(),
                            mostPlayedTracks = tracks.toStableList(),
                            moodCollections = listOf(nostalgicMood).toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = hiResTrack,
                        isPlaying = false,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
                        onExpandNowPlaying = {},
                        onTogglePlayPause = {},
                        onSelectTrack = { _, _ -> },
                        onPlayTracks = { list, shuffle ->
                            playedMoodTracks = list
                            playedShuffle = shuffle
                        },
                        onToggleFavorite = {},
                        onOpenSettings = {},
                        onAddFolder = {},
                        onScanDevice = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_mood_row"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("home_mood_section_header").assertIsDisplayed().assertTextContains("Made for Your Mood")
        composeRule.onNodeWithText("Nostalgic Nights", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Melodies that stay with you", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("home_mood_card_${SmartMoodTag.LATE_NIGHT.id}").performClick()
        assertEquals(2, playedMoodTracks.size)
        assertEquals(false, playedShuffle)
    }

    @Test
    fun `all inline CadenceIcons build valid non-empty 24dp ImageVectors`() {
        val allIcons = listOf(
            CadenceIcons.Pause,
            CadenceIcons.SkipNext,
            CadenceIcons.SkipPrevious,
            CadenceIcons.Shuffle,
            CadenceIcons.Repeat,
            CadenceIcons.RepeatOne,
            CadenceIcons.Speed,
            CadenceIcons.Bedtime,
            CadenceIcons.QueueMusic,
            CadenceIcons.PlaylistAdd,
            CadenceIcons.PlaylistPlay,
            CadenceIcons.QueuePlayNext,
            CadenceIcons.LibraryMusic,
            CadenceIcons.LibraryMusicOutlined,
            CadenceIcons.LibraryAdd,
            CadenceIcons.MusicNote,
            CadenceIcons.AudioFile,
            CadenceIcons.Sort,
            CadenceIcons.CreateNewFolder,
            CadenceIcons.FolderOpen,
            CadenceIcons.DeleteOutline,
            CadenceIcons.DeleteSweep,
            CadenceIcons.Save,
            CadenceIcons.BluetoothAudio,
            CadenceIcons.Headphones,
            CadenceIcons.Speaker,
            CadenceIcons.SpeakerGroup,
            CadenceIcons.Usb,
            CadenceIcons.Lyrics,
            CadenceIcons.GraphicEq,
            CadenceIcons.HighQuality,
            CadenceIcons.AutoAwesome,
            CadenceIcons.AutoAwesomeOutlined,
            CadenceIcons.Bolt,
            CadenceIcons.Explore,
            CadenceIcons.History,
            CadenceIcons.Insights,
            CadenceIcons.LocalFireDepartment,
            CadenceIcons.NightsStay,
            CadenceIcons.Schedule,
            CadenceIcons.Spa,
            CadenceIcons.Tune,
            CadenceIcons.Waves,
            CadenceIcons.Code,
            CadenceIcons.PersonOutline,
            CadenceIcons.BrightnessAuto,
            CadenceIcons.DarkMode,
            CadenceIcons.LightMode
        )
        assertEquals(48, allIcons.size)
        allIcons.forEach { vec ->
            assertEquals(24f, vec.viewportWidth, 0.01f)
            assertEquals(24f, vec.viewportHeight, 0.01f)
            assertTrue("Icon ${vec.name} must contain at least one vector path", vec.root.size > 0)
        }
    }

    @Test
    fun `HomeScreen hero Your Library and Mood cards dynamically adapt to MaterialTheme colorScheme across Light and Dark palettes`() {
        var cobaltLightScheme: ColorScheme? = null
        var matchaDarkScheme: ColorScheme? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.LIGHT) {
                cobaltLightScheme = MaterialTheme.colorScheme
            }
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                matchaDarkScheme = MaterialTheme.colorScheme
            }
        }
        composeRule.waitForIdle()

        val light = requireNotNull(cobaltLightScheme)
        val dark = requireNotNull(matchaDarkScheme)

        val lightHero = resolveHomeHeroColors(light)
        val darkHero = resolveHomeHeroColors(dark)

        // Hero card uses expressive theme-tinted primaryContainer.opaqueOver(surfaceContainer, 0.72f) with 0.22f primary border
        val expectedLightSurface = light.primaryContainer.opaqueOver(light.surfaceContainer, 0.72f)
        val expectedDarkSurface = dark.primaryContainer.opaqueOver(dark.surfaceContainer, 0.72f)
        assertEquals(expectedLightSurface, lightHero.cardSurface)
        assertEquals(expectedDarkSurface, darkHero.cardSurface)
        assertNotEquals("Hero surface must not be flat neutral grey surfaceContainerHigh", dark.surfaceContainerHigh, darkHero.cardSurface)
        assertEquals(1f, lightHero.cardSurface.alpha, 0.001f)
        assertEquals(1f, darkHero.cardSurface.alpha, 0.001f)
        assertEquals(light.primary.copy(alpha = 0.22f), lightHero.cardBorder)
        assertEquals(light.onPrimaryContainer.opaqueOver(expectedLightSurface, 0.14f), lightHero.hifiPillContainer)
        assertEquals(1f, lightHero.hifiPillContainer.alpha, 0.001f)
        assertEquals(light.onPrimaryContainer, lightHero.hifiPillText)
        assertEquals(light.onPrimaryContainer, lightHero.titleColor)
        assertEquals(light.onPrimaryContainer.copy(alpha = 0.78f), lightHero.artistColor)
        assertEquals(light.onPrimaryContainer.copy(alpha = 0.78f), lightHero.timestampColor)
        assertEquals(light.primary, lightHero.playButtonContainer)
        assertEquals(light.onPrimary, lightHero.playButtonContent)
        assertEquals(light.primary, lightHero.wavePlayedColor)
        assertEquals(light.onPrimaryContainer.copy(alpha = 0.24f), lightHero.waveUnplayedColor)

        assertNotEquals("Hero surface must adapt between Light Cobalt and Dark Matcha", lightHero.cardSurface, darkHero.cardSurface)
        assertNotEquals("Hero play button must adapt across themes", lightHero.playButtonContainer, darkHero.playButtonContainer)
        assertNotEquals("Hero title color must adapt across Light and Dark", lightHero.titleColor, darkHero.titleColor)

        // Your Library 2x2 grid cards map cleanly to calm low-glare tonal surfaces with vibrant 44.dp icon badges
        val lightGrid = resolveHomeLibraryGridColors(light)
        val darkGrid = resolveHomeLibraryGridColors(dark)

        assertEquals(light.primary.opaqueOver(light.surfaceContainerHigh, 0.12f), lightGrid.favorites.topColor)
        assertEquals(light.primary, lightGrid.favorites.iconContainerColor)
        assertEquals(light.onPrimary, lightGrid.favorites.iconTint)
        assertEquals(light.onSurface, lightGrid.favorites.titleColor)
        assertEquals(light.onSurfaceVariant, lightGrid.favorites.subtitleColor)

        assertEquals(light.tertiary.opaqueOver(light.surfaceContainerHigh, 0.12f), lightGrid.topRotation.topColor)
        assertEquals(light.tertiary, lightGrid.topRotation.iconContainerColor)
        assertEquals(light.onTertiary, lightGrid.topRotation.iconTint)
        assertEquals(light.onSurface, lightGrid.topRotation.titleColor)
        assertEquals(light.onSurfaceVariant, lightGrid.topRotation.subtitleColor)

        assertEquals(light.secondary.opaqueOver(light.surfaceContainerHigh, 0.12f), lightGrid.newAdds.topColor)
        assertEquals(light.secondary, lightGrid.newAdds.iconContainerColor)
        assertEquals(light.onSecondary, lightGrid.newAdds.iconTint)
        assertEquals(light.onSurface, lightGrid.newAdds.titleColor)
        assertEquals(light.onSurfaceVariant, lightGrid.newAdds.subtitleColor)

        assertEquals(light.primary.opaqueOver(lightGrid.shuffleAll.topColor, 0.16f), lightGrid.shuffleAll.iconContainerColor)
        assertEquals(light.primary, lightGrid.shuffleAll.iconTint)
        assertEquals(light.onSurface, lightGrid.shuffleAll.titleColor)
        assertEquals(light.onSurfaceVariant, lightGrid.shuffleAll.subtitleColor)

        listOf(lightGrid.favorites, lightGrid.topRotation, lightGrid.newAdds, lightGrid.shuffleAll).forEach { card ->
            assertEquals("Library card top surface must be pre-blended opaque", 1f, card.topColor.alpha, 0.001f)
            assertEquals("Library card bottom surface must be pre-blended opaque", 1f, card.bottomColor.alpha, 0.001f)
        }
        assertEquals(
            "All 4 Your Library cards should have distinct surface colors",
            4,
            setOf(
                lightGrid.favorites.topColor,
                lightGrid.topRotation.topColor,
                lightGrid.newAdds.topColor,
                lightGrid.shuffleAll.topColor
            ).size
        )
        assertNotEquals(lightGrid.favorites.topColor, darkGrid.favorites.topColor)
        assertNotEquals(lightGrid.topRotation.topColor, darkGrid.topRotation.topColor)
        assertNotEquals(lightGrid.newAdds.topColor, darkGrid.newAdds.topColor)
        assertNotEquals(lightGrid.shuffleAll.topColor, darkGrid.shuffleAll.topColor)

        // Mood cards also adapt to theme without hardcoded hex values
        val moodPlaylist = MoodSmartPlaylist(
            mood = SmartMoodTag.ACOUSTIC,
            title = SmartMoodTag.ACOUSTIC.label,
            subtitle = "Warm strings",
            tracks = tracks.toStableList(),
            totalMatchCount = tracks.size
        )
        val lightMood = resolveHomeMoodDisplay(moodPlaylist, light)
        val darkMood = resolveHomeMoodDisplay(moodPlaylist, dark)
        assertNotEquals(lightMood.topColor, darkMood.topColor)
        assertEquals(light.onTertiaryContainer, lightMood.primaryText)
        assertEquals(dark.onTertiaryContainer, darkMood.primaryText)
    }

    @Test
    fun `HomeScreen renders cleanly in Light mode with Cobalt palette across Hero Your Library and Mood cards`() {
        val mood = MoodSmartPlaylist(
            mood = SmartMoodTag.SOULFUL,
            title = SmartMoodTag.SOULFUL.label,
            subtitle = "Rich vocals",
            tracks = tracks.toStableList(),
            totalMatchCount = tracks.size
        )

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.COBALT, appearanceMode = AppearanceMode.LIGHT) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = tracks.toStableList(),
                            recentlyPlayed = tracks.toStableList(),
                            recentlyAdded = tracks.toStableList(),
                            likedTracks = listOf(hiResTrack).toStableList(),
                            mostPlayedTracks = tracks.toStableList(),
                            moodCollections = listOf(mood).toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = hiResTrack,
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

        composeRule.onNodeWithTag("home_continue_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_grid"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_shortcut_liked").assertIsDisplayed()
        composeRule.onNodeWithTag("home_shortcut_most_played").assertIsDisplayed()
        composeRule.onNodeWithTag("home_shortcut_recent_added").assertIsDisplayed()
        composeRule.onNodeWithTag("home_shortcut_shuffle_all").assertIsDisplayed()

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_mood_row"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_mood_card_${SmartMoodTag.SOULFUL.id}").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen Split-Row Hero card hides HI-FI pill for lossy tracks toggles Play Pause cleanly and keeps zero overlap at 360dp`() {
        val sampleTrack = acousticTrack.copy(
            title = "Velvet Horizon",
            artist = "Klara Lind & Quartet"
        )
        var playingState by mutableStateOf(true)
        var toggleCalls = 0
        var expandCalls = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = listOf(sampleTrack, hiResTrack).toStableList(),
                            recentlyPlayed = listOf(sampleTrack).toStableList(),
                            recentlyAdded = listOf(sampleTrack).toStableList(),
                            likedTracks = listOf(sampleTrack).toStableList(),
                            mostPlayedTracks = listOf(sampleTrack).toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = sampleTrack,
                        isPlaying = playingState,
                        artworkShape = ExpressiveArtworkShape.CLOVER,
                        onExpandNowPlaying = { expandCalls++ },
                        onTogglePlayPause = {
                            toggleCalls++
                            playingState = !playingState
                        },
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

        // 1. Lossy MP3 track omits the HI-FI badge pill cleanly while keeping unclipped Title & Artist on top-left
        composeRule.onNodeWithTag("home_hero_hifi_badge", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("NOW PLAYING", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PAUSED", useUnmergedTree = true).assertDoesNotExist()

        val titleBounds = composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val artistNode = composeRule.onNodeWithTag("home_hero_artist", useUnmergedTree = true).assertIsDisplayed()
        val artistBounds = artistNode.getUnclippedBoundsInRoot()
        val artBounds = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val playBounds = composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val waveBounds = composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()

        assertTrue("Lossy Hero card must remain compact (~155-195dp, was ${cardBounds.height.value})", cardBounds.height.value in 155f..195f)
        assertEquals(80f, artBounds.width.value, 1.5f)
        assertEquals(80f, artBounds.height.value, 1.5f)
        assertEquals(48f, playBounds.width.value, 1.5f)
        assertEquals(48f, playBounds.height.value, 1.5f)
        composeRule.onNodeWithTag("home_hero_favorite_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_next_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_previous_button", useUnmergedTree = true).assertDoesNotExist()
        assertTrue("Title must stay left of top-right artwork", titleBounds.right.value <= artBounds.left.value - 8f)
        assertTrue("Artist must stay left of top-right artwork", artistBounds.right.value <= artBounds.left.value - 8f)
        assertTrue("Wavy bar must stay left of Play/Pause button", waveBounds.right.value <= playBounds.left.value - 12f)

        val artistLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        artistNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(artistLayouts)
        assertTrue("Artist subtitle must resolve text layout", artistLayouts.isNotEmpty())
        assertEquals(1, artistLayouts.first().lineCount)
        assertFalse(
            "Artist subtitle 'Klara Lind & Quartet' must fit cleanly at 360.dp",
            artistLayouts.first().isLineEllipsized(0) || artistLayouts.first().didOverflowWidth
        )

        // 2. Displays Pause while playing, toggles cleanly to Play on click without expanding card
        composeRule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleCalls)
        assertEquals("Tapping Play/Pause button must not trigger card expand", 0, expandCalls)
        composeRule.onNodeWithText("PAUSED", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `HomeScreen final mood section scrolls clear above the floating Mini-Player and navigation dock`() {
        val mood = MoodSmartPlaylist(
            mood = SmartMoodTag.LATE_NIGHT,
            title = SmartMoodTag.LATE_NIGHT.label,
            subtitle = "Nocturnal & ambient flow",
            tracks = tracks.toStableList(),
            totalMatchCount = tracks.size
        )

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = tracks.toStableList(),
                            recentlyPlayed = tracks.toStableList(),
                            recentlyAdded = tracks.toStableList(),
                            likedTracks = listOf(hiResTrack).toStableList(),
                            mostPlayedTracks = tracks.toStableList(),
                            moodCollections = listOf(mood).toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = hiResTrack,
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

        // Scroll all the way to the bottom of the Home feed
        composeRule.onNodeWithTag("home_list").performTouchInput {
            swipe(
                start = Offset(width / 2f, height * 0.85f),
                end = Offset(width / 2f, height * 0.10f),
                durationMillis = 150
            )
            swipe(
                start = Offset(width / 2f, height * 0.85f),
                end = Offset(width / 2f, height * 0.10f),
                durationMillis = 150
            )
        }
        composeRule.waitForIdle()

        val moodRowBounds = composeRule.onNodeWithTag("home_mood_row").getUnclippedBoundsInRoot()
        assertTrue(
            "Final mood row bottom (${moodRowBounds.bottom.value}dp) must clear the 168.dp floating Mini-Player + nav dock on an 800.dp viewport (<= 640.dp)",
            moodRowBounds.bottom.value <= 640f
        )
    }

    @Test
    fun `Recently Played shelf enforces 140dp item width 12dp spacing and unclipped ascenders for title and artist`() {
        val longTitleTallAscenderTrack = hiResTrack.copy(
            id = "rp_tall_1",
            title = "Interlude: Jumping Over Tall Horizons (Extended Studio Mix)",
            artist = "Furkan Şimşek & Istanbul Jazz Collective"
        )
        val neighborTrack = acousticTrack.copy(
            id = "rp_tall_2",
            title = "Fjord Echoes",
            artist = "Şebnem Ferah & Friends"
        )
        val shelfTracks = listOf(longTitleTallAscenderTrack, neighborTrack)

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.MATCHA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = shelfTracks.toStableList(),
                            recentlyPlayed = shelfTracks.toStableList(),
                            recentlyAdded = shelfTracks.toStableList(),
                            likedTracks = listOf(longTitleTallAscenderTrack).toStableList(),
                            mostPlayedTracks = shelfTracks.toStableList(),
                            moodCollections = emptyList<MoodSmartPlaylist>().toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = longTitleTallAscenderTrack,
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
            .performScrollToNode(hasTestTag("home_recent_row"))
        composeRule.waitForIdle()

        val firstTileBounds = composeRule.onNodeWithTag("home_recent_tile_rp_tall_1").getUnclippedBoundsInRoot()
        val secondTileBounds = composeRule.onNodeWithTag("home_recent_tile_rp_tall_2").getUnclippedBoundsInRoot()
        val firstArtBounds = composeRule.onNodeWithTag("home_recent_art_rp_tall_1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val firstTitleNode = composeRule.onNodeWithTag("home_recent_title_rp_tall_1", useUnmergedTree = true).assertIsDisplayed()
        val firstArtistNode = composeRule.onNodeWithTag("home_recent_artist_rp_tall_1", useUnmergedTree = true).assertIsDisplayed()
        val firstTitleBounds = firstTitleNode.getUnclippedBoundsInRoot()
        val firstArtistBounds = firstArtistNode.getUnclippedBoundsInRoot()

        // 1. Item width locked strictly to 140.dp and 12.dp horizontal gap between neighboring tiles
        assertEquals("Recently Played tile width must be locked to 140.dp", 140f, firstTileBounds.width.value, 1.5f)
        assertEquals("Neighboring Recently Played tile width must be locked to 140.dp", 140f, secondTileBounds.width.value, 1.5f)
        assertEquals(
            "Horizontal gap between Recently Played tiles must be 12.dp",
            12f,
            secondTileBounds.left.value - firstTileBounds.right.value,
            1.5f
        )

        // 2. Title and artist stay strictly inside their own 140.dp column without touching neighbor
        assertTrue(
            "Title right (${firstTitleBounds.right}) must stay inside 140.dp tile right (${firstTileBounds.right})",
            firstTitleBounds.right.value <= firstTileBounds.right.value + 0.5f
        )
        assertTrue(
            "Artist right (${firstArtistBounds.right}) must stay inside 140.dp tile right (${firstTileBounds.right})",
            firstArtistBounds.right.value <= firstTileBounds.right.value + 0.5f
        )
        assertTrue(
            "Artist right (${firstArtistBounds.right}) must remain 12.dp clear of neighboring tile left (${secondTileBounds.left})",
            secondTileBounds.left.value - firstArtistBounds.right.value >= 10.5f
        )

        // 3. Clean vertical stacking: 8.dp below artwork, 2.dp between title and artist
        assertEquals(
            "Gap below artwork to title must be 8.dp",
            8f,
            firstTitleBounds.top.value - firstArtBounds.bottom.value,
            1.5f
        )
        assertEquals(
            "Gap between title and artist must be 2.dp",
            2f,
            firstArtistBounds.top.value - firstTitleBounds.bottom.value,
            1.5f
        )

        // 4. Zero vertical glyph clipping on title and tall-ascender artist subtitle (F, I, J, Ş)
        val titleLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        firstTitleNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(titleLayouts)
        assertTrue("Title must resolve text layout", titleLayouts.isNotEmpty())
        val titleLayout = titleLayouts.first()
        assertEquals(1, titleLayout.lineCount)
        assertTrue(
            "Title line top (${titleLayout.getLineTop(0)}) must not be clipped above 0",
            titleLayout.getLineTop(0) >= 0f
        )
        assertTrue(
            "Title line bottom (${titleLayout.getLineBottom(0)}) must fit inside measured height (${titleLayout.size.height})",
            titleLayout.getLineBottom(0) <= titleLayout.size.height + 0.5f
        )
        assertTrue(
            "Title measured height (${titleLayout.size.height}) must fit full paragraph height (${titleLayout.multiParagraph.height})",
            titleLayout.multiParagraph.height <= titleLayout.size.height + 0.5f
        )
        assertTrue("Long title should ellipsize cleanly inside 140.dp", titleLayout.isLineEllipsized(0))

        val artistLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        firstArtistNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(artistLayouts)
        assertTrue("Artist must resolve text layout", artistLayouts.isNotEmpty())
        val artistLayout = artistLayouts.first()
        assertEquals(1, artistLayout.lineCount)
        assertTrue(
            "Artist line top (${artistLayout.getLineTop(0)}) must not be clipped above 0",
            artistLayout.getLineTop(0) >= 0f
        )
        assertTrue(
            "Artist line bottom (${artistLayout.getLineBottom(0)}) must fit inside measured height (${artistLayout.size.height})",
            artistLayout.getLineBottom(0) <= artistLayout.size.height + 0.5f
        )
        assertTrue(
            "Artist measured height (${artistLayout.size.height}) must fit full paragraph height (${artistLayout.multiParagraph.height})",
            artistLayout.multiParagraph.height <= artistLayout.size.height + 0.5f
        )
        assertTrue(
            "Artist line height (${firstArtistBounds.height.value}dp) must allow unclipped ascenders (>= 16.dp)",
            firstArtistBounds.height.value >= 16f
        )
        assertTrue("Long artist should ellipsize cleanly inside 140.dp", artistLayout.isLineEllipsized(0))
    }

    @Test
    fun `formatHomeGreeting covers all 24 hours without midnight Good evening bug and formatHomeSubtitle rotates context-aware lines`() {
        // Explicitly assert midnight (00:04 -> hour = 0) and 01:00–04:59 NEVER say "Good evening!"
        for (hour in 0..4) {
            val bare = formatHomeGreeting(hourOfDay = hour)
            assertEquals("Hour $hour must use late night greeting", "Late night vibes", bare)
            assertFalse("Hour $hour must NEVER return Good evening!", bare.contains("evening", ignoreCase = true))
        }
        for (hour in 5..11) {
            assertEquals("Good morning!", formatHomeGreeting(hourOfDay = hour))
        }
        for (hour in 12..16) {
            assertEquals("Good afternoon!", formatHomeGreeting(hourOfDay = hour))
        }
        for (hour in 17..20) {
            assertEquals("Good evening!", formatHomeGreeting(hourOfDay = hour))
        }
        for (hour in 21..23) {
            assertEquals("Good night!", formatHomeGreeting(hourOfDay = hour))
        }

        // Dynamic context-aware subheadings: deterministic per sessionSeed/hour, library-aware, never static
        val midnightA = formatHomeSubtitle(hourOfDay = 0, trackCount = 42, hasLosslessTracks = true, sessionSeed = 7)
        val midnightB = formatHomeSubtitle(hourOfDay = 0, trackCount = 42, hasLosslessTracks = true, sessionSeed = 7)
        assertEquals("Same session seed and hour must not flicker", midnightA, midnightB)

        val lateNightPool = homeSubtitleCandidates(hourOfDay = 0, trackCount = 42, hasLosslessTracks = true)
        assertTrue(lateNightPool.contains("Quiet tracks for midnight"))
        assertTrue(lateNightPool.contains("Low-light listening"))
        assertTrue(lateNightPool.contains("Your late-night rotation"))

        val morningPool = homeSubtitleCandidates(hourOfDay = 8, trackCount = 42, hasLosslessTracks = false)
        assertTrue(morningPool.contains("Set your morning tempo"))
        assertTrue(morningPool.contains("Fresh tracks for today"))
        assertTrue(morningPool.contains("42 tracks ready today"))

        val afternoonPool = homeSubtitleCandidates(hourOfDay = 14, trackCount = 42, hasLosslessTracks = true)
        assertTrue(afternoonPool.contains("42 offline tracks ready"))
        assertTrue(afternoonPool.contains("Your lossless collection"))
        assertTrue(afternoonPool.contains("From your local library"))

        val emptyLibraryAfternoon = homeSubtitleCandidates(hourOfDay = 14, trackCount = 0, hasLosslessTracks = false)
        assertTrue(
            "Empty library must not claim '0 offline tracks'",
            emptyLibraryAfternoon.none { it.startsWith("0 offline") }
        )

        for (hour in 0..23) {
            val subtitle = formatHomeSubtitle(hourOfDay = hour, trackCount = 24, hasLosslessTracks = true, sessionSeed = 11)
            assertTrue("Subtitle for hour $hour must be non-blank", subtitle.isNotBlank())
            assertTrue("Subtitle '$subtitle' must stay concise (<= 28 chars) so it never ellipsizes on 360.dp", subtitle.length <= 28)
            assertFalse("Subtitle for hour $hour must not use old static prompt", subtitle.contains("Pick up where you left off"))
        }

        // Greeting accent styling highlights only the final word in primary color
        val base = Color.White
        val accent = Color.Red
        val lateNightStyled = formatHighlightedHomeGreeting("Late night vibes", base, accent)
        assertEquals("Late night vibes", lateNightStyled.text)
        assertEquals(2, lateNightStyled.spanStyles.size)
        assertEquals(base, lateNightStyled.spanStyles[0].item.color)
        assertEquals("Late night ", lateNightStyled.text.substring(lateNightStyled.spanStyles[0].start, lateNightStyled.spanStyles[0].end))
        assertEquals(accent, lateNightStyled.spanStyles[1].item.color)
        assertEquals("vibes", lateNightStyled.text.substring(lateNightStyled.spanStyles[1].start, lateNightStyled.spanStyles[1].end))

        val morningStyled = formatHighlightedHomeGreeting("Good morning!", base, accent)
        assertEquals("morning!", morningStyled.text.substring(morningStyled.spanStyles[1].start, morningStyled.spanStyles[1].end))
        assertEquals(accent, morningStyled.spanStyles[1].item.color)

        // ScallopedCookieShape(lobes = 8) morphs cleanly between circle (0f) and cached 8-lobe cookie (1f)
        val density = Density(2f)
        val buttonSize = Size(104f, 104f)
        val pausedShape = ScallopedCookieShape(lobes = 8, lobeDepth = 0f)
        val playingShape = ScallopedCookieShape(lobes = 8, lobeDepth = 1f)
        assertTrue(
            "Paused ScallopedCookieShape (lobeDepth = 0f) must produce a clean circular Outline.Rounded",
            pausedShape.createOutline(buttonSize, LayoutDirection.Ltr, density) is Outline.Rounded
        )
        val firstScallopOutline = playingShape.createOutline(buttonSize, LayoutDirection.Ltr, density)
        val cachedScallopOutline = playingShape.createOutline(buttonSize, LayoutDirection.Ltr, density)
        assertTrue(
            "Playing ScallopedCookieShape (lobeDepth = 1f) must produce an 8-lobe Outline.Generic",
            firstScallopOutline is Outline.Generic
        )
        assertTrue(
            "ScallopedCookieShape must cache Outline per Size for zero-allocation redraws",
            firstScallopOutline === cachedScallopOutline
        )
    }
}
