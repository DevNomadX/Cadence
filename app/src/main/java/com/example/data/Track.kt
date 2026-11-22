package com.example.data

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.example.ui.theme.ExpressivePalette

/**
 * Strongly-typed immutable collection wrappers guaranteeing Compose compiler stability
 * and skippability for LazyColumn/LazyRow items and screen state parameters.
 */
@Immutable
data class StableList<out T>(
    val items: List<T> = emptyList()
) : List<T> by items

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
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val fileSizeBytes: Long = 0L,
    val dateAdded: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val suggestedPalette: ExpressivePalette = ExpressivePalette.TERRACOTTA,
    val isUserImported: Boolean = true
)

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
