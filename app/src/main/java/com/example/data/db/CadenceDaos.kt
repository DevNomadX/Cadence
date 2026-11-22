package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class TrackDedupeTuple(
    val localUriString: String?,
    val dedupeKey: String
)

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY dateAdded DESC, title ASC")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("SELECT localUriString, dedupeKey FROM tracks")
    suspend fun getTrackDedupeTuples(): List<TrackDedupeTuple>

    @Query("SELECT id FROM tracks WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findTrackIdByDedupeKey(dedupeKey: String): String?

    @Query("SELECT id FROM tracks WHERE localUriString = :uri LIMIT 1")
    suspend fun findTrackIdByUri(uri: String): String?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIgnore(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun recordPlay(trackId: String, timestamp: Long)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<String>)

    @Query("DELETE FROM tracks WHERE isUserImported = 0 OR id LIKE 'curated_%'")
    suspend fun deleteLegacyCuratedTracks()
}

data class PlaylistWithCountRow(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val trackCount: Int,
    val firstTrackId: String?
)

@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, p.updatedAt,
               COUNT(pt.trackId) AS trackCount,
               (SELECT pt2.trackId FROM playlist_tracks pt2
                WHERE pt2.playlistId = p.id
                ORDER BY pt2.position ASC LIMIT 1) AS firstTrackId
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
        """
    )
    fun observePlaylistsWithCounts(): Flow<List<PlaylistWithCountRow>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    suspend fun getPlaylistTracksOnce(playlistId: Long): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: Long, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun containsTrack(playlistId: Long, trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(entry: PlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistTrackIdsOrdered(playlistId: Long): List<String>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Transaction
    suspend fun replacePlaylistTracks(playlistId: Long, trackIds: List<String>) {
        clearPlaylistTracks(playlistId)
        val now = System.currentTimeMillis()
        if (trackIds.isNotEmpty()) {
            insertPlaylistTracks(
                trackIds.mapIndexed { idx, trackId ->
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = idx,
                        addedAt = now
                    )
                }
            )
        }
        touchPlaylist(playlistId, now)
    }
}

@Dao
interface QueueDao {

    @Query("SELECT trackId FROM queue_items ORDER BY position ASC")
    fun observeQueueTrackIds(): Flow<List<String>>

    @Query("SELECT trackId FROM queue_items ORDER BY position ASC")
    suspend fun getQueueTrackIdsOnce(): List<String>

    @Query("DELETE FROM queue_items")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Transaction
    suspend fun replaceQueue(trackIds: List<String>) {
        clearQueue()
        if (trackIds.isNotEmpty()) {
            insertQueueItems(
                trackIds.mapIndexed { index, trackId ->
                    QueueItemEntity(position = index, trackId = trackId)
                }
            )
        }
    }
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM saved_folders ORDER BY displayName ASC")
    fun observeSavedFolders(): Flow<List<SavedFolderEntity>>

    @Query("SELECT * FROM saved_folders")
    suspend fun getSavedFoldersOnce(): List<SavedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: SavedFolderEntity)

    @Query("DELETE FROM saved_folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)
}
