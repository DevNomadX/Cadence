package com.example.data.intelligence

import android.graphics.Bitmap
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.data.StableList
import com.example.data.Track
import com.example.data.resolveAudioCodecLabel
import com.example.data.toStableList
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Multi-label smart mood tags computed deterministically offline on Dispatchers.Default.
 * Tracks can match multiple tags simultaneously (e.g., Late Night + Acoustic + Lossless Hi-Fi).
 */
@Keep
enum class SmartMoodTag(
    val id: String,
    val label: String,
    val filterLabel: String
) {
    LATE_NIGHT("late_night", "Late Night", "Late Night"),
    ACOUSTIC("acoustic", "Acoustic", "Acoustic"),
    SOULFUL("soulful", "Soulful", "Soulful"),
    UPBEAT("upbeat", "Upbeat", "Upbeat"),
    LOSSLESS_HIFI("lossless_hifi", "Lossless Hi-Fi", "Lossless");

    companion object {
        val discoverMoods: List<SmartMoodTag> = listOf(
            LATE_NIGHT,
            ACOUSTIC,
            SOULFUL,
            UPBEAT
        )

        fun fromId(id: String?): SmartMoodTag? =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) }

        fun encode(tags: Collection<SmartMoodTag>): String {
            if (tags.isEmpty()) return ""
            val unique = LinkedHashSet<SmartMoodTag>(tags.size)
            for (tag in entries) {
                if (tag in tags) unique.add(tag)
            }
            return unique.joinToString(",") { it.id }
        }

        fun decode(raw: String?): StableList<SmartMoodTag> {
            if (raw.isNullOrBlank()) return StableList()
            val result = ArrayList<SmartMoodTag>(5)
            raw.split(',').forEach { token ->
                val parsed = fromId(token)
                if (parsed != null && parsed !in result) {
                    result.add(parsed)
                }
            }
            return result.toStableList()
        }
    }
}

/**
 * M3 Expressive filter pills displayed directly below the Library search bar.
 */
@Keep
enum class LibraryMoodFilter(
    val id: String,
    val label: String,
    val tag: SmartMoodTag?
) {
    ALL("all", "All", null),
    LATE_NIGHT("late_night", "Late Night", SmartMoodTag.LATE_NIGHT),
    ACOUSTIC("acoustic", "Acoustic", SmartMoodTag.ACOUSTIC),
    SOULFUL("soulful", "Soulful", SmartMoodTag.SOULFUL),
    UPBEAT("upbeat", "Upbeat", SmartMoodTag.UPBEAT),
    LOSSLESS("lossless", "Lossless", SmartMoodTag.LOSSLESS_HIFI);

    companion object {
        fun fromId(id: String?): LibraryMoodFilter =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: ALL
    }
}

/**
 * Curated offline smart playlist surfaced on the Discover "Mood Playlists" shelf.
 */
@Keep
@Immutable
data class MoodSmartPlaylist(
    val mood: SmartMoodTag,
    val title: String,
    val subtitle: String,
    val tracks: StableList<Track>,
    val totalMatchCount: Int
)

/**
 * Multi-signal local affinity score breakdown (0.0f..1.0f).
 */
@Keep
@Immutable
data class TrackAffinityBreakdown(
    val score: Float,
    val completionSignal: Float,
    val skipPenalty: Float,
    val timeOfDayBoost: Float,
    val recencyAdjustment: Float
)

/**
 * Output of the balanced 60 / 25 / 15 mix builder so tests and UI can inspect mix composition.
 */
@Keep
@Immutable
data class BalancedMixResult(
    val tracks: StableList<Track>,
    val favoritesCount: Int,
    val forgottenGemsCount: Int,
    val underPlayedCount: Int,
    val backfillCount: Int
)

/**
 * Zero-bloat, 100% offline local AI recommendation, multi-signal affinity scoring,
 * deterministic multi-label mood classification, and balanced smart-playlist curation engine.
 * Designed to execute strictly on Dispatchers.Default / Dispatchers.IO.
 */
