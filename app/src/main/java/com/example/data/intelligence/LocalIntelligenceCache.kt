package com.example.data.intelligence

import android.os.Build
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.data.StableList
import com.example.data.Track
import com.example.data.toStableList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Context-aware listening routines paired with local time-of-day, day-of-week,
 * mood affinity weights, and Tune Mix preset parameters.
 */
@Keep
enum class RoutineMixType(
    val id: String,
    val title: String,
    val timeWindowLabel: String,
    val description: String,
    val tuneMood: String,
    val tuneEnergy: String?,
    val tuneDiscoveryBalance: Float
) {
    LATE_NIGHT_WIND_DOWN(
        id = "late_night_wind_down",
        title = "Late-Night Wind Down",
        timeWindowLabel = "21:00–05:00",
        description = "Calm acoustic textures & low-BPM nocturnal flow",
        tuneMood = "nocturnal",
        tuneEnergy = "low",
        tuneDiscoveryBalance = 0.35f
    ),
    MORNING_FOCUS(
        id = "morning_focus",
        title = "Morning Focus",
        timeWindowLabel = "05:00–12:00",
        description = "Steady groove & low vocal fatigue for deep work",
        tuneMood = "focused",
        tuneEnergy = "medium",
        tuneDiscoveryBalance = 0.35f
    ),
    AFTERNOON_MOMENTUM(
        id = "afternoon_momentum",
        title = "Afternoon Momentum",
        timeWindowLabel = "12:00–17:00",
        description = "Balanced favorites & warm mid-tempo energy",
        tuneMood = "warm",
        tuneEnergy = "medium",
        tuneDiscoveryBalance = 0.45f
    ),
    EVENING_UNWIND(
        id = "evening_unwind",
        title = "Evening Unwind",
        timeWindowLabel = "17:00–21:00",
        description = "Warm harmonic favorites with smooth transitions",
        tuneMood = "warm",
        tuneEnergy = "low",
        tuneDiscoveryBalance = 0.30f
    ),
    WEEKEND_ENERGY(
        id = "weekend_energy",
        title = "Weekend Energy",
        timeWindowLabel = "Fri Eve · Sat–Sun",
        description = "Upbeat tempo lift with fresh library rediscoveries",
        tuneMood = "energetic",
        tuneEnergy = "high",
        tuneDiscoveryBalance = 0.72f
    )
}

/**
 * Immutable context-aware Routine Mix surfaced on Discover's "For This Moment" shelf.
 */
@Keep
@Immutable
data class RoutineMix(
    val type: RoutineMixType,
    val title: String,
    val subtitle: String,
    val timeWindowLabel: String,
    val isActiveNow: Boolean,
    val tracks: StableList<Track>,
    val tuneConfig: DiscoverTuneConfig
)

/**
 * Precomputed, normalized audio & mood feature profile for a single track inside a
 * versioned [LibraryIntelligenceSnapshot]. Eliminates redundant O(N) string/BPM/mood
 * computations across Home, Discover, Match Mood, and Tune Mix.
 */
internal data class CachedTrackVector(
    val trackId: String,
    val effectiveBpm: Int,
    val energyLevel: String,
    val moodVector: Map<String, Double>,
    val primaryMood: String,
    val smartMoodScores: Map<SmartMoodTag, Float>,
    val smartMoodTags: StableList<SmartMoodTag>,
    val isLowVocalFatigue: Boolean
)

/**
 * Immutable versioned cache snapshot invalidated automatically whenever the Room library
 * track list or play-history counters change.
 */
