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
import kotlin.math.abs

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

    const val DEFAULT_PICK_COUNT = 6
    const val MIN_PICK_COUNT = 5
    const val MAX_PICK_COUNT = 6

    fun generateDailyPicks(
        libraryTracks: List<Track>,
        epochDay: Long = currentLocalEpochDay(),
        nowMs: Long = System.currentTimeMillis(),
        targetCount: Int = DEFAULT_PICK_COUNT,
        engine: MusicIntelligenceEngine = MusicIntelligenceEngine()
    ): StableList<Track> {
        val clean = libraryTracks
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        if (clean.isEmpty()) return StableList()

        val desiredCount = minOf(clean.size, targetCount.coerceIn(MIN_PICK_COUNT, MAX_PICK_COUNT))
        val snapshot = TrackIntelligenceCache.getOrBuildSnapshot(clean, engine)
        val affinities = snapshot.getOrComputeAffinities(
            tracks = clean,
            nowMs = nowMs,
            localHourOfDay = 12 // Fixed midday anchor so picks stay locked for the whole calendar day
        )

        // Pre-compute deterministic date-seeded score per track before sorting (TimSort safe)
        val scored = clean.map { track ->
            val vec = snapshot.vectorsById[track.id]
            val affinity = affinities[track.id]?.score?.toDouble() ?: 0.45
            val qualityNudge = if (track.isHiFi) 0.04 else 0.0
            val coldStartNudge = if (!snapshot.hasListeningHistory) {
                val recency = if (track.dateAdded > 0L) 0.03 else 0.0
                val moodRichness = if ((vec?.smartMoodTags?.isNotEmpty()) == true) 0.03 else 0.0
                recency + moodRichness
            } else {
                0.0
            }

            // Strong deterministic daily rotation hash (0.0..1.0) so day D stays 100% locked
            // while day D+1 rotates meaningfully across the user's taste pool.
            val dailyRoll = deterministicUnitRoll(epochDay, track.id)
            val skipDrag = if (track.skipCount > track.completedPlayCount + 2) -0.24 else 0.0

            // Weight user taste affinity alongside date-seeded rotation:
            // keeps picks aligned with taste while guaranteeing fresh picks on the next calendar day.
            val tasteWeight = if (snapshot.hasListeningHistory) 0.46 else 0.22
            val rotationWeight = 1.0 - tasteWeight
            val rawScore = (affinity + qualityNudge + coldStartNudge + skipDrag) * tasteWeight +
                dailyRoll * rotationWeight

            val safeScore = if (rawScore.isFinite()) rawScore else dailyRoll
            Triple(track, safeScore, vec?.primaryMood ?: "calm")
        }.sortedWith(
            compareByDescending<Triple<Track, Double, String>> { it.second }
                .thenBy { it.first.id }
        )

        val picked = ArrayList<Track>(desiredCount)
        val usedIds = HashSet<String>(desiredCount * 2)
        val artistCounts = HashMap<String, Int>()
        val moodCounts = HashMap<String, Int>()

        // Pass 1: Respect artist diversity (max 2 per artist) and encourage mood variety
        for ((track, _, mood) in scored) {
            if (picked.size >= desiredCount) break
            val artistKey = LocalMoodAndAffinityEngine.normalizedArtistKey(track.artist)
            val artistCount = artistCounts[artistKey] ?: 0
            val moodCount = moodCounts[mood] ?: 0
            if (artistCount >= LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST) continue
            if (clean.size >= 10 && moodCount >= 3) continue
            if (usedIds.add(track.id)) {
                artistCounts[artistKey] = artistCount + 1
                moodCounts[mood] = moodCount + 1
                picked.add(track)
            }
        }

        // Pass 2: Relax mood variety cap while keeping artist cap = 2
        if (picked.size < desiredCount) {
            for ((track, _, _) in scored) {
                if (picked.size >= desiredCount) break
                if (track.id in usedIds) continue
                val artistKey = LocalMoodAndAffinityEngine.normalizedArtistKey(track.artist)
                val artistCount = artistCounts[artistKey] ?: 0
                if (artistCount >= LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST) continue
                if (usedIds.add(track.id)) {
                    artistCounts[artistKey] = artistCount + 1
                    picked.add(track)
                }
            }
        }

        // Pass 3: Cold-start / small single-artist library fallback so we never under-fill
        if (picked.size < desiredCount) {
            for ((track, _, _) in scored) {
                if (picked.size >= desiredCount) break
                if (usedIds.add(track.id)) {
                    picked.add(track)
                }
            }
        }

        return picked.toStableList()
    }

    internal fun deterministicUnitRoll(epochDay: Long, trackId: String): Double {
        var h = epochDay * -7046029254386353131L + 1442695040888963407L
        for (ch in trackId) {
            h = (h xor ch.code.toLong()) * 6364136223846793005L + 1442695040888963407L
        }
        // Final avalanche (SplitMix64)
        h = (h xor (h ushr 30)) * -4658895280553007687L
        h = (h xor (h ushr 27)) * -7723592293110705685L
        h = h xor (h ushr 31)
        val positive53 = (h ushr 11) and ((1L shl 53) - 1L)
        return positive53.toDouble() / (1L shl 53).toDouble()
    }
}

