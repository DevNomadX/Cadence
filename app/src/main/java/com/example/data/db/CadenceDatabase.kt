package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        QueueItemEntity::class,
        SavedFolderEntity::class,
        DailyPicksSnapshotEntity::class,
        DailyPicksSnapshotItemEntity::class,
        SessionSnapshotEntity::class,
        SessionSnapshotItemEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class CadenceDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun folderDao(): FolderDao
    abstract fun recommendationSnapshotDao(): RecommendationSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: CadenceDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN codec TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN bitDepth INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN completedPlayCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN skipCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN affinityScore REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tracks ADD COLUMN moodTags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tracks ADD COLUMN artworkColorArgb INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_picks_snapshots` (
                        `dateKey` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_picks_snapshot_items` (
                        `dateKey` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `trackId` TEXT NOT NULL,
                        PRIMARY KEY(`dateKey`, `position`),
                        FOREIGN KEY(`dateKey`) REFERENCES `daily_picks_snapshots`(`dateKey`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_picks_snapshot_items_dateKey` ON `daily_picks_snapshot_items` (`dateKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_daily_picks_snapshot_items_trackId` ON `daily_picks_snapshot_items` (`trackId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_snapshots` (
                        `sessionId` TEXT NOT NULL,
                        `contextKey` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_snapshot_items` (
                        `sessionId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `trackId` TEXT NOT NULL,
                        PRIMARY KEY(`sessionId`, `position`),
                        FOREIGN KEY(`sessionId`) REFERENCES `session_snapshots`(`sessionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_snapshot_items_sessionId` ON `session_snapshot_items` (`sessionId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_snapshot_items_trackId` ON `session_snapshot_items` (`trackId`)"
                )
            }
        }

        fun getInstance(context: Context): CadenceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CadenceDatabase::class.java,
                    "cadence_library.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