internal class LibraryIntelligenceSnapshot(
    val fingerprint: Long,
    val trackCount: Int,
    val maxLibraryPlays: Int,
    val hasListeningHistory: Boolean,
    val vectorsById: Map<String, CachedTrackVector>,
    val trackFingerprintsById: Map<String, Long>
) {
    private val affinityCache = ConcurrentHashMap<Long, Map<String, TrackAffinityBreakdown>>()

    fun containsUnchangedSubset(tracks: List<Track>): Boolean {
        if (tracks.isEmpty() || tracks.size > trackCount) return false
        for (t in tracks) {
            if (t.id.isBlank()) continue
            val cachedFp = trackFingerprintsById[t.id] ?: return false
            if (cachedFp != TrackIntelligenceCache.computeTrackFingerprint(t)) return false
        }
        return true
    }

    fun getOrComputeAffinities(
        tracks: List<Track>,
        nowMs: Long,
        localHourOfDay: Int
    ): Map<String, TrackAffinityBreakdown> {
        val dayBucket = nowMs / LocalMoodAndAffinityEngine.ONE_DAY_MS
        val bucketKey = (dayBucket shl 5) xor localHourOfDay.coerceIn(0, 23).toLong()
        affinityCache[bucketKey]?.let { existing ->
            if (tracks.all { it.id.isBlank() || it.id in existing }) {
                return existing
            }
        }

        val computed = HashMap<String, TrackAffinityBreakdown>(tracks.size)
        for (track in tracks) {
            computed[track.id] = LocalMoodAndAffinityEngine.computeLocalAffinity(
                track = track,
                nowMs = nowMs,
                localHourOfDay = localHourOfDay,
                maxLibraryPlays = maxLibraryPlays
            )
        }
        if (affinityCache.size >= 8) {
            affinityCache.clear()
        }
        affinityCache[bucketKey] = computed
        return computed
    }
}

/**
 * Thread-safe, versioned in-memory memoization cache for Cadence's offline local intelligence
 * engine. Shared across Home ("Daily Picks", "Made for Your Mood"), Discover ("For This Moment"
 * Routine Mixes & full Discover feed), Now Playing ("Match Mood"), and Tune Mix.
 *
 * Invalidates automatically when any track is added/removed or when play-history counters
 * (playCount, completedPlayCount, skipCount, lastPlayedAt, isFavorite) change.
 */
object TrackIntelligenceCache {

    @Volatile
    private var cachedSnapshot: LibraryIntelligenceSnapshot? = null

    private val hitCounter = AtomicLong(0L)
    private val missCounter = AtomicLong(0L)

    internal val cacheHits: Long get() = hitCounter.get()
    internal val cacheMisses: Long get() = missCounter.get()

    internal fun clearForTesting() {
        cachedSnapshot = null
        hitCounter.set(0L)
        missCounter.set(0L)
    }

    internal fun computeTrackFingerprint(t: Track): Long {
        var h = -3750763034362895579L
        h = mix(h, t.id.hashCode().toLong())
        h = mix(h, t.playCount.toLong())
        h = mix(h, t.completedPlayCount.toLong())
        h = mix(h, t.skipCount.toLong())
        h = mix(h, t.lastPlayedAt)
        h = mix(h, if (t.isFavorite) 1L else 0L)
        h = mix(h, t.bpm.toLong())
        h = mix(h, t.genre.hashCode().toLong())
        h = mix(h, t.title.hashCode().toLong())
        h = mix(h, t.artworkColorArgb.toLong())
        h = mix(h, t.bitrateKbps.toLong())
        h = mix(h, t.sampleRateHz.toLong())
        h = mix(h, t.bitDepth.toLong())
        h = mix(h, t.moodTags.size.toLong())
        return h
    }

    /**
     * Computes a fast structural + play-history version fingerprint for [tracks].
     * Changes immediately whenever tracks are inserted/deleted or play/favorite history updates.
     */
    fun computeLibraryFingerprint(tracks: List<Track>): Long {
        var h = -3750763034362895579L // FNV-1a 64-bit offset basis
        h = mix(h, tracks.size.toLong())
        for (t in tracks) {
            h = mix(h, computeTrackFingerprint(t))
        }
        return h
    }

