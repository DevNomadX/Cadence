package com.example.data.intelligence

import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.data.StableList
import com.example.data.StableMap
import com.example.data.Track
import com.example.data.toStableList
import com.example.data.toStableMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Explicit time-of-day category buckets used by [RecommendationCore] for deterministic
 * contextual weighting.
 */
@Keep
enum class TimeOfDayCategory {
    MORNING,    // 05:00 - 11:59
    AFTERNOON,  // 12:00 - 16:59
    EVENING,    // 17:00 - 20:59
    LATE_NIGHT; // 21:00 - 04:59

    companion object {
        fun fromHour(hourOfDay: Int): TimeOfDayCategory {
            val h = ((hourOfDay % 24) + 24) % 24
            return when (h) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..20 -> EVENING
                else -> LATE_NIGHT
            }
        }
    }
}

/**
 * Primitive Structure-of-Arrays (SoA) feature matrix for zero-boxing, low-GC inference on
 * constrained RAM devices. Every parallel array has identical length [size].
 */
@Keep
class TrackFeatureMatrix(
    val trackIds: Array<String>,
    val playCounts: IntArray,
    val completedPlayCounts: IntArray,
    val skipCounts: IntArray,
    val lastPlayedAtMs: LongArray,
    val durationMs: LongArray,
    val favoriteFlags: IntArray,
    val categoryBitmasks: IntArray,
    val bpmValues: IntArray = IntArray(trackIds.size) { 96 },
    val artistHashes: IntArray = IntArray(trackIds.size) { it }
) {
    val size: Int get() = trackIds.size

    init {
        val n = trackIds.size
        require(playCounts.size == n) { "playCounts size mismatch: ${playCounts.size} != $n" }
        require(completedPlayCounts.size == n) { "completedPlayCounts size mismatch: ${completedPlayCounts.size} != $n" }
        require(skipCounts.size == n) { "skipCounts size mismatch: ${skipCounts.size} != $n" }
        require(lastPlayedAtMs.size == n) { "lastPlayedAtMs size mismatch: ${lastPlayedAtMs.size} != $n" }
        require(durationMs.size == n) { "durationMs size mismatch: ${durationMs.size} != $n" }
        require(favoriteFlags.size == n) { "favoriteFlags size mismatch: ${favoriteFlags.size} != $n" }
        require(categoryBitmasks.size == n) { "categoryBitmasks size mismatch: ${categoryBitmasks.size} != $n" }
        require(bpmValues.size == n) { "bpmValues size mismatch: ${bpmValues.size} != $n" }
        require(artistHashes.size == n) { "artistHashes size mismatch: ${artistHashes.size} != $n" }
    }

    companion object {
        const val CATEGORY_CALM_NOCTURNAL: Int = 1 shl 0
        const val CATEGORY_ACOUSTIC_FOCUS: Int = 1 shl 1
        const val CATEGORY_WARM_SOULFUL: Int = 1 shl 2
        const val CATEGORY_UPBEAT_ENERGETIC: Int = 1 shl 3

        /**
         * Packs a list of domain [Track] items into a deduplicated primitive [TrackFeatureMatrix],
         * discarding blank IDs and duplicate entries deterministically.
         */
        fun fromTracks(tracks: List<Track>): TrackFeatureMatrix {
            if (tracks.isEmpty()) {
                return TrackFeatureMatrix(
                    trackIds = emptyArray(),
                    playCounts = IntArray(0),
                    completedPlayCounts = IntArray(0),
                    skipCounts = IntArray(0),
                    lastPlayedAtMs = LongArray(0),
                    durationMs = LongArray(0),
                    favoriteFlags = IntArray(0),
                    categoryBitmasks = IntArray(0),
                    bpmValues = IntArray(0),
                    artistHashes = IntArray(0)
                )
            }

            val deduped = ArrayList<Track>(tracks.size)
            val seenIds = HashSet<String>(tracks.size * 2)
            for (i in tracks.indices) {
                val t = tracks[i]
                val cleanId = t.id.trim()
                if (cleanId.isNotEmpty() && seenIds.add(cleanId)) {
                    deduped.add(t)
                }
            }

            val n = deduped.size
            val ids = Array(n) { "" }
            val plays = IntArray(n)
            val completed = IntArray(n)
            val skips = IntArray(n)
            val lastPlayed = LongArray(n)
            val durations = LongArray(n)
            val favorites = IntArray(n)
            val categories = IntArray(n)
            val bpms = IntArray(n)
            val artists = IntArray(n)

            for (i in 0 until n) {
                val t = deduped[i]
                ids[i] = t.id.trim()
                plays[i] = t.playCount.coerceAtLeast(0)
                completed[i] = t.completedPlayCount.coerceAtLeast(0)
                skips[i] = t.skipCount.coerceAtLeast(0)
                lastPlayed[i] = t.lastPlayedAt.coerceAtLeast(0L)
                durations[i] = t.durationMs.coerceAtLeast(0L)
                favorites[i] = if (t.isFavorite) 1 else 0
                categories[i] = encodeCategoryBitmask(t)
                bpms[i] = if (t.bpm > 0) t.bpm.coerceIn(45, 200) else inferDefaultBpm(categories[i])
                artists[i] = LocalMoodAndAffinityEngine.normalizedArtistKey(t.artist).hashCode()
            }

            return TrackFeatureMatrix(
                trackIds = ids,
                playCounts = plays,
                completedPlayCounts = completed,
                skipCounts = skips,
                lastPlayedAtMs = lastPlayed,
                durationMs = durations,
                favoriteFlags = favorites,
                categoryBitmasks = categories,
                bpmValues = bpms,
                artistHashes = artists
            )
        }

        internal fun encodeCategoryBitmask(track: Track): Int {
            var mask = 0
            val tags = track.resolvedMoodTags()
            for (i in tags.indices) {
                when (tags[i]) {
                    SmartMoodTag.LATE_NIGHT -> mask = mask or CATEGORY_CALM_NOCTURNAL
                    SmartMoodTag.ACOUSTIC -> mask = mask or CATEGORY_ACOUSTIC_FOCUS
                    SmartMoodTag.SOULFUL -> mask = mask or CATEGORY_WARM_SOULFUL
                    SmartMoodTag.UPBEAT -> mask = mask or CATEGORY_UPBEAT_ENERGETIC
                    SmartMoodTag.LOSSLESS_HIFI -> Unit
                }
            }
            if (mask == 0) {
                mask = when {
                    track.bpm >= 118 -> CATEGORY_UPBEAT_ENERGETIC
                    track.bpm in 1..88 -> CATEGORY_CALM_NOCTURNAL or CATEGORY_ACOUSTIC_FOCUS
                    else -> CATEGORY_WARM_SOULFUL
                }
            }
            return mask
        }

        internal fun moodNameToCategoryBit(mood: String?): Int = when (mood?.trim()?.lowercase(Locale.ROOT)) {
            "nocturnal", "calm", "late_night", "melancholic", "chill", "ambient" -> CATEGORY_CALM_NOCTURNAL
            "organic", "focused", "acoustic", "minimal", "focus", "study" -> CATEGORY_ACOUSTIC_FOCUS
            "warm", "soulful", "cozy", "sunset" -> CATEGORY_WARM_SOULFUL
            "energetic", "uplifting", "upbeat", "energy", "workout", "bright" -> CATEGORY_UPBEAT_ENERGETIC
            else -> 0
        }

        internal fun smartMoodTagToCategoryBit(tag: SmartMoodTag): Int = when (tag) {
            SmartMoodTag.LATE_NIGHT -> CATEGORY_CALM_NOCTURNAL
            SmartMoodTag.ACOUSTIC -> CATEGORY_ACOUSTIC_FOCUS
            SmartMoodTag.SOULFUL -> CATEGORY_WARM_SOULFUL
            SmartMoodTag.UPBEAT -> CATEGORY_UPBEAT_ENERGETIC
            SmartMoodTag.LOSSLESS_HIFI -> 0
        }

        private fun inferDefaultBpm(categoryMask: Int): Int = when {
            (categoryMask and CATEGORY_UPBEAT_ENERGETIC) != 0 -> 122
            (categoryMask and CATEGORY_CALM_NOCTURNAL) != 0 -> 78
            (categoryMask and CATEGORY_ACOUSTIC_FOCUS) != 0 -> 92
            else -> 96
        }
    }
}

