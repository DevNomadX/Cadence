package com.example.data

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import com.example.ui.theme.ExpressivePalette

/**
 * Strongly-typed immutable collection wrappers guaranteeing Compose compiler stability
 * and skippability for LazyColumn/LazyRow items and screen state parameters.
 */
@Keep
@Immutable
data class StableList<out T>(
    val items: List<T> = emptyList()
) : List<T> by items

@Keep
@Immutable
data class StableMap<K, out V>(
    val entriesMap: Map<K, V> = emptyMap()
) : Map<K, V> by entriesMap

fun <T> List<T>.toStableList(): StableList<T> =
    if (this is StableList<T>) this else StableList(this)

fun <K, V> Map<K, V>.toStableMap(): StableMap<K, V> =
    if (this is StableMap<K, V>) this else StableMap(this)

enum class RepeatMode(val id: String) {
    OFF("off"),
    ALL("all"),
    ONE("one");

    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromId(id: String?): RepeatMode =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class AudioFocusBehavior(
    val id: String,
    val label: String,
    val description: String
) {
    DUCK("duck", "Duck volume", "Lower volume during navigation or notifications"),
    PAUSE("pause", "Always pause", "Pause playback whenever another app requests audio"),
    IGNORE("ignore", "Mix audio", "Continue playing alongside other apps");

    companion object {
        fun fromId(id: String?): AudioFocusBehavior =
            entries.firstOrNull { it.id == id } ?: DUCK
    }
}

enum class LibraryCategory(val id: String) {
    SONGS("songs"),
    ALBUMS("albums"),
    ARTISTS("artists"),
    FOLDERS("folders"),
    GENRES("genres"),
    PLAYLISTS("playlists"),
    FAVORITES("favorites"),
    RECENT("recent"),
    MOST_PLAYED("most_played")
}

enum class LibrarySortOrder(val id: String, val label: String) {
    TITLE_ASC("title_asc", "Title A–Z"),
    ARTIST_ASC("artist_asc", "Artist"),
    DATE_ADDED_DESC("date_added_desc", "Date Added"),
    DURATION_DESC("duration_desc", "Duration"),
    MOST_PLAYED("most_played", "Most Played");

