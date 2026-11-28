package com.example

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesRepository
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.TrackEntity
import com.example.data.intelligence.DiscoverCategory
import com.example.data.intelligence.PlaybackSignal
import com.example.data.intelligence.RawPlaybackEvent
import com.example.data.intelligence.RecommendationCore
import com.example.data.intelligence.RecommendationSnapshotRepository
import com.example.data.intelligence.RoutineMixType
import com.example.data.intelligence.SignalProcessor
import com.example.data.intelligence.SignalType
import com.example.data.intelligence.SmartMoodTag
import com.example.data.intelligence.TimeOfDayCategory
import com.example.data.intelligence.TrackFeatureMatrix
import com.example.data.library.LibraryRepository
import com.example.data.toStableList
import com.example.ui.player.PlayerViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecommendationCoreAndSnapshotTest {

    private lateinit var context: Context
    private lateinit var db: CadenceDatabase
    private lateinit var snapshotRepository: RecommendationSnapshotRepository

    private val anchorNowMs = 1_780_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        snapshotRepository = RecommendationSnapshotRepository(
            snapshotDao = db.recommendationSnapshotDao(),
            trackDao = db.trackDao(),
            ioDispatcher = Dispatchers.Unconfined,
            computeDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // 1. SignalProcessor Hard Boundaries
    // -------------------------------------------------------------------------

    @Test
    fun `SignalProcessor ignores scrub jumps and brief accidental taps under 5 seconds`() {
        // Brief taps (< 5_000ms) must be dropped completely
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "t1",
                    activePlayDurationMs = 0L,
                    trackDurationMs = 200_000L,
                    wasUserSkip = true,
                    timestampMs = anchorNowMs
                )
            )
        )
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "t1",
                    activePlayDurationMs = 4_999L,
                    trackDurationMs = 200_000L,
                    wasUserSkip = true,
                    timestampMs = anchorNowMs
                )
            )
        )

        // Mid-track scrub jumps must be ignored even when duration >= 5_000ms
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "t1",
                    activePlayDurationMs = 180_000L,
                    trackDurationMs = 200_000L,
                    wasScrubJump = true,
                    wasUserSkip = false,
                    timestampMs = anchorNowMs
                )
            )
        )
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "t1",
                    activePlayDurationMs = 12_000L,
                    trackDurationMs = 200_000L,
                    wasScrubJump = true,
                    wasUserSkip = true,
                    timestampMs = anchorNowMs
                )
            )
        )
    }

    @Test
    fun `SignalProcessor applies early-skip penalty under 25 percent and caps threshold at 45 seconds`() {
        // 2-minute track (120s): 25% = 30_000ms (< 45_000ms cap)
        assertEquals(30_000L, SignalProcessor.earlySkipCutoffMs(120_000L))
        val shortTrackEarlySkip = SignalProcessor.process(
            RawPlaybackEvent(
                trackId = "short_1",
                activePlayDurationMs = 15_000L,
                trackDurationMs = 120_000L,
                wasUserSkip = true,
                timestampMs = anchorNowMs
            )
        )
        assertNotNull(shortTrackEarlySkip)
        assertEquals(SignalType.EARLY_SKIP_PENALTY, shortTrackEarlySkip!!.type)
        assertTrue(shortTrackEarlySkip.affinityDelta < 0f)

        // Skipping at 30_000ms (at/past 25% cutoff) is NOT penalized
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "short_1",
                    activePlayDurationMs = 30_000L,
                    trackDurationMs = 120_000L,
                    wasUserSkip = true,
                    timestampMs = anchorNowMs
                )
            )
        )

        // 10-minute long mix (600s): 25% would be 150s, strictly capped at 45_000ms
        assertEquals(45_000L, SignalProcessor.earlySkipCutoffMs(600_000L))
        val longMixEarlySkip = SignalProcessor.process(
            RawPlaybackEvent(
                trackId = "long_mix",
                activePlayDurationMs = 30_000L,
                trackDurationMs = 600_000L,
                wasUserSkip = true,
                timestampMs = anchorNowMs
            )
        )
        assertNotNull(longMixEarlySkip)
        assertEquals(SignalType.EARLY_SKIP_PENALTY, longMixEarlySkip!!.type)

        // Listening for 50s (< 25% of 600s, but >= 45s cap) must NOT be penalized
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "long_mix",
                    activePlayDurationMs = 50_000L,
                    trackDurationMs = 600_000L,
                    wasUserSkip = true,
                    timestampMs = anchorNowMs
                )
            )
        )
    }

    @Test
    fun `SignalProcessor boosts completions past 80 percent and applies immediate manual favorite boost`() {
        // Exactly 80% is not strictly past 80%
        assertNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "t_complete",
                    activePlayDurationMs = 160_000L,
                    trackDurationMs = 200_000L,
                    timestampMs = anchorNowMs
                )
            )
        )

        // 85% completion triggers positive COMPLETION_BOOST
        val completed = SignalProcessor.process(
            RawPlaybackEvent(
                trackId = "t_complete",
                activePlayDurationMs = 170_000L,
                trackDurationMs = 200_000L,
                timestampMs = anchorNowMs
            )
        )
        assertNotNull(completed)
        assertEquals(SignalType.COMPLETION_BOOST, completed!!.type)
        assertTrue(completed.affinityDelta > 0f)
        assertEquals(0.85f, completed.completionRatio, 0.001f)

        // Manual favorite fires immediately with high weight even at 0ms active listen
        val favBoost = SignalProcessor.processFavoriteToggle(
            trackId = "t_fav",
            isFavorite = true,
            trackDurationMs = 240_000L,
            activePlayDurationMs = 0L,
            timestampMs = anchorNowMs
        )
        assertNotNull(favBoost)
        assertEquals(SignalType.MANUAL_FAVORITE_BOOST, favBoost!!.type)
        assertTrue(favBoost.affinityDelta > completed.affinityDelta)

        // Unfavoriting does not emit a bogus signal
        assertNull(
            SignalProcessor.processFavoriteToggle(
                trackId = "t_fav",
                isFavorite = false,
                timestampMs = anchorNowMs
            )
        )
    }

    // -------------------------------------------------------------------------
    // 2. RecommendationCore Determinism & Primitive Array Scoring
    // -------------------------------------------------------------------------

    @Test
    fun `RecommendationCore produces bit-for-bit identical deterministic scores and track orders`() {
        val library = buildMixedTestLibrary(anchorNowMs)
        val matrix = TrackFeatureMatrix.fromTracks(library)
        val signals = listOfNotNull(
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "fav_1",
                    activePlayDurationMs = 195_000L,
                    trackDurationMs = 210_000L,
                    timestampMs = anchorNowMs - 48L * RecommendationCore.ONE_HOUR_MS
                )
            ),
            SignalProcessor.process(
                RawPlaybackEvent(
                    trackId = "under_1",
                    activePlayDurationMs = 12_000L,
                    trackDurationMs = 200_000L,
                    wasUserSkip = true,
                    timestampMs = anchorNowMs - 2L * RecommendationCore.ONE_HOUR_MS
                )
            ),
            SignalProcessor.processFavoriteToggle(
                trackId = "gem_2",
                isFavorite = true,
                timestampMs = anchorNowMs - 30L * RecommendationCore.ONE_DAY_MS
            )
        )

        val scoresRun1 = RecommendationCore.computeAffinityScores(
            matrix = matrix,
            signals = signals,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        )
        val scoresRun2 = RecommendationCore.computeAffinityScores(
            matrix = matrix,
            signals = signals,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        )
        assertArrayEquals(scoresRun1, scoresRun2, 0.0f)
        for (score in scoresRun1) {
            assertTrue("Score must be normalized in 0.0..1.0, was $score", score in 0.0f..1.0f)
        }

        val mixRun1 = RecommendationCore.generatePlaylist(
            matrix = matrix,
            targetSize = 10,
            signals = signals,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        )
        val mixRun2 = RecommendationCore.generatePlaylist(
            matrix = matrix,
            targetSize = 10,
            signals = signals,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        )
        assertEquals(mixRun1.trackIds, mixRun2.trackIds)
        assertEquals(mixRun1.scoresByTrackId, mixRun2.scoresByTrackId)
        assertEquals(10, mixRun1.trackIds.size)
        assertEquals(10, mixRun1.trackIds.toSet().size)

        // Verify time-of-day category weighting shifts calm vs upbeat scores appropriately
        val calmTrack = makeTrack(
            id = "calm_only",
            bpm = 74,
            genre = "Ambient",
            moodTags = listOf(SmartMoodTag.LATE_NIGHT)
        )
        val upbeatTrack = makeTrack(
            id = "upbeat_only",
            bpm = 128,
            genre = "Dance",
            moodTags = listOf(SmartMoodTag.UPBEAT)
        )
        val todMatrix = TrackFeatureMatrix.fromTracks(listOf(calmTrack, upbeatTrack))
        val lateNightScores = RecommendationCore.computeAffinityScores(
            matrix = todMatrix,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        )
        val afternoonScores = RecommendationCore.computeAffinityScores(
            matrix = todMatrix,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.AFTERNOON
        )
        assertTrue("Calm track should outrank upbeat track late at night", lateNightScores[0] > lateNightScores[1])
        assertTrue("Upbeat track should outrank calm track in the afternoon", afternoonScores[1] > afternoonScores[0])
    }

    // -------------------------------------------------------------------------
    // 3. Waterfall Bucket Distribution (60/25/15, Cold-Start & Small Library)
    // -------------------------------------------------------------------------

    @Test
    fun `RecommendationCore hits 60-25-15 ratio on rich libraries and safely falls back on cold start or small libraries`() {
        // A. Rich library (24 tracks: 10 favorites, 8 forgotten gems, 6 under-played)
        val richLibrary = buildMixedTestLibrary(anchorNowMs)
        val richMix = RecommendationCore.generatePlaylist(
            tracks = richLibrary,
            targetSize = 20,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.EVENING
        )
        assertEquals(20, richMix.trackIds.size)
        assertEquals("Must contain zero duplicates", 20, richMix.trackIds.toSet().size)
        assertEquals("60% of 20 = 12 favorites", 12, richMix.favoritesCount)
        assertEquals("25% of 20 = 5 forgotten gems", 5, richMix.forgottenGemsCount)
        assertEquals("15% of 20 = 3 under-played", 3, richMix.underPlayedCount)
        assertEquals(0, richMix.waterfallFallbackCount)

        // B. Cold start with zero listening history (all tracks playCount = 0, lastPlayedAt = 0)
        val coldStartLibrary = (1..12).map { i ->
            makeTrack(
                id = "cold_$i",
                playCount = 0,
                completedPlayCount = 0,
                skipCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false
            )
        }
        val coldMix = RecommendationCore.generatePlaylist(
            tracks = coldStartLibrary,
            targetSize = 10,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.MORNING
        )
        assertEquals("Cold start must fill all 10 slots via waterfall fallback", 10, coldMix.trackIds.size)
        assertEquals("Cold start must have zero duplicates", 10, coldMix.trackIds.toSet().size)
        assertTrue("Waterfall fallback must backfill empty favorite/forgotten buckets", coldMix.waterfallFallbackCount > 0)

        // C. Small libraries (< 10 tracks) & empty library
        val smallLibrary = (1..4).map { i -> makeTrack(id = "small_$i") }
        val smallMix = RecommendationCore.generatePlaylist(
            tracks = smallLibrary,
            targetSize = 10,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.AFTERNOON
        )
        assertEquals("Output must cap safely at min(targetSize, availableValidTracks)", 4, smallMix.trackIds.size)
        assertEquals(4, smallMix.trackIds.toSet().size)

        val emptyMix = RecommendationCore.generatePlaylist(
            tracks = emptyList(),
            targetSize = 10,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.AFTERNOON
        )
        assertTrue(emptyMix.trackIds.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 4. Immutable Room Snapshots, Deleted-File Read Filter & Safe MIGRATION_4_5
    // -------------------------------------------------------------------------

    @Test
    fun `Locked Daily Picks and Session snapshots enforce size limits, stay immutable, and filter deleted tracks on read`() = runTest {
        val library = buildMixedTestLibrary(anchorNowMs)
        db.trackDao().upsertTracks(library.map { TrackEntity.fromDomain(it) })

        val dateKey = "2026-11-26"
        val initialDaily = snapshotRepository.getOrGenerateDailyPicks(
            dateKey = dateKey,
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.AFTERNOON
        )
        assertEquals("Daily Picks must store at most 6 distinct tracks", 6, initialDaily.size)
        val initialDailyIds = initialDaily.map { it.id }
        assertEquals(6, initialDailyIds.toSet().size)

        // Attempting to re-lock or re-generate for the same calendar day must NOT mutate or reshuffle
        val attemptedRelock = snapshotRepository.lockDailyPicksIfAbsent(
            dateKey = dateKey,
            trackIds = library.reversed().map { it.id },
            createdAtMs = anchorNowMs + 60_000L
        )
        assertFalse("Existing Daily Picks snapshot must remain immutable", attemptedRelock)
        val reloadedDailyIds = snapshotRepository.getOrGenerateDailyPicks(
            dateKey = dateKey,
            nowMs = anchorNowMs + 3_600_000L,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        ).map { it.id }
        assertEquals(initialDailyIds, reloadedDailyIds)

        // Lock a Context-Aware Session snapshot (up to 10 distinct tracks)
        val sessionId = "session_late_night_001"
        val initialSession = snapshotRepository.getOrCreateSessionSnapshot(
            sessionId = sessionId,
            contextKey = "mood:late_night",
            nowMs = anchorNowMs,
            timeOfDay = TimeOfDayCategory.LATE_NIGHT
        )
        assertEquals("Session snapshot must store at most 10 distinct tracks", 10, initialSession.size)
        val initialSessionIds = initialSession.map { it.id }
        assertEquals(10, initialSessionIds.toSet().size)

        // Delete track at index 1 from Daily Picks and track at index 2 from Session in Room `tracks` table
        val deletedDailyId = initialDailyIds[1]
        val deletedSessionId = initialSessionIds[2]
        db.trackDao().deleteTrackById(deletedDailyId)
        if (deletedSessionId != deletedDailyId) {
            db.trackDao().deleteTrackById(deletedSessionId)
        }

        val deletedSet = setOf(deletedDailyId, deletedSessionId)

        // Reading locked Daily Picks via both suspend API and reactive Flow must filter deleted tracks
        // while preserving exact locked ordering of remaining tracks
        val expectedRemainingDaily = initialDailyIds.filterNot { it in deletedSet }
        val filteredDailyOnce = snapshotRepository.loadDailyPicksOnce(dateKey).map { it.id }
        val filteredDailyFlow = snapshotRepository.observeDailyPicks(dateKey).first().map { it.id }
        assertEquals(expectedRemainingDaily, filteredDailyOnce)
        assertEquals(expectedRemainingDaily, filteredDailyFlow)

        // Underlying stored snapshot rows must NOT have been mutated on read or deletion
        val rawStoredDailyIds = db.recommendationSnapshotDao().getRawDailyPicksTrackIds(dateKey)
        assertEquals(initialDailyIds, rawStoredDailyIds)

        // Reading locked Session snapshot must similarly filter deleted tracks without mutating remaining items
        val expectedRemainingSession = initialSessionIds.filterNot { it in deletedSet }
        val filteredSessionOnce = snapshotRepository.loadSessionSnapshotOnce(sessionId).map { it.id }
        val filteredSessionFlow = snapshotRepository.observeSessionSnapshot(sessionId).first().map { it.id }
        assertEquals(expectedRemainingSession, filteredSessionOnce)
        assertEquals(expectedRemainingSession, filteredSessionFlow)

        val rawStoredSessionIds = db.recommendationSnapshotDao().getRawSessionTrackIds(sessionId)
        assertEquals(initialSessionIds, rawStoredSessionIds)
    }

    @Test
    fun `MIGRATION_4_5 upgrades existing v4 database cleanly without data loss or schema mismatch`() = runTest {
        val dbName = "migration_4_5_verification.db"
        context.deleteDatabase(dbName)
        val dbFile: File = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Construct an authentic v4 SQLite schema containing an existing user track
        val helperConfig = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `tracks` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `artist` TEXT NOT NULL,
                            `album` TEXT NOT NULL,
                            `genre` TEXT NOT NULL,
                            `durationMs` INTEGER NOT NULL,
                            `bpm` INTEGER NOT NULL,
                            `artworkResId` INTEGER,
                            `artworkPath` TEXT,
                            `localUriString` TEXT,
                            `folderName` TEXT NOT NULL,
                            `folderPath` TEXT NOT NULL,
                            `mimeType` TEXT NOT NULL,
                            `codec` TEXT NOT NULL,
                            `bitrateKbps` INTEGER NOT NULL,
                            `sampleRateHz` INTEGER NOT NULL,
                            `bitDepth` INTEGER NOT NULL,
                            `fileSizeBytes` INTEGER NOT NULL,
                            `dateAdded` INTEGER NOT NULL,
                            `playCount` INTEGER NOT NULL,
                            `completedPlayCount` INTEGER NOT NULL,
                            `skipCount` INTEGER NOT NULL,
                            `lastPlayedAt` INTEGER NOT NULL,
                            `isFavorite` INTEGER NOT NULL,
                            `synthProfileName` TEXT,
                            `suggestedPaletteId` TEXT NOT NULL,
                            `isUserImported` INTEGER NOT NULL,
                            `dedupeKey` TEXT NOT NULL,
                            `affinityScore` REAL NOT NULL,
                            `moodTags` TEXT NOT NULL,
                            `artworkColorArgb` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_title` ON `tracks` (`title`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_artist` ON `tracks` (`artist`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_album` ON `tracks` (`album`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_folderPath` ON `tracks` (`folderPath`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_genre` ON `tracks` (`genre`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_dedupeKey` ON `tracks` (`dedupeKey`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_dateAdded` ON `tracks` (`dateAdded`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_playCount` ON `tracks` (`playCount`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_lastPlayedAt` ON `tracks` (`lastPlayedAt`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `playlists` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                            `playlistId` INTEGER NOT NULL,
                            `trackId` TEXT NOT NULL,
                            `position` INTEGER NOT NULL,
                            `addedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`playlistId`, `position`),
                            FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` ON `playlist_tracks` (`playlistId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `queue_items` (
                            `position` INTEGER NOT NULL,
                            `trackId` TEXT NOT NULL,
                            PRIMARY KEY(`position`)
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_queue_items_trackId` ON `queue_items` (`trackId`)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `saved_folders` (
                            `treeUri` TEXT NOT NULL,
                            `displayName` TEXT NOT NULL,
                            `lastScannedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`treeUri`)
                        )
                        """.trimIndent()
                    )

                    db.execSQL(
                        """
                        INSERT INTO tracks (
                            id, title, artist, album, genre, durationMs, bpm, artworkResId, artworkPath,
                            localUriString, folderName, folderPath, mimeType, codec, bitrateKbps, sampleRateHz,
                            bitDepth, fileSizeBytes, dateAdded, playCount, completedPlayCount, skipCount,
                            lastPlayedAt, isFavorite, synthProfileName, suggestedPaletteId, isUserImported,
                            dedupeKey, affinityScore, moodTags, artworkColorArgb
                        ) VALUES (
                            'legacy_v4_track', 'Amber Echoes', 'Solis', 'Evening Tape', 'Soul', 215000, 92, NULL, NULL,
                            'content://media/external/audio/media/42', 'Music', 'music', 'audio/flac', 'FLAC', 980, 44100,
                            16, 25000000, 1700000000000, 5, 4, 0,
                            1779000000000, 1, NULL, 'terracotta', 1,
                            'amber echoes|solis|107', 0.82, 'soulful', 0
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val rawHelper = FrameworkSQLiteOpenHelperFactory().create(helperConfig)
        rawHelper.writableDatabase.close()
        rawHelper.close()

        // 2. Open with Room at version 5 using MIGRATION_4_5 (strictly WITHOUT destructive fallback!)
        val migratedDb = Room.databaseBuilder(context, CadenceDatabase::class.java, dbName)
            .addMigrations(CadenceDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        try {
            val preservedTrack = migratedDb.trackDao().getTrackById("legacy_v4_track")
            assertNotNull("Existing v4 track must survive MIGRATION_4_5 intact", preservedTrack)
            assertEquals("Amber Echoes", preservedTrack!!.title)

            // Verify new snapshot tables are live and pass strict Room validation
            val locked = migratedDb.recommendationSnapshotDao().lockDailyPicksIfAbsent(
                dateKey = "2026-11-26",
                trackIds = listOf("legacy_v4_track", "missing_deleted_id"),
                createdAtMs = anchorNowMs
            )
            assertTrue(locked)
            val resolved = migratedDb.recommendationSnapshotDao().getDailyPicksTracksOnce("2026-11-26")
            assertEquals(listOf("legacy_v4_track"), resolved.map { it.id })
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    // -------------------------------------------------------------------------
    // 5. PlayerViewModel Integration: Locked 6-Track Daily Picks & 10-Track Discover Mixes
    // -------------------------------------------------------------------------

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `PlayerViewModel Daily Picks stays strictly locked at 6 tracks across play skip and favorite events`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var vm: PlayerViewModel? = null
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val prefsRepo = PreferencesRepository(app)
            val libraryRepo = LibraryRepository(app, prefsRepo, injectedDatabase = db)
            val library = buildMixedTestLibrary(System.currentTimeMillis())
            db.trackDao().upsertTracks(library.map { TrackEntity.fromDomain(it) })

            val createdVm = PlayerViewModel.createForTesting(app, prefsRepo, libraryRepo)
            vm = createdVm
            val initialHome = withTimeout(10_000L) {
                createdVm.homeState.first { it.isInitialLibraryLoaded && it.dailyPicks.size == 6 }
            }
            val lockedIds = initialHome.dailyPicks.map { it.id }
            assertEquals("Daily Picks must lock strictly at 6 tracks", 6, lockedIds.size)
            assertEquals("Daily Picks must have 6 distinct tracks", 6, lockedIds.toSet().size)

            val track0 = initialHome.dailyPicks[0]
            val track1 = initialHome.dailyPicks[1]
            val track2 = initialHome.dailyPicks[2]
            val track3 = initialHome.dailyPicks[3]
            val nowMs = System.currentTimeMillis()

            // 1. Completion (> 80%) on track 0 via SignalProcessor
            val compSignal = createdVm.recordPlaybackSignalForTest(
                RawPlaybackEvent(
                    trackId = track0.id,
                    activePlayDurationMs = 190_000L,
                    trackDurationMs = 210_000L,
                    timestampMs = nowMs
                )
            )
            assertEquals(SignalType.COMPLETION_BOOST, compSignal?.type)

            // 2. Early skip (< min(25%, 45s)) on track 1 via SignalProcessor
            val skipSignal = createdVm.recordPlaybackSignalForTest(
                RawPlaybackEvent(
                    trackId = track1.id,
                    activePlayDurationMs = 12_000L,
                    trackDurationMs = 210_000L,
                    wasUserSkip = true,
                    timestampMs = nowMs + 1_000L
                )
            )
            assertEquals(SignalType.EARLY_SKIP_PENALTY, skipSignal?.type)

            // 3. Noise events (scrub jump + <5s accidental tap) on track 2 must be filtered completely
            assertNull(
                createdVm.recordPlaybackSignalForTest(
                    RawPlaybackEvent(
                        trackId = track2.id,
                        activePlayDurationMs = 185_000L,
                        trackDurationMs = 210_000L,
                        wasScrubJump = true,
                        timestampMs = nowMs + 2_000L
                    )
                )
            )
            assertNull(
                createdVm.recordPlaybackSignalForTest(
                    RawPlaybackEvent(
                        trackId = track2.id,
                        activePlayDurationMs = 3_000L,
                        trackDurationMs = 210_000L,
                        wasUserSkip = true,
                        timestampMs = nowMs + 3_000L
                    )
                )
            )

            // 4. Toggle favorite on track 3
            val expectedFav = !track3.isFavorite
            libraryRepo.toggleFavorite(track3.id)

            // Wait for homeState to reflect live updated metadata on the locked Daily Picks
            val afterEventsHome = withTimeout(10_000L) {
                createdVm.homeState.first { state ->
                    val updated0 = state.dailyPicks.firstOrNull { it.id == track0.id }
                    val updated1 = state.dailyPicks.firstOrNull { it.id == track1.id }
                    val updated3 = state.dailyPicks.firstOrNull { it.id == track3.id }
                    updated0 != null &&
                        updated0.completedPlayCount == track0.completedPlayCount + 1 &&
                        updated1 != null &&
                        updated1.skipCount == track1.skipCount + 1 &&
                        updated3 != null &&
                        updated3.isFavorite == expectedFav
                }
            }

            // Daily Picks MUST remain strictly locked at the exact same 6 track IDs in exact same order
            assertEquals(lockedIds, afterEventsHome.dailyPicks.map { it.id })
            // Noise events must not have incremented track2 counters
            val afterTrack2 = afterEventsHome.dailyPicks.first { it.id == track2.id }
            assertEquals(track2.completedPlayCount, afterTrack2.completedPlayCount)
            assertEquals(track2.skipCount, afterTrack2.skipCount)

            // 5. Deleting a track from Room removes only that dead file without reshuffling or replacing it
            val deletedId = lockedIds[2]
            db.trackDao().deleteTrackById(deletedId)
            val afterDeleteHome = withTimeout(10_000L) {
                createdVm.homeState.first { state -> state.dailyPicks.none { it.id == deletedId } }
            }
            assertEquals(lockedIds.filterNot { it == deletedId }, afterDeleteHome.dailyPicks.map { it.id })
        } finally {
            vm?.clearAndJoinForTest()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `PlayerViewModel Discover Routine Mood and Tune Mixes stay strictly locked at 10 tracks during playback`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        var vm: PlayerViewModel? = null
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val prefsRepo = PreferencesRepository(app)
            val libraryRepo = LibraryRepository(app, prefsRepo, injectedDatabase = db)
            val library = buildMultiMoodLargeLibrary(System.currentTimeMillis())
            db.trackDao().upsertTracks(library.map { TrackEntity.fromDomain(it) })

            val createdVm = PlayerViewModel.createForTesting(app, prefsRepo, libraryRepo)
            vm = createdVm
            val initialDiscover = withTimeout(10_000L) {
                createdVm.discoverState.first { state ->
                    state.isInitialLibraryLoaded &&
                        state.routineMixes.isNotEmpty() &&
                        state.moodPlaylists.isNotEmpty() &&
                        state.sections.any { it.category == DiscoverCategory.MADE_FOR_YOU } &&
                        state.sections.any { it.category == DiscoverCategory.SMART_MIX }
                }
            }

            val lockedRoutineIds = initialDiscover.routineMixes.associate { it.type to it.tracks.map { t -> t.id } }
            val lockedMoodIds = initialDiscover.moodPlaylists.associate { it.mood to it.tracks.map { t -> t.id } }
            val lockedMadeForYouIds = initialDiscover.sections
                .first { it.category == DiscoverCategory.MADE_FOR_YOU }
                .tracks.map { it.id }
            val lockedSmartMixIds = initialDiscover.sections
                .first { it.category == DiscoverCategory.SMART_MIX }
                .tracks.map { it.id }

            // Every Routine Mix, Mood Playlist, Made for You, and Smart Mix must lock at 10 distinct tracks
            lockedRoutineIds.forEach { (type, ids) ->
                assertEquals("Routine mix $type must lock at 10 tracks", 10, ids.size)
                assertEquals("Routine mix $type must contain distinct tracks", 10, ids.toSet().size)
            }
            lockedMoodIds.forEach { (mood, ids) ->
                assertEquals("Mood playlist $mood must lock at 10 tracks", 10, ids.size)
                assertEquals("Mood playlist $mood must contain distinct tracks", 10, ids.toSet().size)
            }
            assertEquals("Made for You must lock at 10 tracks", 10, lockedMadeForYouIds.size)
            assertEquals("Smart Mix must lock at 10 tracks", 10, lockedSmartMixIds.size)

            val initialCompletedTotal = initialDiscover.insights.completedPlaysTotal
            assertTrue("Listening Insights must compute Hi-Fi percentage", initialDiscover.insights.hiFiPercentage > 0)
            assertTrue("Listening Insights must resolve top mood", initialDiscover.insights.topMood.isNotBlank())

            // Simulate active playback telemetry (completions >80%, early skips <25%, favorite toggle)
            // directly on tracks inside the active Routine Mix & Mood Playlist
            val activeRoutineTracks = initialDiscover.routineMixes.first().tracks
            val moodTracks = initialDiscover.moodPlaylists.first().tracks
            val nowMs = System.currentTimeMillis()

            createdVm.recordPlaybackSignalForTest(
                RawPlaybackEvent(
                    trackId = activeRoutineTracks[0].id,
                    activePlayDurationMs = 195_000L,
                    trackDurationMs = 210_000L,
                    timestampMs = nowMs
                )
            )
            createdVm.recordPlaybackSignalForTest(
                RawPlaybackEvent(
                    trackId = activeRoutineTracks[1].id,
                    activePlayDurationMs = 14_000L,
                    trackDurationMs = 210_000L,
                    wasUserSkip = true,
                    timestampMs = nowMs + 1_000L
                )
            )
            createdVm.recordPlaybackSignalForTest(
                RawPlaybackEvent(
                    trackId = moodTracks[0].id,
                    activePlayDurationMs = 188_000L,
                    trackDurationMs = 210_000L,
                    timestampMs = nowMs + 2_000L
                )
            )
            libraryRepo.toggleFavorite(moodTracks[1].id)

            // Wait until Room telemetry updates propagate into Discover's live ListeningInsights
            val afterPlaybackDiscover = withTimeout(10_000L) {
                createdVm.discoverState.first { state ->
                    state.insights.completedPlaysTotal >= initialCompletedTotal + 2
                }
            }

            // Assert ZERO mutation, replacement, or reordering across EVERY locked Routine, Mood, & Discover mix
            afterPlaybackDiscover.routineMixes.forEach { mix ->
                assertEquals(
                    "Routine mix ${mix.type} must not mutate during playback",
                    lockedRoutineIds[mix.type],
                    mix.tracks.map { it.id }
                )
            }
            afterPlaybackDiscover.moodPlaylists.forEach { moodPl ->
                assertEquals(
                    "Mood playlist ${moodPl.mood} must not mutate during playback",
                    lockedMoodIds[moodPl.mood],
                    moodPl.tracks.map { it.id }
                )
            }
            assertEquals(
                "Made for You must not mutate during playback",
                lockedMadeForYouIds,
                afterPlaybackDiscover.sections.first { it.category == DiscoverCategory.MADE_FOR_YOU }.tracks.map { it.id }
            )
            assertEquals(
                "Smart Mix must not mutate during playback",
                lockedSmartMixIds,
                afterPlaybackDiscover.sections.first { it.category == DiscoverCategory.SMART_MIX }.tracks.map { it.id }
            )

            // Trigger Tune Mix (generateSmartPlaylist) -> must generate a new locked 10-track session snapshot in Room
            createdVm.generateSmartPlaylist(
                targetMood = "nocturnal",
                energyFilter = "low",
                discoveryBalance = 0.35f
            )
            val tunedState = withTimeout(10_000L) {
                createdVm.smartPlaylistState.first { !it.isGenerating && it.output != null && it.activeSessionId != null }
            }
            val tuneSessionId = tunedState.activeSessionId!!
            val tuneTrackIds = tunedState.output!!.recommendedTracks.map { it.trackId }
            assertEquals("Tune Mix must produce strictly 10 tracks", 10, tuneTrackIds.size)
            assertEquals("Tune Mix must contain 10 distinct tracks", 10, tuneTrackIds.toSet().size)

            // Verify Tune Mix session snapshot is persisted immutably in Room and survives playback telemetry unchanged
            val persistedTuneIds = db.recommendationSnapshotDao().getSessionTracksOnce(tuneSessionId).map { it.id }
            assertEquals(tuneTrackIds, persistedTuneIds)

            createdVm.recordPlaybackSignalForTest(
                RawPlaybackEvent(
                    trackId = tuneTrackIds[0],
                    activePlayDurationMs = 15_000L,
                    trackDurationMs = 210_000L,
                    wasUserSkip = true,
                    timestampMs = nowMs + 5_000L
                )
            )
            val afterSkipTuneIds = db.recommendationSnapshotDao().getSessionTracksOnce(tuneSessionId).map { it.id }
            assertEquals("Locked Tune Mix snapshot must stay immutable after skip", tuneTrackIds, afterSkipTuneIds)
        } finally {
            vm?.clearAndJoinForTest()
            Dispatchers.resetMain()
        }
    }

    // -------------------------------------------------------------------------
    // 10. For This Moment Routine Mixes — Distinct Mood/Tempo Profiles,
    //     ${date}_${routineId} Room Snapshots, & Cross-Card Lead/Cover Diversity
    // -------------------------------------------------------------------------

    @Test
    fun routineMixes_weekendProfiles_distinctTenTrackRoomSnapshots_distinctLeadTracksAndCovers() = runTest {
        val library = buildMultiMoodLargeLibrary(anchorNowMs).mapIndexed { idx, track ->
            track.copy(artworkPath = "content://media/external/audio/albumart/${1000 + idx}")
        }
        db.trackDao().upsertTracks(library.map { TrackEntity.fromDomain(it) })

        val mixesFirst = RecommendationCore.buildRoutineMixes(
            tracks = library,
            nowMs = anchorNowMs,
            localHourOfDay = 14,
            dayOfWeek = Calendar.SATURDAY
        )
        val mixesRepeat = RecommendationCore.buildRoutineMixes(
            tracks = library,
            nowMs = anchorNowMs,
            localHourOfDay = 14,
            dayOfWeek = Calendar.SATURDAY
        )

        val expectedTypes = listOf(
            RoutineMixType.WEEKEND_ENERGY,
            RoutineMixType.AFTERNOON_MOMENTUM,
            RoutineMixType.EVENING_UNWIND,
            RoutineMixType.LATE_NIGHT_WIND_DOWN
        )
        assertEquals(expectedTypes, mixesFirst.map { it.type })

        // Verify deterministic repeatability for the same calendar day
        assertEquals(
            mixesFirst.map { it.type to it.tracks.map { t -> t.id } },
            mixesRepeat.map { it.type to it.tracks.map { t -> t.id } }
        )

        // Verify distinct explicit mood/tempo profiles across the 4 routines
        val weekendProfile = RecommendationCore.profileForRoutine(RoutineMixType.WEEKEND_ENERGY)
        val afternoonProfile = RecommendationCore.profileForRoutine(RoutineMixType.AFTERNOON_MOMENTUM)
        val eveningProfile = RecommendationCore.profileForRoutine(RoutineMixType.EVENING_UNWIND)
        val lateNightProfile = RecommendationCore.profileForRoutine(RoutineMixType.LATE_NIGHT_WIND_DOWN)
        assertTrue(weekendProfile.energyWeight > afternoonProfile.energyWeight)
        assertTrue(weekendProfile.targetBpmMin > lateNightProfile.targetBpmMax)
        assertTrue(eveningProfile.warmHarmonicWeight > lateNightProfile.warmHarmonicWeight)
        assertTrue(lateNightProfile.acousticCalmWeight > weekendProfile.acousticCalmWeight)

        // Verify 10 distinct tracks per routine, 4 distinct #1 lead tracks, and 4 distinct cover URIs
        mixesFirst.forEach { mix ->
            assertEquals("${mix.title} must contain 10 tracks", 10, mix.tracks.size)
            assertEquals("${mix.title} must contain 10 unique tracks", 10, mix.tracks.map { it.id }.toSet().size)
        }
        val leadTrackIds = mixesFirst.map { it.tracks.first().id }
        val leadArtworkPaths = mixesFirst.map { it.tracks.first().artworkPath }
        assertEquals("All 4 routines must have distinct #1 lead tracks", 4, leadTrackIds.toSet().size)
        assertEquals("All 4 routines must have distinct #1 cover artwork", 4, leadArtworkPaths.toSet().size)

        // Verify mood/tempo separation on lead tracks
        val weekendLead = mixesFirst.first { it.type == RoutineMixType.WEEKEND_ENERGY }.tracks.first()
        val lateNightLead = mixesFirst.first { it.type == RoutineMixType.LATE_NIGHT_WIND_DOWN }.tracks.first()
        assertTrue("Weekend Energy lead track should be high-BPM (was ${weekendLead.bpm})", weekendLead.bpm >= 112)
        assertTrue("Late-Night Wind Down lead track should be calm/low-BPM (was ${lateNightLead.bpm})", lateNightLead.bpm <= 100)

        // Verify no two routines share identical 10-track rankings
        val rankingsByRoutine = mixesFirst.map { it.tracks.map { t -> t.id } }
        assertEquals("All 4 routines must produce distinct 10-track rankings", 4, rankingsByRoutine.toSet().size)

        // Lock all 4 routines in Room under "${date}_${routineId}" session IDs
        val dateKey = "2026-11-28"
        val lockedMixes = snapshotRepository.ensureRoutineMixesLocked(
            dateKey = dateKey,
            routineMixes = mixesFirst,
            nowMs = anchorNowMs
        )
        assertEquals(4, lockedMixes.size)

        val expectedSessionIds = mapOf(
            RoutineMixType.WEEKEND_ENERGY to "2026-11-28_weekend_energy",
            RoutineMixType.AFTERNOON_MOMENTUM to "2026-11-28_afternoon_momentum",
            RoutineMixType.EVENING_UNWIND to "2026-11-28_evening_unwind",
            RoutineMixType.LATE_NIGHT_WIND_DOWN to "2026-11-28_late_night_wind_down"
        )

        val persistedLeadIds = mutableSetOf<String>()
        val persistedLeadArtwork = mutableSetOf<String?>()
        val persistedRankings = mutableSetOf<List<String>>()
        for ((type, expectedSessionId) in expectedSessionIds) {
            assertEquals(expectedSessionId, RecommendationSnapshotRepository.routineSessionId(dateKey, type.id))
            val storedTracks = db.recommendationSnapshotDao().getSessionTracksOnce(expectedSessionId)
            assertEquals("$expectedSessionId must persist 10 tracks", 10, storedTracks.size)
            assertEquals("$expectedSessionId must persist 10 unique tracks", 10, storedTracks.map { it.id }.toSet().size)
            val expectedIds = mixesFirst.first { it.type == type }.tracks.map { it.id }
            assertEquals(expectedIds, storedTracks.map { it.id })
            persistedLeadIds += storedTracks.first().id
            persistedLeadArtwork += storedTracks.first().artworkPath
            persistedRankings += storedTracks.map { it.id }
        }
        assertEquals(4, persistedLeadIds.size)
        assertEquals(4, persistedLeadArtwork.size)
        assertEquals(4, persistedRankings.size)

        // Subsequent ensureRoutineMixesLocked call with reversed candidates on the same date must NOT mutate snapshots
        val alteredCandidates = mixesFirst.map { mix ->
            mix.copy(tracks = mix.tracks.reversed().toStableList())
        }
        val reloadedMixes = snapshotRepository.ensureRoutineMixesLocked(
            dateKey = dateKey,
            routineMixes = alteredCandidates,
            nowMs = anchorNowMs + 60_000L
        )
        reloadedMixes.forEach { reloaded ->
            val originalIds = mixesFirst.first { it.type == reloaded.type }.tracks.map { it.id }
            assertEquals(
                "Locked Room snapshot for ${reloaded.type} must remain immutable on same date",
                originalIds,
                reloaded.tracks.map { it.id }
            )
        }
    }

    @Test
    fun routineMixes_sharedArtworkAndUniformMetadata_stillSelectsDistinctLeadsAndCovers() {
        // Case A: Multiple top favorites share identical album cover URI; other tracks have distinct artwork
        val sharedArtworkLibrary = buildMultiMoodLargeLibrary(anchorNowMs).mapIndexed { idx, track ->
            val sharedUri = if (idx % 12 < 4) {
                "content://media/external/audio/albumart/shared_favorite_cover"
            } else {
                "content://media/external/audio/albumart/unique_$idx"
            }
            track.copy(artworkPath = sharedUri)
        }
        val sharedMixes = RecommendationCore.buildRoutineMixes(
            tracks = sharedArtworkLibrary,
            nowMs = anchorNowMs,
            localHourOfDay = 14,
            dayOfWeek = Calendar.SATURDAY
        )
        assertEquals(4, sharedMixes.size)
        assertEquals(
            "Shared-artwork library must still select 4 distinct #1 lead tracks",
            4,
            sharedMixes.map { it.tracks.first().id }.toSet().size
        )
        assertEquals(
            "Shared-artwork library must still avoid duplicate side-by-side cover thumbnails",
            4,
            sharedMixes.map { it.tracks.first().artworkPath }.toSet().size
        )

        // Case B: Untagged / cold-start uniform metadata (bpm=0, genre="", playCount=0)
        val uniformLibrary = (1..24).map { i ->
            Track(
                id = "uniform_$i",
                title = "Untitled Cut $i",
                artist = "Artist $i",
                album = "Album $i",
                genre = "",
                durationMs = 200_000L,
                bpm = 0,
                playCount = 0,
                completedPlayCount = 0,
                skipCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false,
                artworkPath = "content://media/external/audio/albumart/uniform_$i"
            )
        }
        val uniformRun1 = RecommendationCore.buildRoutineMixes(
            tracks = uniformLibrary,
            nowMs = anchorNowMs,
            localHourOfDay = 14,
            dayOfWeek = Calendar.SATURDAY
        )
        val uniformRun2 = RecommendationCore.buildRoutineMixes(
            tracks = uniformLibrary,
            nowMs = anchorNowMs,
            localHourOfDay = 14,
            dayOfWeek = Calendar.SATURDAY
        )
        assertEquals(
            "Uniform cold-start routine mixes must be deterministic for the day",
            uniformRun1.map { it.tracks.map { t -> t.id } },
            uniformRun2.map { it.tracks.map { t -> t.id } }
        )
        assertEquals(4, uniformRun1.map { it.tracks.first().id }.toSet().size)
        assertEquals(4, uniformRun1.map { it.tracks.first().artworkPath }.toSet().size)
        assertNotEquals(
            uniformRun1[0].tracks.map { it.id },
            uniformRun1[1].tracks.map { it.id }
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun PlayerViewModel.clearAndJoinForTest() {
        viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        val method = PlayerViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(this)
    }

    private fun buildMultiMoodLargeLibrary(nowMs: Long): List<Track> {
        val list = ArrayList<Track>(48)
        val moods = listOf(
            Triple(SmartMoodTag.LATE_NIGHT, "Ambient", 76),
            Triple(SmartMoodTag.ACOUSTIC, "Acoustic Folk", 88),
            Triple(SmartMoodTag.SOULFUL, "Neo-Soul", 96),
            Triple(SmartMoodTag.UPBEAT, "Electronic Pop", 126)
        )
        for (mIdx in moods.indices) {
            val (tag, genre, baseBpm) = moods[mIdx]
            for (i in 1..12) {
                val isFav = i <= 5
                val isForgotten = i in 6..8
                val isHiFi = i % 2 == 0
                list += Track(
                    id = "${tag.id}_$i",
                    title = "${tag.label} Cut $i",
                    artist = "${tag.label} Artist $i",
                    album = "${tag.label} LP $i",
                    genre = genre,
                    durationMs = 210_000L,
                    bpm = baseBpm + (i % 7),
                    codec = if (isHiFi) "FLAC" else "MP3",
                    bitDepth = if (isHiFi) 24 else 16,
                    sampleRateHz = if (isHiFi) 96_000 else 44_100,
                    bitrateKbps = if (isHiFi) 1411 else 320,
                    playCount = when {
                        isFav -> 8 + i
                        isForgotten -> 6 + i
                        else -> i % 2
                    },
                    completedPlayCount = when {
                        isFav -> 7 + i
                        isForgotten -> 5 + i
                        else -> 0
                    },
                    skipCount = 0,
                    lastPlayedAt = when {
                        isFav -> nowMs - (3L + i) * RecommendationCore.ONE_DAY_MS
                        isForgotten -> nowMs - (28L + i) * RecommendationCore.ONE_DAY_MS
                        else -> 0L
                    },
                    isFavorite = isFav || isForgotten,
                    moodTags = listOfNotNull(tag, if (isHiFi) SmartMoodTag.LOSSLESS_HIFI else null).toStableList()
                )
            }
        }
        return list
    }

    private fun buildMixedTestLibrary(nowMs: Long): List<Track> {
        val list = ArrayList<Track>(28)
        // 14 high-affinity favorites (played 3–6 days ago so not in 24h cooldown and not >=21d forgotten)
        for (i in 1..14) {
            list += makeTrack(
                id = "fav_$i",
                title = "Favorite Track $i",
                artist = "Artist Fav $i",
                playCount = 12 + i,
                completedPlayCount = 11 + i,
                skipCount = 0,
                lastPlayedAt = nowMs - (3L + (i % 4)) * RecommendationCore.ONE_DAY_MS,
                isFavorite = true,
                moodTags = listOf(SmartMoodTag.SOULFUL, SmartMoodTag.ACOUSTIC)
            )
        }
        // 8 forgotten gems (high affinity, untouched for 25–40 days)
        for (i in 1..8) {
            list += makeTrack(
                id = "gem_$i",
                title = "Forgotten Gem $i",
                artist = "Artist Gem $i",
                playCount = 9 + i,
                completedPlayCount = 8 + i,
                skipCount = 0,
                lastPlayedAt = nowMs - (25L + i * 2L) * RecommendationCore.ONE_DAY_MS,
                isFavorite = true,
                moodTags = listOf(SmartMoodTag.LATE_NIGHT)
            )
        }
        // 6 under-played tracks (0 or 1 play, 0 skips)
        for (i in 1..6) {
            list += makeTrack(
                id = "under_$i",
                title = "Underplayed Discovery $i",
                artist = "Artist Fresh $i",
                playCount = i % 2,
                completedPlayCount = 0,
                skipCount = 0,
                lastPlayedAt = 0L,
                isFavorite = false,
                moodTags = listOf(SmartMoodTag.UPBEAT)
            )
        }
        return list
    }

    private fun makeTrack(
        id: String,
        title: String = "Track $id",
        artist: String = "Artist $id",
        genre: String = "Indie",
        bpm: Int = 96,
        durationMs: Long = 210_000L,
        playCount: Int = 0,
        completedPlayCount: Int = 0,
        skipCount: Int = 0,
        lastPlayedAt: Long = 0L,
        isFavorite: Boolean = false,
        moodTags: List<SmartMoodTag> = emptyList()
    ): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = "Album $id",
        genre = genre,
        durationMs = durationMs,
        bpm = bpm,
        playCount = playCount,
        completedPlayCount = completedPlayCount,
        skipCount = skipCount,
        lastPlayedAt = lastPlayedAt,
        isFavorite = isFavorite,
        moodTags = moodTags.toStableList()
    )
}
