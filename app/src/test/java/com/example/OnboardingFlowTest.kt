package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesRepository
import com.example.data.SavedFolderItem
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.data.db.CadenceDatabase
import com.example.data.db.SavedFolderEntity
import com.example.data.db.TrackEntity
import com.example.data.library.LibraryRepository
import com.example.ui.CadenceApp
import com.example.ui.player.PlayerViewModel
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressivePalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xhdpi")
class OnboardingFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var db: CadenceDatabase
    private lateinit var prefs: PreferencesRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesRepository(app)
        // Reset onboarding & theme prefs cleanly before each test
        app.getSharedPreferences(PreferencesRepository.SYNC_THEME_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking {
            prefs.setOnboardingCompleted(false)
            prefs.setPalette(ExpressivePalette.BERRY)
            prefs.setAppearanceMode(AppearanceMode.SYSTEM)
            prefs.setScanAllFoldersEnabled(false)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun SemanticsNodeInteraction.assertSingleLineUnclipped(label: String) {
        val node = fetchSemanticsNode("Expected text node for $label")
        val results = mutableListOf<TextLayoutResult>()
        val getTextLayout = node.config[SemanticsActions.GetTextLayoutResult].action
        val ok = getTextLayout?.invoke(results) == true
        assertTrue("Expected text layout result for $label", ok && results.isNotEmpty())
        val layout = results.first()
        assertEquals("Expected single line for $label", 1, layout.lineCount)
        assertFalse("Expected unclipped non-ellipsized text for $label", layout.isLineEllipsized(0))
        assertFalse("Expected zero visual overflow for $label", layout.hasVisualOverflow)
    }

    @Test
    fun `fresh install launches Step 1 navigates Steps 1 to 2 to 3 unclipped at 360dp applies live theme and enters HomeScreen`() {
        val libraryRepo = LibraryRepository(app, prefs, db)
        val viewModel = PlayerViewModel.createForTesting(app, prefs, libraryRepo)

        composeRule.setContent {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(780.dp)
            ) {
                CadenceApp(viewModel = viewModel)
            }
        }

        // Wait for startup upgrade-safe check to resolve into Onboarding Step 1
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("onboarding_step_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Step 1 assertions: emblem, headline, unclipped 1-line subtitle, 3 borderless feature rows
        composeRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_progress_dots").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_emblem").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_title")
            .assertIsDisplayed()
            .assertTextContains("Your Music, Purely Offline.")
        composeRule.onNodeWithTag("onboarding_subtitle")
            .assertIsDisplayed()
            .assertSingleLineUnclipped("onboarding_subtitle")
        composeRule.onNodeWithTag("onboarding_feature_privacy").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_feature_hifi").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_feature_mixes").assertIsDisplayed()

        // Advance Step 1 -> Step 2
        composeRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding_step_2").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_step2_title")
            .assertIsDisplayed()
            .assertSingleLineUnclipped("onboarding_step2_title")
        composeRule.onNodeWithTag("onboarding_step2_subtitle")
            .assertIsDisplayed()
            .assertSingleLineUnclipped("onboarding_step2_subtitle")
        assertEquals(0, composeRule.onAllNodesWithTag("onboarding_name_input").fetchSemanticsNodes().size)

        // Switch Appearance Mode live -> Dark
        composeRule.onNodeWithTag("onboarding_appearance_dark").performClick()
        composeRule.waitForIdle()

        // Verify all 10 synced palettes (titles + 1-line subtitles) render cleanly without clipping at 360dp
        for (palette in ExpressivePalette.entries) {
            composeRule.onNodeWithTag("onboarding_step_2")
                .performScrollToNode(hasTestTag("onboarding_palette_${palette.id}"))
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("onboarding_palette_title_${palette.id}", useUnmergedTree = true)
                .assertSingleLineUnclipped("onboarding_palette_title_${palette.id}")
            composeRule.onNodeWithTag("onboarding_palette_tagline_${palette.id}", useUnmergedTree = true)
                .assertSingleLineUnclipped("onboarding_palette_tagline_${palette.id}")
        }

        composeRule.onNodeWithTag("onboarding_step_2")
            .performScrollToNode(hasTestTag("onboarding_palette_arctic_cyan"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_palette_arctic_cyan").performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            viewModel.themeState.value.palette == ExpressivePalette.ARCTIC_CYAN &&
                viewModel.themeState.value.appearanceMode == AppearanceMode.DARK
        }
        assertEquals(ExpressivePalette.ARCTIC_CYAN, viewModel.themeState.value.palette)
        assertEquals(AppearanceMode.DARK, viewModel.themeState.value.appearanceMode)

        // Advance Step 2 -> Step 3
        composeRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding_step_3").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_step3_title")
            .assertIsDisplayed()
            .assertSingleLineUnclipped("onboarding_step3_title")
        composeRule.onNodeWithTag("onboarding_step3_subtitle")
            .assertIsDisplayed()
            .assertSingleLineUnclipped("onboarding_step3_subtitle")
        composeRule.onNodeWithTag("onboarding_audio_permission_card").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_notification_permission_row").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_notif_title", useUnmergedTree = true)
            .assertSingleLineUnclipped("onboarding_notif_title")

        composeRule.onNodeWithTag("onboarding_step_3")
            .performScrollToNode(hasTestTag("onboarding_folder_selection_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_folder_selection_card").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_folder_mode_all").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_folder_mode_specific").assertIsDisplayed()

        // Complete onboarding -> transitions cleanly to HomeScreen
        composeRule.onNodeWithTag("onboarding_start_listening_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("cadence_floating_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cadence_floating_root").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().size)
        assertTrue(runBlocking { prefs.hasCompletedOnboarding.first() })
        assertTrue(PreferencesRepository.readStartupUserProfileSnapshot(app).hasCompletedOnboarding)
    }

    @Test
    fun `Step 3 permission badges folder mode toggle removable chips and Back navigation work cleanly at 360dp`() {
        var audioGranted by mutableStateOf(false)
        var notifGranted by mutableStateOf(false)
        var folders by mutableStateOf(
            listOf(
                SavedFolderItem("content://tree/music", "FLAC Archive", 0L),
                SavedFolderItem("content://tree/roadtrip", "Road Trip", 0L)
            )
        )
        var addFolderClicks = 0
        var startedScanAll: Boolean? = null

        composeRule.setContent {
            CadenceTheme(palette = ExpressivePalette.BERRY, appearanceMode = AppearanceMode.LIGHT) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .height(780.dp)
                ) {
                    OnboardingScreen(
                        themePrefs = ThemePreferences(),
                        savedFolders = StableList(folders),
                        isAudioPermissionGranted = audioGranted,
                        isNotificationPermissionGranted = notifGranted,
                        showNotificationPermissionRow = true,
                        onRequestAudioPermission = { audioGranted = true },
                        onRequestNotificationPermission = { notifGranted = true },
                        onAddFolder = { addFolderClicks += 1 },
                        onRemoveFolder = { uri -> folders = folders.filterNot { it.treeUri == uri } },
                        onStartListening = { startedScanAll = it }
                    )
                }
            }
        }

        // Navigate 1 -> 2 -> 3
        composeRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeRule.waitForIdle()

        // Grant Audio Permission -> switches from Grant Access to Granted ✓
        composeRule.onNodeWithTag("onboarding_grant_audio_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_audio_granted_badge")
            .assertIsDisplayed()
            .assertTextContains("Granted ✓")

        // Allow Lock-Screen Notifications -> switches from Allow to Allowed ✓
        composeRule.onNodeWithTag("onboarding_allow_notifications_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_notifications_allowed_badge")
            .assertIsDisplayed()
            .assertTextContains("Allowed ✓")

        // Switch to Specific Folders Only -> verify + Add Folder & removable chips
        composeRule.onNodeWithTag("onboarding_step_3")
            .performScrollToNode(hasTestTag("onboarding_folder_mode_specific"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_folder_mode_specific").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding_step_3")
            .performScrollToNode(hasTestTag("onboarding_add_folder_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_folder_chip_0").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_folder_chip_1").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_add_folder_button").performClick()
        assertEquals(1, addFolderClicks)

        // Remove first folder chip
        composeRule.onNodeWithTag("onboarding_remove_folder_0").performClick()
        composeRule.waitForIdle()
        assertEquals(1, folders.size)
        assertEquals("Road Trip", folders.first().displayName)

        // Verify Back navigates 3 -> 2 -> 1 cleanly
        composeRule.onNodeWithTag("onboarding_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_step_2").assertIsDisplayed()

        composeRule.onNodeWithTag("onboarding_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_step_1").assertIsDisplayed()

        // Return to Step 3 and finish with Specific Folders Only selected
        composeRule.onNodeWithTag("onboarding_get_started_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_continue_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_start_listening_button").performClick()
        assertEquals(false, startedScanAll)
    }

    @Test
    fun `existing install with indexed tracks skips onboarding and auto-persists hasCompletedOnboarding`() {
        runBlocking {
            db.trackDao().upsertTracks(
                listOf(
                    TrackEntity(
                        id = "existing_track_1",
                        title = "Midnight Echo",
                        artist = "Aria Voss",
                        album = "Nocturne",
                        genre = "Ambient",
                        durationMs = 210_000L,
                        bpm = 96,
                        artworkResId = null,
                        artworkPath = null,
                        localUriString = "content://media/external/audio/media/42",
                        folderName = "Music",
                        folderPath = "/storage/emulated/0/Music",
                        mimeType = "audio/flac",
                        bitrateKbps = 980,
                        sampleRateHz = 44_100,
                        fileSizeBytes = 24_000_000L,
                        dateAdded = System.currentTimeMillis(),
                        playCount = 3,
                        lastPlayedAt = System.currentTimeMillis(),
                        isFavorite = false,
                        suggestedPaletteId = ExpressivePalette.BERRY.id,
                        isUserImported = true,
                        dedupeKey = "midnight|aria|210000"
                    )
                )
            )
        }

        val libraryRepo = LibraryRepository(app, prefs, db)
        val viewModel = PlayerViewModel.createForTesting(app, prefs, libraryRepo)

        composeRule.setContent {
            CadenceApp(viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("cadence_floating_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().size)
        assertFalse(viewModel.onboardingGateState.value.shouldShowOnboarding)
        assertTrue(viewModel.onboardingGateState.value.isResolved)
        assertTrue(runBlocking { prefs.hasCompletedOnboarding.first() })
    }

    @Test
    fun `existing install with saved folders skips onboarding and auto-persists hasCompletedOnboarding`() {
        runBlocking {
            db.folderDao().upsertFolder(
                SavedFolderEntity(
                    treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                    displayName = "Music",
                    lastScannedAt = System.currentTimeMillis()
                )
            )
        }

        val libraryRepo = LibraryRepository(app, prefs, db)
        val viewModel = PlayerViewModel.createForTesting(app, prefs, libraryRepo)

        composeRule.setContent {
            CadenceApp(viewModel = viewModel)
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("cadence_floating_root").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodesWithTag("onboarding_screen").fetchSemanticsNodes().size)
        assertFalse(viewModel.onboardingGateState.value.shouldShowOnboarding)
        assertTrue(viewModel.onboardingGateState.value.isResolved)
        assertTrue(runBlocking { prefs.hasCompletedOnboarding.first() })
    }
}
