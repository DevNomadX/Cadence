package com.example.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlin.math.roundToInt

/**
 * Canonical music AudioAttributes ensuring Android's system audio enhancers
 * (Dirac / Dolby / Bluetooth music codec profiles) engage automatically.
 */
fun buildMusicAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

/**
 * Custom Media3 RenderersFactory enabling 32-bit float PCM output on DefaultAudioSink
 * for cleaner dynamic headroom on lossless and high-bitrate tracks.
 */
@Keep
@UnstableApi
class FloatOutputRenderersFactory(
    context: Context
) : DefaultRenderersFactory(context) {

    init {
        setEnableDecoderFallback(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    internal fun createAudioSinkForTesting(
        context: Context,
        enableAudioTrackPlaybackParams: Boolean = true
    ): AudioSink = buildAudioSink(
        context = context,
        enableFloatOutput = false,
        enableAudioTrackPlaybackParams = enableAudioTrackPlaybackParams
    )
}

/**
 * Lifecycle-safe broadcaster for Android's global AudioEffect control session intents
 * (`ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` / `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`).
 * Guarantees any previous session ID is closed before opening a new one, and cleanly closes
 * on service destruction.
 */
@Keep
class AudioEffectSessionBroadcaster(
    private val context: Context
) {
    private val appContext = context.applicationContext

    var activeSessionId: Int = C.AUDIO_SESSION_ID_UNSET
        private set

    fun onAudioSessionIdChanged(newSessionId: Int) {
        val validNew = if (newSessionId != C.AUDIO_SESSION_ID_UNSET && newSessionId > 0) {
            newSessionId
        } else {
            C.AUDIO_SESSION_ID_UNSET
        }
        if (validNew == activeSessionId) return

        closeActiveSession()

        if (validNew != C.AUDIO_SESSION_ID_UNSET) {
            activeSessionId = validNew
            sendSessionIntent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, validNew)
        }
    }

    fun closeActiveSession() {
        val current = activeSessionId
        if (current != C.AUDIO_SESSION_ID_UNSET && current > 0) {
            activeSessionId = C.AUDIO_SESSION_ID_UNSET
            sendSessionIntent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, current)
        }
    }

    private fun sendSessionIntent(action: String, sessionId: Int) {
        runCatching {
            val intent = Intent(action).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, appContext.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            appContext.sendBroadcast(intent)
        }
    }
}

/**
 * Abstraction for attaching/releasing the platform audio effect so [AcousticEnhanceEngine]
 * can be deterministically unit tested without relying on real DSP HAL availability.
 */
internal interface AcousticEffectHandle {
    fun release()
}

internal interface AcousticEffectBackend {
    fun attach(audioSessionId: Int): AcousticEffectHandle?
}

/**
 * Platform implementation preferring API 28+ [DynamicsProcessing] with `-2.0 dB` pre-gain
 * headroom and a gentle mastering curve (`+2.0 dB` @ 60 Hz, `-1.0 dB` @ 400 Hz, `+2.0 dB` @ 12 kHz),
 * falling back cleanly to [Equalizer] with `-200 mB` headroom offset clamped to hardware range.
 * Every native AudioEffect call is wrapped in `runCatching` so USB DACs or unsupported devices never crash.
 */
internal object PlatformAcousticEffectBackend : AcousticEffectBackend {

    override fun attach(audioSessionId: Int): AcousticEffectHandle? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dpHandle = runCatching { attachDynamicsProcessing(audioSessionId) }.getOrNull()
            if (dpHandle != null) return dpHandle
        }
        return runCatching { attachFallbackEqualizer(audioSessionId) }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun attachDynamicsProcessing(audioSessionId: Int): AcousticEffectHandle {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ true,
            /* preEqBandCount = */ AcousticEnhanceEngine.PRE_EQ_BANDS.size,
            /* mbcInUse = */ false,
            /* mbcBandCount = */ 0,
            /* postEqInUse = */ false,
            /* postEqBandCount = */ 0,
            /* limiterInUse = */ true
        ).build()

        val dp = DynamicsProcessing(0, audioSessionId, config)
        try {
            dp.setInputGainAllChannelsTo(AcousticEnhanceEngine.INPUT_PRE_GAIN_DB)

            val preEq = DynamicsProcessing.Eq(
                /* inUse = */ true,
                /* enabled = */ true,
                /* bandCount = */ AcousticEnhanceEngine.PRE_EQ_BANDS.size
            )
            AcousticEnhanceEngine.PRE_EQ_BANDS.forEachIndexed { index, bandSpec ->
                val band = preEq.getBand(index)
                band.isEnabled = true
                band.cutoffFrequency = bandSpec.cutoffHz
                band.gain = bandSpec.gainDb
            }
            dp.setPreEqAllChannelsTo(preEq)

            val limiter = DynamicsProcessing.Limiter(
                /* inUse = */ true,
                /* enabled = */ true,
                /* linkGroup = */ 0,
                /* attackTime = */ 2.0f,
                /* releaseTime = */ 60.0f,
                /* ratio = */ 4.0f,
                /* threshold = */ -0.5f,
                /* postGain = */ 0.0f
            )
            dp.setLimiterAllChannelsTo(limiter)
            dp.enabled = true
        } catch (t: Throwable) {
            runCatching { dp.release() }
            throw t
        }

        return object : AcousticEffectHandle {
            override fun release() {
                runCatching { dp.enabled = false }
                runCatching { dp.release() }
            }
        }
    }

    private fun attachFallbackEqualizer(audioSessionId: Int): AcousticEffectHandle {
        val eq = Equalizer(0, audioSessionId)
        try {
            val range = eq.bandLevelRange
            val minMb = range.getOrNull(0)?.toInt() ?: -1500
            val maxMb = range.getOrNull(1)?.toInt() ?: 1500
            val bandCount = eq.numberOfBands.toInt().coerceAtLeast(0)

            for (bandIdx in 0 until bandCount) {
                val band = bandIdx.toShort()
                val centerHz = (eq.getCenterFreq(band) / 1000).coerceAtLeast(20)
                val targetMb = AcousticEnhanceEngine.computeFallbackEqualizerMillibels(
                    centerFreqHz = centerHz,
                    minBandMb = minMb,
                    maxBandMb = maxMb
                )
                eq.setBandLevel(band, targetMb)
            }
            eq.enabled = true
        } catch (t: Throwable) {
            runCatching { eq.release() }
            throw t
        }

        return object : AcousticEffectHandle {
            override fun release() {
                runCatching { eq.enabled = false }
                runCatching { eq.release() }
            }
        }
    }
}

