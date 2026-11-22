package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.Track
import com.example.ui.theme.ExpressivePalette

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
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val fileSizeBytes: Long,
    val dateAdded: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
    val isFavorite: Boolean,
    val synthProfileName: String? = null,
    val suggestedPaletteId: String,
    val isUserImported: Boolean,
    val dedupeKey: String
) {
    fun toDomain(): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        bpm = bpm,
        genreTag = genre.ifBlank { mimeType.substringAfter('/').uppercase().ifBlank { "Audio" } },
        artworkResId = artworkResId,
        artworkPath = artworkPath,
        localUriString = localUriString,
        folderName = folderName,
        folderPath = folderPath,
        mimeType = mimeType,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        fileSizeBytes = fileSizeBytes,
        dateAdded = dateAdded,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        isFavorite = isFavorite,
        suggestedPalette = ExpressivePalette.fromId(suggestedPaletteId),
        isUserImported = isUserImported
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
            bitrateKbps = track.bitrateKbps,
            sampleRateHz = track.sampleRateHz,
            fileSizeBytes = track.fileSizeBytes,
            dateAdded = track.dateAdded,
            playCount = track.playCount,
            lastPlayedAt = track.lastPlayedAt,
            isFavorite = track.isFavorite,
            synthProfileName = null,
            suggestedPaletteId = track.suggestedPalette.id,
            isUserImported = track.isUserImported,
            dedupeKey = buildDedupeKey(track.title, track.artist, track.durationMs)
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