object LocalMoodAndAffinityEngine {

    internal const val ONE_HOUR_MS = 60L * 60L * 1000L
    internal const val TWENTY_FOUR_HOURS_MS = 24L * ONE_HOUR_MS
    internal const val ONE_DAY_MS = TWENTY_FOUR_HOURS_MS
    internal const val TWENTY_ONE_DAYS_MS = 21L * ONE_DAY_MS
    internal const val MAX_SONGS_PER_ARTIST = 2

    private const val MOOD_THRESHOLD = 0.50f
    private const val UNKNOWN_ARTIST_KEY = "unknown artist"

    private val lateNightGenres = listOf(
        "lo-fi", "lofi", "ambient", "chill", "downtempo", "trip-hop", "trip hop",
        "dream pop", "synthwave", "darkwave", "nocturnal", "dub", "sleep", "drone",
        "bedroom pop", "shoegaze", "chillwave", "vaporwave"
    )
    private val acousticGenres = listOf(
        "acoustic", "folk", "indie folk", "singer-songwriter", "singer songwriter",
        "unplugged", "classical", "jazz", "bossa", "chamber", "orchestral",
        "country", "bluegrass", "americana", "piano", "guitar", "instrumental"
    )
    private val soulfulGenres = listOf(
        "soul", "r&b", "rnb", "neo-soul", "neo soul", "blues", "gospel",
        "motown", "quiet storm", "smooth jazz", "ballad", "vocal jazz", "funk soul"
    )
    private val upbeatGenres = listOf(
        "pop", "dance", "edm", "house", "techno", "disco", "synthpop", "synth-pop",
        "hip-hop", "hip hop", "rap", "trap", "rock", "punk", "metal",
        "drum & bass", "drum and bass", "dnb", "garage", "electro", "electronic",
        "upbeat", "workout", "club", "party", "funk", "afrobeats", "reggaeton", "k-pop"
    )

    private val lateNightKeywords = listOf(
        "night", "midnight", "slowed", "reverb", "late", "moon", "after hours",
        "afterhours", "3am", "2am", "sleep", "dream", "rain", "nocturne",
        "shadow", "chill", "lofi", "lo-fi", "dusk", "twilight", "insomnia"
    )
    private val acousticKeywords = listOf(
        "acoustic", "unplugged", "live", "instrumental", "piano", "solo",
        "strings", "guitar", "stripped", "session", "tiny desk", "cello",
        "violin", "harp", "woodwind", "folk"
    )
    private val soulfulKeywords = listOf(
        "soul", "soulful", "blues", "velvet", "warm", "golden", "amber",
        "groove", "ballad", "honey", "devotion", "serenade", "tender", "gospel",
        "heartache", "motown", "croon"
    )
    private val upbeatKeywords = listOf(
        "remix", "club", "edit", "dance", "radio edit", "extended", "vip",
        "bootleg", "party", "hype", "run", "workout", "rush", "electric",
        "turbo", "festival", "bounce", "groove mix", "hyper"
    )

    fun currentLocalHour(nowMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
    }

    /**
     * Lightweight downsampled dominant chromatic color extraction from an already-decoded Bitmap.
     * Avoids allocating extra Bitmaps or pulling in external Palette dependencies.
     */
    fun extractDominantColorArgb(bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
            return 0
        }
        val w = bitmap.width
        val h = bitmap.height
        val grid = 12
        val stepX = max(1, w / grid)
        val stepY = max(1, h / grid)

        var weightedR = 0.0
        var weightedG = 0.0
        var weightedB = 0.0
        var totalWeight = 0.0

        var fallbackR = 0L
        var fallbackG = 0L
        var fallbackB = 0L
        var sampleCount = 0