/**
 * Immutable result emitted by [RecommendationCore.generatePlaylist], exposing both the
 * ordered distinct track IDs and bucket allocation telemetry for deterministic testing.
 */
@Keep
@Immutable
data class RecommendationResult(
    val trackIds: StableList<String>,
    val scoresByTrackId: StableMap<String, Float>,
    val favoritesCount: Int,
    val forgottenGemsCount: Int,
    val underPlayedCount: Int,
    val waterfallFallbackCount: Int
)

/**
 * Standalone, deterministic primitive-array recommendation engine — the single source of truth
 * for Cadence's affinity scoring, Daily Picks, Discover Routine Mixes, Mood Playlists, Made for You,
 * Smart Mix, Tune Mix, and Listening Insights. Completely isolated from Jetpack Compose UI,
 * Android Context, ViewModels, and Room DAOs.
 *
 * Key properties:
 * - Uses primitive arrays (`FloatArray`, `IntArray`, `LongArray`, `BooleanArray`) to eliminate
 *   boxing and garbage-collection spikes on low-RAM devices.
 * - Combines normalized completion rates, logarithmic recency decay, bounded [PlaybackSignal]
 *   history, and explicit [TimeOfDayCategory] weighting into clamped `0.0f..1.0f` affinity scores.
 * - Enforces the `60%` high-affinity favorites / `25%` forgotten gems / `15%` under-played
 *   distribution using a deterministic waterfall fallback so cold starts and small libraries
 *   (`< 10` tracks) always produce duplicate-free, bounded lists of size `min(targetSize, available)`.
 */
@Keep
object RecommendationCore {

    const val DAILY_PICKS_MAX_SIZE: Int = 6
    const val SESSION_SNAPSHOT_MAX_SIZE: Int = 10

    const val FAVORITES_BUCKET_RATIO: Float = 0.60f
    const val FORGOTTEN_GEMS_BUCKET_RATIO: Float = 0.25f
    const val UNDERPLAYED_BUCKET_RATIO: Float = 0.15f

    const val ONE_HOUR_MS: Long = 60L * 60L * 1000L
    const val ONE_DAY_MS: Long = 24L * ONE_HOUR_MS
    const val TWENTY_FOUR_HOURS_MS: Long = ONE_DAY_MS
    const val FORGOTTEN_GEM_MIN_AGE_MS: Long = 21L * ONE_DAY_MS

    private const val BASE_PRIOR_SCORE: Float = 0.44f
    private val UNKNOWN_ARTIST_HASH: Int = "unknown artist".hashCode()

    /**
     * Computes deterministic normalized affinity scores (`0.0f..1.0f`) aligned 1:1 with
     * [matrix] indices using primitive arrays exclusively.
     */
    fun computeAffinityScores(
        matrix: TrackFeatureMatrix,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long,
        timeOfDay: TimeOfDayCategory
    ): FloatArray {
        val n = matrix.size
        if (n == 0) return FloatArray(0)

        val signalCompletions = IntArray(n)
        val signalSkips = IntArray(n)
        val signalFavoriteFlags = IntArray(n)
        val signalDeltaSums = FloatArray(n)
        val effectiveLastPlayed = LongArray(n) { matrix.lastPlayedAtMs[it] }

        accumulateSignalsIntoPrimitives(
            matrix = matrix,
            signals = signals,
            signalCompletions = signalCompletions,
            signalSkips = signalSkips,
            signalFavoriteFlags = signalFavoriteFlags,
            signalDeltaSums = signalDeltaSums,
            effectiveLastPlayed = effectiveLastPlayed
        )

        var maxEffectivePlays = 1
        for (i in 0 until n) {
            val totalCompleted = matrix.completedPlayCounts[i] + signalCompletions[i]
            val totalSkips = matrix.skipCounts[i] + signalSkips[i]
            val totalPlays = max(matrix.playCounts[i], totalCompleted + totalSkips)
            if (totalPlays > maxEffectivePlays) {
                maxEffectivePlays = totalPlays
            }
        }
        val logMaxPlaysDenom = ln(maxEffectivePlays.toDouble() + 2.0)
        val log25 = ln(25.0)

        val scores = FloatArray(n)
        for (i in 0 until n) {
            scores[i] = computeSingleTrackScore(
                playCount = matrix.playCounts[i],
                completedCount = matrix.completedPlayCounts[i] + signalCompletions[i],
                skipCount = matrix.skipCounts[i] + signalSkips[i],
                isFavorite = matrix.favoriteFlags[i] != 0 || signalFavoriteFlags[i] != 0,
                signalDeltaSum = signalDeltaSums[i],
                lastPlayedAtMs = effectiveLastPlayed[i],
                categoryMask = matrix.categoryBitmasks[i],
                nowMs = nowMs,
                timeOfDay = timeOfDay,
                logMaxPlaysDenom = logMaxPlaysDenom,
                log25 = log25
            )
        }

        return scores
    }

