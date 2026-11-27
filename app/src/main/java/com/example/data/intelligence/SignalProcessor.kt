package com.example.data.intelligence

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlin.math.min

/**
 * Bounded playback telemetry signal categories produced by [SignalProcessor].
 */
@Keep
enum class SignalType {
    /** Track played past 80% of its total duration -> positive affinity boost. */
    COMPLETION_BOOST,

    /** Track skipped before min(trackDurationMs * 0.25, 45_000L) after >= 5s active play -> negative penalty. */
    EARLY_SKIP_PENALTY,

    /** User explicitly liked/favorited the track -> immediate high-weight affinity boost. */
    MANUAL_FAVORITE_BOOST
}

/**
 * Lightweight, immutable normalized telemetry struct emitted by [SignalProcessor].
 * Completely decoupled from Compose UI, ViewModels, and Room entities.
 */
@Keep
@Immutable
data class PlaybackSignal(
    val trackId: String,
    val type: SignalType,
    val affinityDelta: Float,
    val activePlayDurationMs: Long,
    val trackDurationMs: Long,
    val completionRatio: Float,
    val timestampMs: Long
)

/**
 * Raw playback interaction input consumed by [SignalProcessor].
 */
@Keep
@Immutable
data class RawPlaybackEvent(
    val trackId: String,
    val activePlayDurationMs: Long,
    val trackDurationMs: Long,
    val wasScrubJump: Boolean = false,
    val wasUserSkip: Boolean = false,
    val wasManualFavorite: Boolean = false,
    val timestampMs: Long
)

/**
 * Pure Kotlin telemetry & signal bounding layer that normalizes raw playback interactions into
 * lightweight, immutable [PlaybackSignal] structs while enforcing strict anti-noise boundaries:
 *
 * 1. **Noise Filter:** Mid-track scrubbing jumps and brief accidental taps (`activePlayDurationMs < 5_000L`)
 *    are ignored completely so they never pollute affinity scores.
 * 2. **Capped Early-Skip Penalty:** Skips before `min(trackDurationMs * 0.25, 45_000L)` register a
 *    negative affinity penalty; the 45-second cap prevents long mixes or podcasts from being penalized
 *    after several minutes of listening.
 * 3. **Completion Boost:** Listens strictly past `80%` of track duration register a positive affinity boost.
 * 4. **Manual Favorite Boost:** Liking/favoriting a track emits an immediate high-weight affinity boost.
 */
@Keep
object SignalProcessor {

    /** Minimum active listening time (ms) required before any playback or skip signal is accepted. */
    const val MIN_ACTIVE_PLAY_MS: Long = 5_000L

    /** Fractional threshold below which a user skip counts as an early skip (25%). */
    const val EARLY_SKIP_RATIO_THRESHOLD: Double = 0.25

    /** Hard upper cap (ms) on the early-skip threshold (45 seconds) to protect long tracks & mixes. */
    const val EARLY_SKIP_MAX_CAP_MS: Long = 45_000L

    /** Fractional threshold past which a listen counts as a full completion (> 80%). */
    const val COMPLETION_RATIO_THRESHOLD: Double = 0.80

    /** Normalized affinity deltas applied by each bounded signal type. */
    const val COMPLETION_BOOST_DELTA: Float = 0.18f
    const val EARLY_SKIP_PENALTY_DELTA: Float = -0.22f
    const val MANUAL_FAVORITE_BOOST_DELTA: Float = 0.35f

    /**
     * Computes the exact early-skip cutoff in milliseconds: `min(trackDurationMs * 0.25, 45_000L)`.
     * Returns `0L` for invalid/non-positive track durations.
     */
    fun earlySkipCutoffMs(trackDurationMs: Long): Long {
        if (trackDurationMs <= 0L) return 0L
        val quarterTrackMs = (trackDurationMs.toDouble() * EARLY_SKIP_RATIO_THRESHOLD).toLong()
        return min(quarterTrackMs, EARLY_SKIP_MAX_CAP_MS).coerceAtLeast(0L)
    }

