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
    val anchorTrackTitle: String? = null,
    val smartMixOutput: MusicIntelligenceOutput? = null
)

/**
 * Local, zero-server Discover suggestion engine computing 6 distinct personalized categories
 * directly from Room track metadata (play count, last-played timestamp, date added, genre,
 * artist, album, duration, BPM, and favorite status).
 *
 * Avoids duplicate tracks across sections whenever the library size permits, and provides
 * graceful metadata-driven fallbacks when listening history is sparse or empty.
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
        maxTracksPerSection: Int = 10
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

        // Pre-compute BPM & mood vectors once for O(N) efficiency across all 6 sections
        val bpmById = HashMap<String, Int>(cleanTracks.size)
        val moodVecById = HashMap<String, Map<String, Double>>(cleanTracks.size)
        val primaryMoodById = HashMap<String, String>(cleanTracks.size)
        for (t in cleanTracks) {
            val bpm = engine.effectiveBpmFor(t)
            val vec = engine.moodVectorFor(t)
            bpmById[t.id] = bpm
            moodVecById[t.id] = vec
            primaryMoodById[t.id] = vec.maxByOrNull { it.value }?.key ?: "calm"
        }

        // Compute adaptive per-section target so small libraries still spread cleanly with minimal overlap
        val targetSize = when {
            cleanTracks.size >= cap * 4 -> cap
            cleanTracks.size >= 18 -> minOf(cap, 6)
            cleanTracks.size >= 12 -> minOf(cap, 4)
            else -> minOf(cap, maxOf(1, cleanTracks.size / 2).coerceAtLeast(minOf(2, cleanTracks.size)))
        }

        val sections = ArrayList<DiscoverSection>(6)

        // 1. Made for you
        val madeForYou = buildMadeForYou(
            tracks = cleanTracks,
            usedIds = usedIds,
            skippedSet = skippedSet,
            bpmById = bpmById,
            primaryMoodById = primaryMoodById,
            nowMs = nowMs,
            seed = daySeed + 11,
            targetSize = targetSize
        )
        sections += madeForYou
        madeForYou.tracks.forEach { usedIds.add(it.id) }

        // 2. Smart Mix
        val (smartMixSection, smartMixOutput) = buildSmartMix(
            tracks = cleanTracks,
            usedIds = usedIds,
            skippedTrackIds = skippedTrackIds,
            bpmById = bpmById,
            primaryMoodById = primaryMoodById,
            shuffleNonce = shuffleNonce,
            targetSize = targetSize
        )
        sections += smartMixSection
        smartMixSection.tracks.forEach { usedIds.add(it.id) }

        // 3. Rediscover (30+ days unplayed or 0 plays)
        val rediscover = buildRediscover(
            tracks = cleanTracks,
            usedIds = usedIds,
            nowMs = nowMs,
            seed = daySeed + 31,
            targetSize = targetSize
        )
        sections += rediscover
        rediscover.tracks.forEach { usedIds.add(it.id) }

        // 4. Forgotten favorites (high all-time play count, not played recently)
        val forgottenFavorites = buildForgottenFavorites(
            tracks = cleanTracks,
            usedIds = usedIds,
            nowMs = nowMs,
            seed = daySeed + 47,
            targetSize = targetSize
        )
        sections += forgottenFavorites
        forgottenFavorites.tracks.forEach { usedIds.add(it.id) }

        // 5. Continue the vibe (matching current or most recently played track)
        val (continueVibe, anchorTitle) = buildContinueTheVibe(
            tracks = cleanTracks,
            currentTrackId = currentTrackId,
            usedIds = usedIds,
            bpmById = bpmById,
            moodVecById = moodVecById,
            primaryMoodById = primaryMoodById,
            targetSize = targetSize
        )
        sections += continueVibe
        continueVibe.tracks.forEach { usedIds.add(it.id) }

        // 6. Explore your library (deeper cuts, underplayed albums, rotating genre spotlight)
        val exploreLibrary = buildExploreYourLibrary(
            tracks = cleanTracks,
            usedIds = usedIds,
            seed = daySeed + 73,
            shuffleNonce = shuffleNonce,
            targetSize = targetSize
        )
        sections += exploreLibrary

        return DiscoverFeed(
            sections = sections.distinctBy { it.category }.toStableList(),
            anchorTrackTitle = anchorTitle,
            smartMixOutput = smartMixOutput
        )
    }

    // --- 1. Made for you ---
    private fun buildMadeForYou(
        tracks: List<Track>,
        usedIds: Set<String>,
        skippedSet: Set<String>,
        bpmById: Map<String, Int>,
        primaryMoodById: Map<String, String>,
        nowMs: Long,
        seed: Int,
        targetSize: Int
    ): DiscoverSection {
        val hasHistory = tracks.any { it.playCount > 0 || it.isFavorite || it.lastPlayedAt > 0L }
        val rng = Random(seed)

        if (!hasHistory) {
            // Cold-start graceful fallback: pre-assign deterministic tie-breakers before sorting
            // (never call rng.nextDouble() inside a Comparator, which violates TimSort's contract!)
            val newestFirst = tracks
                .map { it to rng.nextDouble() }
                .sortedWith(compareByDescending<Pair<Track, Double>> { it.first.dateAdded }.thenBy { it.second })
                .map { it.first }
            val picked = pickDistinctTracks(newestFirst, usedIds, targetSize)
            val cues = picked.associate { t ->
                val genre = t.genre.ifBlank { primaryMoodById[t.id]?.replaceFirstChar { it.uppercase() } ?: "Fresh" }
                t.id to "New · $genre"
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
            val safePlays = t.playCount.coerceAtLeast(0)
            val weight = safePlays.toDouble() +
                (if (t.isFavorite) 4.0 else 0.0) +
                (if (t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) in 0L..FOURTEEN_DAYS_MS) 2.0 else 0.0)
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
        val maxArtistWeight = artistAffinity.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxGenreWeight = genreAffinity.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val maxPlays = (tracks.maxOfOrNull { it.playCount.coerceAtLeast(0) } ?: 0).coerceAtLeast(1).toDouble()

        val scored = tracks.map { t ->
            val aScore = (artistAffinity[t.artist.trim().lowercase(Locale.ROOT)] ?: 0.0) / maxArtistWeight
            val gScore = (genreAffinity[t.genre.trim().lowercase(Locale.ROOT)] ?: 0.0) / maxGenreWeight
            val favScore = if (t.isFavorite) 0.32 else 0.0
            val playScore = (t.playCount.coerceAtLeast(0) / maxPlays) * 0.25
            val recentFavBoost = if (t.isFavorite && t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) in 0L..THIRTY_DAYS_MS) 0.14 else 0.0
            val skipPenalty = if (t.id in skippedSet) -0.35 else 0.0
            val jitter = rng.nextDouble(0.0, 0.08)
            val total = safeFinite(aScore * 0.34 + gScore * 0.24 + favScore + playScore + recentFavBoost + skipPenalty + jitter)
            t to total
        }.sortedByDescending { it.second }.map { it.first }

        val picked = pickDistinctTracks(scored, usedIds, targetSize)
        val topArtistDisplay = tracks
            .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown Artist", ignoreCase = true) }
            .maxByOrNull { artistAffinity[it.artist.trim().lowercase(Locale.ROOT)] ?: 0.0 }
            ?.artist
        val topGenreDisplay = tracks
            .filter { it.genre.isNotBlank() }
            .maxByOrNull { genreAffinity[it.genre.trim().lowercase(Locale.ROOT)] ?: 0.0 }
            ?.genre

        val subtitle = when {
            topArtistDisplay != null && topGenreDisplay != null ->
                "Daily mix shaped by $topArtistDisplay, $topGenreDisplay & your favorites"
            topArtistDisplay != null ->
                "Daily mix built around $topArtistDisplay and your top listens"
            topGenreDisplay != null ->
                "Daily mix blending your favorite $topGenreDisplay picks"
            else ->
                "Daily mix tailored to your favorite tracks and recent spins"
        }

        val cues = picked.associate { t ->
            val cue = when {
                t.isFavorite -> "Favorite · ${bpmById[t.id] ?: 90} BPM"
                t.playCount >= 3 -> "${t.playCount} plays"
                t.genre.isNotBlank() -> t.genre
                else -> "${bpmById[t.id] ?: 90} BPM"
            }
            t.id to cue
        }

        return DiscoverSection(
            category = DiscoverCategory.MADE_FOR_YOU,
            title = "Made for you",
            subtitle = subtitle,
            badgeText = "Daily Mix",
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
    }

    // --- 2. Smart Mix ---
    private fun buildSmartMix(
        tracks: List<Track>,
        usedIds: Set<String>,
        skippedTrackIds: List<String>,
        bpmById: Map<String, Int>,
        primaryMoodById: Map<String, String>,
        shuffleNonce: Int,
        targetSize: Int
    ): Pair<DiscoverSection, MusicIntelligenceOutput> {
        val unusedPool = tracks.filter { it.id !in usedIds }
        val candidatePool = if (unusedPool.size >= minOf(targetSize, 4)) unusedPool else tracks
        val history = engine.deriveListeningHistory(candidatePool, skippedTrackIds)

        // Rotate mood subtly when user taps Refresh so Smart Mix always feels alive
        val rotatedMood = if (shuffleNonce > 0) {
            val moods = listOf("calm", "focused", "warm", "nocturnal", "energetic", "organic", "uplifting")
            moods[safeMod(shuffleNonce - 1, moods.size)]
        } else {
            null
        }

        val output = engine.analyzeAndRecommend(
            request = MusicIntelligenceRequest(
                seedTrackId = null,
                targetMood = rotatedMood,
                maxTracks = targetSize.coerceAtLeast(1)
            ),
            history = history,
            libraryTracks = candidatePool
        )

        val lookup = tracks.associateBy { it.id }
        val resolved = output.recommendedTracks
            .mapNotNull { lookup[it.trackId] }
            .distinctBy { it.id }
            .ifEmpty { pickDistinctTracks(tracks, usedIds, targetSize) }

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
                .map { it to rng.nextDouble() }
                .sortedWith(
                    compareBy<Pair<Track, Double>> { if (it.first.playCount <= 0) 0 else 1 }
                        .thenBy { it.first.lastPlayedAt }
                        .thenBy { it.second }
                )
                .map { it.first }
        } else {
            // Fallback: every track played in the last 30 days -> pick least recently played
            tracks
                .map { it to rng.nextDouble() }
                .sortedWith(
                    compareBy<Pair<Track, Double>> { it.first.lastPlayedAt }
                        .thenBy { it.first.playCount }
                        .thenBy { it.second }
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

    // --- 4. Forgotten favorites (high all-time play count, not played recently) ---
    private fun buildForgottenFavorites(
        tracks: List<Track>,
        usedIds: Set<String>,
        nowMs: Long,
        seed: Int,
        targetSize: Int
    ): DiscoverSection {
        val rng = Random(seed)
        val playedTracks = tracks.filter { it.playCount > 0 }
        val veryRecentIds = tracks
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(5)
            .mapTo(HashSet()) { it.id }

        val minHighPlayThreshold = if (playedTracks.isNotEmpty()) {
            val sortedCounts = playedTracks.map { it.playCount }.sortedDescending()
            val topThirdIdx = (sortedCounts.size / 3).coerceAtMost(sortedCounts.lastIndex)
            maxOf(2, sortedCounts[topThirdIdx])
        } else {
            3
        }

        // Strict forgotten favorites: high all-time plays (or favorited & played) and not played recently
        val forgottenStrict = tracks.filter { t ->
            val highPlay = t.playCount >= minHighPlayThreshold || (t.isFavorite && t.playCount >= 1)
            val notRecent = (t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) >= FOURTEEN_DAYS_MS) ||
                (t.lastPlayedAt == 0L && t.playCount >= minHighPlayThreshold) ||
                (t.id !in veryRecentIds && t.playCount >= minHighPlayThreshold)
            highPlay && notRecent
        }

        val (ordered, isTrueForgotten) = when {
            forgottenStrict.isNotEmpty() -> {
                val ranked = forgottenStrict.map { t ->
                    val ageDays = if (t.lastPlayedAt > 0L) {
                        ((nowMs - t.lastPlayedAt) / ONE_DAY_MS).coerceIn(1L, 36_500L).toDouble()
                    } else {
                        30.0
                    }
                    val favBoost = if (t.isFavorite) 1.35 else 1.0
                    val safePlays = t.playCount.coerceAtLeast(0).toDouble()
                    val score = safeFinite(ln(safePlays + 1.0) * ln(ageDays + 2.0) * favBoost)
                    t to score
                }.sortedByDescending { it.second }.map { it.first }
                ranked to true
            }
            playedTracks.isNotEmpty() -> {
                // Fallback when user has some plays, all recent: top played excluding very newest
                val ranked = playedTracks.sortedWith(
                    compareByDescending<Track> { it.playCount }
                        .thenBy { it.lastPlayedAt }
                )
                ranked to false
            }
            else -> {
                // Cold-start fallback: favorited tracks or standout album cuts (longer durations)
                val ranked = tracks
                    .map { it to rng.nextDouble() }
                    .sortedWith(
                        compareByDescending<Pair<Track, Double>> { if (it.first.isFavorite) 1 else 0 }
                            .thenByDescending { it.first.durationMs }
                            .thenBy { it.second }
                    )
                    .map { it.first }
                ranked to false
            }
        }

        val picked = pickDistinctTracks(ordered, usedIds, targetSize, fallbackPool = tracks)

        val cues = picked.associate { t ->
            val cue = when {
                t.playCount > 0 && t.lastPlayedAt > 0L && (nowMs - t.lastPlayedAt) >= FOURTEEN_DAYS_MS -> {
                    val days = (nowMs - t.lastPlayedAt) / ONE_DAY_MS
                    "${t.playCount} plays · ${days}d ago"
                }
                t.playCount > 0 -> "${t.playCount} all-time plays"
                t.isFavorite -> "Saved favorite"
                else -> t.album.ifBlank { "Standout cut" }
            }
            t.id to cue
        }

        return DiscoverSection(
            category = DiscoverCategory.FORGOTTEN_FAVORITES,
            title = "Forgotten favorites",
            subtitle = if (isTrueForgotten) {
                "Most-played tracks you haven't revisited lately"
            } else if (playedTracks.isNotEmpty()) {
                "High-rotation favorites ready for another encore"
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
        bpmById: Map<String, Int>,
        moodVecById: Map<String, Map<String, Double>>,
        primaryMoodById: Map<String, String>,
        targetSize: Int
    ): Pair<DiscoverSection, String?> {
        val anchor = currentTrackId?.let { id -> tracks.firstOrNull { it.id == id } }
            ?: tracks.filter { it.lastPlayedAt > 0L }.maxByOrNull { it.lastPlayedAt }
            ?: tracks.maxByOrNull { it.dateAdded }
            ?: tracks.firstOrNull()
            ?: return DiscoverSection(
                category = DiscoverCategory.CONTINUE_THE_VIBE,
                title = "Continue the vibe",
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

            val score = safeFinite(
                moodSim * 0.35 +
                    bpmScore * 0.25 +
                    (if (sameGenre) 0.28 else 0.0) +
                    (if (sameArtist) 0.22 else 0.0)
            )
            t to score
        }.sortedByDescending { it.second }.map { it.first }

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
            title = "Continue the vibe",
            subtitle = "Matches the mood & sound of \"$anchorDisplayTitle\"",
            badgeText = badge,
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
        return section to anchorDisplayTitle
    }

    // --- 6. Explore your library ---
    private fun buildExploreYourLibrary(
        tracks: List<Track>,
        usedIds: Set<String>,
        seed: Int,
        shuffleNonce: Int,
        targetSize: Int
    ): DiscoverSection {
        val rng = Random(seed)
        val unused = tracks.filter { it.id !in usedIds }
        val workingPool = if (unused.size >= 3) unused else tracks

        // Identify underplayed albums & genre spotlights
        val genres = workingPool
            .map { it.genre.trim() }
            .filter { it.isNotBlank() && !it.equals("Audio", ignoreCase = true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
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

        val sortedCounts = tracks.map { it.playCount.coerceAtLeast(0) }.sorted()
        val medianPlays = sortedCounts.getOrElse(sortedCounts.size / 2) { 0 }

        val scored = workingPool.map { t ->
            val albumKey = t.album.ifBlank { "Unknown Album" }
            val albumAvg = (albumAvgPlays[albumKey] ?: 0.0).coerceAtLeast(0.0)
            val deepCutBonus = if (t.playCount.coerceAtLeast(0) <= medianPlays) 0.40 else 0.0
            val underplayedAlbumBonus = (1.0 / (albumAvg + 1.0)) * 0.30
            val genreSpotlightBonus = if (spotlightGenre != null &&
                t.genre.trim().equals(spotlightGenre, ignoreCase = true)
            ) 0.35 else 0.0
            val unusedBonus = if (t.id !in usedIds) 0.25 else 0.0
            val jitter = rng.nextDouble(0.0, 0.12)
            t to safeFinite(deepCutBonus + underplayedAlbumBonus + genreSpotlightBonus + unusedBonus + jitter)
        }.sortedByDescending { it.second }.map { it.first }

        val picked = pickDistinctTracks(scored, usedIds, targetSize, fallbackPool = tracks)

        val cues = picked.associate { t ->
            val cue = when {
                spotlightGenre != null && t.genre.trim().equals(spotlightGenre, ignoreCase = true) ->
                    "$spotlightGenre spotlight"
                t.album.isNotBlank() && !t.album.equals("Unknown Album", ignoreCase = true) ->
                    t.album
                t.playCount <= 0 -> "Deep cut"
                else -> t.folderName.ifBlank { "Hidden gem" }
            }
            t.id to cue
        }

        val subtitle = if (spotlightGenre != null) {
            "Deeper cuts, underplayed albums & a $spotlightGenre spotlight"
        } else {
            "Deeper cuts and underplayed albums from your local collection"
        }

        return DiscoverSection(
            category = DiscoverCategory.EXPLORE_YOUR_LIBRARY,
            title = "Explore your library",
            subtitle = subtitle,
            badgeText = spotlightGenre ?: "Deep cuts",
            tracks = picked.toStableList(),
            trackCues = cues.toStableMap()
        )
    }

    /**
     * Selects up to [targetSize] tracks with artist-spacing variety, strongly prioritizing
     * tracks not yet present in [usedIds]. Only falls back to already-used tracks if the
     * library doesn't have enough unused tracks left to populate the section.
     */
    private fun pickDistinctTracks(
        rankedPrimary: List<Track>,
        usedIds: Set<String>,
        targetSize: Int,
        fallbackPool: List<Track> = emptyList()
    ): List<Track> {
        val result = ArrayList<Track>(targetSize)
        val localSeen = HashSet<String>(targetSize * 2)

        fun appendWithArtistSpacing(source: List<Track>) {
            val remaining = source.filterTo(ArrayList()) { it.id !in localSeen }
            while (result.size < targetSize && remaining.isNotEmpty()) {
                val prevArtist = result.lastOrNull()?.artist?.trim()?.lowercase(Locale.ROOT)
                val pickIdx = if (!prevArtist.isNullOrEmpty() && prevArtist != "unknown artist") {
                    val altIdx = remaining.indexOfFirst {
                        it.artist.trim().lowercase(Locale.ROOT) != prevArtist
                    }
                    if (altIdx in 0..3) altIdx else 0
                } else {
                    0
                }
                val chosen = remaining.removeAt(pickIdx)
                if (localSeen.add(chosen.id)) {
                    result += chosen
                }
            }
        }

        // Pass 1: primary ranked tracks NOT yet used in previous sections
        appendWithArtistSpacing(rankedPrimary.filter { it.id !in usedIds })

        // Pass 2: fallback pool tracks NOT yet used in previous sections
        if (result.size < targetSize && fallbackPool.isNotEmpty()) {
            appendWithArtistSpacing(fallbackPool.filter { it.id !in usedIds })
        }

        // Pass 3 (small-library graceful fallback): allow overlap from primary ranked if section is still tiny
        val minDesired = minOf(targetSize, maxOf(1, (rankedPrimary.size + fallbackPool.size).coerceAtMost(3)))
        if (result.size < minDesired) {
            appendWithArtistSpacing(rankedPrimary)
            if (result.size < minDesired && fallbackPool.isNotEmpty()) {
                appendWithArtistSpacing(fallbackPool)
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
        internal const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
        internal const val FOURTEEN_DAYS_MS = 14L * ONE_DAY_MS
        internal const val THIRTY_DAYS_MS = 30L * ONE_DAY_MS
    }
}