    /**
     * Computes a detailed [TrackAffinityBreakdown] for a single [Track] using the exact same
     * primitive scoring equations as [computeAffinityScores].
     */
    fun computeAffinityBreakdown(
        track: Track,
        nowMs: Long = System.currentTimeMillis(),
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.fromHour(
            LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
        ),
        maxLibraryPlays: Int = 10,
        sessionSkipped: Boolean = false
    ): TrackAffinityBreakdown {
        val plays = track.playCount.coerceAtLeast(0)
        val completed = track.completedPlayCount.coerceAtLeast(0)
        val skips = track.skipCount.coerceAtLeast(0)
        val totalPlays = max(plays, completed + skips)
        val safeMaxPlays = max(1, max(maxLibraryPlays, totalPlays))
        val logMaxPlaysDenom = ln(safeMaxPlays.toDouble() + 2.0)
        val log25 = ln(25.0)

        val completionRate = if (totalPlays > 0) {
            (completed.toFloat() / totalPlays.toFloat()).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val completionDepth = if (completed > 0) {
            (ln(completed.toDouble() + 1.0) / logMaxPlaysDenom).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val playVolume = if (totalPlays > 0) {
            (ln(totalPlays.toDouble() + 1.0) / logMaxPlaysDenom).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val completionSignal = (
            (if (totalPlays > 0) (completionRate - 0.5f) * 0.24f else 0f) +
                completionDepth * 0.20f +
                playVolume * 0.06f +
                (if (track.isFavorite) 0.18f else 0f)
            ).coerceIn(-0.14f, 0.46f)

        val skipRatio = if (totalPlays > 0) {
            (skips.toFloat() / totalPlays.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val sessionSkipExtra = if (sessionSkipped) 0.14f else 0f
        val skipPenalty = (
            skipRatio * 0.24f +
                min(skips, 6) * 0.03f +
                sessionSkipExtra
            ).coerceIn(0f, 0.46f)

        val categoryMask = TrackFeatureMatrix.encodeCategoryBitmask(track)
        val timeOfDayBoost = computeTimeOfDayWeight(categoryMask, timeOfDay)
        val recencyAdjustment = computeLogarithmicRecency(
            lastPlayedAtMs = track.lastPlayedAt,
            nowMs = nowMs,
            isHighAffinity = track.isFavorite || completed >= 2 || (totalPlays >= 3 && skips <= completed + 1),
            totalCompleted = completed,
            totalSkips = skips,
            log25 = log25
        )

        val raw = BASE_PRIOR_SCORE + completionSignal - skipPenalty + timeOfDayBoost + recencyAdjustment
        val finalScore = if (raw.isFinite()) raw.coerceIn(0f, 1f) else BASE_PRIOR_SCORE

        return TrackAffinityBreakdown(
            score = finalScore,
            completionSignal = if (completionSignal.isFinite()) completionSignal else 0f,
            skipPenalty = if (skipPenalty.isFinite()) skipPenalty else 0f,
            timeOfDayBoost = if (timeOfDayBoost.isFinite()) timeOfDayBoost else 0f,
            recencyAdjustment = if (recencyAdjustment.isFinite()) recencyAdjustment else 0f
        )
    }

    /**
     * Generates a deterministic, duplicate-free playlist targeting `60%` high-affinity favorites,
     * `25%` forgotten gems, and `15%` under-played tracks using a waterfall fallback when any
     * bucket runs dry or during cold starts.
     *
     * Output length is strictly capped at `min(targetSize, availableValidTracks)`.
     */
    fun generatePlaylist(
        matrix: TrackFeatureMatrix,
        targetSize: Int,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long,
        timeOfDay: TimeOfDayCategory
    ): RecommendationResult = generateTunedPlaylist(
        matrix = matrix,
        targetSize = targetSize,
        signals = signals,
        nowMs = nowMs,
        timeOfDay = timeOfDay
    )

    /**
     * Full-featured deterministic primitive-array playlist generator unifying Daily Picks,
     * Discover Routine Mixes, Mood Playlists, Made for You, Smart Mix, and Tune Mix.
     */
    fun generateTunedPlaylist(
        matrix: TrackFeatureMatrix,
        targetSize: Int,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long,
        timeOfDay: TimeOfDayCategory,
        tuneConfig: DiscoverTuneConfig? = null,
        preferredCategoryMask: Int = 0,
        seedTrackId: String? = null,
        excludedTrackIds: Set<String> = emptySet(),
        rotationSeed: Long? = null,
        rotationWeight: Float = 0f,
        maxPerArtist: Int = 0,
        strictArtistCap: Boolean = false
    ): RecommendationResult {
        val n = matrix.size
        val effectiveTarget = min(targetSize.coerceAtLeast(0), n)
        if (effectiveTarget == 0) {
            return RecommendationResult(
                trackIds = StableList(),
                scoresByTrackId = StableMap(),
                favoritesCount = 0,
                forgottenGemsCount = 0,
                underPlayedCount = 0,
                waterfallFallbackCount = 0
            )
        }

        val baseScores = computeAffinityScores(
            matrix = matrix,
            signals = signals,
            nowMs = nowMs,
            timeOfDay = timeOfDay
        )

        // Apply optional contextual tuning, category preference, seed anchor, and deterministic rotation
        val effectiveScores = if (
            tuneConfig == null &&
            preferredCategoryMask == 0 &&
            seedTrackId == null &&
            excludedTrackIds.isEmpty() &&
            rotationSeed == null
        ) {
            baseScores
        } else {
            val tuned = FloatArray(n)
            val tuneMoodMask = TrackFeatureMatrix.moodNameToCategoryBit(tuneConfig?.selectedMood)
            val energyKey = tuneConfig?.energyFilter?.trim()?.lowercase(Locale.ROOT)
            val cleanRotationWeight = rotationWeight.coerceIn(0f, 0.80f)
            val tasteWeight = 1f - cleanRotationWeight
            val seedIdx = if (seedTrackId != null) {
                matrix.trackIds.indexOfFirst { it == seedTrackId }
            } else {
                -1
            }
            val seedMask = if (seedIdx >= 0) matrix.categoryBitmasks[seedIdx] else 0
            val seedBpm = if (seedIdx >= 0) matrix.bpmValues[seedIdx] else 0

            for (i in 0 until n) {
                var s = baseScores[i]
                val mask = matrix.categoryBitmasks[i]
                val bpm = matrix.bpmValues[i]

                if (preferredCategoryMask != 0) {
                    val overlap = mask and preferredCategoryMask
                    s += if (overlap != 0) 0.16f else -0.07f
                }
                if (tuneMoodMask != 0) {
                    s += if ((mask and tuneMoodMask) != 0) 0.18f else -0.07f
                }
                if (energyKey != null) {
                    s += when (energyKey) {
                        "low" -> if (bpm <= 95 || (mask and (TrackFeatureMatrix.CATEGORY_CALM_NOCTURNAL or TrackFeatureMatrix.CATEGORY_ACOUSTIC_FOCUS)) != 0) 0.16f else -0.14f
                        "high" -> if (bpm >= 114 || (mask and TrackFeatureMatrix.CATEGORY_UPBEAT_ENERGETIC) != 0) 0.18f else -0.14f
                        "medium" -> if (bpm in 88..118 || (mask and TrackFeatureMatrix.CATEGORY_WARM_SOULFUL) != 0) 0.12f else -0.05f
                        else -> 0f
                    }
                }
                if (seedIdx >= 0) {
                    if (i == seedIdx) {
                        s = 0.99f
                    } else {
                        if (seedMask != 0 && (mask and seedMask) != 0) s += 0.10f
                        if (seedBpm > 0 && abs(bpm - seedBpm) <= 14) s += 0.08f
                    }
                }
                if (excludedTrackIds.isNotEmpty() && matrix.trackIds[i] in excludedTrackIds) {
                    s -= 0.24f
                }
                val clampedBase = if (s.isFinite()) s.coerceIn(0f, 1f) else BASE_PRIOR_SCORE
                tuned[i] = if (rotationSeed != null && cleanRotationWeight > 0f && i != seedIdx) {
                    val roll = deterministicUnitRoll(rotationSeed, matrix.trackIds[i])
                    (clampedBase * tasteWeight + roll * cleanRotationWeight).coerceIn(0f, 1f)
                } else {
                    clampedBase
                }
            }
            tuned
        }

        // Compute effective per-track history incorporating bounded signals for accurate bucketing
        val signalCompletions = IntArray(n)
        val signalSkips = IntArray(n)
        val signalFavoriteFlags = IntArray(n)
        val signalDeltaSums = FloatArray(n)
        val effectiveLastPlayed = LongArray(n) { matrix.lastPlayedAtMs[it] }
        accumulateSignalsIntoPrimitives(
            matrix = matrix,
            signals = signals,
            signalCompletions = signalCompletions,
            signalSkips = signalSkips,
            signalFavoriteFlags = signalFavoriteFlags,
            signalDeltaSums = signalDeltaSums,
            effectiveLastPlayed = effectiveLastPlayed
        )

        // Build and sort master candidate index array deterministically (score DESC, trackId ASC)
        val masterIndices = IntArray(n) { it }
        sortIndicesByScoreDesc(masterIndices, effectiveScores, matrix.trackIds)

        // Partition indices into primitive bucket arrays while preserving sorted score order
        val favBucket = IntArray(n)
        var favLen = 0
        val forgottenBucket = IntArray(n)
        var forgottenLen = 0
        val underplayedBucket = IntArray(n)
        var underplayedLen = 0

        for (pos in 0 until n) {
            val idx = masterIndices[pos]
            val totalCompleted = matrix.completedPlayCounts[idx] + signalCompletions[idx]
            val totalSkips = matrix.skipCounts[idx] + signalSkips[idx]
            val totalPlays = max(matrix.playCounts[idx], totalCompleted + totalSkips)
            val isFavorite = matrix.favoriteFlags[idx] != 0 || signalFavoriteFlags[idx] != 0
            val lastPlayed = effectiveLastPlayed[idx]
            val ageMs = if (lastPlayed > 0L) (nowMs - lastPlayed).coerceAtLeast(0L) else -1L

            val isHighAffinity = isFavorite ||
                totalCompleted >= 2 ||
                (totalPlays >= 2 && totalSkips <= totalCompleted + 1)

            val isForgottenGem = ageMs >= FORGOTTEN_GEM_MIN_AGE_MS &&
                isHighAffinity &&
                totalSkips <= max(1, totalCompleted)

            val isUnderPlayed = totalPlays <= 1 && totalSkips <= 1 && !isForgottenGem

            val isFavoriteCandidate = (isHighAffinity || effectiveScores[idx] >= 0.56f) &&
                !isForgottenGem &&
                !isUnderPlayed &&
                totalSkips <= max(1, totalCompleted + 1)

            if (isFavoriteCandidate) {
                favBucket[favLen++] = idx
            }
            if (isForgottenGem) {
                forgottenBucket[forgottenLen++] = idx
            }
            if (isUnderPlayed) {
                underplayedBucket[underplayedLen++] = idx
            }
        }

        // Compute target 60 / 25 / 15 quotas (nudged cleanly only when user tunes Familiar<->Fresh)
        val favRatio = if (tuneConfig != null && abs(tuneConfig.discoveryBalance - 0.5f) >= 0.05f) {
            val delta = tuneConfig.discoveryBalance.coerceIn(0f, 1f) - 0.5f
            (FAVORITES_BUCKET_RATIO - delta * 0.35f).coerceIn(0.35f, 0.75f)
        } else {
            FAVORITES_BUCKET_RATIO
        }
        val favQuota = (effectiveTarget * favRatio).roundToInt().coerceIn(0, effectiveTarget)
        val forgottenQuota = (effectiveTarget * FORGOTTEN_GEMS_BUCKET_RATIO).roundToInt()
            .coerceIn(0, (effectiveTarget - favQuota).coerceAtLeast(0))
        val underplayedQuota = (effectiveTarget - favQuota - forgottenQuota).coerceAtLeast(0)

        val selectedIndices = IntArray(effectiveTarget)
        val selectedFlags = BooleanArray(n)
        var selectedCount = 0
        val artistCounts = if (maxPerArtist > 0) HashMap<Int, Int>(effectiveTarget * 2) else null

        fun canSelectArtist(idx: Int, enforceArtistCap: Boolean): Boolean {
            if (!enforceArtistCap || maxPerArtist <= 0 || artistCounts == null) return true
            val hash = matrix.artistHashes[idx]
            return (artistCounts[hash] ?: 0) < maxPerArtist
        }

        fun markSelected(idx: Int) {
            selectedFlags[idx] = true
            selectedIndices[selectedCount++] = idx
            if (artistCounts != null) {
                val hash = matrix.artistHashes[idx]
                artistCounts[hash] = (artistCounts[hash] ?: 0) + 1
            }
        }

        fun pullFromBucket(bucket: IntArray, bucketLen: Int, quota: Int, enforceArtistCap: Boolean): Int {
            var added = 0
            var cursor = 0
            while (cursor < bucketLen && added < quota && selectedCount < effectiveTarget) {
                val idx = bucket[cursor++]
                if (!selectedFlags[idx] && canSelectArtist(idx, enforceArtistCap)) {
                    markSelected(idx)
                    added++
                }
            }
            return added
        }

        // Pin explicit seed track at index 0 when requested
        if (seedTrackId != null) {
            val seedIdx = matrix.trackIds.indexOfFirst { it == seedTrackId }
            if (seedIdx >= 0 && selectedCount < effectiveTarget) {
                markSelected(seedIdx)
            }
        }

        // Primary 60 / 25 / 15 allocation
        val enforceCap = maxPerArtist > 0
        val favoritesPicked = pullFromBucket(favBucket, favLen, (favQuota - selectedCount).coerceAtLeast(0), enforceCap)
        val forgottenPicked = pullFromBucket(forgottenBucket, forgottenLen, forgottenQuota, enforceCap)
        val underplayedPicked = pullFromBucket(underplayedBucket, underplayedLen, underplayedQuota, enforceCap)

        // Deterministic Waterfall Fallback: cascade through remaining buckets -> master sorted list
        // First honoring artist diversity cap, then relaxing cap ONLY if library has too few distinct artists.
        var fallbackPicked = 0
        if (selectedCount < effectiveTarget) {
            fallbackPicked += pullFromBucket(favBucket, favLen, effectiveTarget - selectedCount, enforceCap)
        }
        if (selectedCount < effectiveTarget) {
            fallbackPicked += pullFromBucket(forgottenBucket, forgottenLen, effectiveTarget - selectedCount, enforceCap)
        }
        if (selectedCount < effectiveTarget) {
            fallbackPicked += pullFromBucket(underplayedBucket, underplayedLen, effectiveTarget - selectedCount, enforceCap)
        }
        if (selectedCount < effectiveTarget) {
            fallbackPicked += pullFromBucket(masterIndices, n, effectiveTarget - selectedCount, enforceCap)
        }
        if (selectedCount < effectiveTarget && enforceCap && !strictArtistCap) {
            fallbackPicked += pullFromBucket(masterIndices, n, effectiveTarget - selectedCount, false)
        }

        // Light adjacent-artist spacing when artist diversity cap is active (keeps seed at index 0)
        if (enforceCap && selectedCount > 2) {
            spaceAdjacentArtistsInPlace(selectedIndices, selectedCount, matrix.artistHashes, preserveFirst = seedTrackId != null)
        }

        val orderedIds = ArrayList<String>(selectedCount)
        val scoreMap = LinkedHashMap<String, Float>(selectedCount)
        for (i in 0 until selectedCount) {
            val idx = selectedIndices[i]
            val id = matrix.trackIds[idx]
            orderedIds.add(id)
            scoreMap[id] = effectiveScores[idx]
        }

        return RecommendationResult(
            trackIds = orderedIds.toStableList(),
            scoresByTrackId = scoreMap.toStableMap(),
            favoritesCount = favoritesPicked,
            forgottenGemsCount = forgottenPicked,
            underPlayedCount = underplayedPicked,
            waterfallFallbackCount = fallbackPicked
        )
    }

    /**
     * Convenience overload operating directly on domain [Track] lists.
     */
    fun generatePlaylist(
        tracks: List<Track>,
        targetSize: Int,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long,
        timeOfDay: TimeOfDayCategory
    ): RecommendationResult = generatePlaylist(
        matrix = TrackFeatureMatrix.fromTracks(tracks),
        targetSize = targetSize,
        signals = signals,
        nowMs = nowMs,
        timeOfDay = timeOfDay
    )

    /**
     * Generates up to [DAILY_PICKS_MAX_SIZE] (6) distinct Daily Picks track IDs.
     * When [epochDay] is provided, applies a deterministic calendar-day rotation weight so
     * the same day stays identical while the next calendar day rotates cleanly across the library.
     */
    fun generateDailyPicks(
        tracks: List<Track>,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long,
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.AFTERNOON,
        epochDay: Long? = null,
        targetSize: Int = DAILY_PICKS_MAX_SIZE
    ): RecommendationResult {
        val matrix = TrackFeatureMatrix.fromTracks(tracks)
        val boundedTarget = targetSize.coerceIn(1, DAILY_PICKS_MAX_SIZE)
        if (epochDay == null) {
            return generateTunedPlaylist(
                matrix = matrix,
                targetSize = boundedTarget,
                signals = signals,
                nowMs = nowMs,
                timeOfDay = timeOfDay,
                maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST
            )
        }
        val hasHistory = tracks.any {
            it.playCount > 0 || it.completedPlayCount > 0 || it.isFavorite || it.lastPlayedAt > 0L
        }
        return generateTunedPlaylist(
            matrix = matrix,
            targetSize = boundedTarget,
            signals = signals,
            nowMs = nowMs,
            timeOfDay = timeOfDay,
            rotationSeed = epochDay,
            rotationWeight = if (hasHistory) 0.54f else 0.78f,
            maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST
        )
    }

    /**
     * Generates up to [SESSION_SNAPSHOT_MAX_SIZE] (10) distinct Context-Aware / Mood Session track IDs.
     */
    fun generateSessionMix(
        tracks: List<Track>,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long,
        timeOfDay: TimeOfDayCategory,
        tuneConfig: DiscoverTuneConfig? = null,
        preferredCategoryMask: Int = 0,
        seedTrackId: String? = null,
        excludedTrackIds: Set<String> = emptySet(),
        rotationSeed: Long? = null,
        targetSize: Int = SESSION_SNAPSHOT_MAX_SIZE
    ): RecommendationResult = generateTunedPlaylist(
        matrix = TrackFeatureMatrix.fromTracks(tracks),
        targetSize = targetSize.coerceIn(1, SESSION_SNAPSHOT_MAX_SIZE),
        signals = signals,
        nowMs = nowMs,
        timeOfDay = timeOfDay,
        tuneConfig = tuneConfig,
        preferredCategoryMask = preferredCategoryMask,
        seedTrackId = seedTrackId,
        excludedTrackIds = excludedTrackIds,
        rotationSeed = rotationSeed,
        rotationWeight = if (rotationSeed != null) 0.08f else 0f,
        maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST
    )

    /**
     * Builds Discover & Home Mood Playlists (Late Night, Acoustic, Soulful, Upbeat) directly
     * from [RecommendationCore] scoring, capped at [maxTracksPerMood] (default 10) distinct tracks
     * with strict max 2 per artist.
     */
    fun buildMoodPlaylists(
        tracks: List<Track>,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.fromHour(
            LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
        ),
        maxTracksPerMood: Int = SESSION_SNAPSHOT_MAX_SIZE
    ): List<MoodSmartPlaylist> {
        val clean = tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty()) return emptyList()
        val trackById = clean.associateBy { it.id }
        val limit = maxTracksPerMood.coerceIn(1, 20)

        return SmartMoodTag.discoverMoods.mapNotNull { mood ->
            val matching = clean.filter { mood in it.resolvedMoodTags() }
            if (matching.isEmpty()) return@mapNotNull null

            val result = generateTunedPlaylist(
                matrix = TrackFeatureMatrix.fromTracks(matching),
                targetSize = min(limit, matching.size),
                signals = signals,
                nowMs = nowMs,
                timeOfDay = timeOfDay,
                preferredCategoryMask = TrackFeatureMatrix.smartMoodTagToCategoryBit(mood),
                maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST,
                strictArtistCap = true
            )
            val resolvedTracks = result.trackIds.mapNotNull { trackById[it] }
            if (resolvedTracks.isEmpty()) return@mapNotNull null

            val subtitle = when (mood) {
                SmartMoodTag.LATE_NIGHT -> "Nocturnal & ambient flow"
                SmartMoodTag.ACOUSTIC -> "Warm organic & stripped"
                SmartMoodTag.SOULFUL -> "Velvet vocals & deep groove"
                SmartMoodTag.UPBEAT -> "High-tempo energy lift"
                SmartMoodTag.LOSSLESS_HIFI -> "Bit-perfect studio fidelity"
            }

            MoodSmartPlaylist(
                mood = mood,
                title = mood.label,
                subtitle = subtitle,
                tracks = resolvedTracks.toStableList(),
                totalMatchCount = matching.size
            )
        }
    }

    /**
     * Builds Discover's context-aware "For This Moment" Routine Mixes through [RecommendationCore],
     * placing the active routine first and capping each mix at [maxTracksPerMix] (default 10).
     */
    fun buildRoutineMixes(
        tracks: List<Track>,
        signals: List<PlaybackSignal> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = LocalMoodAndAffinityEngine.currentLocalHour(nowMs),
        dayOfWeek: Int = currentLocalDayOfWeek(nowMs),
        maxTracksPerMix: Int = SESSION_SNAPSHOT_MAX_SIZE
    ): StableList<RoutineMix> {
        val clean = tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty()) return StableList()

        val trackById = clean.associateBy { it.id }
        val matrix = TrackFeatureMatrix.fromTracks(clean)
        val activeType = RoutineMixCurator.resolveActiveRoutineType(localHourOfDay, dayOfWeek)
        val orderedTypes = buildOrderedRoutineTypes(activeType)
        val cap = min(clean.size, maxTracksPerMix.coerceIn(1, SESSION_SNAPSHOT_MAX_SIZE))

        val mixes = orderedTypes.map { type ->
            val routineTune = DiscoverTuneConfig(
                selectedMood = type.tuneMood,
                energyFilter = type.tuneEnergy,
                discoveryBalance = type.tuneDiscoveryBalance
            )
            val routineTimeOfDay = when (type) {
                RoutineMixType.LATE_NIGHT_WIND_DOWN -> TimeOfDayCategory.LATE_NIGHT
                RoutineMixType.MORNING_FOCUS -> TimeOfDayCategory.MORNING
                RoutineMixType.AFTERNOON_MOMENTUM -> TimeOfDayCategory.AFTERNOON
                RoutineMixType.EVENING_UNWIND -> TimeOfDayCategory.EVENING
                RoutineMixType.WEEKEND_ENERGY -> TimeOfDayCategory.AFTERNOON
            }
            val preferredMask = when (type) {
                RoutineMixType.LATE_NIGHT_WIND_DOWN ->
                    TrackFeatureMatrix.CATEGORY_CALM_NOCTURNAL or TrackFeatureMatrix.CATEGORY_ACOUSTIC_FOCUS
                RoutineMixType.MORNING_FOCUS ->
                    TrackFeatureMatrix.CATEGORY_ACOUSTIC_FOCUS
                RoutineMixType.AFTERNOON_MOMENTUM ->
                    TrackFeatureMatrix.CATEGORY_WARM_SOULFUL or TrackFeatureMatrix.CATEGORY_UPBEAT_ENERGETIC
                RoutineMixType.EVENING_UNWIND ->
                    TrackFeatureMatrix.CATEGORY_WARM_SOULFUL or TrackFeatureMatrix.CATEGORY_ACOUSTIC_FOCUS
                RoutineMixType.WEEKEND_ENERGY ->
                    TrackFeatureMatrix.CATEGORY_UPBEAT_ENERGETIC
            }
            val rec = generateTunedPlaylist(
                matrix = matrix,
                targetSize = cap,
                signals = signals,
                nowMs = nowMs,
                timeOfDay = routineTimeOfDay,
                tuneConfig = routineTune,
                preferredCategoryMask = preferredMask,
                maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST
            )
            val orderedTracks = rec.trackIds.mapNotNull { trackById[it] }
            // Ensure the lead track matches the routine's signature energy/tempo profile cleanly
            val leadAligned = alignRoutineLeadTrack(type, orderedTracks)

            RoutineMix(
                type = type,
                title = type.title,
                subtitle = type.description,
                timeWindowLabel = type.timeWindowLabel,
                isActiveNow = type == activeType,
                tracks = leadAligned.toStableList(),
                tuneConfig = routineTune
            )
        }
        return mixes.toStableList()
    }

    /**
     * Derives [ListeningInsights] (`Top Mood`, `Top Genre`, `Top Artist`, `Listening Time`,
     * `Hi-Fi Library %`, `Completed Plays`) directly from Room track metadata & telemetry counters.
     */
    fun computeListeningInsights(tracks: List<Track>): ListeningInsights {
        val clean = tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty()) return ListeningInsights()

        val moodWeights = HashMap<String, Double>()
        val genreWeights = HashMap<String, Double>()
        val artistWeights = HashMap<String, Double>()
        val artistDisplayByKey = HashMap<String, String>()
        val genreDisplayByKey = HashMap<String, String>()

        var totalListeningMs = 0L
        var catalogDurationMs = 0L
        var hiFiCount = 0
        var completedTotal = 0

        for (i in clean.indices) {
            val t = clean[i]
            val plays = t.playCount.coerceAtLeast(0)
            val completed = t.completedPlayCount.coerceAtLeast(0)
            val effectivePlays = max(plays, completed)
            val safeDur = t.durationMs.coerceAtLeast(0L)

            completedTotal += completed
            catalogDurationMs += safeDur
            if (effectivePlays > 0) {
                totalListeningMs += safeDur * effectivePlays.toLong()
            }
            if (t.isHiFi) {
                hiFiCount++
            }

            val affinityWeight = (plays.toDouble() + completed * 2.0 + if (t.isFavorite) 3.0 else 0.0)
                .coerceAtLeast(0.25)

            val moodLabel = resolvePrimaryInsightMood(t)
            moodWeights[moodLabel] = (moodWeights[moodLabel] ?: 0.0) + affinityWeight

            val cleanGenre = t.genre.trim()
            if (cleanGenre.isNotEmpty() && !cleanGenre.equals("Audio", ignoreCase = true)) {
                val gKey = cleanGenre.lowercase(Locale.ROOT)
                genreWeights[gKey] = (genreWeights[gKey] ?: 0.0) + affinityWeight
                genreDisplayByKey.putIfAbsent(gKey, cleanGenre)
            }

            val cleanArtist = t.artist.trim()
            if (cleanArtist.isNotEmpty() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
                val aKey = cleanArtist.lowercase(Locale.ROOT)
                artistWeights[aKey] = (artistWeights[aKey] ?: 0.0) + affinityWeight
                artistDisplayByKey.putIfAbsent(aKey, cleanArtist)
            }
        }

        val topMoodRaw = moodWeights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key ?: "Calm"
        val topGenreKey = genreWeights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
        val topArtistKey = artistWeights.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key

        val hiFiPct = ((hiFiCount.toDouble() / clean.size.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        return ListeningInsights(
            topMood = topMoodRaw,
            topGenre = topGenreKey?.let { genreDisplayByKey[it] } ?: "",
            topArtist = topArtistKey?.let { artistDisplayByKey[it] } ?: "",
            totalListeningMs = if (totalListeningMs > 0L) totalListeningMs else catalogDurationMs,
            totalTrackCount = clean.size,
            hiFiTrackCount = hiFiCount,
            hiFiPercentage = hiFiPct,
            completedPlaysTotal = completedTotal
        )
    }

    /**
     * Builds a structured [MusicIntelligenceOutput] directly from a locked [RecommendationCore]
     * session snapshot so Tune Mix and Discover Smart Mix share the exact same single source of truth.
     */
    fun buildTunedSmartMixOutput(
        orderedTracks: List<Track>,
        scoresByTrackId: Map<String, Float>,
        tuneConfig: DiscoverTuneConfig,
        seedTrack: Track? = null,
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.AFTERNOON
    ): MusicIntelligenceOutput {
        if (orderedTracks.isEmpty()) {
            return MusicIntelligenceOutput(
                schemaVersion = MusicIntelligenceEngine.SCHEMA_VERSION,
                playlistProfile = PlaylistProfile("Empty Local Mix", "matcha", 0.0),
                moodClassifications = emptyList(),
                tempoDescriptors = TempoDescriptors(BpmRange(0, 0), "downtempo_chill", "steady", true),
                recommendedTracks = emptyList(),
                rawJson = "{}"
            )
        }

        val bpms = IntArray(orderedTracks.size)
        var minBpm = Int.MAX_VALUE
        var maxBpm = 0
        var sumBpm = 0
        var anyInferred = false
        for (i in orderedTracks.indices) {
            val t = orderedTracks[i]
            val inferred = t.bpm <= 0
            if (inferred) anyInferred = true
            val b = if (inferred) 92 else t.bpm.coerceIn(45, 200)
            bpms[i] = b
            if (b < minBpm) minBpm = b
            if (b > maxBpm) maxBpm = b
            sumBpm += b
        }
        val avgBpm = (sumBpm.toDouble() / orderedTracks.size.toDouble()).roundToInt()
        val descriptor = when {
            avgBpm < 70 -> "largo_ambient"
            avgBpm <= 94 -> "downtempo_chill"
            avgBpm <= 114 -> "midtempo_groove"
            avgBpm <= 132 -> "upbeat_driving"
            else -> "fast_intense"
        }

        val primaryMood = tuneConfig.selectedMood?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
            ?: resolvePrimaryInsightMood(orderedTracks.first()).lowercase(Locale.ROOT)
        val pacingCurve = when {
            primaryMood in setOf("nocturnal", "calm", "late_night") || timeOfDay == TimeOfDayCategory.LATE_NIGHT -> "wind_down"
            primaryMood in setOf("energetic", "uplifting", "upbeat") || timeOfDay == TimeOfDayCategory.MORNING -> "gradual_build"
            primaryMood in setOf("focused", "minimal", "acoustic") -> "steady"
            else -> "wave"
        }
        val palette = when (primaryMood) {
            "warm", "soulful" -> "desert_copper"
            "organic", "calm", "acoustic" -> "matcha"
            "nocturnal", "late_night", "melancholic", "cinematic" -> "cobalt"
            "minimal", "focused" -> "obsidian_mono"
            "uplifting", "energetic", "upbeat" -> "citrus"
            else -> "berry"
        }
        val title = if (seedTrack != null) {
            "${seedTrack.title} · Smart Mix"
        } else {
            when (primaryMood) {
                "nocturnal", "late_night" -> "Indigo After Hours"
                "calm" -> "Quiet Horizon"
                "warm", "soulful" -> "Golden Amber Glow"
                "focused", "minimal" -> "Minimal Deep Focus"
                "organic", "acoustic" -> "Botanical Acoustics"
                "energetic", "upbeat" -> "Citrus Kinetic Pulse"
                "uplifting" -> "Sunlit Momentum"
                else -> "Cadence Smart Mix"
            }
        }

        var scoreSum = 0.0
        val recommendations = ArrayList<RecommendedTrack>(orderedTracks.size)
        for (i in orderedTracks.indices) {
            val t = orderedTracks[i]
            val sc = (scoresByTrackId[t.id] ?: 0.65f).toDouble().coerceIn(0.0, 1.0)
            scoreSum += sc
            val trackMood = resolvePrimaryInsightMood(t).lowercase(Locale.ROOT)
            val trackTempo = when {
                bpms[i] < 70 -> "largo_ambient"
                bpms[i] <= 94 -> "downtempo_chill"
                bpms[i] <= 114 -> "midtempo_groove"
                bpms[i] <= 132 -> "upbeat_driving"
                else -> "fast_intense"
            }
            val reason = when {
                seedTrack != null && t.id == seedTrack.id -> "Seed anchor, ${bpms[i]} BPM match"
                t.isFavorite -> "Favorite track · ${bpms[i]} BPM $trackMood fit"
                t.completedPlayCount >= 2 -> "${t.completedPlayCount} full listens · $trackMood flow"
                else -> "Aligned $trackMood mix at ${bpms[i]} BPM"
            }
            recommendations.add(
                RecommendedTrack(
                    rank = i + 1,
                    trackId = t.id,
                    score = (sc * 100.0).roundToInt() / 100.0,
                    moodTag = trackMood,
                    tempoDescriptor = trackTempo,
                    reason = reason
                )
            )
        }

        val avgScore = scoreSum / orderedTracks.size.toDouble()
        val bpmSpreadPenalty = ((maxBpm - minBpm) / 140.0).coerceIn(0.0, 0.25)
        val cohesion = (avgScore - bpmSpreadPenalty * 0.4 + 0.10).coerceIn(0.35, 0.98)

        val energyLevel = when {
            avgBpm < 90 -> "low"
            avgBpm <= 116 -> "medium"
            else -> "high"
        }
        val valence = when (primaryMood) {
            "nocturnal", "late_night", "melancholic" -> "dark"
            "warm", "soulful", "uplifting", "energetic", "upbeat", "organic" -> "bright"
            else -> "neutral"
        }

        return MusicIntelligenceOutput(
            schemaVersion = MusicIntelligenceEngine.SCHEMA_VERSION,
            playlistProfile = PlaylistProfile(
                suggestedTitle = title,
                suggestedPalette = palette,
                cohesionScore = (cohesion * 100.0).roundToInt() / 100.0
            ),
            moodClassifications = listOf(
                MoodClassification(
                    mood = primaryMood,
                    confidence = cohesion.coerceIn(0.45, 0.98),
                    energyLevel = energyLevel,
                    valence = valence
                )
            ),
            tempoDescriptors = TempoDescriptors(
                dominantBpmRange = BpmRange(min = minBpm, max = maxBpm),
                descriptor = descriptor,
                pacingCurve = pacingCurve,
                tempoInferred = anyInferred
            ),
            recommendedTracks = recommendations,
            rawJson = "{}"
        )
    }

    internal fun computeTimeOfDayWeight(categoryMask: Int, timeOfDay: TimeOfDayCategory): Float {
        val isCalm = (categoryMask and TrackFeatureMatrix.CATEGORY_CALM_NOCTURNAL) != 0
        val isAcoustic = (categoryMask and TrackFeatureMatrix.CATEGORY_ACOUSTIC_FOCUS) != 0
        val isWarm = (categoryMask and TrackFeatureMatrix.CATEGORY_WARM_SOULFUL) != 0
        val isUpbeat = (categoryMask and TrackFeatureMatrix.CATEGORY_UPBEAT_ENERGETIC) != 0

        var weight = 0f
        when (timeOfDay) {
            TimeOfDayCategory.MORNING -> {
                if (isUpbeat) weight += 0.08f
                if (isAcoustic) weight += 0.06f
                if (isWarm) weight += 0.03f
                if (isCalm && !isUpbeat && !isAcoustic) weight -= 0.04f
            }
            TimeOfDayCategory.AFTERNOON -> {
                if (isUpbeat) weight += 0.09f
                if (isWarm) weight += 0.04f
                if (isAcoustic) weight += 0.03f
                if (isCalm && !isUpbeat) weight -= 0.04f
            }
            TimeOfDayCategory.EVENING -> {
                if (isWarm) weight += 0.09f
                if (isAcoustic) weight += 0.05f
                if (isCalm) weight += 0.04f
                if (isUpbeat && !isWarm) weight -= 0.03f
            }
            TimeOfDayCategory.LATE_NIGHT -> {
                if (isCalm) weight += 0.11f
                if (isAcoustic) weight += 0.06f
                if (isWarm) weight += 0.05f
                if (isUpbeat && !isCalm && !isWarm) weight -= 0.06f
            }
        }
        return weight.coerceIn(-0.08f, 0.14f)
    }

    private fun computeSingleTrackScore(
        playCount: Int,
        completedCount: Int,
        skipCount: Int,
        isFavorite: Boolean,
        signalDeltaSum: Float,
        lastPlayedAtMs: Long,
        categoryMask: Int,
        nowMs: Long,
        timeOfDay: TimeOfDayCategory,
        logMaxPlaysDenom: Double,
        log25: Double
    ): Float {
        val totalPlays = max(playCount, completedCount + skipCount)

        // 1. Normalized completion rate & engagement depth
        val completionRate = if (totalPlays > 0) {
            (completedCount.toFloat() / totalPlays.toFloat()).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val completionDepth = if (completedCount > 0) {
            (ln(completedCount.toDouble() + 1.0) / logMaxPlaysDenom).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val playVolume = if (totalPlays > 0) {
            (ln(totalPlays.toDouble() + 1.0) / logMaxPlaysDenom).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val completionContribution = (
            (if (totalPlays > 0) (completionRate - 0.5f) * 0.24f else 0f) +
                completionDepth * 0.20f +
                playVolume * 0.06f +
                (if (isFavorite) 0.18f else 0f)
            ).coerceIn(-0.14f, 0.46f)

        // 2. Bounded early-skip penalty
        val skipRatio = if (totalPlays > 0) {
            (skipCount.toFloat() / totalPlays.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val skipPenalty = (
            skipRatio * 0.24f +
                min(skipCount, 6) * 0.03f
            ).coerceIn(0f, 0.42f)

        // 3. Direct bounded signal delta
        val boundedSignalDelta = signalDeltaSum.coerceIn(-0.30f, 0.35f) * 0.35f

        // 4. Logarithmic recency decay (24h cooldown) & forgotten-gem rediscovery lift (21d+)
        val highAffinityFlag = isFavorite || completedCount >= 2 || (totalPlays >= 3 && skipCount <= completedCount + 1)
        val recencyContribution = computeLogarithmicRecency(
            lastPlayedAtMs = lastPlayedAtMs,
            nowMs = nowMs,
            isHighAffinity = highAffinityFlag,
            totalCompleted = completedCount,
            totalSkips = skipCount,
            log25 = log25
        )

        // 5. Explicit time-of-day category weighting
        val timeWeight = computeTimeOfDayWeight(categoryMask, timeOfDay)

        val raw = BASE_PRIOR_SCORE +
            completionContribution -
            skipPenalty +
            boundedSignalDelta +
            recencyContribution +
            timeWeight

        return if (raw.isFinite()) raw.coerceIn(0f, 1f) else BASE_PRIOR_SCORE
    }

    private fun computeLogarithmicRecency(
        lastPlayedAtMs: Long,
        nowMs: Long,
        isHighAffinity: Boolean,
        totalCompleted: Int,
        totalSkips: Int,
        log25: Double
    ): Float {
        val ageMs = if (lastPlayedAtMs > 0L) (nowMs - lastPlayedAtMs).coerceAtLeast(0L) else -1L
        return when {
            ageMs in 0L until TWENTY_FOUR_HOURS_MS -> {
                val hoursAgo = ageMs.toDouble() / ONE_HOUR_MS.toDouble()
                val logDecay = (1.0 - (ln(hoursAgo + 1.0) / log25)).coerceIn(0.0, 1.0)
                -(0.06f + (logDecay * 0.18).toFloat())
            }
            ageMs >= FORGOTTEN_GEM_MIN_AGE_MS && isHighAffinity && totalSkips <= max(1, totalCompleted) -> {
                val ageCycles = (ageMs.toDouble() / FORGOTTEN_GEM_MIN_AGE_MS.toDouble()).coerceIn(1.0, 6.0)
                (0.08 + 0.05 * ln(ageCycles + 1.0)).toFloat().coerceAtMost(0.16f)
            }
            else -> 0f
        }
    }

    private fun resolvePrimaryInsightMood(track: Track): String {
        val tags = track.resolvedMoodTags()
        val firstMood = tags.firstOrNull { it != SmartMoodTag.LOSSLESS_HIFI }
        return when (firstMood) {
            SmartMoodTag.LATE_NIGHT -> "Nocturnal"
            SmartMoodTag.ACOUSTIC -> "Acoustic"
            SmartMoodTag.SOULFUL -> "Soulful"
            SmartMoodTag.UPBEAT -> "Upbeat"
            else -> when {
                track.bpm >= 118 -> "Upbeat"
                track.bpm in 1..86 -> "Calm"
                else -> "Warm"
            }
        }
    }

    private fun buildOrderedRoutineTypes(active: RoutineMixType): List<RoutineMixType> {
        val complements = when (active) {
            RoutineMixType.MORNING_FOCUS -> listOf(
                RoutineMixType.AFTERNOON_MOMENTUM,
                RoutineMixType.EVENING_UNWIND,
                RoutineMixType.LATE_NIGHT_WIND_DOWN
            )
            RoutineMixType.AFTERNOON_MOMENTUM -> listOf(
                RoutineMixType.EVENING_UNWIND,
                RoutineMixType.MORNING_FOCUS,
                RoutineMixType.WEEKEND_ENERGY
            )
            RoutineMixType.EVENING_UNWIND -> listOf(
                RoutineMixType.LATE_NIGHT_WIND_DOWN,
                RoutineMixType.AFTERNOON_MOMENTUM,
                RoutineMixType.WEEKEND_ENERGY
            )
            RoutineMixType.LATE_NIGHT_WIND_DOWN -> listOf(
                RoutineMixType.EVENING_UNWIND,
                RoutineMixType.MORNING_FOCUS,
                RoutineMixType.WEEKEND_ENERGY
            )
            RoutineMixType.WEEKEND_ENERGY -> listOf(
                RoutineMixType.AFTERNOON_MOMENTUM,
                RoutineMixType.EVENING_UNWIND,
                RoutineMixType.LATE_NIGHT_WIND_DOWN
            )
        }
        return listOf(active) + complements
    }

    private fun alignRoutineLeadTrack(type: RoutineMixType, tracks: List<Track>): List<Track> {
        if (tracks.size <= 1) return tracks
        val preferredIdx = when (type) {
            RoutineMixType.LATE_NIGHT_WIND_DOWN -> tracks.indexOfFirst { it.bpm in 1..100 }
            RoutineMixType.WEEKEND_ENERGY -> tracks.indexOfFirst { it.bpm >= 112 }
            else -> 0
        }
        if (preferredIdx <= 0) return tracks
        val mutable = ArrayList(tracks)
        val lead = mutable.removeAt(preferredIdx)
        mutable.add(0, lead)
        return mutable
    }

    private fun deterministicUnitRoll(seed: Long, trackId: String): Float {
        var h = seed * -7046029254386353131L + 1442695040888963407L
        for (i in trackId.indices) {
            h = (h xor trackId[i].code.toLong()) * 6364136223846793005L + 1442695040888963407L
        }
        h = (h xor (h ushr 30)) * -4658895280553007687L
        h = (h xor (h ushr 27)) * -7723592293110705685L
        h = h xor (h ushr 31)
        val positive24 = (h ushr 40) and ((1L shl 24) - 1L)
        return positive24.toFloat() / (1L shl 24).toFloat()
    }

    private fun spaceAdjacentArtistsInPlace(
        indices: IntArray,
        count: Int,
        artistHashes: IntArray,
        preserveFirst: Boolean
    ) {
        val start = if (preserveFirst) 1 else 0
        for (i in (start + 1) until count) {
            val prevArtist = artistHashes[indices[i - 1]]
            if (prevArtist == UNKNOWN_ARTIST_HASH) continue
            if (artistHashes[indices[i]] == prevArtist) {
                var swapIdx = -1
                val limit = min(count - 1, i + 4)
                for (j in (i + 1)..limit) {
                    if (artistHashes[indices[j]] != prevArtist) {
                        swapIdx = j
                        break
                    }
                }
                if (swapIdx > i) {
                    val tmp = indices[i]
                    indices[i] = indices[swapIdx]
                    indices[swapIdx] = tmp
                }
            }
        }
    }

    private fun accumulateSignalsIntoPrimitives(
        matrix: TrackFeatureMatrix,
        signals: List<PlaybackSignal>,
        signalCompletions: IntArray,
        signalSkips: IntArray,
        signalFavoriteFlags: IntArray,
        signalDeltaSums: FloatArray,
        effectiveLastPlayed: LongArray
    ) {
        if (signals.isEmpty() || matrix.size == 0) return
        val indexById = HashMap<String, Int>(matrix.size * 2)
        for (i in 0 until matrix.size) {
            indexById[matrix.trackIds[i]] = i
        }
        for (sIdx in signals.indices) {
            val s = signals[sIdx]
            val trackIndex = indexById[s.trackId] ?: continue
            signalDeltaSums[trackIndex] += s.affinityDelta
            when (s.type) {
                SignalType.COMPLETION_BOOST -> {
                    signalCompletions[trackIndex] += 1
                    if (s.timestampMs > effectiveLastPlayed[trackIndex]) {
                        effectiveLastPlayed[trackIndex] = s.timestampMs
                    }
                }
                SignalType.EARLY_SKIP_PENALTY -> {
                    signalSkips[trackIndex] += 1
                }
                SignalType.MANUAL_FAVORITE_BOOST -> {
                    signalFavoriteFlags[trackIndex] = 1
                }
            }
        }
    }

    /**
     * Allocation-light, deterministic O(N log N) top-down merge sort over primitive [IntArray] indices.
     * Sorts by score descending, with deterministic tie-break by trackId lexicographically ascending.
     */
    private fun sortIndicesByScoreDesc(
        indices: IntArray,
        scores: FloatArray,
        trackIds: Array<String>
    ) {
        val n = indices.size
        if (n <= 1) return
        val scratch = IntArray(n)
        mergeSort(indices, scratch, 0, n, scores, trackIds)
    }

    private fun mergeSort(
        arr: IntArray,
        scratch: IntArray,
        left: Int,
        right: Int,
        scores: FloatArray,
        trackIds: Array<String>
    ) {
        val len = right - left
        if (len <= 16) {
            for (i in (left + 1) until right) {
                val key = arr[i]
                var j = i - 1
                while (j >= left && compareTrackIndex(key, arr[j], scores, trackIds) < 0) {
                    arr[j + 1] = arr[j]
                    j--
                }
                arr[j + 1] = key
            }
            return
        }
        val mid = left + (len ushr 1)
        mergeSort(arr, scratch, left, mid, scores, trackIds)
        mergeSort(arr, scratch, mid, right, scores, trackIds)

        var i = left
        var j = mid
        var k = left
        while (i < mid && j < right) {
            if (compareTrackIndex(arr[i], arr[j], scores, trackIds) <= 0) {
                scratch[k++] = arr[i++]
            } else {
                scratch[k++] = arr[j++]
            }
        }
        while (i < mid) scratch[k++] = arr[i++]
        while (j < right) scratch[k++] = arr[j++]
        System.arraycopy(scratch, left, arr, left, len)
    }

    private fun compareTrackIndex(
        a: Int,
        b: Int,
        scores: FloatArray,
        trackIds: Array<String>
    ): Int {
        val sa = scores[a]
        val sb = scores[b]
        if (sa != sb) {
            return if (sa > sb) -1 else 1
        }
        return trackIds[a].compareTo(trackIds[b])
    }
}
