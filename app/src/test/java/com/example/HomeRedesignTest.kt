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
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HomeSessionPrompts
import com.example.ui.screens.formatHomeGreeting
import com.example.ui.screens.resolveHomeHeroColors
import com.example.ui.screens.resolveHomeLibraryGridColors
import com.example.ui.screens.resolveHomeMoodDisplay
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
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
    fun `HomeScreen header hero card 310dp single play button interactive wavy seek and Your Library 156dp grid match spec`() {
        assertEquals("Good morning!", formatHomeGreeting("", hourOfDay = 7))
        assertEquals("Good afternoon!", formatHomeGreeting("", hourOfDay = 15))
        assertEquals("Good evening!", formatHomeGreeting("", hourOfDay = 20))
        assertEquals("Good evening!", formatHomeGreeting("", hourOfDay = 1))

        val progressFlow = MutableStateFlow(PlaybackProgress(positionMs = 60_000L, durationMs = 240_000L))
        var expandedCount = 0
        var toggleCount = 0
        val soughtFractions = mutableListOf<Float>()

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = tracks.toStableList(),
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

        // 1. Compact Header: Greeting + session subtitle + 42.dp circular Search & Settings buttons
        composeRule.onNodeWithTag("home_header_title").assertIsDisplayed()
        val subtitleNode = composeRule.onNodeWithTag("home_header_subtitle").assertIsDisplayed()
        val subtitleText = subtitleNode.fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .joinToString("") { it.text }
        assertTrue(
            "Session subtitle '$subtitleText' must come from HomeSessionPrompts",
            subtitleText in HomeSessionPrompts
        )

        val searchBounds = composeRule.onNodeWithTag("home_search_button").getUnclippedBoundsInRoot()
        val settingsBounds = composeRule.onNodeWithTag("settings_button").getUnclippedBoundsInRoot()
        assertEquals(42f, searchBounds.width.value, 1.5f)
        assertEquals(42f, searchBounds.height.value, 1.5f)
        assertEquals(42f, settingsBounds.width.value, 1.5f)
        assertEquals(42f, settingsBounds.height.value, 1.5f)

        // 2. Main Now Playing Hero Card: 310.dp height, 108.dp artwork, HI-RES badge, ONLY ONE Play/Pause button
        val heroBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()
        assertEquals(310f, heroBounds.height.value, 2f)

        val artBounds = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(108f, artBounds.width.value, 1.5f)
        assertEquals(108f, artBounds.height.value, 1.5f)

        composeRule.onNodeWithTag("home_hero_hifi_badge", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("HI-RES", useUnmergedTree = true)
            .assertIsDisplayed()

        val playBounds = composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val waveBounds = composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("Stadium Play/Pause pill must be 42.dp tall", 42f, playBounds.height.value, 1.5f)
        assertTrue("Stadium pill must be wider than tall to fit icon + label", playBounds.width.value > playBounds.height.value)
        assertTrue("Stadium pill must sit above the wavy progress bar", playBounds.bottom.value <= waveBounds.top.value + 1f)
        composeRule.onNodeWithTag("home_continue_play_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Pause")

        // Assert strictly ZERO Next/Previous/Shuffle/Repeat controls inside the Hero card
        composeRule.onNodeWithContentDescription("Next track", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Previous track", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Toggle shuffle", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cycle repeat mode", useUnmergedTree = true).assertDoesNotExist()

        // Interactive tap-to-seek and drag-to-seek on wavy progress bar do NOT trigger card expand
        composeRule.onNodeWithTag("home_continue_elapsed", useUnmergedTree = true).assertTextContains("1:00")
        composeRule.onNodeWithTag("home_continue_duration", useUnmergedTree = true).assertTextContains("4:00")

        composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).performTouchInput {
            click(Offset(width * 0.75f, height / 2f))
        }
        composeRule.waitForIdle()
        assertEquals(1, soughtFractions.size)
        assertTrue("Tap seek should land near 0.75, was ${soughtFractions.last()}", soughtFractions.last() in 0.65f..0.85f)
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
        assertTrue("Drag seek should land near 0.60, was ${soughtFractions.last()}", soughtFractions.last() in 0.48f..0.72f)
        assertEquals("Drag on wavy scrubber must not trigger card expand", 0, expandedCount)

        // Single Play/Pause click toggles playback without expanding card
        composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).performClick()
        assertEquals(1, toggleCount)
        assertEquals(0, expandedCount)

        // Tapping Hero card header opens full Now Playing sheet
        composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true).performClick()
        assertEquals(1, expandedCount)

        // 3. Your Library 2x2 Grid: 156.dp tall cards + 48.dp icon containers
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_grid"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_your_library_header").assertIsDisplayed().assertTextContains("Your Library")

        listOf(
            "home_shortcut_liked",
            "home_shortcut_most_played",
            "home_shortcut_recent_added",
            "home_shortcut_shuffle_all"
        ).forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals("Card $tag must be 156.dp tall", 156f, bounds.height.value, 2f)
            val iconBounds = composeRule.onNodeWithTag("${tag}_icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("Icon badge in $tag must be 48.dp", 48f, iconBounds.width.value, 1.5f)
            assertEquals("Icon badge in $tag must be 48.dp", 48f, iconBounds.height.value, 1.5f)
        }

        // 4. Recently Played borderless shelf: 132.dp square artwork
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_recently_played_header"))
        composeRule.waitForIdle()
        val recentArtBounds = composeRule.onNodeWithTag("home_recent_art_${hiResTrack.id}", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(132f, recentArtBounds.width.value, 1.5f)
        assertEquals(132f, recentArtBounds.height.value, 1.5f)

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
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.DARK) {
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

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_mood_section_header"))
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

        // Hero card derives directly from MaterialTheme.colorScheme roles with opaque pre-blended surfaces
        assertEquals(light.primaryContainer.opaqueOver(light.surfaceContainerHigh, 0.94f), lightHero.topColor)
        assertEquals(light.onPrimaryContainer, lightHero.titleColor)
        assertEquals(light.onPrimaryContainer, lightHero.timestampColor)
        assertEquals(light.primary, lightHero.playButtonContainer)
        assertEquals(light.onPrimary, lightHero.playButtonContent)
        assertEquals(light.primary, lightHero.wavePlayedColor)
        assertEquals(light.surface.opaqueOver(lightHero.topColor, 0.28f), lightHero.pillContainer)
        assertEquals(light.onPrimaryContainer.opaqueOver(lightHero.bottomColor, 0.24f), lightHero.waveUnplayedColor)
        assertEquals(1f, lightHero.topColor.alpha, 0.001f)
        assertEquals(1f, lightHero.bottomColor.alpha, 0.001f)
        assertEquals(1f, lightHero.pillContainer.alpha, 0.001f)
        assertEquals(1f, lightHero.waveUnplayedColor.alpha, 0.001f)

        assertNotEquals("Hero surface must adapt between Light Cobalt and Dark Matcha", lightHero.topColor, darkHero.topColor)
        assertNotEquals("Hero play button must adapt across themes", lightHero.playButtonContainer, darkHero.playButtonContainer)
        assertNotEquals("Hero title color must adapt across Light and Dark", lightHero.titleColor, darkHero.titleColor)

        // Your Library 2x2 grid cards map cleanly to distinct M3 tonal container roles
        val lightGrid = resolveHomeLibraryGridColors(light)
        val darkGrid = resolveHomeLibraryGridColors(dark)

        assertEquals(light.primary, lightGrid.favorites.iconContainerColor)
        assertEquals(light.onPrimary, lightGrid.favorites.iconTint)
        assertEquals(light.onPrimaryContainer, lightGrid.favorites.titleColor)

        assertEquals(light.tertiary, lightGrid.topRotation.iconContainerColor)
        assertEquals(light.onTertiary, lightGrid.topRotation.iconTint)
        assertEquals(light.onTertiaryContainer, lightGrid.topRotation.titleColor)

        assertEquals(light.secondary, lightGrid.newAdds.iconContainerColor)
        assertEquals(light.onSecondary, lightGrid.newAdds.iconTint)
        assertEquals(light.onSecondaryContainer, lightGrid.newAdds.titleColor)

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

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_mood_section_header"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_mood_card_${SmartMoodTag.SOULFUL.id}").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen Hero card gives full width to artist subtitle without truncation and toggles Stadium Play Pause pill above wavy bar at 360dp`() {
        val longArtistTrack = hiResTrack.copy(
            title = "Velvet Horizon",
            artist = "Klara Lind & Quartet"
        )
        var playingState by mutableStateOf(true)
        var toggleCalls = 0
        var expandCalls = 0

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.DARK) {
                Box(modifier = Modifier.width(360.dp).height(800.dp)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            allTracks = listOf(longArtistTrack, acousticTrack).toStableList(),
                            recentlyPlayed = listOf(longArtistTrack).toStableList(),
                            recentlyAdded = listOf(longArtistTrack).toStableList(),
                            likedTracks = listOf(longArtistTrack).toStableList(),
                            mostPlayedTracks = listOf(longArtistTrack).toStableList(),
                            isInitialLibraryLoaded = true
                        ),
                        currentTrack = longArtistTrack,
                        isPlaying = playingState,
                        artworkShape = ExpressiveArtworkShape.SCALLOP,
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

        // 1. Artist subtitle gets full remaining width (~162.dp on 360.dp screen) without horizontal truncation
        val artistNode = composeRule.onNodeWithTag("home_hero_artist", useUnmergedTree = true).assertIsDisplayed()
        val artistBounds = artistNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Artist subtitle should receive full remaining width (~162.dp) at 360.dp, was ${artistBounds.width.value}",
            artistBounds.width.value >= 155f
        )
        val artistLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        artistNode.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(artistLayouts)
        assertTrue("Artist subtitle must resolve text layout", artistLayouts.isNotEmpty())
        val artistLayout = artistLayouts.first()
        assertEquals(1, artistLayout.lineCount)
        assertFalse(
            "Artist subtitle 'Klara Lind & Quartet' must not ellipsize at 360.dp (width=${artistLayout.size.width}, didOverflowWidth=${artistLayout.didOverflowWidth})",
            artistLayout.isLineEllipsized(0) || artistLayout.didOverflowWidth
        )

        val artBounds = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val playBounds = composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val waveBounds = composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()

        // 2. Stadium Play/Pause pill sits bottom-right above the wavy progress bar and below the artwork row
        assertEquals(42f, playBounds.height.value, 1.5f)
        assertTrue("Stadium pill must sit below the artwork row", playBounds.top.value >= artBounds.bottom.value - 1f)
        assertTrue("Stadium pill must sit right above the wavy progress bar", playBounds.bottom.value <= waveBounds.top.value + 1f)
        assertTrue(
            "Stadium pill must align to the trailing end of the hero card",
            playBounds.right.value >= cardBounds.right.value - 24f
        )

        // 3. Displays "Pause" while playing, toggles cleanly to "Play" on click without expanding card
        composeRule.onNodeWithTag("home_continue_play_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Pause")

        composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleCalls)
        assertEquals("Tapping Stadium Play/Pause pill must not trigger card expand", 0, expandCalls)
        composeRule.onNodeWithTag("home_continue_play_label", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Play")
    }
}
