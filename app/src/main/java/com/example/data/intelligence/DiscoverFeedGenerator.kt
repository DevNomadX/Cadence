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
import kotlin.math.roundToInt
import kotlin.random.Random

@Keep
enum class DiscoverCategory(val id: String) {
    MADE_FOR_YOU("made_for_you"),
    SMART_MIX("smart_mix"),
    REDISCOVER("rediscover"),
    FORGOTTEN_FAVORITES("forgotten_favorites"),
    CONTINUE_THE_VIBE("continue_the_vibe"),
    EXPLORE_YOUR_LIBRARY("explore_your_library")
}

@Keep
@Immutable
data class DiscoverTuneConfig(
    val selectedMood: String? = null,
    val energyFilter: String? = null,
    val discoveryBalance: Float = 0.5f
) {
    val isCustomized: Boolean
        get() = selectedMood != null || energyFilter != null || abs(discoveryBalance - 0.5f) >= 0.05f

    val balanceLabel: String
        get() = when {
            discoveryBalance <= 0.32f -> "Familiar"
            discoveryBalance >= 0.68f -> "Fresh"
            else -> "Balanced"
        }
}

@Keep
@Immutable
data class ListeningInsights(
    val topMood: String = "",
    val topGenre: String = "",
    val topArtist: String = "",
    val totalListeningMs: Long = 0L,
    val totalTrackCount: Int = 0,
    val hiFiTrackCount: Int = 0,
    val hiFiPercentage: Int = 0,
    val completedPlaysTotal: Int = 0
)

@Keep
@Immutable
data class DiscoverSection(
    val category: DiscoverCategory,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val tracks: StableList<Track>,
    val trackCues: StableMap<String, String> = StableMap()
)

@Keep
@Immutable
data class DiscoverFeed(
    val sections: StableList<DiscoverSection> = StableList(),
    val moodPlaylists: StableList<MoodSmartPlaylist> = StableList(),
    val routineMixes: StableList<RoutineMix> = StableList(),
    val anchorTrackTitle: String? = null,
    val smartMixOutput: MusicIntelligenceOutput? = null,
    val insights: ListeningInsights = ListeningInsights()
)

/**
 * Local, zero-server Discover suggestion engine computing 6 distinct personalized categories
 * plus local Listening Insights directly from Room track metadata (completion ratio, skip count,
 * play count, last-played recency decay, date added, genre, artist, album, duration, BPM, Hi-Fi,
 * and favorite status).
 *
 * Pre-computes all deterministic tie-breakers prior to sorting so TimSort never violates
 * transitivity on large libraries (32+ tracks), avoids duplicate tracks across sections whenever
 * library size permits, and provides graceful metadata-driven fallbacks when history is sparse.
 */
