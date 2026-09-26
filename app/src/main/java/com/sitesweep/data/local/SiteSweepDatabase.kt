package com.sitesweep.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sitesweep.data.local.dao.CaptureDao
import com.sitesweep.data.local.dao.SessionDao
import com.sitesweep.data.local.dao.VoiceNoteDao
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity

/**
 * Main Room database instance for SiteSweep.
 * Stores Sessions, Captures, and VoiceNotes entirely on-device with zero network access.
 */
@Database(
    entities = [
        SessionEntity::class,
        CaptureEntity::class,
        VoiceNoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SiteSweepDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun captureDao(): CaptureDao
    abstract fun voiceNoteDao(): VoiceNoteDao

    companion object {
        private const val DATABASE_NAME = "sitesweep.db"

        @Volatile
        private var INSTANCE: SiteSweepDatabase? = null

        fun getInstance(context: Context): SiteSweepDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SiteSweepDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
