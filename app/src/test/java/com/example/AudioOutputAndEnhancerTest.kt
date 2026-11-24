package com.example

import android.app.Application
import android.media.audiofx.AudioEffect
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.core.app.ApplicationProvider
import com.example.data.AudioSettings
import com.example.data.PreferencesRepository
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.playback.AcousticEffectBackend
import com.example.playback.AcousticEffectHandle
import com.example.playback.AcousticEnhanceEngine
import com.example.playback.AudioEffectSessionBroadcaster
import com.example.playback.CadencePlaybackService
import com.example.playback.FloatOutputRenderersFactory
import com.example.playback.PlatformAcousticEffectBackend
import com.example.playback.buildMusicAudioAttributes
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CadenceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xhdpi")
class AudioOutputAndEnhancerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun musicAudioAttributes_usesMediaUsageAndMusicContentType() {
        val attrs = buildMusicAudioAttributes()
        assertEquals(C.USAGE_MEDIA, attrs.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, attrs.contentType)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun floatOutputRenderersFactory_buildsDefaultAudioSinkWithFloatEnabled() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val factory = FloatOutputRenderersFactory(app)
        val sink = factory.createAudioSinkForTesting(
            context = app,
            enableAudioTrackPlaybackParams = true
        )
        assertTrue(sink is DefaultAudioSink)
    }

    @Test
    fun playbackService_configuresMusicAttributes_andBroadcastsAudioEffectSessions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.clearBroadcastIntents()

        val controller = Robolectric.buildService(CadencePlaybackService::class.java).create()
        val service = controller.get()

        val player = service.playerForTesting
        assertNotNull("ExoPlayer should be initialized in onCreate", player)
        assertEquals(C.USAGE_MEDIA, player!!.audioAttributes.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, player.audioAttributes.contentType)

        // Simulate Media3 allocating audio session 42, then switching to 77, then releasing service
        shadowApp.clearBroadcastIntents()
        service.notifyAudioSessionIdForTesting(42)
        val openFirst = shadowApp.broadcastIntents.filter {
            it.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
        }
        assertEquals(1, openFirst.size)
        assertEquals(42, openFirst[0].getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))
        assertEquals(app.packageName, openFirst[0].getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
        assertEquals(
            AudioEffect.CONTENT_TYPE_MUSIC,
            openFirst[0].getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE, -1)
        )

        // Switch to a new session ID: must close 42 before opening 77
        shadowApp.clearBroadcastIntents()
        service.notifyAudioSessionIdForTesting(77)
        val switchIntents = shadowApp.broadcastIntents
        assertEquals(2, switchIntents.size)
        assertEquals(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, switchIntents[0].action)
        assertEquals(42, switchIntents[0].getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))
        assertEquals(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, switchIntents[1].action)
        assertEquals(77, switchIntents[1].getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))

        // Service destroy must broadcast ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION for session 77
        shadowApp.clearBroadcastIntents()
        controller.destroy()
        val closeIntents = shadowApp.broadcastIntents.filter {
            it.action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
        }
        assertEquals(1, closeIntents.size)
        assertEquals(77, closeIntents[0].getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1))
        assertEquals(app.packageName, closeIntents[0].getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME))
    }

    @Test
    fun audioEffectSessionBroadcaster_ignoresUnsetAndDeduplicates() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.clearBroadcastIntents()

        val broadcaster = AudioEffectSessionBroadcaster(app)
        broadcaster.onAudioSessionIdChanged(C.AUDIO_SESSION_ID_UNSET)
        broadcaster.onAudioSessionIdChanged(0)
        assertTrue(shadowApp.broadcastIntents.isEmpty())

        broadcaster.onAudioSessionIdChanged(15)
        broadcaster.onAudioSessionIdChanged(15)
        assertEquals(1, shadowApp.broadcastIntents.size)
        assertEquals(15, broadcaster.activeSessionId)

        broadcaster.closeActiveSession()
        broadcaster.closeActiveSession()
        assertEquals(2, shadowApp.broadcastIntents.size)
        assertEquals(C.AUDIO_SESSION_ID_UNSET, broadcaster.activeSessionId)
    }

    @Test
    fun acousticEnhanceEngine_lifecycleAndCrashSafety() {
        val attachedSessions = mutableListOf<Int>()
        val releasedSessions = mutableListOf<Int>()

        val fakeBackend = object : AcousticEffectBackend {
            override fun attach(audioSessionId: Int): AcousticEffectHandle {
                attachedSessions += audioSessionId
                return object : AcousticEffectHandle {
                    override fun release() {
                        releasedSessions += audioSessionId
                    }
                }
            }
        }

        val engine = AcousticEnhanceEngine(fakeBackend)

        // Disabled -> never attaches
        engine.update(enabled = false, audioSessionId = 10)
        assertFalse(engine.isAttached)
        assertTrue(attachedSessions.isEmpty())

        // Enabled with valid session -> attaches
        engine.update(enabled = true, audioSessionId = 10)
        assertTrue(engine.isAttached)
        assertEquals(10, engine.attachedSessionId)
        assertEquals(listOf(10), attachedSessions)

        // Same session repeated -> no duplicate attach
        engine.update(enabled = true, audioSessionId = 10)
        assertEquals(listOf(10), attachedSessions)

        // Session change -> releases old and attaches new
        engine.update(enabled = true, audioSessionId = 25)
        assertEquals(listOf(10), releasedSessions)
        assertEquals(listOf(10, 25), attachedSessions)
        assertEquals(25, engine.attachedSessionId)

        // Toggle off -> immediately releases
        engine.update(enabled = false, audioSessionId = 25)
        assertFalse(engine.isAttached)
        assertEquals(listOf(10, 25), releasedSessions)
    }

    @Test
    fun acousticEnhanceEngine_doesNotCrashWhenPlatformAudioEffectThrows() {
        val throwingBackend = object : AcousticEffectBackend {
            override fun attach(audioSessionId: Int): AcousticEffectHandle {
                throw UnsupportedOperationException("DSP HAL unavailable on USB DAC")
            }
        }
        val engine = AcousticEnhanceEngine(throwingBackend)
        engine.update(enabled = true, audioSessionId = 99)
        assertFalse(engine.isAttached)

        val throwingReleaseBackend = object : AcousticEffectBackend {
            override fun attach(audioSessionId: Int): AcousticEffectHandle =
                object : AcousticEffectHandle {
                    override fun release() {
                        throw RuntimeException("Native release failure")
                    }
                }
        }
        val engine2 = AcousticEnhanceEngine(throwingReleaseBackend)
        engine2.update(enabled = true, audioSessionId = 99)
        assertTrue(engine2.isAttached)
        engine2.release()
        assertFalse(engine2.isAttached)

        // Platform backend itself must also never crash under Robolectric
        val platformEngine = AcousticEnhanceEngine(PlatformAcousticEffectBackend)
        platformEngine.update(enabled = true, audioSessionId = 101)
        platformEngine.release()
        assertFalse(platformEngine.isAttached)
    }

    @Test
    fun fallbackEqualizerCurve_appliesHeadroomOffsetAndClampsRange() {
        // 60 Hz (+2.0 dB) - 200 mB headroom = 0 mB
        assertEquals(
            0.toShort(),
            AcousticEnhanceEngine.computeFallbackEqualizerMillibels(60, -1500, 1500)
        )
        // 400 Hz (-1.0 dB) - 200 mB headroom = -300 mB
        assertEquals(
            (-300).toShort(),
            AcousticEnhanceEngine.computeFallbackEqualizerMillibels(400, -1500, 1500)
        )
        // 12 kHz (+2.0 dB) - 200 mB headroom = 0 mB
        assertEquals(
            0.toShort(),
            AcousticEnhanceEngine.computeFallbackEqualizerMillibels(12_000, -1500, 1500)
        )
        // Tight hardware range clamping
        assertEquals(
            (-150).toShort(),
            AcousticEnhanceEngine.computeFallbackEqualizerMillibels(400, -150, 150)
        )
    }

    @Test
    fun preferencesRepository_persistsAcousticEnhanceDefaultFalse() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = PreferencesRepository(app)

        assertFalse(repo.audioSettingsFlow.first().acousticEnhanceEnabled)

        repo.setAcousticEnhanceEnabled(true)
        assertTrue(repo.audioSettingsFlow.first().acousticEnhanceEnabled)

        repo.setAcousticEnhanceEnabled(false)
        assertFalse(repo.audioSettingsFlow.first().acousticEnhanceEnabled)
    }

    @Test
    fun settingsScreen_acousticEnhanceToggle_rendersAndUpdatesState() {
        var audioSettings by mutableStateOf(AudioSettings(acousticEnhanceEnabled = false))
        var lastCallbackValue: Boolean? = null

        composeRule.setContent {
            CadenceTheme {
                SettingsScreen(
                    themePrefs = ThemePreferences(),
                    audioSettings = audioSettings,
                    savedFolders = StableList(emptyList()),
                    minTrackDurationSec = 60,
                    currentTrack = null,
                    isPlaying = false,
                    onBack = {},
                    onSelectPalette = {},
                    onSelectAppearance = {},
                    onSelectArtworkShape = {},
                    onToggleWavyScrubber = {},
                    onToggleAmbientAura = {},
                    onTogglePerformanceMode = {},
                    onAddFolder = {},
                    onRemoveFolder = {},
                    onRescanFolders = {},
                    onSelectMinTrackDurationSec = {},
                    onToggleGaplessPlayback = {},
                    onChangeCrossfadeSec = {},
                    onTogglePauseOnHeadphoneDisconnect = {},
                    onSelectAudioFocusBehavior = {},
                    onToggleAcousticEnhance = { enabled ->
                        lastCallbackValue = enabled
                        audioSettings = audioSettings.copy(acousticEnhanceEnabled = enabled)
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("toggle_acoustic_enhance"))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Acoustic Enhance").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_acoustic_enhance").assertIsOff()

        composeRule.onNodeWithTag("toggle_acoustic_enhance").performClick()
        composeRule.waitForIdle()

        assertEquals(true, lastCallbackValue)
        composeRule.onNodeWithTag("toggle_acoustic_enhance").assertIsOn()
    }
}
