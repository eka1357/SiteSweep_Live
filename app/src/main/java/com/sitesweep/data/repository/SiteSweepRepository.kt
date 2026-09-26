package com.sitesweep.data.repository

import com.sitesweep.data.local.SiteSweepDatabase
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Clean repository interface.
 * As mandated by AGENTS.md, the Repository is the single architectural entity that touches Room.
 */
interface SiteSweepRepository {
    // Session operations
    suspend fun createSession(label: String): SessionEntity
    suspend fun createSession(session: SessionEntity): SessionEntity = session
    suspend fun endSession(id: String)
    suspend fun getSessionById(id: String): SessionEntity?
    fun getAllSessions(): Flow<List<SessionEntity>>
    suspend fun deleteSession(id: String)

    // Capture operations
    suspend fun insertCapture(capture: CaptureEntity)
    suspend fun getCaptureById(id: String): CaptureEntity?
    fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>>
    suspend fun getCapturesByLocationKey(locationKey: String): List<CaptureEntity>
    fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>>
    fun getAllCaptures(): Flow<List<CaptureEntity>>

    // Voice note operations
    suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity)
    fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>>
    fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>>
}

class SiteSweepRepositoryImpl(
    private val database: SiteSweepDatabase
) : SiteSweepRepository {

    private val sessionDao = database.sessionDao()
    private val captureDao = database.captureDao()
    private val voiceNoteDao = database.voiceNoteDao()

    override suspend fun createSession(label: String): SessionEntity {
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            startedAt = System.currentTimeMillis(),
            label = label
        )
        sessionDao.insertSession(session)
        return session
    }

    override suspend fun createSession(session: SessionEntity): SessionEntity {
        sessionDao.insertSession(session)
        return session
    }

    override suspend fun endSession(id: String) {
        sessionDao.endSession(id, System.currentTimeMillis())
    }

    override suspend fun getSessionById(id: String): SessionEntity? {
        return sessionDao.getSessionById(id)
    }

    override fun getAllSessions(): Flow<List<SessionEntity>> {
        return sessionDao.getAllSessions()
    }

    override suspend fun deleteSession(id: String) {
        sessionDao.deleteSession(id)
    }

    override suspend fun insertCapture(capture: CaptureEntity) {
        captureDao.insertCapture(capture)
    }

    override suspend fun getCaptureById(id: String): CaptureEntity? {
        return captureDao.getCaptureById(id)
    }

    override fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>> {
        return captureDao.getCapturesForSession(sessionId)
    }

    override suspend fun getCapturesByLocationKey(locationKey: String): List<CaptureEntity> {
        return captureDao.getCapturesByLocationKey(locationKey)
    }

    override fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>> {
        val parentPrefix = if (locationKey.length > 4) locationKey.dropLast(1) else locationKey
        return captureDao.observeCapturesByLocationKey(locationKey, parentPrefix)
    }

    override fun getAllCaptures(): Flow<List<CaptureEntity>> {
        return captureDao.getAllCaptures()
    }

    override suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {
        voiceNoteDao.insertVoiceNote(voiceNote)
    }

    override fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>> {
        return voiceNoteDao.getVoiceNotesForSession(sessionId)
    }

    override fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>> {
        return voiceNoteDao.getVoiceNotesForCapture(captureId)
    }
}
