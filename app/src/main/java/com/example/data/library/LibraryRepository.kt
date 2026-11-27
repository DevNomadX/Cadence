package com.example.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.LibrarySortOrder
import com.example.data.PlaylistSummary
import com.example.data.PreferencesRepository
import com.example.data.SavedFolderItem
import com.example.data.Track
import com.example.data.TrackGroup
import com.example.data.db.CadenceDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.intelligence.LocalMoodAndAffinityEngine
import com.example.data.intelligence.PlaybackSignal
import com.example.data.intelligence.RawPlaybackEvent
import com.example.data.intelligence.RecommendationCore
import com.example.data.intelligence.RecommendationSnapshotRepository
import com.example.data.intelligence.SignalProcessor
import com.example.data.intelligence.SignalType
import com.example.data.intelligence.SmartMoodTag
import com.example.data.intelligence.TimeOfDayCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single source of truth for the local music library, playlists, saved folders, and persisted queue.
 */
class LibraryRepository(
    context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val injectedDatabase: CadenceDatabase? = null
) {
    private val appContext = context.applicationContext
    private val database by lazy { injectedDatabase ?: CadenceDatabase.getInstance(appContext) }
    private val trackDao by lazy { database.trackDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val queueDao by lazy { database.queueDao() }
    private val folderDao by lazy { database.folderDao() }
    private val recommendationSnapshotDao by lazy { database.recommendationSnapshotDao() }
    private val artworkCache = ArtworkCacheManager(appContext)
    private val recentSignalsLock = Any()
    private val recentSignals = ArrayDeque<PlaybackSignal>(MAX_RECENT_SIGNALS)

    val recommendationSnapshots: RecommendationSnapshotRepository by lazy {
        RecommendationSnapshotRepository(
            snapshotDao = recommendationSnapshotDao,
            trackDao = trackDao
        )
    }

    val scanner: LocalLibraryScanner by lazy {
        LocalLibraryScanner(
            context = appContext,
            trackDao = trackDao,
            folderDao = folderDao,
            artworkCache = artworkCache
        )
    }

    val scanState: StateFlow<ScanState> get() = scanner.scanState

    val allTracksFlow: Flow<List<Track>> by lazy {
        trackDao.observeAllTracks()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)
    }

    val playlistsFlow: Flow<List<PlaylistSummary>> by lazy {
        combine(
            playlistDao.observePlaylistsWithCounts(),
            allTracksFlow
        ) { rows, tracks ->
            val trackMap = tracks.associateBy { it.id }
            rows.map { row ->
                PlaylistSummary(
                    id = row.id,
                    name = row.name,
                    trackCount = row.trackCount,
                    coverTrack = row.firstTrackId?.let { trackMap[it] },
                    updatedAt = row.updatedAt
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    val savedFoldersFlow: Flow<List<SavedFolderItem>> by lazy {
        folderDao.observeSavedFolders()
            .map { rows ->
                rows.map { SavedFolderItem(it.treeUri, it.displayName, it.lastScannedAt) }
            }
            .flowOn(Dispatchers.Default)
    }

    fun recentSignalsSnapshot(): List<PlaybackSignal> = synchronized(recentSignalsLock) {
        recentSignals.toList()
    }

    private fun appendRecentSignal(signal: PlaybackSignal) {
        synchronized(recentSignalsLock) {
            recentSignals.addLast(signal)
            while (recentSignals.size > MAX_RECENT_SIGNALS) {
                recentSignals.removeFirst()
            }
        }
    }

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        // Purge any pre-seeded demo/curated tracks and legacy synthesized WAV files
        trackDao.deleteLegacyCuratedTracks()
        runCatching {
            val legacySynthDir = File(appContext.cacheDir, "cadence_synth")
            if (legacySynthDir.exists()) legacySynthDir.deleteRecursively()
        }

        if (!preferencesRepository.isLegacyMigrationDone()) {
            val prefs = preferencesRepository.preferencesFlow.first()
            if (prefs.legacyImportedTrackUris.isNotEmpty()) {
                scanner.importAudioFiles(
                    uris = prefs.legacyImportedTrackUris.map { Uri.parse(it) },
                    minDurationSec = prefs.minTrackDurationSec
                )
            }
            prefs.legacyFavoriteTrackIds
                .filterNot { it.startsWith("curated_") }
                .forEach { favId -> trackDao.setFavorite(favId, true) }
            preferencesRepository.markLegacyMigrationDone()
        }
    }

    suspend fun getSavedFoldersOnce() = withContext(Dispatchers.IO) {
        folderDao.getSavedFoldersOnce()
    }

    suspend fun runBackgroundEnrichment(): Int = scanner.runStage2BackgroundEnrichment()

    suspend fun pruneAllFolderTracksIfNoScope(): Int = withContext(Dispatchers.IO) {
        val toRemove = trackDao.getAllTracksOnce()
            .filter { it.isUserImported && it.folderPath != "imported" }
            .map { it.id }
        if (toRemove.isNotEmpty()) {
            trackDao.syncFolderTracks(upserts = emptyList(), deleteIds = toRemove)
        }
        toRemove.size
    }

    private suspend fun backfillMissingTrackIntelligence() = withContext(Dispatchers.IO) {
        val allEntities = trackDao.getAllTracksOnce()
        val pending = allEntities.filter { it.moodTags.isBlank() }
        if (pending.isEmpty()) return@withContext
        val nowMs = System.currentTimeMillis()
        val timeOfDay = TimeOfDayCategory.fromHour(LocalMoodAndAffinityEngine.currentLocalHour(nowMs))
        val maxPlays = allEntities.maxOfOrNull { it.playCount.coerceAtLeast(0) }?.coerceAtLeast(1) ?: 1

        for (entity in pending) {
            val colorArgb = if (entity.artworkColorArgb != 0) {
                entity.artworkColorArgb
            } else {
                artworkCache.extractDominantColorFromPath(entity.artworkPath)
            }
            val (encodedTags, score) = withContext(Dispatchers.Default) {
                val baseTrack = entity.toDomain().copy(artworkColorArgb = colorArgb)
                val tags = LocalMoodAndAffinityEngine.classifyTrackMoods(baseTrack)
                val encoded = SmartMoodTag.encode(tags)
                val enriched = baseTrack.copy(moodTags = SmartMoodTag.decode(encoded))
                val affinity = RecommendationCore.computeAffinityBreakdown(
                    track = enriched,
                    nowMs = nowMs,
                    timeOfDay = timeOfDay,
                    maxLibraryPlays = maxPlays
                ).score
                encoded to affinity
            }
            trackDao.updateTrackIntelligence(
                trackId = entity.id,
                affinityScore = score,
                moodTags = encodedTags,
                artworkColorArgb = colorArgb
            )
        }
    }

    private suspend fun recalculateAndCacheAffinity(trackId: String) {
        val updated = trackDao.getTrackById(trackId) ?: return
        val score = withContext(Dispatchers.Default) {
            RecommendationCore.computeAffinityBreakdown(updated.toDomain()).score
        }
        trackDao.updateAffinityScore(trackId, score)
    }

    suspend fun removeSavedFolder(treeUri: String) = withContext(Dispatchers.IO) {
        folderDao.deleteFolder(treeUri)
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    suspend fun getAllTracksOnce(): List<Track> = withContext(Dispatchers.IO) {
        trackDao.getAllTracksOnce().map { it.toDomain() }
    }

    /**
     * Normalizes a [RawPlaybackEvent] via [SignalProcessor] strictly on [Dispatchers.IO],
     * updates Room telemetry counters & cached [RecommendationCore] affinity, and returns
     * the emitted [PlaybackSignal] (or `null` when filtered as noise).
     */
    suspend fun processPlaybackEvent(event: RawPlaybackEvent): PlaybackSignal? = withContext(Dispatchers.IO) {
        val signal = SignalProcessor.process(event) ?: return@withContext null
        appendRecentSignal(signal)
        when (signal.type) {
            SignalType.COMPLETION_BOOST -> {
                trackDao.recordCompletedPlay(signal.trackId, signal.timestampMs)
            }
            SignalType.EARLY_SKIP_PENALTY -> {
                trackDao.recordSkip(signal.trackId)
            }
            SignalType.MANUAL_FAVORITE_BOOST -> {
                trackDao.setFavorite(signal.trackId, true)
            }
        }
        recalculateAndCacheAffinity(signal.trackId)
        signal
    }

    suspend fun toggleFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val current = trackDao.getTrackById(trackId) ?: return@withContext false
        val newValue = !current.isFavorite
        trackDao.setFavorite(trackId, newValue)
        if (newValue) {
            SignalProcessor.processFavoriteToggle(
                trackId = trackId,
                isFavorite = true,
                trackDurationMs = current.durationMs,
                timestampMs = System.currentTimeMillis()
            )?.let { appendRecentSignal(it) }
        }
        recalculateAndCacheAffinity(trackId)
        newValue
    }

    suspend fun recordTrackPlay(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.recordPlay(trackId, System.currentTimeMillis())
        recalculateAndCacheAffinity(trackId)
    }

    suspend fun recordTrackCompletion(trackId: String) = withContext(Dispatchers.IO) {
        val entity = trackDao.getTrackById(trackId)
        val nowMs = System.currentTimeMillis()
        val dur = entity?.durationMs?.coerceAtLeast(SignalProcessor.MIN_ACTIVE_PLAY_MS)
            ?: (SignalProcessor.MIN_ACTIVE_PLAY_MS * 2L)
        val processed = processPlaybackEvent(
            RawPlaybackEvent(
                trackId = trackId,
                activePlayDurationMs = dur,
                trackDurationMs = dur,
                timestampMs = nowMs
            )
        )
        if (processed == null) {
            trackDao.recordCompletedPlay(trackId, nowMs)
            recalculateAndCacheAffinity(trackId)
        }
    }

    suspend fun recordTrackSkip(trackId: String) = withContext(Dispatchers.IO) {
        val entity = trackDao.getTrackById(trackId)
        val nowMs = System.currentTimeMillis()
        val dur = entity?.durationMs?.takeIf { it >= 30_000L } ?: 180_000L
        val activeMs = SignalProcessor.MIN_ACTIVE_PLAY_MS
        val processed = processPlaybackEvent(
            RawPlaybackEvent(
                trackId = trackId,
                activePlayDurationMs = activeMs,
                trackDurationMs = dur,
                wasUserSkip = true,
                timestampMs = nowMs
            )
        )
        if (processed == null) {
            trackDao.recordSkip(trackId)
            recalculateAndCacheAffinity(trackId)
        }
    }

    suspend fun removeTrack(trackId: String) = withContext(Dispatchers.IO) {
        trackDao.deleteTrackById(trackId)
    }

    // --- Playlists ---

    fun observePlaylistTracks(playlistId: Long): Flow<List<Track>> =
        playlistDao.observePlaylistTracks(playlistId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(Dispatchers.Default)

    suspend fun getPlaylistTracksOnce(playlistId: Long): List<Track> = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistTracksOnce(playlistId).map { it.toDomain() }
    }

    suspend fun createPlaylist(name: String, initialTrackIds: List<String> = emptyList()): Long =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim().ifBlank { "Untitled Playlist" }
            val now = System.currentTimeMillis()
            val id = playlistDao.insertPlaylist(
                PlaylistEntity(name = trimmed, createdAt = now, updatedAt = now)
            )
            if (initialTrackIds.isNotEmpty()) {
                playlistDao.replacePlaylistTracks(id, initialTrackIds)
            }
            id
        }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            playlistDao.renamePlaylist(playlistId, trimmed, System.currentTimeMillis())
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (playlistDao.containsTrack(playlistId, trackId)) {
                return@withContext false
            }
            val nextPos = playlistDao.getMaxPosition(playlistId) + 1
            val now = System.currentTimeMillis()
            playlistDao.insertPlaylistTrack(
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = nextPos,
                    addedAt = now
                )
            )
            playlistDao.touchPlaylist(playlistId, now)
            true
        }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) =
        withContext(Dispatchers.IO) {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            val remaining = playlistDao.getPlaylistTrackIdsOrdered(playlistId)
            playlistDao.replacePlaylistTracks(playlistId, remaining)
        }

    // --- Queue Persistence ---

    suspend fun saveQueueTrackIds(trackIds: List<String>) = withContext(Dispatchers.IO) {
        queueDao.replaceQueue(trackIds)
    }

    suspend fun loadSavedQueueTrackIds(): List<String> = withContext(Dispatchers.IO) {
        queueDao.getQueueTrackIdsOnce()
    }

    // --- Library Grouping & Sorting Helpers ---

    companion object {
        private const val MAX_RECENT_SIGNALS = 64

        fun sortTracks(tracks: List<Track>, order: LibrarySortOrder): List<Track> = when (order) {
            LibrarySortOrder.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
            LibrarySortOrder.ARTIST_ASC -> tracks.sortedWith(
                compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
            )
            LibrarySortOrder.DATE_ADDED_DESC -> tracks.sortedByDescending { it.dateAdded }
            LibrarySortOrder.DURATION_DESC -> tracks.sortedByDescending { it.durationMs }
            LibrarySortOrder.MOST_PLAYED -> tracks.sortedWith(
                compareByDescending<Track> { it.playCount }.thenByDescending { it.lastPlayedAt }
            )
        }

        fun groupAlbums(tracks: List<Track>): List<TrackGroup> =
            tracks.groupBy { it.album.ifBlank { "Unknown Album" } }
                .entries
                .map { (album, list) ->
                    TrackGroup(
                        key = album,
                        title = album,
                        subtitle = list.firstOrNull()?.artist ?: "Various Artists",
                        trackCount = list.size,
                        totalDurationMs = list.sumOf { it.durationMs },
                        coverTrack = list.firstOrNull { it.artworkResId != null || it.artworkPath != null }
                            ?: list.firstOrNull()
                    )
                }
                .sortedBy { it.title.lowercase() }

        fun groupArtists(tracks: List<Track>): List<TrackGroup> =
            tracks.groupBy { it.artist.ifBlank { "Unknown Artist" } }
                .entries
                .map { (artist, list) ->
                    val albumCount = list.map { it.album }.distinct().size
                    TrackGroup(
                        key = artist,
                        title = artist,
                        subtitle = "$albumCount album${if (albumCount == 1) "" else "s"}",
                        trackCount = list.size,
                        totalDurationMs = list.sumOf { it.durationMs },
                        coverTrack = list.firstOrNull { it.artworkResId != null || it.artworkPath != null }
                            ?: list.firstOrNull()
                    )
                }
                .sortedBy { it.title.lowercase() }

        fun groupFolders(tracks: List<Track>): List<TrackGroup> =
            tracks.groupBy { it.folderPath.ifBlank { it.folderName } }
                .entries
                .map { (path, list) ->
                    val name = list.firstOrNull()?.folderName?.ifBlank { path.substringAfterLast('/') } ?: path
                    TrackGroup(
                        key = path,
                        title = name,
                        subtitle = path,
                        trackCount = list.size,
                        totalDurationMs = list.sumOf { it.durationMs },
                        coverTrack = list.firstOrNull { it.artworkResId != null || it.artworkPath != null }
                            ?: list.firstOrNull()
                    )
                }
                .sortedBy { it.title.lowercase() }

        fun groupGenres(tracks: List<Track>): List<TrackGroup> =
            tracks.filter { it.genre.isNotBlank() }
                .groupBy { it.genre.trim() }
                .entries
                .map { (genre, list) ->
                    TrackGroup(
                        key = genre,
                        title = genre,
                        subtitle = "${list.size} track${if (list.size == 1) "" else "s"}",
                        trackCount = list.size,
                        totalDurationMs = list.sumOf { it.durationMs },
                        coverTrack = list.firstOrNull { it.artworkResId != null || it.artworkPath != null }
                            ?: list.firstOrNull()
                    )
                }
                .sortedBy { it.title.lowercase() }
    }
}
