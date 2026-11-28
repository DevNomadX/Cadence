package com.example.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
 * - 32-bit float PCM output via FloatOutputRenderersFactory
 * - Canonical USAGE_MEDIA + AUDIO_CONTENT_TYPE_MUSIC AudioAttributes & global AudioEffect session broadcasts
 * - Optional lightweight Acoustic Enhance mastering engine with headroom protection
 * - Gapless playback vs brief inter-track separation (with safe cancellation on pause/seek/error/focus loss)
 * - Smooth crossfade volume transitions (0–10s) coordinated with ducking gain
 * - Automatic pause on headphone disconnect (AUDIO_BECOMING_NOISY)
 * - Explicit AudioManager focus handling obeying DUCK, PAUSE, and IGNORE policies
 * - Android 13–16+ notification & foreground service SecurityException protection
 */
class CadencePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private lateinit var sessionBroadcaster: AudioEffectSessionBroadcaster
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val acousticEnhancer = AcousticEnhanceEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var currentSettings: AudioSettings = AudioSettings()
    private var fadeMonitorJob: Job? = null
    private var gapDelayJob: Job? = null
    private var isInInterTrackGapPause: Boolean = false
    private var isPausingForAudioFocus: Boolean = false
    private var hasAudioFocus: Boolean = false
    private var resumeOnAudioFocusGain: Boolean = false
    private var crossfadeVolumeScale: Float = 1.0f
    private var audioFocusVolumeScale: Float = 1.0f

    internal val playerForTesting: ExoPlayer?
        get() = exoPlayer

    internal fun notifyAudioSessionIdForTesting(audioSessionId: Int) {
        transitionListener.onAudioSessionIdChanged(audioSessionId)
    }

    internal fun applyAudioSettingsForTesting(settings: AudioSettings) {
        applyAudioSettings(settings)
    }

    internal fun simulateAudioFocusChangeForTesting(focusChange: Int) {
        audioFocusChangeListener.onAudioFocusChange(focusChange)
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val player = exoPlayer ?: return@OnAudioFocusChangeListener
        val behavior = currentSettings.audioFocusBehavior
        if (behavior == AudioFocusBehavior.IGNORE) {
            return@OnAudioFocusChangeListener
        }

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (behavior == AudioFocusBehavior.DUCK) {
                    audioFocusVolumeScale = DUCK_VOLUME_SCALE
                    applyEffectiveVolume()
                } else {
                    // AudioFocusBehavior.PAUSE ("Always pause")
                    cancelGapDelay()
                    resumeOnAudioFocusGain = player.playWhenReady || player.isPlaying
                    audioFocusVolumeScale = 1.0f
                    applyEffectiveVolume()
                    pauseForAudioFocus(player)
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                cancelGapDelay()
                resumeOnAudioFocusGain = player.playWhenReady || player.isPlaying
                audioFocusVolumeScale = 1.0f
                applyEffectiveVolume()
                pauseForAudioFocus(player)
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                cancelGapDelay()
                hasAudioFocus = false
                resumeOnAudioFocusGain = false
                audioFocusVolumeScale = 1.0f
                applyEffectiveVolume()
                pauseForAudioFocus(player)
                abandonAudioFocus()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                audioFocusVolumeScale = 1.0f
                applyEffectiveVolume()
                if (resumeOnAudioFocusGain) {
                    resumeOnAudioFocusGain = false
                    player.play()
                }
            }
        }
    }

    private fun pauseForAudioFocus(player: ExoPlayer) {
        isPausingForAudioFocus = true
        try {
            player.pause()
        } finally {
            isPausingForAudioFocus = false
        }
    }

    private val transitionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            if (::sessionBroadcaster.isInitialized) {
                sessionBroadcaster.onAudioSessionIdChanged(audioSessionId)
            }
            acousticEnhancer.update(currentSettings.acousticEnhanceEnabled, audioSessionId)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val player = exoPlayer ?: return
            if (playWhenReady) {
                isInInterTrackGapPause = false
                if (currentSettings.audioFocusBehavior != AudioFocusBehavior.IGNORE && !hasAudioFocus) {
                    if (!requestAudioFocus()) {
                        pauseForAudioFocus(player)
                        return
                    }
                }
            } else {
                if (!isInInterTrackGapPause && !isPausingForAudioFocus) {
                    cancelGapDelay()
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    ) {
                        resumeOnAudioFocusGain = false
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                maybeStartFadeMonitor()
            } else {
                stopFadeMonitor()
                crossfadeVolumeScale = 1.0f
                applyEffectiveVolume()
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
                cancelGapDelay()
                isInInterTrackGapPause = true
                player.pause()
                gapDelayJob = serviceScope.launch {
                    try {
                        delay(NON_GAPLESS_GAP_MS)
                        if (isActive && isInInterTrackGapPause && mediaSession != null) {
                            isInInterTrackGapPause = false
                            player.play()
                        }
                    } finally {
                        isInInterTrackGapPause = false
                    }
                }
                return
            }

            if (settings.crossfadeDurationSec > 0 && player.isPlaying) {
                maybeStartFadeMonitor()
            } else {
                crossfadeVolumeScale = 1.0f
                applyEffectiveVolume()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                cancelGapDelay()
                crossfadeVolumeScale = 1.0f
                applyEffectiveVolume()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            cancelGapDelay()
            stopFadeMonitor()
            crossfadeVolumeScale = 1.0f
            applyEffectiveVolume()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        sessionBroadcaster = AudioEffectSessionBroadcaster(applicationContext)
        val renderersFactory = FloatOutputRenderersFactory(this)
        val audioAttributes = buildMusicAudioAttributes()

        // Manage audio focus explicitly via AudioManager so AudioFocusBehavior (DUCK / PAUSE / IGNORE)
        // is strictly honored instead of ExoPlayer's fixed duck-only default.
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(transitionListener)
        exoPlayer = player

        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET && initialSessionId > 0) {
            sessionBroadcaster.onAudioSessionIdChanged(initialSessionId)
            acousticEnhancer.update(currentSettings.acousticEnhanceEnabled, initialSessionId)
        }

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

        // Always keep USAGE_MEDIA + AUDIO_CONTENT_TYPE_MUSIC so system audio enhancers
        // (Dirac / Dolby / Bluetooth music codec profiles) stay engaged.
        player.setAudioAttributes(buildMusicAudioAttributes(), /* handleAudioFocus = */ false)

        if (settings.audioFocusBehavior == AudioFocusBehavior.IGNORE) {
            resumeOnAudioFocusGain = false
            audioFocusVolumeScale = 1.0f
            applyEffectiveVolume()
            abandonAudioFocus()
        } else if (player.isPlaying && !hasAudioFocus) {
            requestAudioFocus()
        }

        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET && sessionId > 0 && ::sessionBroadcaster.isInitialized) {
            sessionBroadcaster.onAudioSessionIdChanged(sessionId)
        }
        acousticEnhancer.update(settings.acousticEnhanceEnabled, sessionId)

        if (settings.crossfadeDurationSec > 0 && player.isPlaying) {
            maybeStartFadeMonitor()
        } else {
            stopFadeMonitor()
            crossfadeVolumeScale = 1.0f
            applyEffectiveVolume()
        }
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        val result = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        PlatformAudioAttributes.Builder()
                            .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
                            .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setWillPauseWhenDucked(currentSettings.audioFocusBehavior == AudioFocusBehavior.PAUSE)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                    .also { audioFocusRequest = it }
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasAudioFocus = granted
        return granted
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusChangeListener)
            }
        }
        hasAudioFocus = false
    }

    private fun applyEffectiveVolume() {
        val player = exoPlayer ?: return
        val effective = (crossfadeVolumeScale * audioFocusVolumeScale).coerceIn(0f, 1f)
        if (kotlin.math.abs(player.volume - effective) > 0.01f) {
            player.volume = effective
        }
    }

    private fun cancelGapDelay() {
        isInInterTrackGapPause = false
        gapDelayJob?.cancel()
        gapDelayJob = null
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
                    crossfadeVolumeScale = 1.0f
                    applyEffectiveVolume()
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
                    crossfadeVolumeScale = targetVolume
                    applyEffectiveVolume()
                    val nextDelay = if (remaining < halfFade + 800L || position < halfFade + 400L) 60L else 400L
                    delay(nextDelay)
                } else {
                    crossfadeVolumeScale = 1.0f
                    applyEffectiveVolume()
                    delay(500L)
                }
            }
        }
    }

    private fun stopFadeMonitor() {
        fadeMonitorJob?.cancel()
        fadeMonitorJob = null
    }

    @OptIn(UnstableApi::class)
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        // Protect Android 13–16+ devices when POST_NOTIFICATIONS is denied or background FGS start is restricted.
        runCatching {
            super.onUpdateNotification(session, startInForegroundRequired)
        }
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
        cancelGapDelay()
        abandonAudioFocus()
        serviceScope.cancel()
        acousticEnhancer.release()
        if (::sessionBroadcaster.isInitialized) {
            sessionBroadcaster.closeActiveSession()
        }
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
        internal const val DUCK_VOLUME_SCALE = 0.22f
    }
}
