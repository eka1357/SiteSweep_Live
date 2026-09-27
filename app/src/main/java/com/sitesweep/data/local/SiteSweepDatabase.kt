package com.sitesweep.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sitesweep.data.local.dao.CaptureDao
import com.sitesweep.data.local.dao.IssueDao
import com.sitesweep.data.local.dao.SessionDao
import com.sitesweep.data.local.dao.VoiceNoteDao
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity

/**
 * Main Room database instance for SiteSweep.
 * Stores Sessions, Captures, VoiceNotes, and Issues entirely on-device with zero network access.
 */
@Database(
    entities = [
        SessionEntity::class,
        CaptureEntity::class,
        VoiceNoteEntity::class,
        IssueEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SiteSweepDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun captureDao(): CaptureDao
    abstract fun voiceNoteDao(): VoiceNoteDao
    abstract fun issueDao(): IssueDao

    companion object {
        private const val DATABASE_NAME = "sitesweep.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `issues` (
                        `id` TEXT NOT NULL,
                        `originCaptureId` TEXT NOT NULL,
                        `latestCaptureId` TEXT,
                        `locationKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `assignedTo` TEXT,
                        `engineerNotes` TEXT,
                        `resolutionNotes` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `resolvedAt` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`originCaptureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`latestCaptureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issues_locationKey` ON `issues` (`locationKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issues_status` ON `issues` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issues_originCaptureId` ON `issues` (`originCaptureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_issues_latestCaptureId` ON `issues` (`latestCaptureId`)")
            }
        }

        @Volatile
        private var INSTANCE: SiteSweepDatabase? = null

        fun getInstance(context: Context): SiteSweepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SiteSweepDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
