package com.example.data.db

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.Track
import com.example.data.intelligence.SmartMoodTag
import com.example.ui.theme.ExpressivePalette

@Keep
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["folderPath"]),
        Index(value = ["genre"]),
        Index(value = ["dedupeKey"]),
        Index(value = ["dateAdded"]),
        Index(value = ["playCount"]),
        Index(value = ["lastPlayedAt"])
    ]
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val bpm: Int,
    val artworkResId: Int?,
    val artworkPath: String?,
    val localUriString: String?,
    val folderName: String,
    val folderPath: String,
    val mimeType: String,
    val codec: String = "",
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val bitDepth: Int = 0,
    val fileSizeBytes: Long,
    val dateAdded: Long,
    val playCount: Int,
    val completedPlayCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAt: Long,
    val isFavorite: Boolean,
    val synthProfileName: String? = null,
    val suggestedPaletteId: String,
    val isUserImported: Boolean,
    val dedupeKey: String,
    val affinityScore: Float = 0f,
    val moodTags: String = "",
    val artworkColorArgb: Int = 0
) {
    fun toDomain(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        bpm = bpm,
        genreTag = genre.ifBlank {
            codec.ifBlank { mimeType.substringAfter('/').uppercase() }.ifBlank { "Audio" }
        },
        artworkResId = artworkResId,
        artworkPath = artworkPath,
        localUriString = localUriString,
        folderName = folderName,
        folderPath = folderPath,
        mimeType = mimeType,
        codec = codec,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        bitDepth = bitDepth,
        fileSizeBytes = fileSizeBytes,
        dateAdded = dateAdded,
        playCount = playCount,
        completedPlayCount = completedPlayCount,
        skipCount = skipCount,
        lastPlayedAt = lastPlayedAt,
        isFavorite = isFavorite,
        suggestedPalette = ExpressivePalette.fromId(suggestedPaletteId),
        isUserImported = isUserImported,
        affinityScore = affinityScore,
        moodTags = SmartMoodTag.decode(moodTags),
        artworkColorArgb = artworkColorArgb
    )

    companion object {
        fun buildDedupeKey(title: String, artist: String, durationMs: Long): String {
            val normTitle = title.trim().lowercase().replace(Regex("\\s+"), " ")
            val normArtist = artist.trim().lowercase().replace(Regex("\\s+"), " ")
            val durBucket = (durationMs / 2000L) // 2-second tolerance bucket
            return "$normTitle|$normArtist|$durBucket"
        }

        fun fromDomain(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            genre = track.genre,
            durationMs = track.durationMs,
            bpm = track.bpm,
            artworkResId = track.artworkResId,
            artworkPath = track.artworkPath,
            localUriString = track.localUriString,
            folderName = track.folderName,
            folderPath = track.folderPath,
            mimeType = track.mimeType,
            codec = track.codec,
            bitrateKbps = track.bitrateKbps,
            sampleRateHz = track.sampleRateHz,
            bitDepth = track.bitDepth,
            fileSizeBytes = track.fileSizeBytes,
            dateAdded = track.dateAdded,
            playCount = track.playCount,
            completedPlayCount = track.completedPlayCount,
            skipCount = track.skipCount,
            lastPlayedAt = track.lastPlayedAt,
            isFavorite = track.isFavorite,
            synthProfileName = null,
            suggestedPaletteId = track.suggestedPalette.id,
            isUserImported = track.isUserImported,
            dedupeKey = buildDedupeKey(track.title, track.artist, track.durationMs),
            affinityScore = track.affinityScore,
            moodTags = SmartMoodTag.encode(track.moodTags),
            artworkColorArgb = track.artworkColorArgb
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["trackId"])
    ]
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "queue_items",
    indices = [Index(value = ["trackId"])]
)
data class QueueItemEntity(
    @PrimaryKey val position: Int,
    val trackId: String
)

@Entity(tableName = "saved_folders")
data class SavedFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val lastScannedAt: Long = System.currentTimeMillis()
)

/**
 * Immutable calendar-day Daily Picks snapshot header keyed by `YYYY-MM-DD`.
 * Generated once per calendar day so playback never mutates or reshuffles active Daily Picks.
 */
@Keep
@Entity(tableName = "daily_picks_snapshots")
data class DailyPicksSnapshotEntity(
    @PrimaryKey val dateKey: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Ordered track ID entries (up to 6 distinct tracks) belonging to a locked Daily Picks snapshot.
 * Deliberately does NOT cascade-delete on [TrackEntity] removal so the locked snapshot remains
 * immutable while read queries filter deleted tracks via INNER JOIN against `tracks`.
 */
@Keep
@Entity(
    tableName = "daily_picks_snapshot_items",
    primaryKeys = ["dateKey", "position"],
    foreignKeys = [
        ForeignKey(
            entity = DailyPicksSnapshotEntity::class,
            parentColumns = ["dateKey"],
            childColumns = ["dateKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["dateKey"], name = "index_daily_picks_snapshot_items_dateKey"),
        Index(value = ["trackId"], name = "index_daily_picks_snapshot_items_trackId")
    ]
)
data class DailyPicksSnapshotItemEntity(
    val dateKey: String,
    val position: Int,
    val trackId: String
)

/**
 * Immutable Context-Aware / Mood Session snapshot header keyed by `sessionId`.
 * Locked for the duration of the session once triggered.
 */
@Keep
@Entity(tableName = "session_snapshots")
data class SessionSnapshotEntity(
    @PrimaryKey val sessionId: String,
    val contextKey: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

/**
 * Ordered track ID entries (up to 10 distinct tracks) belonging to a locked Session snapshot.
 * Deliberately does NOT cascade-delete on [TrackEntity] removal so deleted tracks are filtered
 * cleanly on read without mutating remaining tracks.
 */
@Keep
@Entity(
    tableName = "session_snapshot_items",
    primaryKeys = ["sessionId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = SessionSnapshotEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"], name = "index_session_snapshot_items_sessionId"),
        Index(value = ["trackId"], name = "index_session_snapshot_items_trackId")
    ]
)
data class SessionSnapshotItemEntity(
    val sessionId: String,
    val position: Int,
    val trackId: String
)