internal class DiscoverFeedGenerator(
    private val engine: MusicIntelligenceEngine
) {
    fun generate(
        libraryTracks: List<Track>,
        currentTrackId: String? = null,
        skippedTrackIds: List<String> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
        shuffleNonce: Int = 0,
        maxTracksPerSection: Int = 10,
        tuneConfig: DiscoverTuneConfig = DiscoverTuneConfig(),
        localHourOfDay: Int = LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
    ): DiscoverFeed {
        val cleanTracks = libraryTracks
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
        if (cleanTracks.isEmpty()) {
            return DiscoverFeed()
        }

        val cap = maxTracksPerSection.coerceIn(3, 20)
        val daySeed = (nowMs / ONE_DAY_MS).toInt() + shuffleNonce * 97
        val skippedSet = skippedTrackIds.toSet()
        val usedIds = HashSet<String>(cleanTracks.size)

        // Reuse versioned TrackIntelligenceCache snapshot to avoid redundant O(N) recomputation
        val snapshot = TrackIntelligenceCache.getOrBuildSnapshot(cleanTracks, engine)
        val bpmById = HashMap<String, Int>(cleanTracks.size)
        val energyById = HashMap<String, String>(cleanTracks.size)
        val moodVecById = HashMap<String, Map<String, Double>>(cleanTracks.size)
        val primaryMoodById = HashMap<String, String>(cleanTracks.size)
        for (t in cleanTracks) {
            val cached = snapshot.vectorsById[t.id]
            val bpm = cached?.effectiveBpm ?: engine.effectiveBpmFor(t)
            val vec = cached?.moodVector ?: engine.moodVectorFor(t)
            bpmById[t.id] = bpm
            energyById[t.id] = cached?.energyLevel ?: engine.energyLevelFor(bpm)
            moodVecById[t.id] = vec
            primaryMoodById[t.id] = cached?.primaryMood ?: vec.maxByOrNull { it.value }?.key ?: "calm"
        }

        val insights = RecommendationCore.computeListeningInsights(cleanTracks)
        val moodPlaylists = LocalMoodAndAffinityEngine.buildMoodSmartPlaylists(
            tracks = cleanTracks,
            nowMs = nowMs,
            localHourOfDay = localHourOfDay,
            maxTracksPerMood = RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE
        ).toStableList()
        val routineMixes = RoutineMixCurator.buildRoutineMixes(
            libraryTracks = cleanTracks,
            nowMs = nowMs,
            localHourOfDay = localHourOfDay,
            dayOfWeek = currentLocalDayOfWeek(nowMs),
            maxTracksPerMix = minOf(cap, RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE),
            engine = engine
        )

        // Compute adaptive per-section target so small libraries still spread cleanly with minimal overlap
        val targetSize = when {
            cleanTracks.size >= cap * 4 -> cap
            cleanTracks.size >= 18 -> minOf(cap, 6)
            cleanTracks.size >= 12 -> minOf(cap, 4)
            else -> minOf(cap, maxOf(1, cleanTracks.size / 2).coerceAtLeast(minOf(2, cleanTracks.size)))
        }

        val sections = ArrayList<DiscoverSection>(6)

        // 1. Made for you (Daily Mix — routed via RecommendationCore 60/25/15 mix)
        val madeForYou = buildMadeForYou(
            tracks = cleanTracks,
            usedIds = usedIds,
            skippedSet = skippedSet,
            bpmById = bpmById,
            primaryMoodById = primaryMoodById,
            tuneConfig = tuneConfig,
            nowMs = nowMs,
            localHourOfDay = localHourOfDay,
            seed = daySeed + 11,
            targetSize = targetSize
        )
        sections += madeForYou
        madeForYou.tracks.forEach { usedIds.add(it.id) }

        // 2. Smart Mix (routed via RecommendationCore with interactive mood/energy/Familiar↔Fresh tuning)
        val (smartMixSection, smartMixOutput) = buildSmartMix(
            tracks = cleanTracks,
            usedIds = usedIds,
            skippedTrackIds = skippedTrackIds,
            bpmById = bpmById,
            primaryMoodById = primaryMoodById,
            tuneConfig = tuneConfig,
            nowMs = nowMs,
            localHourOfDay = localHourOfDay,
            shuffleNonce = shuffleNonce,
            targetSize = targetSize
        )
        sections += smartMixSection
        smartMixSection.tracks.forEach { usedIds.add(it.id) }

        // 3. Rediscover (30+ days quiet or 0 plays with low skips)
        val rediscover = buildRediscover(
            tracks = cleanTracks,
            usedIds = usedIds,
            nowMs = nowMs,
            seed = daySeed + 31,
            targetSize = targetSize
        )
        sections += rediscover
        rediscover.tracks.forEach { usedIds.add(it.id) }

        // 4. Forgotten favorites (favorited or high-completion tracks untouched for a long time)
        val forgottenFavorites = buildForgottenFavorites(
            tracks = cleanTracks,
            usedIds = usedIds,
            nowMs = nowMs,
            seed = daySeed + 47,
            targetSize = targetSize
        )
        sections += forgottenFavorites
        forgottenFavorites.tracks.forEach { usedIds.add(it.id) }

        // 5. Continue the vibe (matching mood/tempo/artist/genre of anchor with anti-repetition)
        val (continueVibe, anchorTitle) = buildContinueTheVibe(
            tracks = cleanTracks,
            currentTrackId = currentTrackId,
            usedIds = usedIds,
            skippedSet = skippedSet,
            bpmById = bpmById,
            moodVecById = moodVecById,
            primaryMoodById = primaryMoodById,
            nowMs = nowMs,
            targetSize = targetSize
        )
        sections += continueVibe
        continueVibe.tracks.forEach { usedIds.add(it.id) }

        // 6. Deep Cuts / Unplayed Gems (EXPLORE_YOUR_LIBRARY: playCount <= 1 & low skips)
        val exploreLibrary = buildDeepCutsAndGems(
            tracks = cleanTracks,
            usedIds = usedIds,
            skippedSet = skippedSet,
            seed = daySeed + 73,
            shuffleNonce = shuffleNonce,
            targetSize = targetSize
        )
        sections += exploreLibrary

        return DiscoverFeed(
            sections = sections.distinctBy { it.category }.toStableList(),
            moodPlaylists = moodPlaylists,
            routineMixes = routineMixes,
            anchorTrackTitle = anchorTitle,
            smartMixOutput = smartMixOutput,
            insights = insights
        )
    }

    private data class RankedTrack(
        val track: Track,
        val score: Double,
        val tieBreaker: Double
    )

    private fun affinityBreakdown(
        track: Track,
        nowMs: Long,
        skippedSet: Set<String>
    ): TrackAffinityBreakdown = RecommendationCore.computeAffinityBreakdown(
        track = track,
        nowMs = nowMs,
        sessionSkipped = track.id in skippedSet
    )

    // --- 1. Made for you (Daily Mix: 60% Favorites / 25% Forgotten Gems / 15% Under-Played) ---
    private fun buildMadeForYou(
        tracks: List<Track>,
        usedIds: Set<String>,
        skippedSet: Set<String>,
        bpmById: Map<String, Int>,
        primaryMoodById: Map<String, String>,
        tuneConfig: DiscoverTuneConfig,
        nowMs: Long,
        localHourOfDay: Int,
        seed: Int,
        targetSize: Int
    ): DiscoverSection {
        val hasHistory = tracks.any {
            it.playCount > 0 || it.completedPlayCount > 0 || it.isFavorite || it.lastPlayedAt > 0L
        }

        val balanced = LocalMoodAndAffinityEngine.buildBalancedMix(
            tracks = tracks,
            targetSize = targetSize,
            nowMs = nowMs,
            localHourOfDay = localHourOfDay,
            sessionSkippedIds = skippedSet,
            tuneConfig = tuneConfig,
            seed = seed,
            excludedIds = usedIds,
            maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST
        )
        val picked = balanced.tracks.ifEmpty { pickDistinctTracks(tracks, usedIds, targetSize) }

        if (!hasHistory && !tuneConfig.isCustomized) {
            val cues = picked.associate { t ->
                val genre = t.genre.ifBlank { primaryMoodById[t.id]?.replaceFirstChar { it.uppercase() } ?: "Fresh" }
                t.id to if (t.isHiFi) "Hi-Fi · $genre" else "New · $genre"
            }
            return DiscoverSection(
                category = DiscoverCategory.MADE_FOR_YOU,
                title = "Made for you",
                subtitle = "A starter daily mix crafted from your newest local additions",
                badgeText = "Daily Mix",
                tracks = picked.toStableList(),
                trackCues = cues.toStableMap()
            )
        }

        val artistAffinity = HashMap<String, Double>()
        val genreAffinity = HashMap<String, Double>()
        for (t in tracks) {
            val weight = t.playCount.coerceAtLeast(0).toDouble() +
                (t.completedPlayCount.coerceAtLeast(0) * 2.0) +
                (if (t.isFavorite) 4.0 else 0.0) -
                (t.skipCount.coerceAtLeast(0) * 1.2)
            if (weight > 0.0) {
                val aKey = t.artist.trim().lowercase(Locale.ROOT)
                if (aKey.isNotEmpty() && aKey != "unknown artist") {
                    artistAffinity[aKey] = (artistAffinity[aKey] ?: 0.0) + weight
                }
                val gKey = t.genre.trim().lowercase(Locale.ROOT)
                if (gKey.isNotEmpty()) {
                    genreAffinity[gKey] = (genreAffinity[gKey] ?: 0.0) + weight
                }
            }
        }

        val topArtistDisplay = tracks
            .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown Artist", ignoreCase = true) }
            .maxByOrNull { artistAffinity[it.artist.trim().lowercase(Locale.ROOT)] ?: 0.0 }
            ?.artist
        val topGenreDisplay = tracks
            .filter { it.genre.isNotBlank() }
            .maxByOrNull { genreAffinity[it.genre.trim().lowercase(Locale.ROOT)] ?: 0.0 }
            ?.genre

        val isLateNight = localHourOfDay >= 21 || localHourOfDay <= 4
        val subtitle = when {
            tuneConfig.isCustomized -> {
                val moodPart = tuneConfig.selectedMood?.replaceFirstChar { it.uppercase() } ?: "All moods"
                val energyPart = tuneConfig.energyFilter?.replaceFirstChar { it.uppercase() }?.let { " · $it energy" }.orEmpty()
                "Tuned for $moodPart$energyPart · ${tuneConfig.balanceLabel} blend"
            }
            topArtistDisplay != null && topGenreDisplay != null ->
                "Daily mix shaped by $topArtistDisplay, $topGenreDisplay & high-completion listens"
            topArtistDisplay != null ->
                "Daily mix built around $topArtistDisplay and your favorite listens"
            topGenreDisplay != null ->
                "Daily mix blending your favorite $topGenreDisplay picks"
            isLateNight ->
                "Late-night daily mix tuned to your calm favorites & forgotten gems"
            else ->
                "Daily mix tuned to your completed plays and favorite tracks"
        }

        val cues = picked.associate { t ->
            val ageMs = if (t.lastPlayedAt > 0L) (nowMs - t.lastPlayedAt).coerceAtLeast(0L) else -1L
            val cue = when {
                ageMs >= TWENTY_ONE_DAYS_MS && (t.isFavorite || t.completedPlayCount >= 2) -> "Forgotten gem"
                t.completedPlayCount >= 2 -> "${t.completedPlayCount} full listens"
                t.isFavorite -> "Favorite · ${bpmById[t.id] ?: 90} BPM"
                t.playCount <= 1 -> "Fresh pick"
                t.genre.isNotBlank() -> t.genre
                else -> "${bpmById[t.id] ?: 90} BPM"
            }
            t.id to cue
        }

        val badge = if (tuneConfig.isCustomized) {
            "Tuned · ${tuneConfig.balanceLabel}"
        } else {
            "Daily Mix"
        }

        return DiscoverSection(
            category = DiscoverCategory.MADE_FOR_YOU,
            title = "Made for you",
            subtitle = subtitle,
            badgeText = badge,
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
    }

    // --- 2. Smart Mix (routed through RecommendationCore) ---
    private fun buildSmartMix(
        tracks: List<Track>,
        usedIds: Set<String>,
        skippedTrackIds: List<String>,
        bpmById: Map<String, Int>,
        primaryMoodById: Map<String, String>,
        tuneConfig: DiscoverTuneConfig,
        nowMs: Long,
        localHourOfDay: Int,
        shuffleNonce: Int,
        targetSize: Int
    ): Pair<DiscoverSection, MusicIntelligenceOutput> {
        val unusedPool = tracks.filter { it.id !in usedIds }
        val candidatePool = if (unusedPool.size >= minOf(targetSize, 4)) unusedPool else tracks
        val targetMood = tuneConfig.selectedMood ?: if (shuffleNonce > 0) {
            val moods = listOf("calm", "focused", "warm", "nocturnal", "energetic", "organic", "uplifting")
            moods[safeMod(shuffleNonce - 1, moods.size)]
        } else {
            null
        }
        val effectiveTune = tuneConfig.copy(selectedMood = targetMood)
        val timeOfDay = TimeOfDayCategory.fromHour(localHourOfDay)
        val skipSignals = skippedTrackIds.map { skippedId ->
            PlaybackSignal(
                trackId = skippedId,
                type = SignalType.EARLY_SKIP_PENALTY,
                affinityDelta = SignalProcessor.EARLY_SKIP_PENALTY_DELTA,
                activePlayDurationMs = SignalProcessor.MIN_ACTIVE_PLAY_MS,
                trackDurationMs = 180_000L,
                completionRatio = 0.05f,
                timestampMs = nowMs
            )
        }
        val coreResult = RecommendationCore.generateSessionMix(
            tracks = candidatePool,
            signals = skipSignals,
            nowMs = nowMs,
            timeOfDay = timeOfDay,
            tuneConfig = effectiveTune,
            excludedTrackIds = usedIds,
            rotationSeed = if (shuffleNonce > 0) shuffleNonce.toLong() else null,
            targetSize = targetSize.coerceIn(1, RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE)
        )

        val lookup = tracks.associateBy { it.id }
        val resolved = coreResult.trackIds
            .mapNotNull { lookup[it] }
            .distinctBy { it.id }
            .ifEmpty { pickDistinctTracks(tracks, usedIds, targetSize) }

        val output = RecommendationCore.buildTunedSmartMixOutput(
            orderedTracks = resolved,
            scoresByTrackId = coreResult.scoresByTrackId,
            tuneConfig = effectiveTune,
            seedTrack = null,
            timeOfDay = timeOfDay
        )

        val tempo = output.tempoDescriptors
        val primaryMood = output.moodClassifications.firstOrNull()?.mood
            ?: primaryMoodById[resolved.firstOrNull()?.id]
            ?: "balanced"
        val cohesionPct = (safeFinite(output.playlistProfile.cohesionScore).coerceIn(0.0, 1.0) * 100.0).roundToInt()

        val badge = if (tempo.dominantBpmRange.min > 0) {
            "${tempo.dominantBpmRange.min}–${tempo.dominantBpmRange.max} BPM · $cohesionPct%"
        } else {
            primaryMood.replaceFirstChar { it.uppercase() }
        }

        val cues = output.recommendedTracks.associate { rec ->
            val bpm = bpmById[rec.trackId] ?: 90
            rec.trackId to "$bpm BPM · ${rec.moodTag.replaceFirstChar { it.uppercase() }}"
        }

        val section = DiscoverSection(
            category = DiscoverCategory.SMART_MIX,
            title = "Smart Mix",
            subtitle = "${output.playlistProfile.suggestedTitle} — cohesive ${tempo.descriptor.replace('_', ' ')} flow",
            badgeText = badge,
            tracks = resolved.toStableList(),
            trackCues = cues.toStableMap()
        )
        return section to output
    }

    // --- 3. Rediscover (30+ days unplayed or 0 plays) ---
    private fun buildRediscover(
        tracks: List<Track>,
        usedIds: Set<String>,
        nowMs: Long,
        seed: Int,
        targetSize: Int
    ): DiscoverSection {
        val rng = Random(seed)
        val staleOrUnplayed = tracks.filter { t ->
            t.playCount == 0 || (t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) >= THIRTY_DAYS_MS)
        }

        val ordered = if (staleOrUnplayed.isNotEmpty()) {
            staleOrUnplayed
                .map { t ->
                    val unplayedRank = if (t.playCount <= 0) 0 else 1
                    Triple(t, unplayedRank, rng.nextDouble())
                }
                .sortedWith(
                    compareBy<Triple<Track, Int, Double>> { it.first.skipCount.coerceAtLeast(0) }
                        .thenBy { it.second }
                        .thenBy { it.first.lastPlayedAt }
                        .thenBy { it.third }
                        .thenBy { it.first.id }
                )
                .map { it.first }
        } else {
            tracks
                .map { it to rng.nextDouble() }
                .sortedWith(
                    compareBy<Pair<Track, Double>> { it.first.lastPlayedAt }
                        .thenBy { it.first.skipCount.coerceAtLeast(0) }
                        .thenBy { it.first.playCount }
                        .thenBy { it.second }
                        .thenBy { it.first.id }
                )
                .map { it.first }
        }

        val picked = pickDistinctTracks(ordered, usedIds, targetSize, fallbackPool = tracks)
        val hasTrueRediscover = staleOrUnplayed.isNotEmpty()

        val cues = picked.associate { t ->
            val cue = when {
                t.playCount == 0 -> "Never played"
                t.lastPlayedAt > 0L -> {
                    val daysAgo = ((nowMs - t.lastPlayedAt) / ONE_DAY_MS).coerceAtLeast(1L)
                    "${daysAgo}d ago"
                }
                else -> "Ready to spin"
            }
            t.id to cue
        }

        return DiscoverSection(
            category = DiscoverCategory.REDISCOVER,
            title = "Rediscover",
            subtitle = if (hasTrueRediscover) {
                "Songs untouched for 30+ days or waiting for their first play"
            } else {
                "Least recently spun tracks from your local collection"
            },
            badgeText = if (hasTrueRediscover) "30+ days quiet" else "Dust off",
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
    }

    // --- 4. Forgotten favorites (favorited or high-completion tracks not played recently) ---
    private fun buildForgottenFavorites(
        tracks: List<Track>,
        usedIds: Set<String>,
        nowMs: Long,
        seed: Int,
        targetSize: Int
    ): DiscoverSection {
        val rng = Random(seed)
        val playedTracks = tracks.filter { it.playCount > 0 || it.completedPlayCount > 0 }
        val veryRecentIds = tracks
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(5)
            .mapTo(HashSet()) { it.id }

        val minHighPlayThreshold = if (playedTracks.isNotEmpty()) {
            val sortedCounts = playedTracks.map { maxOf(it.playCount, it.completedPlayCount) }.sortedDescending()
            val topThirdIdx = (sortedCounts.size / 3).coerceAtMost(sortedCounts.lastIndex)
            maxOf(2, sortedCounts[topThirdIdx])
        } else {
            3
        }

        val forgottenStrict = tracks.filter { t ->
            val highAffinity = t.playCount >= minHighPlayThreshold ||
                t.completedPlayCount >= 2 ||
                (t.isFavorite && (t.playCount >= 1 || t.completedPlayCount >= 1))
            val lowSkips = t.skipCount <= maxOf(1, t.completedPlayCount + 1)
            val notRecent = (t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) >= FOURTEEN_DAYS_MS) ||
                (t.lastPlayedAt == 0L && (t.playCount >= minHighPlayThreshold || t.completedPlayCount >= 2)) ||
                (t.id !in veryRecentIds && (t.playCount >= minHighPlayThreshold || t.completedPlayCount >= 2))
            highAffinity && lowSkips && notRecent
        }

        val (ordered, isTrueForgotten) = when {
            forgottenStrict.isNotEmpty() -> {
                val ranked = forgottenStrict.map { t ->
                    val ageDays = if (t.lastPlayedAt > 0L) {
                        ((nowMs - t.lastPlayedAt) / ONE_DAY_MS).coerceIn(1L, 36_500L).toDouble()
                    } else {
                        30.0
                    }
                    val breakdown = affinityBreakdown(t, nowMs, emptySet())
                    val favBoost = if (t.isFavorite) 1.38 else 1.0
                    val compBoost = 1.0 + breakdown.completionSignal.coerceAtLeast(0f).toDouble()
                    val skipDampen = (1.0 - breakdown.skipPenalty.toDouble() * 0.75).coerceAtLeast(0.25)
                    val safePlays = maxOf(t.playCount, t.completedPlayCount).coerceAtLeast(0).toDouble()
                    val score = safeFinite(ln(safePlays + 1.0) * ln(ageDays + 2.0) * favBoost * compBoost * skipDampen)
                    RankedTrack(t, score, rng.nextDouble(0.0, 0.01))
                }.sortedWith(
                    compareByDescending<RankedTrack> { it.score }
                        .thenBy { it.tieBreaker }
                        .thenBy { it.track.id }
                ).map { it.track }
                ranked to true
            }
            playedTracks.isNotEmpty() -> {
                val ranked = playedTracks
                    .map { t ->
                        val score = safeFinite(
                            (t.completedPlayCount * 2.0) + t.playCount + (if (t.isFavorite) 4.0 else 0.0) - (t.skipCount * 1.5)
                        )
                        RankedTrack(t, score, rng.nextDouble(0.0, 0.01))
                    }
                    .sortedWith(
                        compareByDescending<RankedTrack> { it.score }
                            .thenBy { it.track.lastPlayedAt }
                            .thenBy { it.track.id }
                    )
                    .map { it.track }
                ranked to false
            }
            else -> {
                val ranked = tracks
                    .map { it to rng.nextDouble() }
                    .sortedWith(
                        compareByDescending<Pair<Track, Double>> { if (it.first.isFavorite) 1 else 0 }
                            .thenByDescending { if (it.first.isHiFi) 1 else 0 }
                            .thenByDescending { it.first.durationMs }
                            .thenBy { it.second }
                            .thenBy { it.first.id }
                    )
                    .map { it.first }
                ranked to false
            }
        }

        val picked = pickDistinctTracks(ordered, usedIds, targetSize, fallbackPool = tracks)

        val cues = picked.associate { t ->
            val cue = when {
                t.completedPlayCount >= 2 && t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) >= FOURTEEN_DAYS_MS -> {
                    val days = (nowMs - t.lastPlayedAt) / ONE_DAY_MS
                    "${t.completedPlayCount} full · ${days}d ago"
                }
                t.playCount > 0 && t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) >= FOURTEEN_DAYS_MS -> {
                    val days = (nowMs - t.lastPlayedAt) / ONE_DAY_MS
                    "${t.playCount} plays · ${days}d ago"
                }
                t.completedPlayCount > 0 -> "${t.completedPlayCount} full listens"
                t.playCount > 0 -> "${t.playCount} all-time plays"
                t.isFavorite -> "Saved favorite"
                else -> t.album.ifBlank { "Standout cut" }
            }
            t.id to cue
        }

        return DiscoverSection(
            category = DiscoverCategory.FORGOTTEN_FAVORITES,
            title = "Forgotten Gems",
            subtitle = if (isTrueForgotten) {
                "Previously loved, high-completion tracks you haven't spun in a while"
            } else if (playedTracks.isNotEmpty()) {
                "High-completion favorites ready for another encore"
            } else {
                "Favorites & standout album cuts from your collection"
            },
            badgeText = if (isTrueForgotten) "Throwback" else "Favorites",
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
    }

    // --- 5. Continue the vibe ---
    private fun buildContinueTheVibe(
        tracks: List<Track>,
        currentTrackId: String?,
        usedIds: Set<String>,
        skippedSet: Set<String>,
        bpmById: Map<String, Int>,
        moodVecById: Map<String, Map<String, Double>>,
        primaryMoodById: Map<String, String>,
        nowMs: Long,
        targetSize: Int
    ): Pair<DiscoverSection, String?> {
        val anchor = currentTrackId?.let { id -> tracks.firstOrNull { it.id == id } }
            ?: tracks.filter { it.lastPlayedAt > 0L }.maxByOrNull { it.lastPlayedAt }
            ?: tracks.maxByOrNull { it.dateAdded }
            ?: tracks.firstOrNull()
            ?: return DiscoverSection(
                category = DiscoverCategory.CONTINUE_THE_VIBE,
                title = "Similar Vibe",
                subtitle = "Matches the mood & sound of your recent listens",
                badgeText = "Vibe",
                tracks = StableList(),
                trackCues = StableMap()
            ) to null

        val anchorBpm = bpmById[anchor.id] ?: 90
        val anchorMoodVec = moodVecById[anchor.id].orEmpty()
        val anchorMood = primaryMoodById[anchor.id] ?: "calm"
        val anchorArtist = anchor.artist.trim().lowercase(Locale.ROOT)
        val anchorGenre = anchor.genre.trim().lowercase(Locale.ROOT)

        val others = tracks.filterNot { it.id == anchor.id }.ifEmpty { tracks }
        val scored = others.map { t ->
            val tBpm = bpmById[t.id] ?: 90
            val bpmDelta = abs(tBpm - anchorBpm)
            val bpmScore = (1.0 - (bpmDelta / 60.0)).coerceIn(0.0, 1.0)
            val moodSim = engine.moodSimilarity(anchorMoodVec, moodVecById[t.id].orEmpty())
            val sameGenre = anchorGenre.isNotEmpty() && t.genre.trim().lowercase(Locale.ROOT) == anchorGenre
            val sameArtist = anchorArtist.isNotEmpty() &&
                anchorArtist != "unknown artist" &&
                t.artist.trim().lowercase(Locale.ROOT) == anchorArtist

            val breakdown = affinityBreakdown(t, nowMs, skippedSet)
            val score = safeFinite(
                moodSim * 0.34 +
                    bpmScore * 0.24 +
                    (if (sameGenre) 0.26 else 0.0) +
                    (if (sameArtist) 0.20 else 0.0) +
                    breakdown.completionSignal.toDouble() * 0.25 +
                    breakdown.recencyAdjustment.toDouble() * 0.55 -
                    breakdown.skipPenalty.toDouble() * 0.65
            )
            RankedTrack(t, score, 0.0)
        }.sortedWith(
            compareByDescending<RankedTrack> { it.score }
                .thenBy { it.track.id }
        ).map { it.track }

        val picked = pickDistinctTracks(scored, usedIds, targetSize, fallbackPool = others)

        val cues = picked.associate { t ->
            val tBpm = bpmById[t.id] ?: 90
            val cue = when {
                anchorArtist.isNotEmpty() && anchorArtist != "unknown artist" &&
                    t.artist.trim().lowercase(Locale.ROOT) == anchorArtist -> "Same artist"
                anchorGenre.isNotEmpty() &&
                    t.genre.trim().lowercase(Locale.ROOT) == anchorGenre -> t.genre
                abs(tBpm - anchorBpm) <= 12 -> "$tBpm BPM match"
                else -> "${(primaryMoodById[t.id] ?: anchorMood).replaceFirstChar { it.uppercase() }} vibe"
            }
            t.id to cue
        }

        val anchorDisplayTitle = anchor.title.ifBlank { "Untitled Track" }
        val badge = anchor.genre.ifBlank { anchorMood.replaceFirstChar { it.uppercase() } }
        val section = DiscoverSection(
            category = DiscoverCategory.CONTINUE_THE_VIBE,
            title = "Similar Vibe",
            subtitle = "Matches the mood & sound of \"$anchorDisplayTitle\"",
            badgeText = badge,
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
        return section to anchorDisplayTitle
    }

    // --- 6. Deep Cuts & Unplayed Gems (EXPLORE_YOUR_LIBRARY) ---
    private fun buildDeepCutsAndGems(
        tracks: List<Track>,
        usedIds: Set<String>,
        skippedSet: Set<String>,
        seed: Int,
        shuffleNonce: Int,
        targetSize: Int
    ): DiscoverSection {
        val rng = Random(seed)
        val unused = tracks.filter { it.id !in usedIds }
        val workingPool = if (unused.size >= 3) unused else tracks

        val genres = workingPool
            .map { it.genre.trim() }
            .filter { it.isNotBlank() && !it.equals("Audio", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

        val spotlightGenre = if (genres.isNotEmpty()) {
            genres[safeMod(seed + shuffleNonce, genres.size)]
        } else {
            null
        }

        val albumAvgPlays = tracks.groupBy { it.album.ifBlank { "Unknown Album" } }
            .mapValues { (_, list) ->
                val avg = if (list.isNotEmpty()) list.map { it.playCount.coerceAtLeast(0) }.average() else 0.0
                safeFinite(avg).coerceAtLeast(0.0)
            }

        val hasTrueDeepCuts = workingPool.any { it.playCount <= 1 && it.skipCount <= 1 }

        val ranked = workingPool.map { t ->
            val albumKey = t.album.ifBlank { "Unknown Album" }
            val albumAvg = (albumAvgPlays[albumKey] ?: 0.0).coerceAtLeast(0.0)
            val isUnplayed = t.playCount <= 0
            val isLowPlay = t.playCount <= 1
            val lowPlayBonus = when {
                isUnplayed && t.skipCount == 0 -> 0.62
                isLowPlay && t.skipCount <= 1 -> 0.48
                else -> 0.05
            }
            val underplayedAlbumBonus = (1.0 / (albumAvg + 1.0)) * 0.24
            val genreSpotlightBonus = if (spotlightGenre != null &&
                t.genre.trim().equals(spotlightGenre, ignoreCase = true)
            ) 0.18 else 0.0
            val hiFiGemBonus = if (t.isHiFi) 0.08 else 0.0
            val unusedBonus = if (t.id !in usedIds) 0.22 else 0.0
            val skipPenalty = affinityBreakdown(t, 0L, skippedSet).skipPenalty.toDouble() * 0.85

            RankedTrack(
                track = t,
                score = safeFinite(lowPlayBonus + underplayedAlbumBonus + genreSpotlightBonus + hiFiGemBonus + unusedBonus - skipPenalty),
                tieBreaker = rng.nextDouble(0.0, 0.08)
            )
        }.sortedWith(
            compareByDescending<RankedTrack> { it.score + it.tieBreaker }
                .thenBy { it.track.id }
        ).map { it.track }

        val picked = pickDistinctTracks(ranked, usedIds, targetSize, fallbackPool = tracks)

        val cues = picked.associate { t ->
            val cue = when {
                t.playCount <= 0 -> if (t.isHiFi) "Unplayed · Hi-Fi" else "Unplayed gem"
                t.playCount == 1 -> "1 spin · Deep cut"
                spotlightGenre != null && t.genre.trim().equals(spotlightGenre, ignoreCase = true) ->
                    "$spotlightGenre spotlight"
                t.album.isNotBlank() && !t.album.equals("Unknown Album", ignoreCase = true) ->
                    t.album
                else -> t.folderName.ifBlank { "Hidden gem" }
            }
            t.id to cue
        }

        val subtitle = when {
            hasTrueDeepCuts && spotlightGenre != null ->
                "Unplayed & low-play tracks worth exploring · $spotlightGenre spotlight"
            hasTrueDeepCuts ->
                "Unplayed and low-play gems hidden across your local folders"
            else ->
                "Deeper album cuts from your local collection"
        }

        return DiscoverSection(
            category = DiscoverCategory.EXPLORE_YOUR_LIBRARY,
            title = "Deep Cuts & Unplayed Gems",
            subtitle = subtitle,
            badgeText = if (hasTrueDeepCuts) "Unplayed Gems" else (spotlightGenre ?: "Deep cuts"),
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
    }

    /**
     * Selects up to [targetSize] tracks enforcing a strict maximum of 2 songs per normalized
     * artist name plus adjacent artist spacing, strongly prioritizing tracks not yet present
     * in [usedIds].
     */
    private fun pickDistinctTracks(
        rankedPrimary: List<Track>,
        usedIds: Set<String>,
        targetSize: Int,
        fallbackPool: List<Track> = emptyList()
    ): List<Track> {
        val result = ArrayList<Track>(targetSize)
        val localSeen = HashSet<String>(targetSize * 2)
        val artistCounts = HashMap<String, Int>()
        val maxPerArtist = LocalMoodAndAffinityEngine.MAX_SONGS_PER_ARTIST

        fun appendWithArtistCapAndSpacing(source: List<Track>) {
            val remaining = source.filterTo(ArrayList()) { it.id !in localSeen }
            while (result.size < targetSize && remaining.isNotEmpty()) {
                val eligibleIdx = remaining.indexOfFirst {
                    val key = LocalMoodAndAffinityEngine.normalizedArtistKey(it.artist)
                    (artistCounts[key] ?: 0) < maxPerArtist
                }
                if (eligibleIdx < 0) break

                val prevArtist = result.lastOrNull()?.let { LocalMoodAndAffinityEngine.normalizedArtistKey(it.artist) }
                val pickIdx = if (prevArtist != null && prevArtist != "unknown artist") {
                    val altIdx = remaining.indexOfFirst {
                        val key = LocalMoodAndAffinityEngine.normalizedArtistKey(it.artist)
                        key != prevArtist && (artistCounts[key] ?: 0) < maxPerArtist
                    }
                    if (altIdx in 0..4) altIdx else eligibleIdx
                } else {
                    eligibleIdx
                }
                val chosen = remaining.removeAt(pickIdx)
                if (localSeen.add(chosen.id)) {
                    val key = LocalMoodAndAffinityEngine.normalizedArtistKey(chosen.artist)
                    artistCounts[key] = (artistCounts[key] ?: 0) + 1
                    result += chosen
                }
            }
        }

        // Pass 1: primary ranked tracks NOT yet used in previous sections
        appendWithArtistCapAndSpacing(rankedPrimary.filter { it.id !in usedIds })

        // Pass 2: fallback pool tracks NOT yet used in previous sections
        if (result.size < targetSize && fallbackPool.isNotEmpty()) {
            appendWithArtistCapAndSpacing(fallbackPool.filter { it.id !in usedIds })
        }

        // Pass 3 (small-library graceful fallback): allow overlap from primary ranked while honoring artist cap
        val minDesired = minOf(targetSize, maxOf(1, (rankedPrimary.size + fallbackPool.size).coerceAtMost(3)))
        if (result.size < minDesired) {
            appendWithArtistCapAndSpacing(rankedPrimary)
            if (result.size < minDesired && fallbackPool.isNotEmpty()) {
                appendWithArtistCapAndSpacing(fallbackPool)
            }
        }

        return result
    }

    private fun safeFinite(value: Double): Double = if (value.isFinite()) value else 0.0

    private fun safeMod(value: Int, modulus: Int): Int {
        if (modulus <= 0) return 0
        return ((value % modulus) + modulus) % modulus
    }

    companion object {
        internal const val ONE_HOUR_MS = 60L * 60L * 1000L
        internal const val SIX_HOURS_MS = 6L * ONE_HOUR_MS
        internal const val ONE_DAY_MS = 24L * ONE_HOUR_MS
        internal const val THREE_DAYS_MS = 3L * ONE_DAY_MS
        internal const val FOURTEEN_DAYS_MS = 14L * ONE_DAY_MS
        internal const val TWENTY_ONE_DAYS_MS = 21L * ONE_DAY_MS
        internal const val THIRTY_DAYS_MS = 30L * ONE_DAY_MS
    }
}
