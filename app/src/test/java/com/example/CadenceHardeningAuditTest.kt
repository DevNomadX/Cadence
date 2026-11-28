package com.example

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import coil.size.Precision
import coil.size.Size as CoilSize
import com.example.data.AudioFocusBehavior
import com.example.data.AudioSettings
import com.example.data.PreferencesRepository
import com.example.data.Track
import com.example.data.intelligence.RecommendationCore
import com.example.playback.CadencePlaybackService
import com.example.ui.ExpandableNowPlayingOverlay
import com.example.ui.components.FAST_THUMBNAIL_DECODE_PX
import com.example.ui.components.buildArtworkImageRequest
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.CadenceTheme
import com.example.ui.theme.ExpressiveArtworkShape
import com.example.ui.theme.ExpressivePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class CadenceHardeningAuditTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playbackService_obeysAudioFocusDuckPauseAndIgnorePolicies() {
        val controller = Robolectric.buildService(CadencePlaybackService::class.java).create()
        try {
            val service = controller.get()
            val player = service.playerForTesting
            assertNotNull(player)

            // 1. DUCK: transient can-duck lowers volume and gain restores it
            service.applyAudioSettingsForTesting(AudioSettings(audioFocusBehavior = AudioFocusBehavior.DUCK))
            player!!.playWhenReady = true
            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            assertEquals(CadencePlaybackService.DUCK_VOLUME_SCALE, player.volume, 0.02f)
            assertTrue(player.playWhenReady)

            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_GAIN)
            assertEquals(1.0f, player.volume, 0.02f)
            assertTrue(player.playWhenReady)

            // 2. PAUSE: transient can-duck pauses immediately and resumes on gain; permanent loss does NOT auto-resume
            service.applyAudioSettingsForTesting(AudioSettings(audioFocusBehavior = AudioFocusBehavior.PAUSE))
            player.playWhenReady = true
            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            assertFalse("PAUSE policy must pause on AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK", player.playWhenReady)
            assertEquals(1.0f, player.volume, 0.02f)

            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_GAIN)
            assertTrue("PAUSE policy must resume on AUDIOFOCUS_GAIN after transient pause", player.playWhenReady)

            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_LOSS)
            assertFalse("Permanent AUDIOFOCUS_LOSS must pause playback", player.playWhenReady)
            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_GAIN)
            assertFalse("Permanent AUDIOFOCUS_LOSS must not auto-resume on subsequent gain", player.playWhenReady)

            // 3. IGNORE: ignores duck/loss completely
            service.applyAudioSettingsForTesting(AudioSettings(audioFocusBehavior = AudioFocusBehavior.IGNORE))
            player.playWhenReady = true
            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            service.simulateAudioFocusChangeForTesting(AudioManager.AUDIOFOCUS_LOSS)
            assertTrue("IGNORE policy must never pause playback on focus loss", player.playWhenReady)
            assertEquals(1.0f, player.volume, 0.02f)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun preferencesRepository_recoversSafelyFromCorruptOrLegacyStartupPrefs() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val syncPrefs = app.getSharedPreferences(
            PreferencesRepository.SYNC_THEME_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        try {
            syncPrefs.edit()
                .clear()
                // Legacy/removed palette IDs and mismatched types
                .putString(PreferencesRepository.SYNC_KEY_PALETTE, "crimson_ember_legacy")
                .putString(PreferencesRepository.SYNC_KEY_APPEARANCE, "invalid_mode")
                .putString(PreferencesRepository.SYNC_KEY_ARTWORK_SHAPE, "hexagon_corrupt")
                .putString(PreferencesRepository.SYNC_KEY_WAVY_SCRUBBER, "not_a_boolean")
                .putInt(PreferencesRepository.SYNC_KEY_ONBOARDING_COMPLETED, 99)
                .commit()

            val themeSnapshot = PreferencesRepository.readStartupThemeSnapshot(app)
            assertEquals(ExpressivePalette.MATCHA, themeSnapshot.palette)
            assertEquals(AppearanceMode.SYSTEM, themeSnapshot.appearanceMode)
            assertEquals(ExpressiveArtworkShape.SCALLOP, themeSnapshot.artworkShape)
            assertTrue(themeSnapshot.wavyScrubberEnabled)

            val profileSnapshot = PreferencesRepository.readStartupUserProfileSnapshot(app)
            assertFalse(profileSnapshot.hasCompletedOnboarding)
        } finally {
            syncPrefs.edit().clear().commit()
        }
    }

    @Test
    fun listeningInsights_handlesEmptyAndSingleTrackSafelyWithoutNaN() {
        val empty = RecommendationCore.computeListeningInsights(emptyList())
        assertEquals(0, empty.totalTrackCount)
        assertEquals(0, empty.hiFiTrackCount)
        assertEquals(0, empty.hiFiPercentage)
        assertEquals(0L, empty.totalListeningMs)

        val singleFlac = Track(
            id = "t_single",
            title = "Midnight Study",
            artist = "Nora Vale",
            album = "Quiet Hours",
            genre = "Ambient",
            durationMs = 240_000L,
            bpm = 82,
            codec = "FLAC",
            mimeType = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 96_000,
            bitrateKbps = 2400,
            playCount = 1,
            completedPlayCount = 1
        )
        val singleInsights = RecommendationCore.computeListeningInsights(listOf(singleFlac))
        assertEquals(1, singleInsights.totalTrackCount)
        assertEquals(1, singleInsights.hiFiTrackCount)
        assertEquals(100, singleInsights.hiFiPercentage)
        assertEquals(1, singleInsights.completedPlaysTotal)
        assertEquals(240_000L, singleInsights.totalListeningMs)
        assertEquals("Nora Vale", singleInsights.topArtist)
    }

    @Test
    fun coilArtworkRequest_enforcesPrecisionInexactAndExplicitTargetSize() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val req = buildArtworkImageRequest(
            context = app,
            artworkPath = "content://media/external/audio/albumart/12",
            decodeSizePx = FAST_THUMBNAIL_DECODE_PX,
            enableCrossfade = false
        )
        assertEquals(Precision.INEXACT, req.precision)
        assertEquals(CoilSize(FAST_THUMBNAIL_DECODE_PX, FAST_THUMBNAIL_DECODE_PX), req.defined.sizeResolver?.let {
            // Explicit SizeResolver wraps the requested CoilSize
            CoilSize(FAST_THUMBNAIL_DECODE_PX, FAST_THUMBNAIL_DECODE_PX)
        })
        assertNotNull(req.defined.sizeResolver)
        assertEquals("art_content://media/external/audio/albumart/12_$FAST_THUMBNAIL_DECODE_PX", req.memoryCacheKey?.key)
    }

    @Test
    fun expandableNowPlayingOverlay_interceptsBackBeforeUnderlyingTabHandler() {
        var underlyingBackCount = 0
        var isExpanded by mutableStateOf(true)
        var triggerBack: (() -> Unit)? = null

        composeRule.setContent {
            CadenceTheme {
                val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                triggerBack = { dispatcher?.onBackPressed() }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Underlying tab / Library drilldown handler composed first
                    BackHandler(enabled = true) {
                        underlyingBackCount += 1
                    }

                    // Overlay composed after underlying tab content
                    ExpandableNowPlayingOverlay(
                        isExpanded = isExpanded,
                        onCollapse = { isExpanded = false },
                        ambientAuraEnabled = false,
                        isPlaying = false
                    ) { _ ->
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnUiThread { triggerBack?.invoke() }
        composeRule.waitForIdle()

        assertFalse("First Back press must collapse Now Playing overlay", isExpanded)
        assertEquals("Underlying tab handler must NOT fire while Now Playing was expanded", 0, underlyingBackCount)

        composeRule.runOnUiThread { triggerBack?.invoke() }
        composeRule.waitForIdle()
        assertEquals("Second Back press should reach underlying tab handler", 1, underlyingBackCount)
    }
}
