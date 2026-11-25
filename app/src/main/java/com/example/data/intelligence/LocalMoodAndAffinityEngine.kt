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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

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
     * Computes a normalized local affinity score (`0.0f..1.0f`) combining:
     * - Completion rate (>80% listens via [Track.completedPlayCount]) & favorite affinity
     * - Early skip penalty (<25% skips via [Track.skipCount])
     * - Time-of-day context (`21:00–04:59` favors calm/warm moods; `06:00–20:59` favors upbeat)
     * - Recency decay (`24-hour` cooldown penalty) & rediscovery boost (`21+ days` untouched high-affinity tracks)
     */
    fun computeLocalAffinity(
        track: Track,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = currentLocalHour(nowMs),
        maxLibraryPlays: Int = 10,
        sessionSkippedIds: Set<String> = emptySet()
    ): TrackAffinityBreakdown {
        val plays = track.playCount.coerceAtLeast(0)
        val completed = track.completedPlayCount.coerceAtLeast(0)
        val skips = track.skipCount.coerceAtLeast(0)
        val safeMaxPlays = maxOf(1, maxLibraryPlays)

        // 1. Completion rate & favorite signal
        val attempts = maxOf(plays, completed + skips)
        val completionRatio = if (attempts > 0) {
            (completed.toFloat() / attempts.toFloat()).coerceIn(0f, 1f)
        } else {
            0.5f
        }
        val completedDepth = if (completed > 0) {
            (ln(completed.toDouble() + 1.0) / ln(safeMaxPlays.toDouble() + 2.0)).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
        val playVolume = if (plays > 0) {
            (ln(plays.toDouble() + 1.0) / ln(safeMaxPlays.toDouble() + 2.0)).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        val completionSignal = (
            (if (attempts > 0) (completionRatio - 0.5f) * 0.24f else 0f) +
                completedDepth * 0.22f +
                playVolume * 0.08f +
                (if (track.isFavorite) 0.16f else 0f)
            ).coerceIn(-0.14f, 0.44f)

        // 2. Skip penalty (<25% early skips)
        val skipRatio = if (attempts > 0) {
            (skips.toFloat() / attempts.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val sessionSkipExtra = if (track.id in sessionSkippedIds) 0.14f else 0f
        val skipPenalty = (
            (skipRatio * 0.24f) +
                (min(skips, 6) * 0.035f) +
                sessionSkipExtra
            ).coerceIn(0f, 0.46f)

        // 3. Time-of-day context (21:00–04:59 calm/warm vs 06:00–20:59 energetic/upbeat)
        val resolvedTags = track.resolvedMoodTags()
        val isLateNightWindow = localHourOfDay >= 21 || localHourOfDay <= 4
        val isDaytimeWindow = localHourOfDay in 6..20
        val timeOfDayBoost = when {
            isLateNightWindow -> {
                var delta = 0f
                if (SmartMoodTag.LATE_NIGHT in resolvedTags) delta += 0.11f
                if (SmartMoodTag.ACOUSTIC in resolvedTags) delta += 0.06f
                if (SmartMoodTag.SOULFUL in resolvedTags) delta += 0.06f
                if (SmartMoodTag.UPBEAT in resolvedTags &&
                    SmartMoodTag.LATE_NIGHT !in resolvedTags &&
                    SmartMoodTag.SOULFUL !in resolvedTags
                ) {
                    delta -= 0.06f
                }
                delta.coerceIn(-0.08f, 0.14f)
            }
            isDaytimeWindow -> {
                var delta = 0f
                if (SmartMoodTag.UPBEAT in resolvedTags) delta += 0.11f
                if (SmartMoodTag.SOULFUL in resolvedTags) delta += 0.02f
                if (SmartMoodTag.LATE_NIGHT in resolvedTags && SmartMoodTag.UPBEAT !in resolvedTags) {
                    delta -= 0.04f
                }
                delta.coerceIn(-0.06f, 0.12f)
            }
            else -> {
                // 05:00 dawn transition
                if (SmartMoodTag.ACOUSTIC in resolvedTags || SmartMoodTag.SOULFUL in resolvedTags) 0.05f else 0f
            }
        }

        // 4. Recency decay (24-hour cooldown) & rediscovery boost (21+ days untouched high-affinity)
        val ageMs = if (track.lastPlayedAt > 0L) (nowMs - track.lastPlayedAt).coerceAtLeast(0L) else -1L
        val isHighAffinityTrack = track.isFavorite || completed >= 2 || (plays >= 3 && skips <= completed + 1)
        val recencyAdjustment = when {
            ageMs in 0L until TWENTY_FOUR_HOURS_MS -> {
                val freshness = 1f - (ageMs.toFloat() / TWENTY_FOUR_HOURS_MS.toFloat()).coerceIn(0f, 1f)
                -(0.08f + freshness * 0.18f) // -0.26f immediately after play, decaying smoothly to -0.08f at 24h
            }
            ageMs >= TWENTY_ONE_DAYS_MS && isHighAffinityTrack && skips <= maxOf(1, completed) -> {
                val ageWeeks = ((ageMs / ONE_DAY_MS).toFloat() / 21f).coerceIn(1f, 3f)
                (0.11f + (ageWeeks - 1f) * 0.025f).coerceAtMost(0.16f)
            }
            else -> 0f
        }

        val rawScore = 0.44f + completionSignal - skipPenalty + timeOfDayBoost + recencyAdjustment
        val finalScore = if (rawScore.isFinite()) rawScore.coerceIn(0f, 1f) else 0.44f

        return TrackAffinityBreakdown(
            score = finalScore,
            completionSignal = safeFiniteFloat(completionSignal),
            skipPenalty = safeFiniteFloat(skipPenalty),
            timeOfDayBoost = safeFiniteFloat(timeOfDayBoost),
            recencyAdjustment = safeFiniteFloat(recencyAdjustment)
        )
    }

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
     * Assembles a balanced offline recommendation mix with ~60% high-affinity favorites,
     * ~25% forgotten gems (21+ days untouched high-affinity tracks), and ~15% under-played
     * tracks (`playCount <= 1` with low skip count), strictly capped at 2 songs per artist.
     *
     * Pre-computes all sort keys prior to `sortedWith(...)` so TimSort transitivity is guaranteed
     * for arbitrarily large libraries (32+ tracks).
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
        maxPerArtist: Int = MAX_SONGS_PER_ARTIST
    ): BalancedMixResult {
        if (tracks.isEmpty() || targetSize <= 0) {
            return BalancedMixResult(StableList(), 0, 0, 0, 0)
        }

        val rng = Random(seed)
        val snapshot = TrackIntelligenceCache.getOrBuildSnapshot(tracks)
        val cachedAffinities = if (sessionSkippedIds.isEmpty()) {
            snapshot.getOrComputeAffinities(tracks, nowMs, localHourOfDay)
        } else {
            null
        }
        val maxPlays = snapshot.maxLibraryPlays

        // Pre-compute all deterministic sort keys BEFORE sorting (TimSort transitivity guarantee)
        val candidates = tracks.map { track ->
            val breakdown = cachedAffinities?.get(track.id) ?: computeLocalAffinity(
                track = track,
                nowMs = nowMs,
                localHourOfDay = localHourOfDay,
                maxLibraryPlays = maxPlays,
                sessionSkippedIds = sessionSkippedIds
            )
            val moodAdjust = computeTuneAdjustment(track, tuneConfig)
            val jitter = rng.nextDouble(0.0, 0.035)
            val unusedPenalty = if (track.id in excludedIds) -0.25 else 0.0
            val ageMs = if (track.lastPlayedAt > 0L) (nowMs - track.lastPlayedAt).coerceAtLeast(0L) else -1L
            val isHighAffinity = track.isFavorite ||
                track.completedPlayCount >= 2 ||
                (track.playCount >= 2 && track.skipCount <= track.completedPlayCount + 1)
            val isForgottenGem = ageMs >= TWENTY_ONE_DAYS_MS &&
                isHighAffinity &&
                track.skipCount <= maxOf(1, track.completedPlayCount)
            val isUnderPlayed = track.playCount <= 1 && track.skipCount <= 1
            val isFavoriteBucket = (isHighAffinity || breakdown.score >= 0.54f) &&
                !isForgottenGem &&
                track.skipCount <= maxOf(1, track.completedPlayCount + 1)

            val primarySortKey = safeFiniteDouble(breakdown.score.toDouble() + moodAdjust + unusedPenalty + jitter)
            val rediscoveryKey = safeFiniteDouble(
                breakdown.score.toDouble() +
                    (if (ageMs >= TWENTY_ONE_DAYS_MS) 0.25 else 0.0) +
                    moodAdjust + unusedPenalty + jitter
            )
            val underplayedKey = safeFiniteDouble(
                breakdown.score.toDouble() +
                    (if (track.playCount == 0) 0.18 else 0.08) +
                    (if (track.isHiFi) 0.04 else 0.0) +
                    moodAdjust + unusedPenalty + jitter
            )

            PrecomputedMixCandidate(
                track = track,
                affinityScore = breakdown.score,
                primarySortKey = primarySortKey,
                rediscoverySortKey = rediscoveryKey,
                underplayedSortKey = underplayedKey,
                isFavoriteBucket = isFavoriteBucket,
                isForgottenGem = isForgottenGem,
                isUnderPlayed = isUnderPlayed
            )
        }

        val favoriteRanked = candidates
            .filter { it.isFavoriteBucket }
            .sortedWith(
                compareByDescending<PrecomputedMixCandidate> { it.primarySortKey }
                    .thenBy { it.track.id }
            )
        val forgottenRanked = candidates
            .filter { it.isForgottenGem }
            .sortedWith(
                compareByDescending<PrecomputedMixCandidate> { it.rediscoverySortKey }
                    .thenBy { it.track.id }
            )
        val underplayedRanked = candidates
            .filter { it.isUnderPlayed }
            .sortedWith(
                compareByDescending<PrecomputedMixCandidate> { it.underplayedSortKey }
                    .thenBy { it.track.id }
            )
        val allRanked = candidates.sortedWith(
            compareByDescending<PrecomputedMixCandidate> { it.primarySortKey }
                .thenBy { it.track.id }
        )

        // Compute 60 / 25 / 15 quotas (nudged cleanly if user customizes discoveryBalance slider)
        val balance = tuneConfig.discoveryBalance.coerceIn(0f, 1f)
        val defaultBalanceDelta = balance - 0.35f
        val favRatio = (0.60f - defaultBalanceDelta * 0.30f).coerceIn(0.35f, 0.75f)
        val forgottenRatio = 0.25f
        val effectiveTarget = minOf(targetSize, tracks.size)
        val favQuota = (effectiveTarget * favRatio).roundToInt().coerceIn(0, effectiveTarget)
        val forgottenQuota = (effectiveTarget * forgottenRatio).roundToInt()
            .coerceIn(0, (effectiveTarget - favQuota).coerceAtLeast(0))
        val underplayedQuota = (effectiveTarget - favQuota - forgottenQuota).coerceAtLeast(0)

        val picked = ArrayList<Track>(effectiveTarget)
        val usedIds = HashSet<String>(effectiveTarget * 2)
        val artistCounts = HashMap<String, Int>()
        val safeCap = maxPerArtist.coerceAtLeast(1)

        fun tryAddFrom(pool: List<PrecomputedMixCandidate>, quota: Int): Int {
            var added = 0
            for (cand in pool) {
                if (added >= quota || picked.size >= effectiveTarget) break
                val track = cand.track
                if (track.id in usedIds) continue
                val artistKey = normalizedArtistKey(track.artist)
                val count = artistCounts[artistKey] ?: 0
                if (count >= safeCap) continue
                usedIds.add(track.id)
                artistCounts[artistKey] = count + 1
                picked.add(track)
                added++
            }
            return added
        }

        // Pick forgotten gems & under-played first up to their exact quotas so high-affinity
        // overlap doesn't starve the 25% and 15% slices, then fill the 60% favorites slice.
        val forgottenAdded = tryAddFrom(forgottenRanked, forgottenQuota)
        val underplayedAdded = tryAddFrom(underplayedRanked, underplayedQuota)
        val favoritesAdded = tryAddFrom(favoriteRanked, favQuota)

        // Graceful fallback backfill (strictly honoring maxPerArtist = 2) when any bucket was sparse
        val backfillAdded = if (picked.size < effectiveTarget) {
            tryAddFrom(allRanked, effectiveTarget - picked.size)
        } else {
            0
        }

        val spaced = spaceAdjacentArtists(picked)
        return BalancedMixResult(
            tracks = spaced.toStableList(),
            favoritesCount = favoritesAdded,
            forgottenGemsCount = forgottenAdded,
            underPlayedCount = underplayedAdded,
            backfillCount = backfillAdded
        )
    }

    /**
     * Builds the compact Discover "Mood Playlists" shelf for Late Night, Acoustic, Soulful, and Upbeat.
     * Enforces strict max 2 songs per artist and omits empty moods cleanly.
     */
    fun buildMoodSmartPlaylists(
        tracks: List<Track>,
        nowMs: Long = System.currentTimeMillis(),
        localHourOfDay: Int = currentLocalHour(nowMs),
        maxTracksPerMood: Int = 16
    ): List<MoodSmartPlaylist> {
        if (tracks.isEmpty()) return emptyList()
        val snapshot = TrackIntelligenceCache.getOrBuildSnapshot(tracks)
        val affinities = snapshot.getOrComputeAffinities(tracks, nowMs, localHourOfDay)

        val precomputed = tracks.map { track ->
            val cached = snapshot.vectorsById[track.id]
            val resolvedTags = cached?.smartMoodTags ?: track.resolvedMoodTags()
            val moodScores = cached?.smartMoodScores ?: computeMoodAffinityScores(
                title = track.title,
                album = track.album,
                artist = track.artist,
                genre = track.genre,
                folderName = track.folderName,
                bpm = track.bpm,
                artworkColorArgb = track.artworkColorArgb
            )
            val affinity = affinities[track.id]?.score ?: computeLocalAffinity(
                track = track,
                nowMs = nowMs,
                localHourOfDay = localHourOfDay,
                maxLibraryPlays = snapshot.maxLibraryPlays
            ).score
            PrecomputedMoodTrack(
                track = track,
                tags = resolvedTags,
                moodScores = moodScores,
                affinityScore = affinity
            )
        }

        return SmartMoodTag.discoverMoods.mapNotNull { mood ->
            val matching = precomputed.filter { mood in it.tags }
            if (matching.isEmpty()) return@mapNotNull null

            val ranked = matching
                .map { item ->
                    val fit = item.moodScores[mood] ?: 0.5f
                    val sortKey = safeFiniteDouble(item.affinityScore * 0.65 + fit * 0.35)
                    item.track to sortKey
                }
                .sortedWith(
                    compareByDescending<Pair<Track, Double>> { it.second }
                        .thenBy { it.first.id }
                )
                .map { it.first }

            val curated = enforceArtistDiversityCap(
                candidates = ranked,
                maxPerArtist = MAX_SONGS_PER_ARTIST,
                limit = maxTracksPerMood
            )
            if (curated.isEmpty()) return@mapNotNull null

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
                tracks = curated.toStableList(),
                totalMatchCount = matching.size
            )
        }
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

    private fun computeTuneAdjustment(track: Track, tuneConfig: DiscoverTuneConfig): Double {
        var adjust = 0.0
        val tags = track.resolvedMoodTags()
        tuneConfig.selectedMood?.lowercase(Locale.ROOT)?.let { selected ->
            val matches = when (selected) {
                "nocturnal", "calm", "late_night" -> SmartMoodTag.LATE_NIGHT in tags
                "organic", "focused", "acoustic" -> SmartMoodTag.ACOUSTIC in tags
                "warm", "soulful" -> SmartMoodTag.SOULFUL in tags
                "energetic", "uplifting", "upbeat" -> SmartMoodTag.UPBEAT in tags
                else -> false
            }
            adjust += if (matches) 0.22 else -0.08
        }
        tuneConfig.energyFilter?.lowercase(Locale.ROOT)?.let { energy ->
            val bpm = track.bpm
            adjust += when (energy) {
                "low" -> if (bpm in 1..95 || SmartMoodTag.LATE_NIGHT in tags || SmartMoodTag.ACOUSTIC in tags) 0.16 else -0.14
                "high" -> if (bpm >= 118 || SmartMoodTag.UPBEAT in tags) 0.16 else -0.14
                "medium" -> if (bpm in 92..118 || SmartMoodTag.SOULFUL in tags) 0.12 else -0.04
                else -> 0.0
            }
        }
        return adjust
    }

    private fun matchesAnyTerm(haystack: String, terms: List<String>): Boolean {
        if (haystack.isEmpty()) return false
        for (term in terms) {
            if (haystack.contains(term)) return true
        }
        return false
    }

    private fun safeFiniteFloat(value: Float): Float = if (value.isFinite()) value else 0f
    private fun safeFiniteDouble(value: Double): Double = if (value.isFinite()) value else 0.0

    private data class PrecomputedMixCandidate(
        val track: Track,
        val affinityScore: Float,
        val primarySortKey: Double,
        val rediscoverySortKey: Double,
        val underplayedSortKey: Double,
        val isFavoriteBucket: Boolean,
        val isForgottenGem: Boolean,
        val isUnderPlayed: Boolean
    )

    private data class PrecomputedMoodTrack(
        val track: Track,
        val tags: StableList<SmartMoodTag>,
        val moodScores: Map<SmartMoodTag, Float>,
        val affinityScore: Float
    )
}