/**
 * Context-aware Time & Routine Mixes curator for Discover's "For This Moment" shelf.
 * Pairs user mood affinity weights, listening history, and local time-of-day / day-of-week.
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
     * Builds the active routine mix FIRST alongside 3 complementary routine mixes (4 total).
     * Cold-start safe: every routine mix returns non-empty tracks whenever [libraryTracks] is non-empty.
     */
    fun buildRoutineMixes(
        libraryTracks: List<Track>,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = LocalMoodAndAffinityEngine.currentLocalHour(nowMs),
        dayOfWeek: Int = currentLocalDayOfWeek(nowMs),
        maxTracksPerMix: Int = 12,
        engine: MusicIntelligenceEngine = MusicIntelligenceEngine()
    ): StableList<RoutineMix> {
        val clean = libraryTracks
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        if (clean.isEmpty()) return StableList()

        val snapshot = TrackIntelligenceCache.getOrBuildSnapshot(clean, engine)
        val affinities = snapshot.getOrComputeAffinities(clean, nowMs, localHourOfDay)
        val activeType = resolveActiveRoutineType(localHourOfDay, dayOfWeek)
        val orderedTypes = buildOrderedRoutineTypes(activeType)
        val cap = minOf(clean.size, maxTracksPerMix.coerceIn(4, 18))

        val mixes = orderedTypes.map { type ->
            val tracks = curateTracksForRoutine(
                type = type,
                tracks = clean,
                snapshot = snapshot,
                affinities = affinities,
                nowMs = nowMs,
                targetSize = cap
            )
            RoutineMix(
                type = type,
                title = type.title,
                subtitle = type.description,
                timeWindowLabel = type.timeWindowLabel,
                isActiveNow = type == activeType,
                tracks = tracks,
                tuneConfig = DiscoverTuneConfig(
                    selectedMood = type.tuneMood,
                    energyFilter = type.tuneEnergy,
                    discoveryBalance = type.tuneDiscoveryBalance
                )
            )
        }
        return mixes.toStableList()
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

    private fun curateTracksForRoutine(
        type: RoutineMixType,
        tracks: List<Track>,
        snapshot: LibraryIntelligenceSnapshot,
        affinities: Map<String, TrackAffinityBreakdown>,
        nowMs: Long,
        targetSize: Int
    ): StableList<Track> {
        val scored = tracks.map { track ->
            val vec = snapshot.vectorsById[track.id]
            val bpm = vec?.effectiveBpm ?: 96
            val moodVec = vec?.moodVector ?: emptyMap()
            val tags = vec?.smartMoodTags ?: StableList()
            val affinity = affinities[track.id]?.score?.toDouble() ?: 0.45
            val ageMs = if (track.lastPlayedAt > 0L) (nowMs - track.lastPlayedAt).coerceAtLeast(0L) else Long.MAX_VALUE

            val routineFit = when (type) {
                RoutineMixType.LATE_NIGHT_WIND_DOWN -> {
                    val calmScore = (moodVec["calm"] ?: 0.35) * 0.42 +
                        (moodVec["nocturnal"] ?: 0.35) * 0.33 +
                        (moodVec["organic"] ?: 0.30) * 0.25
                    val acousticBias = if (SmartMoodTag.ACOUSTIC in tags || SmartMoodTag.LATE_NIGHT in tags) 0.22 else 0.0
                    val bpmBias = when {
                        bpm <= 92 -> 0.30
                        bpm <= 100 -> 0.22
                        bpm <= 112 -> -0.06
                        else -> -0.28
                    }
                    calmScore + acousticBias + bpmBias
                }

                RoutineMixType.MORNING_FOCUS -> {
                    val focusScore = (moodVec["focused"] ?: 0.35) * 0.48 +
                        (moodVec["minimal"] ?: 0.30) * 0.26 +
                        (moodVec["organic"] ?: 0.30) * 0.26
                    val grooveBias = when (bpm) {
                        in 88..116 -> 0.24
                        in 78..124 -> 0.10
                        else -> -0.10
                    }
                    val lowVocalBias = if (vec?.isLowVocalFatigue == true) 0.22 else -0.04
                    focusScore + grooveBias + lowVocalBias
                }

                RoutineMixType.AFTERNOON_MOMENTUM -> {
                    val warmScore = (moodVec["warm"] ?: 0.35) * 0.40 +
                        (moodVec["uplifting"] ?: 0.35) * 0.35 +
                        (moodVec["focused"] ?: 0.30) * 0.25
                    val midTempoBias = when (bpm) {
                        in 96..122 -> 0.24
                        in 86..130 -> 0.10
                        else -> -0.08
                    }
                    val familiarity = when {
                        track.isFavorite -> 0.16
                        track.completedPlayCount >= 2 -> 0.12
                        track.playCount == 0 -> 0.04
                        else -> 0.06
                    }
                    warmScore + midTempoBias + familiarity
                }

                RoutineMixType.EVENING_UNWIND -> {
                    val harmonicScore = (moodVec["warm"] ?: 0.35) * 0.44 +
                        (moodVec["calm"] ?: 0.35) * 0.30 +
                        (moodVec["organic"] ?: 0.30) * 0.26
                    val soulfulBonus = if (SmartMoodTag.SOULFUL in tags || SmartMoodTag.ACOUSTIC in tags) 0.16 else 0.0
                    val favoriteBonus = (if (track.isFavorite) 0.20 else 0.0) +
                        (if (track.completedPlayCount >= 2) 0.10 else 0.0)
                    val tempoBias = when (bpm) {
                        in 74..108 -> 0.18
                        in 109..118 -> 0.04
                        else -> -0.14
                    }
                    harmonicScore + soulfulBonus + favoriteBonus + tempoBias
                }

                RoutineMixType.WEEKEND_ENERGY -> {
                    val energyScore = (moodVec["energetic"] ?: 0.35) * 0.50 +
                        (moodVec["uplifting"] ?: 0.35) * 0.35 +
                        (moodVec["warm"] ?: 0.30) * 0.15
                    val upbeatTagBonus = if (SmartMoodTag.UPBEAT in tags) 0.20 else 0.0
                    val tempoBias = when {
                        bpm >= 118 -> 0.28
                        bpm >= 108 -> 0.16
                        bpm >= 98 -> 0.02
                        else -> -0.18
                    }
                    val rediscoveryBoost = when {
                        track.playCount == 0 -> 0.18
                        ageMs >= 7L * LocalMoodAndAffinityEngine.ONE_DAY_MS -> 0.15
                        else -> 0.0
                    }
                    energyScore + upbeatTagBonus + tempoBias + rediscoveryBoost
                }
            }

            val total = routineFit * 0.64 + affinity * 0.36
            val safeTotal = if (total.isFinite()) total else 0.45
            Triple(track, safeTotal, bpm)
        }.sortedWith(
            compareByDescending<Triple<Track, Double, Int>> { it.second }
                .thenBy { it.first.id }
        )

        val candidates = scored.map { it.first }
        val capped = LocalMoodAndAffinityEngine.enforceArtistDiversityCap(
            candidates = candidates,
            maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST,
            limit = targetSize
        )

        val filled = if (capped.size < targetSize) {
            val seen = capped.mapTo(HashSet(targetSize * 2)) { it.id }
            val extra = candidates.filter { it.id !in seen }.take(targetSize - capped.size)
            capped + extra
        } else {
            capped
        }

        // For Evening Unwind, smooth tempo transitions by gentle BPM progression
        val finalTracks = if (type == RoutineMixType.EVENING_UNWIND && filled.size >= 3) {
            orderForSmoothTempoFlow(filled, snapshot)
        } else {
            filled
        }

        return finalTracks.toStableList()
    }

    private fun orderForSmoothTempoFlow(
        tracks: List<Track>,
        snapshot: LibraryIntelligenceSnapshot
    ): List<Track> {
        val remaining = ArrayList(tracks)
        val ordered = ArrayList<Track>(tracks.size)
        ordered.add(remaining.removeAt(0))
        while (remaining.isNotEmpty()) {
            val prev = ordered.last()
            val prevBpm = snapshot.vectorsById[prev.id]?.effectiveBpm ?: 92
            val prevArtist = LocalMoodAndAffinityEngine.normalizedArtistKey(prev.artist)
            var bestIdx = 0
            var bestCost = Int.MAX_VALUE
            for (i in remaining.indices) {
                val cand = remaining[i]
                val bpm = snapshot.vectorsById[cand.id]?.effectiveBpm ?: 92
                val artistPenalty = if (LocalMoodAndAffinityEngine.normalizedArtistKey(cand.artist) == prevArtist) 28 else 0
                val cost = abs(bpm - prevBpm) + artistPenalty
                if (cost < bestCost) {
                    bestCost = cost
                    bestIdx = i
                }
            }
            ordered.add(remaining.removeAt(bestIdx))
        }
        return ordered
    }
}