    internal fun getOrBuildSnapshot(
        tracks: List<Track>,
        engine: MusicIntelligenceEngine = SharedEngine
    ): LibraryIntelligenceSnapshot {
        val fingerprint = computeLibraryFingerprint(tracks)
        val existing = cachedSnapshot
        if (existing != null) {
            if (existing.fingerprint == fingerprint && existing.trackCount == tracks.size) {
                hitCounter.incrementAndGet()
                return existing
            }
            // Reuse cached vectors for unchanged track subsets (e.g. Discover unusedPool)
            if (existing.containsUnchangedSubset(tracks)) {
                hitCounter.incrementAndGet()
                return existing
            }
        }

        missCounter.incrementAndGet()
        val vectors = HashMap<String, CachedTrackVector>(tracks.size)
        val trackFingerprints = HashMap<String, Long>(tracks.size)
        var maxPlays = 1
        var hasHistory = false

        for (t in tracks) {
            if (t.id.isBlank()) continue
            trackFingerprints[t.id] = computeTrackFingerprint(t)
            if (t.playCount > maxPlays) maxPlays = t.playCount
            if (!hasHistory && (t.playCount > 0 || t.completedPlayCount > 0 || t.isFavorite || t.lastPlayedAt > 0L)) {
                hasHistory = true
            }

            val effectiveBpm = engine.effectiveBpmFor(t).coerceIn(45, 200)
            val energy = engine.energyLevelFor(effectiveBpm)
            val rawVec = engine.moodVectorFor(t)
            val normalizedVec = normalizeMoodVector(rawVec)
            val primaryMood = normalizedVec.maxByOrNull { it.value }?.key ?: "calm"
            val smartScores = LocalMoodAndAffinityEngine.computeMoodAffinityScores(
                title = t.title,
                album = t.album,
                artist = t.artist,
                genre = t.genre,
                folderName = t.folderName,
                bpm = effectiveBpm,
                artworkColorArgb = t.artworkColorArgb
            )
            val resolvedTags = if (t.moodTags.isNotEmpty()) {
                t.moodTags
            } else {
                LocalMoodAndAffinityEngine.classifyTrackMoods(t).toList().toStableList()
            }
            val lowVocal = isLowVocalFatigueTrack(t, effectiveBpm, resolvedTags)

            vectors[t.id] = CachedTrackVector(
                trackId = t.id,
                effectiveBpm = effectiveBpm,
                energyLevel = energy,
                moodVector = normalizedVec,
                primaryMood = primaryMood,
                smartMoodScores = smartScores,
                smartMoodTags = resolvedTags,
                isLowVocalFatigue = lowVocal
            )
        }

        val fresh = LibraryIntelligenceSnapshot(
            fingerprint = fingerprint,
            trackCount = tracks.size,
            maxLibraryPlays = maxPlays,
            hasListeningHistory = hasHistory,
            vectorsById = vectors,
            trackFingerprintsById = trackFingerprints
        )
        cachedSnapshot = fresh
        return fresh
    }

    private fun normalizeMoodVector(raw: Map<String, Double>): Map<String, Double> {
        if (raw.isEmpty()) return mapOf("calm" to 0.5)
        val out = HashMap<String, Double>(raw.size)
        for ((k, v) in raw) {
            out[k] = if (v.isFinite()) v.coerceIn(0.0, 1.0) else 0.35
        }
        return out
    }

    private fun isLowVocalFatigueTrack(
        track: Track,
        effectiveBpm: Int,
        tags: List<SmartMoodTag>
    ): Boolean {
        val blob = "${track.genre} ${track.title} ${track.album}".lowercase(Locale.ROOT)
        val instrumentalCue = LOW_VOCAL_KEYWORDS.any { blob.contains(it) }
        return instrumentalCue ||
            (SmartMoodTag.ACOUSTIC in tags && effectiveBpm <= 114) ||
            (SmartMoodTag.LATE_NIGHT in tags && effectiveBpm <= 108)
    }

    private fun mix(hash: Long, value: Long): Long = (hash xor value) * 1099511628211L

    private val SharedEngine = MusicIntelligenceEngine()

    private val LOW_VOCAL_KEYWORDS = listOf(
        "instrumental", "ambient", "lo-fi", "lofi", "piano", "classical",
        "jazz", "acoustic", "study", "focus", "chamber", "strings", "guitar",
        "chill", "downtempo", "minimal", "electronic", "synth"
    )
}

/**
 * Returns the local calendar epoch day (matching `LocalDate.now().toEpochDay()` on API 26+
 * with an exact local-timezone fallback for API 24–25).
 */
fun currentLocalEpochDay(nowMs: Long = System.currentTimeMillis()): Long {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return Instant.ofEpochMilli(nowMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
    }
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    val offsetMs = cal.timeZone.getOffset(nowMs).toLong()
    val localMs = nowMs + offsetMs
    val dayMs = 86_400_000L
    return if (localMs >= 0L) localMs / dayMs else ((localMs + 1L) / dayMs) - 1L
}

fun currentLocalDayOfWeek(nowMs: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    return cal.get(Calendar.DAY_OF_WEEK)
}

/**
 * Strictly automatic, date-seeded "Daily Picks" curator for HomeScreen.
 *
 * Produces 5–6 songs deterministically keyed to the local calendar date (`epochDay`):
 * - Same `LocalDate` -> identical 5–6 tracks every time.
 * - Next calendar day (`epochDay + 1`) -> automatically rotates to a fresh set of 5–6 tracks.
 * - Handles brand-new cold-start libraries (0 plays, 0 BPM, missing genre, < 10 tracks)
 *   gracefully without returning empty lists or crashing.
 */