    companion object {
        fun fromId(id: String?): LibrarySortOrder =
            entries.firstOrNull { it.id == id } ?: DATE_ADDED_DESC
    }
}

enum class SleepTimerOption(val label: String, val durationMs: Long?) {
    OFF("Off", null),
    FIVE_MIN("5 min", 5 * 60_000L),
    FIFTEEN_MIN("15 min", 15 * 60_000L),
    THIRTY_MIN("30 min", 30 * 60_000L),
    END_OF_TRACK("End of track", -1L)
}

@Keep
@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String = "",
    val durationMs: Long,
    val bpm: Int = 90,
    val genreTag: String = genre.ifBlank { "Audio" },
    @DrawableRes val artworkResId: Int? = null,
    val artworkPath: String? = null,
    val localUriString: String? = null,
    val folderName: String = "Music",
    val folderPath: String = "music",
    val mimeType: String = "audio/*",
    val codec: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val fileSizeBytes: Long = 0L,
    val dateAdded: Long = 0L,
    val playCount: Int = 0,
    val completedPlayCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val suggestedPalette: ExpressivePalette = ExpressivePalette.TERRACOTTA,
    val isUserImported: Boolean = true
) {
    /**
     * Normalized short codec label (e.g., "FLAC", "WAV", "ALAC", "MP3", "AAC").
     * Pure metadata inspection — zero file or disk I/O.
     */
    val resolvedCodec: String
        get() = resolveAudioCodecLabel(
            codec = codec,
            mimeType = mimeType,
            uriString = localUriString
        )

    /** True when the track uses a lossless audio codec/container. */
    val isLosslessFormat: Boolean
        get() = resolvedCodec in LOSSLESS_CODECS

    /** True when the track exceeds CD resolution (24-bit+ or > 48 kHz). */
    val isHiRes: Boolean
        get() = bitDepth >= 24 || sampleRateHz > 48_000

    /**
     * Deterministic Hi-Fi audio quality check:
     * - Lossless formats (FLAC, WAV, ALAC, AIFF, APE, DSD, PCM)
     * - High-resolution audio (> 48 kHz sample rate or >= 24-bit depth)
     * - High-bitrate lossy audio (>= 320 kbps)
     */
    val isHiFi: Boolean
        get() = isLosslessFormat || isHiRes || bitrateKbps >= 320

    /**
     * Tiered badge label for detailed views:
     * - "HI-RES" when bitDepth >= 24 or sampleRateHz > 48 kHz
     * - "LOSSLESS" for 16-bit/standard lossless formats
     * - "HI-FI" for high-bitrate (>= 320 kbps) tracks
     */
    val hiFiLabel: String
        get() = when {
            isHiRes -> "HI-RES"
            isLosslessFormat -> "LOSSLESS"
            bitrateKbps >= 320 -> "HI-FI"
            else -> ""
        }

    /**
     * Detailed technical spec for Now Playing (e.g. "24-bit / 96 kHz FLAC",
     * "16-bit / 44.1 kHz FLAC", "320 kbps MP3"). Empty for non-Hi-Fi tracks.
     */
    val hiFiSpecSummary: String
        get() {
            if (!isHiFi) return ""
            val parts = ArrayList<String>(3)
            val ratePart = formatSampleRateKHz(sampleRateHz)
            when {
                bitDepth > 0 && ratePart.isNotEmpty() -> parts += "$bitDepth-bit / $ratePart"
                bitDepth > 0 -> parts += "$bitDepth-bit"
                ratePart.isNotEmpty() && (isLosslessFormat || isHiRes) -> parts += ratePart
                bitrateKbps >= 320 -> parts += "$bitrateKbps kbps"
                ratePart.isNotEmpty() -> parts += ratePart
            }
            if (parts.isEmpty() && bitrateKbps > 0) {
                parts += "$bitrateKbps kbps"
            }
            val codecTag = resolvedCodec
            if (codecTag.isNotEmpty()) {
                parts += codecTag
            }
            return parts.joinToString(" ")
        }

    /**
     * Builds an immutable [AudioQualitySpec] strictly from this track's extracted file metadata
     * (zero hardcoded or fabricated fields). Returns null if no real technical metadata exists.
     */
    fun resolveAudioQualitySpec(): AudioQualitySpec? {
        val format = resolvedCodec
        val bitDepthStr = if (bitDepth > 0 && (isLosslessFormat || isHiRes)) {
            "$bitDepth-bit"
        } else {
            ""
        }
        val sampleRateStr = formatSampleRateKHz(sampleRateHz)
        val bitrateStr = formatBitrateKbps(bitrateKbps)

        val items = ArrayList<AudioSpecItem>(4)
        if (format.isNotEmpty()) {
            items += AudioSpecItem(label = "Format", value = format, testTagKey = "format")
        }
        if (bitDepthStr.isNotEmpty()) {
            items += AudioSpecItem(label = "Bit Depth", value = bitDepthStr, testTagKey = "bit_depth")
        }
        if (sampleRateStr.isNotEmpty()) {
            items += AudioSpecItem(label = "Sample Rate", value = sampleRateStr, testTagKey = "sample_rate")
        }
        if (bitrateStr.isNotEmpty()) {
            items += AudioSpecItem(label = "Bitrate", value = bitrateStr, testTagKey = "bitrate")
        }
        if (items.isEmpty()) return null

        val tier = when {
            isHiRes -> AudioQualityTier.HI_RES
            isLosslessFormat -> AudioQualityTier.LOSSLESS
            bitrateKbps >= 256 -> AudioQualityTier.HIGH_QUALITY
            else -> AudioQualityTier.STANDARD
        }

        val tierTitle = when (tier) {
            AudioQualityTier.HI_RES -> "HI-RES"
            AudioQualityTier.LOSSLESS -> "LOSSLESS"
            AudioQualityTier.HIGH_QUALITY -> "HIGH QUALITY"
            AudioQualityTier.STANDARD -> "STANDARD"
        }

        val secondaryHeadline = when (tier) {
            AudioQualityTier.HI_RES -> when {
                bitDepth >= 24 -> "$bitDepth-BIT"
                sampleRateStr.isNotEmpty() -> sampleRateStr.uppercase(java.util.Locale.US)
                else -> format
            }
            AudioQualityTier.LOSSLESS -> format.ifEmpty {
                bitDepthStr.uppercase(java.util.Locale.US)
            }
            AudioQualityTier.HIGH_QUALITY, AudioQualityTier.STANDARD -> format.ifEmpty {
                if (bitrateKbps > 0) "$bitrateKbps KBPS" else sampleRateStr.uppercase(java.util.Locale.US)
            }
        }

        val headline = if (secondaryHeadline.isNotEmpty()) {
            "$tierTitle • $secondaryHeadline"
        } else {
            tierTitle
        }

        return AudioQualitySpec(
            tier = tier,
            headline = headline,
            formatLabel = format,
            bitDepthLabel = bitDepthStr,
            sampleRateLabel = sampleRateStr,
            bitrateLabel = bitrateStr,
            detailItems = items.toStableList()
        )
    }

    companion object {
        val LOSSLESS_CODECS: Set<String> = setOf(
            "FLAC", "WAV", "ALAC", "AIFF", "APE", "DSD", "PCM", "WV", "TTA"
        )
    }
}

