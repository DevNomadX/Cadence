package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.data.AudioFocusBehavior
import com.example.data.AudioSettings
import com.example.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground-capable Media3 MediaSessionService hosting ExoPlayer.
 * Observes persisted AudioSettings to live-configure:
 * - Gapless playback vs brief inter-track separation
 * - Smooth crossfade volume transitions (0–10s)
 * - Automatic pause on headphone disconnect (AUDIO_BECOMING_NOISY)
 * - Audio focus & ducking policy (Duck, Always Pause, or Mix)
 */
class CadencePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var currentSettings: AudioSettings = AudioSettings()
    private var fadeMonitorJob: Job? = null
    private var gapDelayJob: Job? = null

    private val transitionListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                maybeStartFadeMonitor()
            } else {
                stopFadeMonitor()
                exoPlayer?.volume = 1.0f
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = exoPlayer ?: return
            val settings = currentSettings

            // When gapless is disabled (and crossfade is off), insert a clean 600ms inter-track pause
            if (!settings.gaplessPlaybackEnabled &&
                settings.crossfadeDurationSec == 0 &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                player.playWhenReady
            ) {
                gapDelayJob?.cancel()
                player.pause()
                gapDelayJob = serviceScope.launch {
                    delay(NON_GAPLESS_GAP_MS)
                    if (isActive && mediaSession != null) {
                        player.play()
                    }
                }
                return
            }

            if (settings.crossfadeDurationSec > 0 && player.isPlaying) {
                maybeStartFadeMonitor()
            } else {
                player.volume = 1.0f
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                exoPlayer?.volume = 1.0f
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val audioAttributes = buildAudioAttributes(AudioFocusBehavior.DUCK)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(transitionListener)
        exoPlayer = player

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        val prefsRepo = PreferencesRepository(applicationContext)
        serviceScope.launch {
            prefsRepo.audioSettingsFlow.collect { settings ->
                applyAudioSettings(settings)
            }
        }
    }

    private fun applyAudioSettings(settings: AudioSettings) {
        currentSettings = settings
        val player = exoPlayer ?: return

        player.setHandleAudioBecomingNoisy(settings.pauseOnHeadphoneDisconnect)

        val handleFocus = settings.audioFocusBehavior != AudioFocusBehavior.IGNORE
        player.setAudioAttributes(buildAudioAttributes(settings.audioFocusBehavior), handleFocus)

        if (settings.crossfadeDurationSec > 0 && player.isPlaying) {
            maybeStartFadeMonitor()
        } else {
            stopFadeMonitor()
            player.volume = 1.0f
        }
    }

    private fun buildAudioAttributes(behavior: AudioFocusBehavior): AudioAttributes {
        // Media3 AudioFocusManager ducks MUSIC on transient focus loss and pauses SPEECH.
        val contentType = when (behavior) {
            AudioFocusBehavior.PAUSE -> C.AUDIO_CONTENT_TYPE_SPEECH
            AudioFocusBehavior.DUCK,
            AudioFocusBehavior.IGNORE -> C.AUDIO_CONTENT_TYPE_MUSIC
        }
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(contentType)
            .build()
    }

    private fun maybeStartFadeMonitor() {
        val crossfadeSec = currentSettings.crossfadeDurationSec
        if (crossfadeSec <= 0) {
            stopFadeMonitor()
            return
        }
        if (fadeMonitorJob?.isActive == true) return

        fadeMonitorJob = serviceScope.launch {
            while (isActive) {
                val player = exoPlayer ?: break
                val fadeMs = currentSettings.crossfadeDurationSec * 1000L
                if (!player.isPlaying || fadeMs <= 0L) {
                    player.volume = 1.0f
                    break
                }
                val duration = player.duration
                val position = player.currentPosition
                if (duration > fadeMs * 2L) {
                    val halfFade = (fadeMs / 2L).coerceAtLeast(250L)
                    val remaining = (duration - position).coerceAtLeast(0L)
                    val targetVolume = when {
                        remaining < halfFade && player.hasNextMediaItem() ->
                            (remaining.toFloat() / halfFade.toFloat()).coerceIn(0.08f, 1.0f)
                        position < halfFade ->
                            (position.toFloat() / halfFade.toFloat()).coerceIn(0.15f, 1.0f)
                        else -> 1.0f
                    }
                    if (kotlin.math.abs(player.volume - targetVolume) > 0.02f) {
                        player.volume = targetVolume
                    }
                    val nextDelay = if (remaining < halfFade + 800L || position < halfFade + 400L) 60L else 400L
                    delay(nextDelay)
                } else {
                    if (player.volume != 1.0f) player.volume = 1.0f
                    delay(500L)
                }
            }
        }
    }

    private fun stopFadeMonitor() {
        fadeMonitorJob?.cancel()
        fadeMonitorJob = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopFadeMonitor()
        gapDelayJob?.cancel()
        serviceScope.cancel()
        exoPlayer?.removeListener(transitionListener)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val NON_GAPLESS_GAP_MS = 600L
    }
}
