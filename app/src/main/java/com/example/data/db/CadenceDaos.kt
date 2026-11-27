package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Upsert
    suspend fun upsertTracksAtomic(tracks: List<TrackEntity>)

    @Transaction
    suspend fun syncFolderTracks(upserts: List<TrackEntity>, deleteIds: List<String>) {
        if (upserts.isNotEmpty()) {
            upsertTracksAtomic(upserts)
        }
        if (deleteIds.isNotEmpty()) {
            deleteIds.chunked(500).forEach { chunk ->
                deleteTracksByIds(chunk)
            }
        }
    }

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun recordPlay(trackId: String, timestamp: Long)

    @Query("UPDATE tracks SET completedPlayCount = completedPlayCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun recordCompletedPlay(trackId: String, timestamp: Long)

    @Query("UPDATE tracks SET skipCount = skipCount + 1 WHERE id = :trackId")
    suspend fun recordSkip(trackId: String)

    @Query("UPDATE tracks SET affinityScore = :affinityScore WHERE id = :trackId")
    suspend fun updateAffinityScore(trackId: String, affinityScore: Float)

    @Query("UPDATE tracks SET affinityScore = :affinityScore, moodTags = :moodTags, artworkColorArgb = :artworkColorArgb WHERE id = :trackId")
    suspend fun updateTrackIntelligence(
        trackId: String,
        affinityScore: Float,
        moodTags: String,
        artworkColorArgb: Int
    )

    @Query(
        """
        UPDATE tracks
        SET codec = :codec,
            bitrateKbps = :bitrateKbps,
            sampleRateHz = :sampleRateHz,
            bitDepth = :bitDepth,
            artworkPath = COALESCE(:artworkPath, artworkPath),
            artworkColorArgb = :artworkColorArgb,
            moodTags = :moodTags,
            affinityScore = :affinityScore
        WHERE id = :trackId
        """
    )
    suspend fun updateEnrichedAudioMetadata(
        trackId: String,
        codec: String,
        bitrateKbps: Int,
        sampleRateHz: Int,
        bitDepth: Int,
        artworkPath: String?,
        artworkColorArgb: Int,
        moodTags: String,
        affinityScore: Float
    )

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

@Dao
interface RecommendationSnapshotDao {

    // --- Daily Picks Snapshot (YYYY-MM-DD key, max 6 distinct tracks) ---

    @Query("SELECT EXISTS(SELECT 1 FROM daily_picks_snapshots WHERE dateKey = :dateKey)")
    suspend fun hasDailyPicksSnapshot(dateKey: String): Boolean

    @Query("SELECT * FROM daily_picks_snapshots WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyPicksHeader(dateKey: String): DailyPicksSnapshotEntity?

    @Query("SELECT trackId FROM daily_picks_snapshot_items WHERE dateKey = :dateKey ORDER BY position ASC")
    suspend fun getRawDailyPicksTrackIds(dateKey: String): List<String>

    /**
     * Reads locked Daily Picks tracks filtered against the live `tracks` table so deleted audio
     * files never surface as dead cards, while preserving exact locked ordering.
     */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN daily_picks_snapshot_items i ON t.id = i.trackId
        WHERE i.dateKey = :dateKey
        ORDER BY i.position ASC
        """
    )
    fun observeDailyPicksTracks(dateKey: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN daily_picks_snapshot_items i ON t.id = i.trackId
        WHERE i.dateKey = :dateKey
        ORDER BY i.position ASC
        """
    )
    suspend fun getDailyPicksTracksOnce(dateKey: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyPicksHeaderIgnore(header: DailyPicksSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDailyPicksItemsIgnore(items: List<DailyPicksSnapshotItemEntity>)

    @Query("DELETE FROM daily_picks_snapshots WHERE dateKey != :keepDateKey")
    suspend fun pruneDailyPicksExcept(keepDateKey: String)

    /**
     * Atomically locks up to 6 distinct track IDs for [dateKey] if and only if no snapshot already
     * exists for that calendar day. Subsequent calls for the same [dateKey] are no-ops so active
     * Daily Picks are never mutated or reshuffled during playback.
     */
    @Transaction
    suspend fun lockDailyPicksIfAbsent(
        dateKey: String,
        trackIds: List<String>,
        createdAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        val insertedRowId = insertDailyPicksHeaderIgnore(
            DailyPicksSnapshotEntity(dateKey = dateKey, createdAtMs = createdAtMs)
        )
        if (insertedRowId == -1L) return false
        val bounded = trackIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(DAILY_PICKS_MAX_ITEMS)
            .toList()
        if (bounded.isNotEmpty()) {
            insertDailyPicksItemsIgnore(
                bounded.mapIndexed { index, trackId ->
                    DailyPicksSnapshotItemEntity(
                        dateKey = dateKey,
                        position = index,
                        trackId = trackId
                    )
                }
            )
        }
        return true
    }

    // --- Context-Aware & Mood Session Snapshot (sessionId key, max 10 distinct tracks) ---

    @Query("SELECT EXISTS(SELECT 1 FROM session_snapshots WHERE sessionId = :sessionId)")
    suspend fun hasSessionSnapshot(sessionId: String): Boolean

    @Query("SELECT * FROM session_snapshots WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionHeader(sessionId: String): SessionSnapshotEntity?

    @Query("SELECT trackId FROM session_snapshot_items WHERE sessionId = :sessionId ORDER BY position ASC")
    suspend fun getRawSessionTrackIds(sessionId: String): List<String>

    /**
     * Reads locked Session snapshot tracks filtered against the live `tracks` table so deleted
     * audio files are cleanly omitted without mutating or reshuffling remaining tracks.
     */
    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN session_snapshot_items i ON t.id = i.trackId
        WHERE i.sessionId = :sessionId
        ORDER BY i.position ASC
        """
    )
    fun observeSessionTracks(sessionId: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN session_snapshot_items i ON t.id = i.trackId
        WHERE i.sessionId = :sessionId
        ORDER BY i.position ASC
        """
    )
    suspend fun getSessionTracksOnce(sessionId: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionHeaderIgnore(header: SessionSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessionItemsIgnore(items: List<SessionSnapshotItemEntity>)

    @Query("DELETE FROM session_snapshots WHERE sessionId = :sessionId")
    suspend fun deleteSessionSnapshot(sessionId: String)

    @Query("DELETE FROM session_snapshots WHERE createdAtMs < :olderThanMs")
    suspend fun pruneExpiredSessions(olderThanMs: Long)

    /**
     * Atomically locks up to 10 distinct track IDs for [sessionId] if and only if no snapshot
     * already exists for that session key.
     */
    @Transaction
    suspend fun lockSessionIfAbsent(
        sessionId: String,
        contextKey: String,
        trackIds: List<String>,
        createdAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        val insertedRowId = insertSessionHeaderIgnore(
            SessionSnapshotEntity(
                sessionId = sessionId,
                contextKey = contextKey,
                createdAtMs = createdAtMs
            )
        )
        if (insertedRowId == -1L) return false
        val bounded = trackIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(SESSION_SNAPSHOT_MAX_ITEMS)
            .toList()
        if (bounded.isNotEmpty()) {
            insertSessionItemsIgnore(
                bounded.mapIndexed { index, trackId ->
                    SessionSnapshotItemEntity(
                        sessionId = sessionId,
                        position = index,
                        trackId = trackId
                    )
                }
            )
        }
        return true
    }

    companion object {
        const val DAILY_PICKS_MAX_ITEMS: Int = 6
        const val SESSION_SNAPSHOT_MAX_ITEMS: Int = 10
    }
}