object DailyPicksCurator {

    const val DEFAULT_PICK_COUNT = RecommendationCore.DAILY_PICKS_MAX_SIZE
    const val MIN_PICK_COUNT = 5
    const val MAX_PICK_COUNT = RecommendationCore.DAILY_PICKS_MAX_SIZE

    fun generateDailyPicks(
        libraryTracks: List<Track>,
        epochDay: Long = currentLocalEpochDay(),
        nowMs: Long = System.currentTimeMillis(),
        targetCount: Int = DEFAULT_PICK_COUNT,
        engine: MusicIntelligenceEngine = MusicIntelligenceEngine(),
        signals: List<PlaybackSignal> = emptyList()
    ): StableList<Track> {
        val clean = libraryTracks
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        if (clean.isEmpty()) return StableList()

        val desiredCount = minOf(clean.size, targetCount.coerceIn(MIN_PICK_COUNT, MAX_PICK_COUNT))
        TrackIntelligenceCache.getOrBuildSnapshot(clean, engine)
        val result = RecommendationCore.generateDailyPicks(
            tracks = clean,
            signals = signals,
            nowMs = nowMs,
            timeOfDay = TimeOfDayCategory.AFTERNOON,
            epochDay = epochDay,
            targetSize = desiredCount
        )
        val byId = clean.associateBy { it.id }
        return result.trackIds.mapNotNull { byId[it] }.toStableList()
    }
}

/**
 * Context-aware Time & Routine Mixes curator for Discover's "For This Moment" shelf.
 * Delegates all mix generation and scoring to [RecommendationCore].
 */
object RoutineMixCurator {

    /**
     * Resolves the active listening routine for the given local hour (0..23) and Calendar day-of-week:
     * - Late-Night Wind Down: 21:00–04:59
     * - Weekend Energy: Fri evening (17:00–20:59) or Sat/Sun daytime & evening (05:00–20:59)
     * - Morning Focus: 05:00–11:59 weekdays (Mon–Fri)
     * - Afternoon Momentum: 12:00–16:59 weekdays (Mon–Fri)
     * - Evening Unwind: 17:00–20:59 weekdays (Mon–Thu)
     */
    fun resolveActiveRoutineType(
        localHourOfDay: Int,
        dayOfWeek: Int
    ): RoutineMixType {
        val hour = localHourOfDay.coerceIn(0, 23)
        if (hour >= 21 || hour < 5) {
            return RoutineMixType.LATE_NIGHT_WIND_DOWN
        }
        val isWeekendDay = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        val isFridayEvening = dayOfWeek == Calendar.FRIDAY && hour in 17..20
        if (isWeekendDay || isFridayEvening) {
            return RoutineMixType.WEEKEND_ENERGY
        }
        return when (hour) {
            in 5..11 -> RoutineMixType.MORNING_FOCUS
            in 12..16 -> RoutineMixType.AFTERNOON_MOMENTUM
            else -> RoutineMixType.EVENING_UNWIND
        }
    }

    /**
     * Builds the active routine mix FIRST alongside 3 complementary routine mixes (4 total) via
     * [RecommendationCore.buildRoutineMixes], capped at [maxTracksPerMix] (default 10).
     */
    fun buildRoutineMixes(
        libraryTracks: List<Track>,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = LocalMoodAndAffinityEngine.currentLocalHour(nowMs),
        dayOfWeek: Int = currentLocalDayOfWeek(nowMs),
        maxTracksPerMix: Int = RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE,
        engine: MusicIntelligenceEngine = MusicIntelligenceEngine(),
        signals: List<PlaybackSignal> = emptyList(),
        shuffleNonce: Int = 0
    ): StableList<RoutineMix> {
        val clean = libraryTracks
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        if (clean.isEmpty()) return StableList()

        TrackIntelligenceCache.getOrBuildSnapshot(clean, engine)
        return RecommendationCore.buildRoutineMixes(
            tracks = clean,
            signals = signals,
            nowMs = nowMs,
            localHourOfDay = localHourOfDay,
            dayOfWeek = dayOfWeek,
            maxTracksPerMix = maxTracksPerMix.coerceIn(1, RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE),
            shuffleNonce = shuffleNonce
        )
    }
}
