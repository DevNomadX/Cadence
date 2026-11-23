package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.LibrarySortOrder
import com.example.data.PreferencesRepository
import com.example.data.Track
import com.example.data.db.CadenceDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistTrackEntity
import com.example.data.db.TrackEntity
import com.example.data.library.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CadenceLibraryAndPlaylistTest {

    private lateinit var context: Context
    private lateinit var db: CadenceDatabase

    private val sampleLocalTracks = listOf(
        Track(
            id = "local_track_1",
            title = "Amber Solstice",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Ambient",
            durationMs = 185_000L,
            bpm = 82,
            localUriString = "content://media/external/audio/media/101",
            folderName = "Ambient",
            folderPath = "Music/Ambient",
            dateAdded = 1_700_000_100L,
            isUserImported = true
        ),
        Track(
            id = "local_track_2",
            title = "Velvet Nocturne",
            artist = "Miro & Sol",
            album = "Afterglow",
            genre = "Electronic",
            durationMs = 198_000L,
            bpm = 96,
            localUriString = "content://media/external/audio/media/102",
            folderName = "Electronic",
            folderPath = "Music/Electronic",
            dateAdded = 1_700_000_200L,
            isUserImported = true
        ),
        Track(
            id = "local_track_3",
            title = "Ceramic Pulse",
            artist = "Aalto Trio",
            album = "Tactile",
            genre = "Jazz",
            durationMs = 172_000L,
            bpm = 112,
            localUriString = "content://media/external/audio/media/103",
            folderName = "Jazz",
            folderPath = "Music/Jazz",
            dateAdded = 1_700_000_300L,
            isUserImported = true
        )
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CadenceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `track persistence favorites and play history work reliably`() = runTest {
        val trackDao = db.trackDao()
        val entities = sampleLocalTracks.map { TrackEntity.fromDomain(it) }
        trackDao.insertTracksIgnore(entities)

        val stored = trackDao.getAllTracksOnce()
        assertEquals(sampleLocalTracks.size, stored.size)

        val targetId = sampleLocalTracks.first().id
        trackDao.setFavorite(targetId, false)
        assertFalse(trackDao.getTrackById(targetId)!!.isFavorite)

        trackDao.setFavorite(targetId, true)
        assertTrue(trackDao.getTrackById(targetId)!!.isFavorite)

        val playedAt = 1_750_000_000L
        trackDao.recordPlay(targetId, playedAt)
        trackDao.recordPlay(targetId, playedAt + 5_000L)
        val updated = trackDao.getTrackById(targetId)!!
        assertEquals(2, updated.playCount)
        assertEquals(playedAt + 5_000L, updated.lastPlayedAt)
    }

    @Test
    fun `playlist CRUD operations and track ordering persist accurately`() = runTest {
        val trackDao = db.trackDao()
        val playlistDao = db.playlistDao()
        val entities = sampleLocalTracks.map { TrackEntity.fromDomain(it) }
        trackDao.insertTracksIgnore(entities)

        val plId = playlistDao.insertPlaylist(PlaylistEntity(name = "Late Night"))
        val t1 = sampleLocalTracks[0].id
        val t2 = sampleLocalTracks[1].id
        val t3 = sampleLocalTracks[2].id

        playlistDao.insertPlaylistTrack(PlaylistTrackEntity(plId, t1, 0))
        playlistDao.insertPlaylistTrack(PlaylistTrackEntity(plId, t2, 1))
        playlistDao.insertPlaylistTrack(PlaylistTrackEntity(plId, t3, 2))

        val initialTracks = playlistDao.getPlaylistTracksOnce(plId)
        assertEquals(listOf(t1, t2, t3), initialTracks.map { it.id })

        playlistDao.renamePlaylist(plId, "Midnight Focus", System.currentTimeMillis())
        val summaries = playlistDao.observePlaylistsWithCounts().first()
        assertEquals("Midnight Focus", summaries.first().name)
        assertEquals(3, summaries.first().trackCount)

        playlistDao.removeTrackFromPlaylist(plId, t2)
        val remainingIds = playlistDao.getPlaylistTrackIdsOrdered(plId)
        playlistDao.replacePlaylistTracks(plId, remainingIds)
        assertEquals(listOf(t1, t3), playlistDao.getPlaylistTracksOnce(plId).map { it.id })

        playlistDao.deletePlaylist(plId)
        assertTrue(playlistDao.observePlaylistsWithCounts().first().isEmpty())
        assertTrue(playlistDao.getPlaylistTracksOnce(plId).isEmpty())
    }

    @Test
    fun `queue persistence replaces and restores track order`() = runTest {
        val queueDao = db.queueDao()
        val ids = listOf("local_track_1", "local_track_2", "local_track_3")
        queueDao.replaceQueue(ids)
        assertEquals(ids, queueDao.getQueueTrackIdsOnce())

        val reordered = listOf("local_track_2", "local_track_3", "local_track_1")
        queueDao.replaceQueue(reordered)
        assertEquals(reordered, queueDao.getQueueTrackIdsOnce())
    }

    @Test
    fun `library grouping sorting and deduplication keys work accurately`() {
        val tracks = sampleLocalTracks + Track(
            id = "local_1",
            title = "Evening Mist",
            artist = "Klara Lind",
            album = "Warm Horizon",
            genre = "Ambient",
            durationMs = 210_000L,
            folderName = "FLAC Archive",
            folderPath = "Music/FLAC Archive",
            playCount = 12,
            dateAdded = 2_000_000_000L,
            isUserImported = true
        )

        val albums = LibraryRepository.groupAlbums(tracks)
        val warmHorizon = albums.first { it.title == "Warm Horizon" }
        assertEquals(2, warmHorizon.trackCount)

        val artists = LibraryRepository.groupArtists(tracks)
        val klara = artists.first { it.title == "Klara Lind" }
        assertEquals(2, klara.trackCount)

        val folders = LibraryRepository.groupFolders(tracks)
        assertTrue(folders.any { it.title == "FLAC Archive" })

        val sortedByDuration = LibraryRepository.sortTracks(tracks, LibrarySortOrder.DURATION_DESC)
        assertEquals("local_1", sortedByDuration.first().id)

        val sortedByMostPlayed = LibraryRepository.sortTracks(tracks, LibrarySortOrder.MOST_PLAYED)
        assertEquals("local_1", sortedByMostPlayed.first().id)

        val keyA = TrackEntity.buildDedupeKey("Amber Solstice", "Klara Lind", 36_100L)
        val keyB = TrackEntity.buildDedupeKey("  amber   solstice ", "klara lind", 36_900L)
        assertEquals(keyA, keyB)
    }

    @Test
    fun `deleteLegacyCuratedTracks purges legacy demo tracks while preserving user local tracks`() = runTest {
        val trackDao = db.trackDao()
        val legacyDemo = TrackEntity.fromDomain(
            Track(
                id = "curated_nocturne",
                title = "Legacy Demo",
                artist = "Synth",
                album = "Demo",
                durationMs = 36_000L,
                isUserImported = false
            )
        )
        val realLocal = TrackEntity.fromDomain(sampleLocalTracks.first())
        trackDao.insertTracksIgnore(listOf(legacyDemo, realLocal))

        assertEquals(2, trackDao.getAllTracksOnce().size)
        trackDao.deleteLegacyCuratedTracks()

        val remaining = trackDao.getAllTracksOnce()
        assertEquals(listOf(realLocal.id), remaining.map { it.id })
    }

    @Test
    fun `fresh LibraryRepository starts completely empty without demo tracks`() = runTest {
        val prefs = PreferencesRepository(context)
        val repo = LibraryRepository(context, prefs)
        repo.ensureInitialized()
        assertTrue("Fresh library must start with zero demo tracks", repo.getAllTracksOnce().isEmpty())
    }

    @Test
    fun `MIGRATION_2_3 adds completedPlayCount and skipCount non-destructively and TrackDao updates them`() = runTest {
        // 1. Verify MIGRATION_2_3 SQL executes cleanly on a v2 schema table and preserves rows
        val sqliteDb = android.database.sqlite.SQLiteDatabase.create(null)
        try {
            sqliteDb.execSQL(
                """
                CREATE TABLE tracks (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    genre TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    bpm INTEGER NOT NULL,
                    localUriString TEXT,
                    artworkPath TEXT,
                    folderName TEXT NOT NULL,
                    folderPath TEXT NOT NULL,
                    dateAdded INTEGER NOT NULL,
                    playCount INTEGER NOT NULL,
                    lastPlayedAt INTEGER NOT NULL,
                    isFavorite INTEGER NOT NULL,
                    isUserImported INTEGER NOT NULL,
                    dedupeKey TEXT NOT NULL,
                    mimeType TEXT NOT NULL,
                    bitrateKbps INTEGER NOT NULL,
                    sampleRateHz INTEGER NOT NULL,
                    bitDepth INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sqliteDb.execSQL(
                """
                INSERT INTO tracks VALUES (
                    'v2_track_1', 'Lossless Dawn', 'Klara Lind', 'Warm Horizon', 'Ambient',
                    210000, 84, 'content://media/1', NULL, 'Music', 'Music',
                    1700000000, 5, 1700000500, 1, 1, 'lossless dawn|klara lind|210',
                    'audio/flac', 1411, 96000, 24
                )
                """.trimIndent()
            )

            val supportDb = object : androidx.sqlite.db.SupportSQLiteDatabase by
                androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
                    .create(
                        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                            .name(null)
                            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                                    db.execSQL(
                                        """
                                        CREATE TABLE tracks (
                                            id TEXT NOT NULL PRIMARY KEY,
                                            title TEXT NOT NULL,
                                            artist TEXT NOT NULL,
                                            album TEXT NOT NULL,
                                            genre TEXT NOT NULL,
                                            durationMs INTEGER NOT NULL,
                                            bpm INTEGER NOT NULL,
                                            localUriString TEXT,
                                            artworkPath TEXT,
                                            folderName TEXT NOT NULL,
                                            folderPath TEXT NOT NULL,
                                            dateAdded INTEGER NOT NULL,
                                            playCount INTEGER NOT NULL,
                                            lastPlayedAt INTEGER NOT NULL,
                                            isFavorite INTEGER NOT NULL,
                                            isUserImported INTEGER NOT NULL,
                                            dedupeKey TEXT NOT NULL,
                                            mimeType TEXT NOT NULL,
                                            bitrateKbps INTEGER NOT NULL,
                                            sampleRateHz INTEGER NOT NULL,
                                            bitDepth INTEGER NOT NULL
                                        )
                                        """.trimIndent()
                                    )
                                    db.execSQL(
                                        """
                                        INSERT INTO tracks VALUES (
                                            'v2_track_1', 'Lossless Dawn', 'Klara Lind', 'Warm Horizon', 'Ambient',
                                            210000, 84, 'content://media/1', NULL, 'Music', 'Music',
                                            1700000000, 5, 1700000500, 1, 1, 'lossless dawn|klara lind|210',
                                            'audio/flac', 1411, 96000, 24
                                        )
                                        """.trimIndent()
                                    )
                                }

                                override fun onUpgrade(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int
                                ) = Unit
                            })
                            .build()
                    ).writableDatabase {}

            try {
                CadenceDatabase.MIGRATION_2_3.migrate(supportDb)
                supportDb.query("SELECT id, completedPlayCount, skipCount FROM tracks WHERE id = 'v2_track_1'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("v2_track_1", cursor.getString(0))
                    assertEquals(0, cursor.getInt(1))
                    assertEquals(0, cursor.getInt(2))
                }
            } finally {
                supportDb.close()
            }
        } finally {
            sqliteDb.close()
        }

        // 2. Verify Room v3 TrackDao completion & skip counters persist cleanly
        val trackDao = db.trackDao()
        trackDao.insertTracksIgnore(sampleLocalTracks.map { TrackEntity.fromDomain(it) })
        val id = sampleLocalTracks.first().id
        val completedAt = 1_760_000_000L
        trackDao.recordCompletedPlay(id, completedAt)
        trackDao.recordCompletedPlay(id, completedAt + 1_000L)
        trackDao.recordSkip(id)

        val updated = trackDao.getTrackById(id)!!.toDomain()
        assertEquals(2, updated.completedPlayCount)
        assertEquals(1, updated.skipCount)
        assertEquals(completedAt + 1_000L, updated.lastPlayedAt)
    }
}
