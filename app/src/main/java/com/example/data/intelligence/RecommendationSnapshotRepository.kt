package com.example.data.intelligence

import android.os.Build
import com.example.data.Track
import com.example.data.db.RecommendationSnapshotDao
import com.example.data.db.TrackDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale

/**
 * Repository coordinating Cadence's decoupled [RecommendationCore] inference engine with Room's
 * immutable snapshot persistence layer ([RecommendationSnapshotDao]) off the main thread.
 *
 * Guarantees:
 * - Zero UI-thread I/O: all Room reads/writes execute on [ioDispatcher] and scoring runs on
 *   [computeDispatcher].
 * - Immutable snapshots: once locked for a `YYYY-MM-DD` date key (up to 6 distinct tracks) or
 *   `sessionId` key (up to 10 distinct tracks), subsequent reads or playback events never mutate
 *   or reshuffle the stored track order.
 * - Deleted-file read filter: SQL `INNER JOIN tracks` automatically omits any track ID that no
 *   longer exists in the live MediaStore/Room `tracks` table without modifying remaining tracks.
 */
class RecommendationSnapshotRepository(
    private val snapshotDao: RecommendationSnapshotDao,
    private val trackDao: TrackDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Reactively observes the locked Daily Picks snapshot for [dateKey] (`YYYY-MM-DD`),
     * automatically omitting any track removed from the local `tracks` table.
     */
    fun observeDailyPicks(dateKey: String): Flow<List<Track>> =
        snapshotDao.observeDailyPicksTracks(dateKey)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    /**
     * Returns the locked Daily Picks for [dateKey] (`YYYY-MM-DD`).
     * - If a snapshot is already locked for [dateKey], returns its existing live tracks in exact
     *   locked order without regenerating or mutating the snapshot.
     * - Otherwise computes up to 6 distinct track IDs via [RecommendationCore], persists them
     *   immutably under [dateKey], and returns the resolved tracks.
     */
    suspend fun getOrGenerateDailyPicks(
        dateKey: String,
        nowMs: Long = System.currentTimeMillis(),
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.AFTERNOON,
        signals: List<PlaybackSignal> = emptyList()
    ): List<Track> = withContext(ioDispatcher) {
        if (snapshotDao.hasDailyPicksSnapshot(dateKey)) {
            return@withContext snapshotDao.getDailyPicksTracksOnce(dateKey).map { it.toDomain() }
        }

        val libraryTracks = trackDao.getAllTracksOnce().map { it.toDomain() }
        if (libraryTracks.isEmpty()) return@withContext emptyList()

        val generated = withContext(computeDispatcher) {
            RecommendationCore.generateDailyPicks(
                tracks = libraryTracks,
                signals = signals,
                nowMs = nowMs,
                timeOfDay = timeOfDay
            )
        }
        snapshotDao.lockDailyPicksIfAbsent(
            dateKey = dateKey,
            trackIds = generated.trackIds,
            createdAtMs = nowMs
        )
        snapshotDao.getDailyPicksTracksOnce(dateKey).map { it.toDomain() }
    }

    /**
     * Explicitly locks up to 6 distinct track IDs for [dateKey] if not already locked.
     */
    suspend fun lockDailyPicksIfAbsent(
        dateKey: String,
        trackIds: List<String>,
        createdAtMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(ioDispatcher) {
        snapshotDao.lockDailyPicksIfAbsent(
            dateKey = dateKey,
            trackIds = trackIds,
            createdAtMs = createdAtMs
        )
    }

    /**
     * Reads the current live tracks for an already-locked Daily Picks snapshot without mutating it.
     */
    suspend fun loadDailyPicksOnce(dateKey: String): List<Track> = withContext(ioDispatcher) {
        snapshotDao.getDailyPicksTracksOnce(dateKey).map { it.toDomain() }
    }

    /**
     * Locks up to 6 distinct Daily Picks for [dateKey] (`YYYY-MM-DD`) once per calendar day using
     * [RecommendationCore.generateDailyPicks] and returns the live Room tracks in exact locked order.
     *
     * Subsequent calls on the same [dateKey] (e.g., after play, pause, skip, or favorite updates)
     * NEVER mutate, reorder, remove, or append tracks; deleted files are automatically excluded by
     * SQL `INNER JOIN tracks`, and optional [eligibleTracks] preserves minimum-duration filtering.
     */
    suspend fun ensureDailyPicksLocked(
        dateKey: String,
        eligibleTracks: List<Track>,
        epochDay: Long = currentLocalEpochDay(),
        nowMs: Long = System.currentTimeMillis(),
        signals: List<PlaybackSignal> = emptyList()
    ): List<Track> = withContext(ioDispatcher) {
        if (eligibleTracks.isEmpty()) return@withContext emptyList()
        val eligibleIds = eligibleTracks.mapTo(HashSet(eligibleTracks.size * 2)) { it.id }

        if (snapshotDao.hasDailyPicksSnapshot(dateKey)) {
            return@withContext snapshotDao.getDailyPicksTracksOnce(dateKey)
                .map { it.toDomain() }
                .filter { it.id in eligibleIds }
        }

        val generated = withContext(computeDispatcher) {
            RecommendationCore.generateDailyPicks(
                tracks = eligibleTracks,
                signals = signals,
                nowMs = nowMs,
                timeOfDay = TimeOfDayCategory.AFTERNOON,
                epochDay = epochDay,
                targetSize = RecommendationCore.DAILY_PICKS_MAX_SIZE
            )
        }
        snapshotDao.lockDailyPicksIfAbsent(
            dateKey = dateKey,
            trackIds = generated.trackIds,
            createdAtMs = nowMs
        )
        snapshotDao.getDailyPicksTracksOnce(dateKey)
            .map { it.toDomain() }
            .filter { it.id in eligibleIds }
    }

    /**
     * Reactively observes the locked Context-Aware / Mood Session snapshot for [sessionId],
     * automatically omitting any track removed from the local `tracks` table.
     */
    fun observeSessionSnapshot(sessionId: String): Flow<List<Track>> =
        snapshotDao.observeSessionTracks(sessionId)
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    /**
     * Returns the locked Context-Aware / Mood Session tracks for [sessionId].
     * - If [sessionId] is already locked in Room, returns its existing live tracks in exact locked
     *   order without mutating or reshuffling.
     * - Otherwise computes up to 10 distinct track IDs via [RecommendationCore], persists them
     *   immutably under [sessionId], and returns the resolved tracks.
     */
    suspend fun getOrCreateSessionSnapshot(
        sessionId: String,
        contextKey: String,
        nowMs: Long = System.currentTimeMillis(),
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.fromHour(
            LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
        ),
        signals: List<PlaybackSignal> = emptyList()
    ): List<Track> = withContext(ioDispatcher) {
        if (snapshotDao.hasSessionSnapshot(sessionId)) {
            return@withContext snapshotDao.getSessionTracksOnce(sessionId).map { it.toDomain() }
        }

        val libraryTracks = trackDao.getAllTracksOnce().map { it.toDomain() }
        if (libraryTracks.isEmpty()) return@withContext emptyList()

        val generated = withContext(computeDispatcher) {
            RecommendationCore.generateSessionMix(
                tracks = libraryTracks,
                signals = signals,
                nowMs = nowMs,
                timeOfDay = timeOfDay
            )
        }
        snapshotDao.lockSessionIfAbsent(
            sessionId = sessionId,
            contextKey = contextKey,
            trackIds = generated.trackIds,
            createdAtMs = nowMs
        )
        snapshotDao.getSessionTracksOnce(sessionId).map { it.toDomain() }
    }

    /**
     * Locks up to 10 distinct [candidateTrackIds] for [sessionId] if not already locked, and returns
     * the live Room tracks in exact locked order. Never mutates or reshuffles an already-locked session.
     */
    suspend fun ensureSessionLocked(
        sessionId: String,
        contextKey: String,
        candidateTrackIds: List<String>,
        eligibleTrackIds: Set<String>? = null,
        nowMs: Long = System.currentTimeMillis()
    ): List<Track> = withContext(ioDispatcher) {
        if (snapshotDao.hasSessionSnapshot(sessionId)) {
            val existing = snapshotDao.getSessionTracksOnce(sessionId).map { it.toDomain() }
            return@withContext if (eligibleTrackIds != null) {
                existing.filter { it.id in eligibleTrackIds }
            } else {
                existing
            }
        }
        val capped = candidateTrackIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE)
            .toList()
        if (capped.isEmpty()) return@withContext emptyList()

        snapshotDao.lockSessionIfAbsent(
            sessionId = sessionId,
            contextKey = contextKey,
            trackIds = capped,
            createdAtMs = nowMs
        )
        val locked = snapshotDao.getSessionTracksOnce(sessionId).map { it.toDomain() }
        if (eligibleTrackIds != null) {
            locked.filter { it.id in eligibleTrackIds }
        } else {
            locked
        }
    }

    /**
     * Generates a fresh 10-track Tune Mix session via [RecommendationCore], locks it immutably in
     * Room under [sessionId], and returns both the locked tracks and structured [MusicIntelligenceOutput].
     */
    suspend fun createLockedTunedSession(
        sessionId: String,
        contextKey: String,
        eligibleTracks: List<Track>,
        tuneConfig: DiscoverTuneConfig,
        seedTrackId: String? = null,
        excludedTrackIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
        timeOfDay: TimeOfDayCategory = TimeOfDayCategory.fromHour(
            LocalMoodAndAffinityEngine.currentLocalHour(nowMs)
        ),
        signals: List<PlaybackSignal> = emptyList()
    ): Pair<List<Track>, MusicIntelligenceOutput> = withContext(ioDispatcher) {
        val clean = eligibleTracks.filter { it.id.isNotBlank() }.distinctBy { it.id }
        if (clean.isEmpty()) {
            val emptyOutput = RecommendationCore.buildTunedSmartMixOutput(
                orderedTracks = emptyList(),
                scoresByTrackId = emptyMap(),
                tuneConfig = tuneConfig,
                seedTrack = null,
                timeOfDay = timeOfDay
            )
            return@withContext emptyList<Track>() to emptyOutput
        }

        val generated = withContext(computeDispatcher) {
            RecommendationCore.generateSessionMix(
                tracks = clean,
                signals = signals,
                nowMs = nowMs,
                timeOfDay = timeOfDay,
                tuneConfig = tuneConfig,
                seedTrackId = seedTrackId,
                excludedTrackIds = excludedTrackIds,
                targetSize = RecommendationCore.SESSION_SNAPSHOT_MAX_SIZE
            )
        }
        snapshotDao.lockSessionIfAbsent(
            sessionId = sessionId,
            contextKey = contextKey,
            trackIds = generated.trackIds,
            createdAtMs = nowMs
        )
        val eligibleIds = clean.mapTo(HashSet(clean.size * 2)) { it.id }
        val lockedTracks = snapshotDao.getSessionTracksOnce(sessionId)
            .map { it.toDomain() }
            .filter { it.id in eligibleIds }
        val seedTrack = seedTrackId?.let { id -> clean.firstOrNull { it.id == id } }
        val output = withContext(computeDispatcher) {
            RecommendationCore.buildTunedSmartMixOutput(
                orderedTracks = lockedTracks,
                scoresByTrackId = generated.scoresByTrackId,
                tuneConfig = tuneConfig,
                seedTrack = seedTrack,
                timeOfDay = timeOfDay
            )
        }
        lockedTracks to output
    }

    /**
     * Explicitly locks up to 10 distinct track IDs for [sessionId] if not already locked.
     */
    suspend fun lockSessionSnapshotIfAbsent(
        sessionId: String,
        contextKey: String,
        trackIds: List<String>,
        createdAtMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(ioDispatcher) {
        snapshotDao.lockSessionIfAbsent(
            sessionId = sessionId,
            contextKey = contextKey,
            trackIds = trackIds,
            createdAtMs = createdAtMs
        )
    }

    /**
     * Reads the current live tracks for an already-locked Session snapshot without mutating it.
     */
    suspend fun loadSessionSnapshotOnce(sessionId: String): List<Track> = withContext(ioDispatcher) {
        snapshotDao.getSessionTracksOnce(sessionId).map { it.toDomain() }
    }

    companion object {
        /**
         * Formats [nowMs] into a deterministic local calendar key (`YYYY-MM-DD`).
         */
        fun formatDateKey(nowMs: Long = System.currentTimeMillis()): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val localDate = Instant.ofEpochMilli(nowMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                return String.format(
                    Locale.US,
                    "%04d-%02d-%02d",
                    localDate.year,
                    localDate.monthValue,
                    localDate.dayOfMonth
                )
            }
            val cal = Calendar.getInstance()
            cal.timeInMillis = nowMs
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        }
    }
}