/**
 * Lightweight, lifecycle-safe Acoustic Enhance mastering engine.
 * Guarantees zero attached effects when disabled (never clamping playback volume or output gain),
 * automatic re-attachment on audio session changes while enabled, and full exception isolation.
 */
@Keep
class AcousticEnhanceEngine internal constructor(
    private val backend: AcousticEffectBackend = PlatformAcousticEffectBackend
) {
    data class PreEqBandSpec(
        val cutoffHz: Float,
        val gainDb: Float
    )

    private var activeHandle: AcousticEffectHandle? = null

    var attachedSessionId: Int = C.AUDIO_SESSION_ID_UNSET
        private set

    val isAttached: Boolean
        get() = activeHandle != null && attachedSessionId != C.AUDIO_SESSION_ID_UNSET

    fun update(enabled: Boolean, audioSessionId: Int) {
        val validSession = audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId > 0
        if (!enabled || !validSession) {
            release()
            return
        }
        if (isAttached && attachedSessionId == audioSessionId) {
            return
        }
        release()
        val handle = runCatching { backend.attach(audioSessionId) }.getOrNull()
        if (handle != null) {
            activeHandle = handle
            attachedSessionId = audioSessionId
        }
    }

    fun release() {
        val handle = activeHandle
        activeHandle = null
        attachedSessionId = C.AUDIO_SESSION_ID_UNSET
        if (handle != null) {
            runCatching { handle.release() }
        }
    }

    companion object {
        const val INPUT_PRE_GAIN_DB: Float = -2.0f
        const val FALLBACK_HEADROOM_OFFSET_MB: Int = -200

        /**
         * Gentle mastering curve with -2.0 dB pre-gain headroom:
         * - +2.0 dB warmth around 60 Hz
         * -  0.0 dB neutral transition around 200 Hz
         * - -1.0 dB muddiness cut around 400 Hz
         * - +0.5 dB subtle vocal presence around 3 kHz
         * - +2.0 dB high-frequency air around 12 kHz
         */
        val PRE_EQ_BANDS: List<PreEqBandSpec> = listOf(
            PreEqBandSpec(cutoffHz = 95f, gainDb = 2.0f),
            PreEqBandSpec(cutoffHz = 250f, gainDb = 0.0f),
            PreEqBandSpec(cutoffHz = 700f, gainDb = -1.0f),
            PreEqBandSpec(cutoffHz = 6_000f, gainDb = 0.5f),
            PreEqBandSpec(cutoffHz = 20_000f, gainDb = 2.0f)
        )

        /**
         * Maps any standard Android Equalizer band center frequency (Hz) to the gentle
         * Acoustic Enhance target millibels including a -200 mB headroom offset to prevent clipping.
         */
        fun computeFallbackEqualizerMillibels(
            centerFreqHz: Int,
            minBandMb: Int,
            maxBandMb: Int
        ): Short {
            val curveDb = when {
                centerFreqHz <= 120 -> 2.0f
                centerFreqHz <= 240 -> 0.5f
                centerFreqHz <= 650 -> -1.0f
                centerFreqHz <= 2_500 -> 0.0f
                centerFreqHz <= 6_500 -> 0.8f
                else -> 2.0f
            }
            val targetMb = (curveDb * 100f).roundToInt() + FALLBACK_HEADROOM_OFFSET_MB
            return targetMb.coerceIn(minBandMb, maxBandMb).toShort()
        }
    }
}