    /**
     * Normalizes a [RawPlaybackEvent] into a bounded [PlaybackSignal], or returns `null` when
     * the event falls inside a noise boundary (blank trackId, scrub jump, `< 5s` tap, or neutral
     * mid-track skip between the early-skip cutoff and 80% completion).
     */
    fun process(event: RawPlaybackEvent): PlaybackSignal? {
        val cleanId = event.trackId.trim()
        if (cleanId.isEmpty()) return null

        val safeDuration = event.trackDurationMs.coerceAtLeast(0L)
        val safeActivePlay = event.activePlayDurationMs.coerceAtLeast(0L)

        // 1. Explicit manual favorite applies an immediate high-weight boost regardless of play duration.
        if (event.wasManualFavorite) {
            val ratio = computeCompletionRatio(safeActivePlay, safeDuration)
            return PlaybackSignal(
                trackId = cleanId,
                type = SignalType.MANUAL_FAVORITE_BOOST,
                affinityDelta = MANUAL_FAVORITE_BOOST_DELTA,
                activePlayDurationMs = safeActivePlay,
                trackDurationMs = safeDuration,
                completionRatio = ratio,
                timestampMs = event.timestampMs
            )
        }

        // 2. Noise filter: ignore scrub jumps, invalid durations, and accidental < 5s taps.
        if (event.wasScrubJump || safeDuration <= 0L || safeActivePlay < MIN_ACTIVE_PLAY_MS) {
            return null
        }

        val completionRatioDouble = (safeActivePlay.toDouble() / safeDuration.toDouble()).coerceIn(0.0, 1.0)
        val completionRatioFloat = completionRatioDouble.toFloat()

        // 3. Completion boost (> 80% of track duration).
        if (completionRatioDouble > COMPLETION_RATIO_THRESHOLD) {
            return PlaybackSignal(
                trackId = cleanId,
                type = SignalType.COMPLETION_BOOST,
                affinityDelta = COMPLETION_BOOST_DELTA,
                activePlayDurationMs = safeActivePlay,
                trackDurationMs = safeDuration,
                completionRatio = completionRatioFloat,
                timestampMs = event.timestampMs
            )
        }

        // 4. Capped early-skip penalty (< min(trackDurationMs * 0.25, 45_000L)).
        if (event.wasUserSkip) {
            val cutoffMs = earlySkipCutoffMs(safeDuration)
            if (safeActivePlay < cutoffMs) {
                return PlaybackSignal(
                    trackId = cleanId,
                    type = SignalType.EARLY_SKIP_PENALTY,
                    affinityDelta = EARLY_SKIP_PENALTY_DELTA,
                    activePlayDurationMs = safeActivePlay,
                    trackDurationMs = safeDuration,
                    completionRatio = completionRatioFloat,
                    timestampMs = event.timestampMs
                )
            }
        }

        // Neutral zone (e.g., listened past early-skip cutoff / 45s cap without exceeding 80% completion).
        return null
    }

    /**
     * Convenience helper for emitting an immediate high-weight [SignalType.MANUAL_FAVORITE_BOOST]
     * when the user favorites a track. Returns `null` if [isFavorite] is `false` or [trackId] is blank.
     */
    fun processFavoriteToggle(
        trackId: String,
        isFavorite: Boolean,
        trackDurationMs: Long = 0L,
        activePlayDurationMs: Long = 0L,
        timestampMs: Long
    ): PlaybackSignal? {
        if (!isFavorite) return null
        return process(
            RawPlaybackEvent(
                trackId = trackId,
                activePlayDurationMs = activePlayDurationMs,
                trackDurationMs = trackDurationMs,
                wasManualFavorite = true,
                timestampMs = timestampMs
            )
        )
    }

    /**
     * Filters and normalizes a batch of raw events deterministically while preserving chronological order.
     */
    fun processBatch(events: List<RawPlaybackEvent>): List<PlaybackSignal> {
        if (events.isEmpty()) return emptyList()
        val result = ArrayList<PlaybackSignal>(events.size)
        for (i in events.indices) {
            val signal = process(events[i])
            if (signal != null) {
                result.add(signal)
            }
        }
        return result
    }

    private fun computeCompletionRatio(activePlayMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L || activePlayMs <= 0L) return 0f
        return (activePlayMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }
}