        var y = stepY / 2
        while (y < h) {
            var x = stepX / 2
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                fallbackR += r
                fallbackG += g
                fallbackB += b
                sampleCount++

                val hsv = rgbToHsv(r, g, b)
                val sat = hsv[1]
                val value = hsv[2]
                if (sat >= 0.16f && value in 0.10f..0.94f) {
                    val weight = (sat * sat + 0.15f) * (1.0f - abs(value - 0.52f) * 0.55f)
                    weightedR += r * weight
                    weightedG += g * weight
                    weightedB += b * weight
                    totalWeight += weight
                }
                x += stepX
            }
            y += stepY
        }

        if (sampleCount == 0) return 0
        val finalR: Int
        val finalG: Int
        val finalB: Int
        if (totalWeight > 0.01) {
            finalR = (weightedR / totalWeight).roundToInt().coerceIn(0, 255)
            finalG = (weightedG / totalWeight).roundToInt().coerceIn(0, 255)
            finalB = (weightedB / totalWeight).roundToInt().coerceIn(0, 255)
        } else {
            finalR = (fallbackR / sampleCount).toInt().coerceIn(0, 255)
            finalG = (fallbackG / sampleCount).toInt().coerceIn(0, 255)
            finalB = (fallbackB / sampleCount).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
    }

    /**
     * Pure-Kotlin RGB -> HSV conversion (safe in pure JVM tests without Android Color shadows).
     * Returns floatArrayOf(hueDeg 0..360, saturation 0..1, value 0..1).
     */
    internal fun rgbToHsv(rInt: Int, gInt: Int, bInt: Int): FloatArray {
        val r = rInt.coerceIn(0, 255) / 255f
        val g = gInt.coerceIn(0, 255) / 255f
        val b = bInt.coerceIn(0, 255) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        val hue = when {
            delta < 1e-5f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val sat = if (maxC < 1e-5f) 0f else (delta / maxC).coerceIn(0f, 1f)
        return floatArrayOf(hue, sat, maxC.coerceIn(0f, 1f))
    }

    internal fun argbToHsv(argb: Int): FloatArray {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return rgbToHsv(r, g, b)
    }

    /**
     * Multi-signal deterministic mood classifier returning all matching [SmartMoodTag]s.
     * Combines BPM, ID3 genre/tag metadata, title/album/folder keywords, dominant artwork color
     * hue/warmth/vibrance, and lossless/Hi-Res audio specs.
     */
    fun classifyTrackMoods(track: Track): Set<SmartMoodTag> = classifyTrackMoods(
        title = track.title,
        album = track.album,
        artist = track.artist,
        genre = track.genre,
        folderName = track.folderName,
        bpm = track.bpm,
        codec = track.codec,
        mimeType = track.mimeType,
        bitDepth = track.bitDepth,
        sampleRateHz = track.sampleRateHz,
        bitrateKbps = track.bitrateKbps,
        artworkColorArgb = track.artworkColorArgb,
        localUriString = track.localUriString
    )

    fun classifyTrackMoods(
        title: String,
        album: String,
        artist: String = "",
        genre: String,
        folderName: String = "",
        bpm: Int,
        codec: String,
        mimeType: String,
        bitDepth: Int,
        sampleRateHz: Int,
        bitrateKbps: Int = 0,
        artworkColorArgb: Int = 0,
        localUriString: String? = null
    ): Set<SmartMoodTag> {
        val scores = computeMoodAffinityScores(
            title = title,
            album = album,
            artist = artist,
            genre = genre,
            folderName = folderName,
            bpm = bpm,
            artworkColorArgb = artworkColorArgb
        )

        val tags = LinkedHashSet<SmartMoodTag>(5)
        for (mood in SmartMoodTag.discoverMoods) {
            if ((scores[mood] ?: 0f) >= MOOD_THRESHOLD) {
                tags.add(mood)
            }
        }

        // Lossless Hi-Fi detection from codec, bit depth (>= 24-bit), or Hi-Res sample rate (> 48 kHz)
        val resolvedCodec = resolveAudioCodecLabel(codec, mimeType, localUriString)
        val isLosslessHiFi = resolvedCodec in Track.LOSSLESS_CODECS ||
            bitDepth >= 24 ||
            sampleRateHz > 48_000
        if (isLosslessHiFi) {
            tags.add(SmartMoodTag.LOSSLESS_HIFI)
        }

        // If no behavioral mood passed the primary threshold, assign the clearest fallback mood
        // only when there is a meaningful directional signal (e.g., explicit non-default BPM or color)
        if (tags.none { it != SmartMoodTag.LOSSLESS_HIFI }) {
            val best = SmartMoodTag.discoverMoods.maxByOrNull { scores[it] ?: 0f }
            val bestScore = best?.let { scores[it] ?: 0f } ?: 0f
            if (best != null && bestScore >= 0.24f) {
                tags.add(best)
            } else if (bpm >= 118) {
                tags.add(SmartMoodTag.UPBEAT)
            } else if (bpm in 1..94) {
                tags.add(SmartMoodTag.LATE_NIGHT)
            }
        }

        return tags
    }

    /**
     * Computes deterministic continuous mood fit scores (`0.0f..1.5f`) per discoverable mood.
     */
    fun computeMoodAffinityScores(
        title: String,
        album: String,
        artist: String = "",
        genre: String,
        folderName: String = "",
        bpm: Int,
        artworkColorArgb: Int = 0
    ): Map<SmartMoodTag, Float> {
        var lateNight = 0f
        var acoustic = 0f
        var soulful = 0f
        var upbeat = 0f

        val genreLower = genre.trim().lowercase(Locale.ROOT)
        val textBlob = "$title $album $folderName".lowercase(Locale.ROOT)
        val artistLower = artist.trim().lowercase(Locale.ROOT)

        // 1. BPM signal (<95 favors Late Night/Soulful/Acoustic; >=118 favors Upbeat)
        if (bpm > 0) {
            when {
                bpm >= 118 -> {
                    upbeat += 0.62f
                    lateNight -= 0.22f
                }
                bpm in 108..117 -> {
                    upbeat += 0.28f
                }
                bpm in 1..84 -> {
                    lateNight += 0.44f
                    soulful += 0.34f
                    acoustic += 0.32f
                    upbeat -= 0.24f
                }
                bpm in 85..94 -> {
                    // 90 is the scanner's default placeholder when BPM tag is absent:
                    // apply a gentle prior for 90, stronger signal for explicit measured BPMs.
                    val weight = if (bpm == 90) 0.16f else 0.36f
                    lateNight += weight
                    soulful += weight * 0.85f
                    acoustic += weight * 0.80f
                }
            }
        }

        // 2. ID3 genre & tag metadata
        if (genreLower.isNotEmpty()) {
            if (matchesAnyTerm(genreLower, lateNightGenres)) lateNight += 0.65f
            if (matchesAnyTerm(genreLower, acousticGenres)) acoustic += 0.65f
            if (matchesAnyTerm(genreLower, soulfulGenres)) soulful += 0.68f
            if (matchesAnyTerm(genreLower, upbeatGenres)) upbeat += 0.65f
        }

        // 3. Title / album / folder keywords
        if (matchesAnyTerm(textBlob, lateNightKeywords)) lateNight += 0.56f
        if (matchesAnyTerm(textBlob, acousticKeywords)) acoustic += 0.60f
        if (matchesAnyTerm(textBlob, soulfulKeywords)) soulful += 0.56f
        if (matchesAnyTerm(textBlob, upbeatKeywords)) upbeat += 0.60f

        // Light artist name hints (e.g., "Acoustic Trio", "DJ ...", "Soul Collective")
        if (artistLower.isNotEmpty()) {
            if (matchesAnyTerm(artistLower, listOf("dj ", "club", "sound system"))) upbeat += 0.22f
            if (matchesAnyTerm(artistLower, listOf("quartet", "trio", "ensemble", "orchestra", "acoustic"))) acoustic += 0.26f
            if (matchesAnyTerm(artistLower, listOf("soul", "blues", "gospel"))) soulful += 0.26f
        }

        // 4. Album-art dominant color hue, warmth & vibrance
        if (artworkColorArgb != 0) {
            val hsv = argbToHsv(artworkColorArgb)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            val isWarmAmberTerracotta = hue in 8f..50f && sat >= 0.24f && value in 0.22f..0.92f
            val isDeepWarmRose = (hue >= 332f || hue <= 10f) && sat >= 0.28f && value in 0.18f..0.75f
            val isDeepIndigoMidnight = hue in 205f..288f && sat >= 0.20f && value <= 0.66f
            val isVibrantEnergetic = sat >= 0.64f && value >= 0.68f

            if (isWarmAmberTerracotta) {
                soulful += 0.36f
                acoustic += 0.22f
            }
            if (isDeepWarmRose) {
                soulful += 0.30f
                lateNight += 0.18f
            }
            if (isDeepIndigoMidnight) {
                lateNight += 0.42f
                soulful += 0.16f
            }
            if (isVibrantEnergetic) {
                upbeat += 0.40f
            }
        }

        return mapOf(
            SmartMoodTag.LATE_NIGHT to lateNight.coerceAtLeast(0f),
            SmartMoodTag.ACOUSTIC to acoustic.coerceAtLeast(0f),
            SmartMoodTag.SOULFUL to soulful.coerceAtLeast(0f),
            SmartMoodTag.UPBEAT to upbeat.coerceAtLeast(0f)
        )
    }

    /**
     * Computes a normalized local affinity score (`0.0f..1.0f`) by delegating directly to
     * [RecommendationCore.computeAffinityBreakdown] as the single source of truth.
     */
    fun computeLocalAffinity(
        track: Track,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = currentLocalHour(nowMs),
        maxLibraryPlays: Int = 10,
        sessionSkippedIds: Set<String> = emptySet()
    ): TrackAffinityBreakdown = RecommendationCore.computeAffinityBreakdown(
        track = track,
        nowMs = nowMs,
        timeOfDay = TimeOfDayCategory.fromHour(localHourOfDay),
        maxLibraryPlays = maxLibraryPlays,
        sessionSkipped = track.id in sessionSkippedIds
    )

    /**
     * Enforces a strict maximum of [maxPerArtist] songs per normalized artist name (case-insensitive)
     * in any generated mix, while gently interleaving artists to prevent back-to-back repeats.
     */
    fun enforceArtistDiversityCap(
        candidates: List<Track>,
        maxPerArtist: Int = MAX_SONGS_PER_ARTIST,
        limit: Int = candidates.size
    ): List<Track> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val safeCap = maxPerArtist.coerceAtLeast(1)
        val artistCounts = HashMap<String, Int>()
        val seenIds = HashSet<String>(minOf(candidates.size, limit) * 2)
        val eligible = ArrayList<Track>(minOf(candidates.size, limit))

        for (track in candidates) {
            if (eligible.size >= limit) break
            if (!seenIds.add(track.id)) continue
            val artistKey = normalizedArtistKey(track.artist)
            val currentCount = artistCounts[artistKey] ?: 0
            if (currentCount >= safeCap) continue
            artistCounts[artistKey] = currentCount + 1
            eligible.add(track)
        }

        return spaceAdjacentArtists(eligible)
    }

    /**
     * Assembles a balanced offline recommendation mix (`60%` high-affinity favorites / `25%`
     * forgotten gems / `15%` under-played tracks) by delegating directly to [RecommendationCore].
     */
    fun buildBalancedMix(
        tracks: List<Track>,
        targetSize: Int = 12,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = currentLocalHour(nowMs),
        sessionSkippedIds: Set<String> = emptySet(),
        tuneConfig: DiscoverTuneConfig = DiscoverTuneConfig(),
        seed: Int = 0,
        excludedIds: Set<String> = emptySet(),
        maxPerArtist: Int = MAX_SONGS_PER_ARTIST,
        signals: List<PlaybackSignal> = emptyList()
    ): BalancedMixResult {
        val clean = tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty() || targetSize <= 0) {
            return BalancedMixResult(StableList(), 0, 0, 0, 0)
        }
        TrackIntelligenceCache.getOrBuildSnapshot(clean)
        val combinedSignals = if (sessionSkippedIds.isEmpty()) {
            signals
        } else {
            val extraSkips = sessionSkippedIds.map { skippedId ->
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
            signals + extraSkips
        }

        val result = RecommendationCore.generateTunedPlaylist(
            matrix = TrackFeatureMatrix.fromTracks(clean),
            targetSize = targetSize,
            signals = combinedSignals,
            nowMs = nowMs,
            timeOfDay = TimeOfDayCategory.fromHour(localHourOfDay),
            tuneConfig = tuneConfig,
            excludedTrackIds = excludedIds,
            rotationSeed = if (seed != 0) seed.toLong() else null,
            rotationWeight = if (seed != 0) 0.03f else 0f,
            maxPerArtist = maxPerArtist.coerceAtLeast(1),
            strictArtistCap = true
        )
        val byId = clean.associateBy { it.id }
        val resolved = result.trackIds.mapNotNull { byId[it] }
        return BalancedMixResult(
            tracks = resolved.toStableList(),
            favoritesCount = result.favoritesCount,
            forgottenGemsCount = result.forgottenGemsCount,
            underPlayedCount = result.underPlayedCount,
            backfillCount = result.waterfallFallbackCount
        )
    }

    /**
     * Builds the compact Discover & Home "Mood Playlists" shelf by delegating directly to
     * [RecommendationCore.buildMoodPlaylists] (capped at [maxTracksPerMood], default 10 tracks).
     */
    fun buildMoodSmartPlaylists(
        tracks: List<Track>,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = currentLocalHour(nowMs),
        maxTracksPerMood: Int = RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE,
        signals: List<PlaybackSignal> = emptyList()
    ): List<MoodSmartPlaylist> {
        val clean = tracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty()) return emptyList()
        TrackIntelligenceCache.getOrBuildSnapshot(clean)
        return RecommendationCore.buildMoodPlaylists(
            tracks = clean,
            signals = signals,
            nowMs = nowMs,
            timeOfDay = TimeOfDayCategory.fromHour(localHourOfDay),
            maxTracksPerMood = maxTracksPerMood
        )
    }

    internal fun normalizedArtistKey(artist: String): String {
        val clean = artist.trim().lowercase(Locale.ROOT)
        return clean.ifEmpty { UNKNOWN_ARTIST_KEY }
    }

    private fun spaceAdjacentArtists(tracks: List<Track>): List<Track> {
        if (tracks.size <= 2) return tracks
        val remaining = ArrayList(tracks)
        val result = ArrayList<Track>(tracks.size)
        while (remaining.isNotEmpty()) {
            val prevArtist = result.lastOrNull()?.let { normalizedArtistKey(it.artist) }
            val pickIdx = if (prevArtist != null && prevArtist != UNKNOWN_ARTIST_KEY) {
                val altIdx = remaining.indexOfFirst { normalizedArtistKey(it.artist) != prevArtist }
                if (altIdx in 0..4) altIdx else 0
            } else {
                0
            }
            result.add(remaining.removeAt(pickIdx))
        }
        return result
    }

    private fun matchesAnyTerm(haystack: String, terms: List<String>): Boolean {
        if (haystack.isEmpty()) return false
        for (term in terms) {
            if (haystack.contains(term)) return true
        }
        return false
    }
}
