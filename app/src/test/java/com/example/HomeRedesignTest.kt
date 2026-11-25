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
import com.example.ui.screens.currentLocalHourOfDay
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
        assertEquals("Late night vibes", formatHomeGreeting("", hourOfDay = 0))
        assertEquals("Late night vibes", formatHomeGreeting("", hourOfDay = 1))
        assertEquals("Good morning!", formatHomeGreeting("", hourOfDay = 7))
        assertEquals("Good afternoon!", formatHomeGreeting("", hourOfDay = 15))
        assertEquals("Good evening!", formatHomeGreeting("", hourOfDay = 20))
        assertEquals("Good night!", formatHomeGreeting("", hourOfDay = 21))

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

        // 1. Editorial Header: Greeting + session subtitle + 42.dp circular Search & Settings buttons
        composeRule.onNodeWithTag("home_header_title").assertIsDisplayed()
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

        val searchBounds = composeRule.onNodeWithTag("home_search_button").getUnclippedBoundsInRoot()
        val settingsBounds = composeRule.onNodeWithTag("settings_button").getUnclippedBoundsInRoot()
        assertEquals(42f, searchBounds.width.value, 1.5f)
        assertEquals(42f, searchBounds.height.value, 1.5f)
        assertEquals(42f, settingsBounds.width.value, 1.5f)
        assertEquals(42f, settingsBounds.height.value, 1.5f)

        // 2. Main Now Playing Hero Card: 3 clean vertical tiers (100.dp artwork + 4-line metadata, wavy bar, centered 54.dp Play/Pause)
        val heroBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()
        assertTrue(
            "Main Now Playing hero card must stay compact and balanced (was ${heroBounds.height.value})",
            heroBounds.height.value in 220f..310f
        )

        val artBounds = composeRule.onNodeWithTag("home_hero_artwork", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(100f, artBounds.width.value, 1.5f)
        assertEquals(100f, artBounds.height.value, 1.5f)

        val statusBounds = composeRule.onNodeWithTag("home_hero_status_badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val titleBounds = composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val artistBounds = composeRule.onNodeWithTag("home_hero_artist", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val hifiBadgeBounds = composeRule.onNodeWithTag("home_hero_hifi_badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        composeRule.onNodeWithText("NOW PLAYING", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("LOSSLESS", useUnmergedTree = true).assertIsDisplayed()

        // Exact 4-line vertical metadata hierarchy: Status -> Title -> Artist -> Hi-Fi Audio badge
        assertTrue("NOW PLAYING must sit above title", statusBounds.bottom.value <= titleBounds.top.value + 1f)
        assertTrue("Title must sit above artist", titleBounds.bottom.value <= artistBounds.top.value + 1f)
        assertTrue("Hi-Fi badge must sit below artist", hifiBadgeBounds.top.value >= artistBounds.bottom.value - 1f)

        val playBounds = composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val waveBounds = composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("Centered Play/Pause button must be 54.dp wide", 54f, playBounds.width.value, 1.5f)
        assertEquals("Centered Play/Pause button must be 54.dp tall", 54f, playBounds.height.value, 1.5f)
        assertTrue("Wavy progress bar must sit below top tier artwork", waveBounds.top.value >= artBounds.bottom.value - 1f)
        assertTrue("Centered Play/Pause button must sit below the wavy progress bar", playBounds.top.value >= waveBounds.bottom.value - 1f)
        val heroCenterX = (heroBounds.left.value + heroBounds.right.value) / 2f
        val playCenterX = (playBounds.left.value + playBounds.right.value) / 2f
        assertEquals("Play/Pause button must be horizontally centered inside the hero card", heroCenterX, playCenterX, 2f)
        composeRule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertIsDisplayed()

        // Assert strictly ZERO Favorite, Shuffle, Previous, Next, Repeat, or Queue controls inside the Hero card
        composeRule.onNodeWithTag("home_hero_favorite_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Toggle favorite", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Next track", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Previous track", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Toggle shuffle", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Cycle repeat mode", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Up Next", useUnmergedTree = true).assertDoesNotExist()

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

        // Hero card derives calm low-glare M3 tonal layers directly from MaterialTheme.colorScheme
        assertEquals(light.primaryContainer.opaqueOver(light.surfaceContainerHigh, 0.24f), lightHero.topColor)
        assertEquals(light.onSurface, lightHero.titleColor)
        assertEquals(light.onSurfaceVariant, lightHero.artistColor)
        assertEquals(light.onSurfaceVariant, lightHero.timestampColor)
        assertEquals(light.primary, lightHero.playButtonContainer)
        assertEquals(light.onPrimary, lightHero.playButtonContent)
        assertEquals(light.primary, lightHero.wavePlayedColor)
        assertEquals(light.primary, lightHero.statusTextColor)
        assertEquals(light.primary, lightHero.statusIconTint)
        assertEquals(light.primary.opaqueOver(lightHero.topColor, 0.14f), lightHero.hifiPillContainer)
        assertEquals(light.primary, lightHero.hifiPillText)
        assertEquals(light.onSurfaceVariant.opaqueOver(lightHero.bottomColor, 0.24f), lightHero.waveUnplayedColor)
        assertEquals(1f, lightHero.topColor.alpha, 0.001f)
        assertEquals(1f, lightHero.bottomColor.alpha, 0.001f)
        assertEquals(1f, lightHero.hifiPillContainer.alpha, 0.001f)
        assertEquals(1f, lightHero.waveUnplayedColor.alpha, 0.001f)

        assertNotEquals("Hero surface must adapt between Light Cobalt and Dark Matcha", lightHero.topColor, darkHero.topColor)
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

        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_mood_section_header"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_mood_card_${SmartMoodTag.SOULFUL.id}").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen Hero card gives full width to artist subtitle without truncation and toggles centered 54dp Play Pause button below wavy bar at 360dp`() {
        val longArtistTrack = acousticTrack.copy(
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
                            allTracks = listOf(longArtistTrack, hiResTrack).toStableList(),
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

        // 1. Artist subtitle gets full remaining width (~168.dp on 360.dp screen) without horizontal truncation
        val artistNode = composeRule.onNodeWithTag("home_hero_artist", useUnmergedTree = true).assertIsDisplayed()
        val artistBounds = artistNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Artist subtitle should receive full remaining width (~168.dp) at 360.dp, was ${artistBounds.width.value}",
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

        // 2. HI-FI AUDIO pill sits directly below the artist subtitle
        val hifiBounds = composeRule.onNodeWithTag("home_hero_hifi_badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("HI-FI AUDIO", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("HI-FI AUDIO pill must sit below artist subtitle", hifiBounds.top.value >= artistBounds.bottom.value - 1f)

        val playBounds = composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val waveBounds = composeRule.onNodeWithTag("home_continue_progress", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardBounds = composeRule.onNodeWithTag("home_continue_card").getUnclippedBoundsInRoot()

        // 3. Single centered 54.dp Play/Pause button sits below wavy progress bar with no Favorite/Shuffle/Prev/Next/Queue buttons
        assertEquals(54f, playBounds.width.value, 1.5f)
        assertEquals(54f, playBounds.height.value, 1.5f)
        assertTrue("Play/Pause button must sit below wavy progress bar", playBounds.top.value >= waveBounds.bottom.value - 1f)
        assertEquals(
            "Play/Pause button must be horizontally centered inside the hero card",
            (cardBounds.left.value + cardBounds.right.value) / 2f,
            (playBounds.left.value + playBounds.right.value) / 2f,
            2f
        )
        composeRule.onNodeWithTag("home_hero_favorite_button", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Toggle favorite", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Up Next", useUnmergedTree = true).assertDoesNotExist()

        // 4. Displays NOW PLAYING + Pause while playing, toggles cleanly to PAUSED + Play on click without expanding card
        composeRule.onNodeWithText("NOW PLAYING", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pause", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("home_continue_play_button", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        assertEquals(1, toggleCalls)
        assertEquals("Tapping centered Play/Pause button must not trigger card expand", 0, expandCalls)
        composeRule.onNodeWithText("PAUSED", useUnmergedTree = true).assertIsDisplayed()
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
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.DARK) {
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
            CadenceTheme(palette = ExpressivePalette.TERRACOTTA, appearanceMode = AppearanceMode.DARK) {
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
            val bare = formatHomeGreeting("", hourOfDay = hour)
            val named = formatHomeGreeting("Alex", hourOfDay = hour)
            assertEquals("Hour $hour must use late night greeting", "Late night vibes", bare)
            assertEquals("Hour $hour named greeting", "Late night vibes, Alex", named)
            assertFalse("Hour $hour must NEVER return Good evening!", bare.contains("evening", ignoreCase = true))
        }
        for (hour in 5..11) {
            assertEquals("Good morning!", formatHomeGreeting("", hourOfDay = hour))
            assertEquals("Good morning, Alex!", formatHomeGreeting("Alex", hourOfDay = hour))
        }
        for (hour in 12..16) {
            assertEquals("Good afternoon!", formatHomeGreeting("", hourOfDay = hour))
            assertEquals("Good afternoon, Alex!", formatHomeGreeting("Alex", hourOfDay = hour))
        }
        for (hour in 17..20) {
            assertEquals("Good evening!", formatHomeGreeting("", hourOfDay = hour))
            assertEquals("Good evening, Alex!", formatHomeGreeting("Alex", hourOfDay = hour))
        }
        for (hour in 21..23) {
            assertEquals("Good night!", formatHomeGreeting("", hourOfDay = hour))
            assertEquals("Good night, Alex!", formatHomeGreeting("Alex", hourOfDay = hour))
        }

        // Dynamic context-aware subheadings: deterministic per sessionSeed/hour, library-aware, never static
        val midnightA = formatHomeSubtitle(hourOfDay = 0, trackCount = 42, hasLosslessTracks = true, sessionSeed = 7)
        val midnightB = formatHomeSubtitle(hourOfDay = 0, trackCount = 42, hasLosslessTracks = true, sessionSeed = 7)
        assertEquals("Same session seed and hour must not flicker", midnightA, midnightB)

        val lateNightPool = homeSubtitleCandidates(hourOfDay = 0, trackCount = 42, hasLosslessTracks = true)
        assertTrue(lateNightPool.contains("Quiet tracks for after midnight"))
        assertTrue(lateNightPool.contains("Low-light listening, ready when you are"))
        assertTrue(lateNightPool.contains("Unwind with your late-night rotation"))

        val morningPool = homeSubtitleCandidates(hourOfDay = 8, trackCount = 42, hasLosslessTracks = false)
        assertTrue(morningPool.contains("Set the tempo for your morning"))
        assertTrue(morningPool.contains("Fresh tracks to start the day"))
        assertTrue(morningPool.contains("42 offline tracks ready for the day"))

        val afternoonPool = homeSubtitleCandidates(hourOfDay = 14, trackCount = 42, hasLosslessTracks = true)
        assertTrue(afternoonPool.contains("42 offline tracks ready to spin"))
        assertTrue(afternoonPool.contains("Dive back into your lossless library"))
        assertTrue(afternoonPool.contains("Curated from your local collection"))

        val emptyLibraryAfternoon = homeSubtitleCandidates(hourOfDay = 14, trackCount = 0, hasLosslessTracks = false)
        assertTrue(
            "Empty library must not claim '0 offline tracks'",
            emptyLibraryAfternoon.none { it.startsWith("0 offline") }
        )

        for (hour in 0..23) {
            val subtitle = formatHomeSubtitle(hourOfDay = hour, trackCount = 24, hasLosslessTracks = true, sessionSeed = 11)
            assertTrue("Subtitle for hour $hour must be non-blank", subtitle.isNotBlank())
            assertFalse("Subtitle for hour $hour must not use old static prompt", subtitle.contains("Pick up where you left off"))
        }
    }
}
