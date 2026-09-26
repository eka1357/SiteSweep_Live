package com.sitesweep.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sitesweep.data.local.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity)

    @Query("SELECT * FROM voice_notes WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE captureId = :captureId ORDER BY timestamp ASC")
    fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>>
}