@Keep
enum class AudioQualityTier {
    HI_RES,
    LOSSLESS,
    HIGH_QUALITY,
    STANDARD
}

@Keep
@Immutable
data class AudioSpecItem(
    val label: String,
    val value: String,
    val testTagKey: String
)

@Keep
@Immutable
data class AudioQualitySpec(
    val tier: AudioQualityTier,
    val headline: String,
    val formatLabel: String,
    val bitDepthLabel: String,
    val sampleRateLabel: String,
    val bitrateLabel: String,
    val detailItems: StableList<AudioSpecItem>
)

fun resolveAudioCodecLabel(
    codec: String,
    mimeType: String,
    uriString: String? = null
): String {
    normalizeAudioCodecToken(codec)?.let { return it }
    normalizeAudioCodecToken(mimeType)?.let { return it }
    if (!uriString.isNullOrBlank()) {
        val decoded = try {
            java.net.URLDecoder.decode(uriString, "UTF-8")
        } catch (_: Exception) {
            uriString
        }
        val filePart = decoded.substringAfterLast('/').substringAfterLast(':')
        val ext = filePart.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        normalizeAudioCodecToken(ext)?.let { return it }
    }
    return ""
}

private fun normalizeAudioCodecToken(raw: String): String? {
    val cleaned = raw.trim().lowercase(java.util.Locale.ROOT)
    if (cleaned.isEmpty() || cleaned == "audio/*" || cleaned == "application/octet-stream") return null
    val sub = cleaned.substringAfter('/', cleaned)
    return when {
        sub == "flac" || sub == "x-flac" || cleaned.contains("flac") -> "FLAC"
        sub == "alac" || cleaned.contains("alac") -> "ALAC"
        sub == "wav" || sub == "x-wav" || sub == "wave" || sub == "vnd.wave" -> "WAV"
        sub == "aiff" || sub == "x-aiff" || sub == "aif" -> "AIFF"
        sub == "ape" || sub == "x-ape" || sub == "x-monkeys-audio" -> "APE"
        sub == "dsd" || sub == "dsf" || sub == "dff" || sub == "x-dsf" || sub == "x-dff" -> "DSD"
        sub == "raw" || sub == "pcm" || sub == "l16" || sub.startsWith("pcm") -> "PCM"
        sub == "wv" || sub == "wavpack" || sub == "x-wavpack" -> "WV"
        sub == "tta" || sub == "x-tta" -> "TTA"
        sub == "mpeg" || sub == "mp3" -> "MP3"
        sub == "mp4a-latm" || sub == "aac" || sub == "aac-adts" || sub == "x-aac" -> "AAC"
        sub == "opus" -> "OPUS"
        sub == "vorbis" || sub == "ogg" || sub == "oga" -> "OGG"
        sub == "mp4" || sub == "m4a" -> "M4A"
        sub == "webm" -> "WEBM"
        sub == "wma" || sub == "x-ms-wma" -> "WMA"
        sub == "*" || sub.isBlank() -> null
        else -> sub.uppercase(java.util.Locale.ROOT)
    }
}

fun formatSampleRateKHz(sampleRateHz: Int): String {
    if (sampleRateHz <= 0) return ""
    return if (sampleRateHz % 1000 == 0) {
        "${sampleRateHz / 1000} kHz"
    } else {
        val kHz = sampleRateHz / 1000.0
        "%.1f kHz".format(java.util.Locale.US, kHz)
    }
}

fun formatBitrateKbps(bitrateKbps: Int): String {
    if (bitrateKbps <= 0) return ""
    val formatted = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(bitrateKbps)
    return "$formatted kbps"
}

@Immutable
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val coverTrack: Track? = null,
    val updatedAt: Long = 0L
)

@Immutable
data class TrackGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val trackCount: Int,
    val totalDurationMs: Long,
    val coverTrack: Track?
)

@Immutable
data class SavedFolderItem(
    val treeUri: String,
    val displayName: String,
    val lastScannedAt: Long = 0L
)

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb.coerceAtLeast(1.0))
    }
}
