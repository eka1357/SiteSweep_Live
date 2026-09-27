package com.sitesweep.data.repository

import com.sitesweep.data.local.SiteSweepDatabase
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.model.IssueDeduplicationResult
import com.sitesweep.data.model.IssueStatus
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

    // Issue operations
    suspend fun insertIssue(issue: IssueEntity) {}
    suspend fun updateIssue(issue: IssueEntity) {}
    suspend fun getIssueById(id: String): IssueEntity? = null
    fun observeIssueById(id: String): Flow<IssueEntity?> = kotlinx.coroutines.flow.flowOf<IssueEntity?>(null)
    fun getAllIssues(): Flow<List<IssueEntity>> = kotlinx.coroutines.flow.flowOf(emptyList<IssueEntity>())
    fun getIssuesByStatus(status: IssueStatus): Flow<List<IssueEntity>> = kotlinx.coroutines.flow.flowOf(emptyList<IssueEntity>())
    suspend fun getActiveIssueByLocationKey(locationKey: String): IssueEntity? = null
    suspend fun updateIssueStatus(id: String, newStatus: IssueStatus, resolutionNotes: String? = null): Boolean = false
    suspend fun assignIssue(id: String, assignedTo: String): Boolean = false
    suspend fun updateIssueNotes(id: String, engineerNotes: String? = null, resolutionNotes: String? = null): Boolean = false
    suspend fun attachCaptureToIssue(issueId: String, captureId: String): Boolean = false
    suspend fun reopenIssue(id: String, reason: String? = null): Boolean = false
    suspend fun deleteIssue(id: String) {}

    // Deterministic issue promotion & deduplication helper
    suspend fun createOrLinkIssueForCapture(
        capture: CaptureEntity,
        customTitle: String? = null,
        notes: String? = null
    ): IssueDeduplicationResult = IssueDeduplicationResult.Rejected("Not implemented")
}

class SiteSweepRepositoryImpl(
    private val database: SiteSweepDatabase
) : SiteSweepRepository {

    private val sessionDao = database.sessionDao()
    private val captureDao = database.captureDao()
    private val voiceNoteDao = database.voiceNoteDao()
    private val issueDao = database.issueDao()

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

    // --- Issue Operations ---

    override suspend fun insertIssue(issue: IssueEntity) {
        issueDao.insertIssue(issue)
    }

    override suspend fun updateIssue(issue: IssueEntity) {
        issueDao.updateIssue(issue)
    }

    override suspend fun getIssueById(id: String): IssueEntity? {
        return issueDao.getIssueById(id)
    }

    override fun observeIssueById(id: String): Flow<IssueEntity?> {
        return issueDao.observeIssueById(id)
    }

    override fun getAllIssues(): Flow<List<IssueEntity>> {
        return issueDao.getAllIssues()
    }

    override fun getIssuesByStatus(status: IssueStatus): Flow<List<IssueEntity>> {
        return issueDao.getIssuesByStatus(status.name)
    }

    override suspend fun getActiveIssueByLocationKey(locationKey: String): IssueEntity? {
        val parentPrefix = if (locationKey.length > 4) locationKey.dropLast(1) else locationKey
        return issueDao.getActiveIssueByLocationPrefix(locationKey, parentPrefix)
            ?: issueDao.getActiveIssueByLocationKey(locationKey)
    }

    override suspend fun updateIssueStatus(
        id: String,
        newStatus: IssueStatus,
        resolutionNotes: String?
    ): Boolean {
        val current = issueDao.getIssueById(id) ?: return false
        val currentStatus = current.issueStatus

        if (!IssueStatus.canTransition(currentStatus, newStatus)) {
            return false // Invalid transition rejected
        }

        val now = System.currentTimeMillis()
        val resolvedAt = if (newStatus == IssueStatus.RESOLVED) now else null
        issueDao.updateStatus(id, newStatus.name, now, resolvedAt)

        if (resolutionNotes != null && resolutionNotes != current.resolutionNotes) {
            issueDao.updateNotes(id, current.engineerNotes, resolutionNotes, now)
        }
        return true
    }

    override suspend fun assignIssue(id: String, assignedTo: String): Boolean {
        val current = issueDao.getIssueById(id) ?: return false
        val currentStatus = current.issueStatus
        val newStatus = if (currentStatus == IssueStatus.OPEN) IssueStatus.ASSIGNED else currentStatus
        val now = System.currentTimeMillis()
        issueDao.updateAssignment(id, assignedTo, newStatus.name, now)
        return true
    }

    override suspend fun updateIssueNotes(
        id: String,
        engineerNotes: String?,
        resolutionNotes: String?
    ): Boolean {
        val current = issueDao.getIssueById(id) ?: return false
        val now = System.currentTimeMillis()
        val eng = engineerNotes ?: current.engineerNotes
        val res = resolutionNotes ?: current.resolutionNotes
        issueDao.updateNotes(id, eng, res, now)
        return true
    }

    override suspend fun attachCaptureToIssue(issueId: String, captureId: String): Boolean {
        val current = issueDao.getIssueById(issueId) ?: return false
        val now = System.currentTimeMillis()
        issueDao.attachLatestCapture(issueId, captureId, now)
        return true
    }

    override suspend fun reopenIssue(id: String, reason: String?): Boolean {
        val current = issueDao.getIssueById(id) ?: return false
        if (!IssueStatus.canReopen(current.issueStatus)) {
            return false
        }
        val now = System.currentTimeMillis()
        val noteAppend = reason?.let { "[REOPENED ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(now))}]: $it" }
        val updatedNotes = listOfNotNull(current.engineerNotes, noteAppend).joinToString("\n\n")

        issueDao.updateStatus(id, IssueStatus.OPEN.name, now, null)
        issueDao.updateNotes(id, updatedNotes, current.resolutionNotes, now)
        return true
    }

    override suspend fun deleteIssue(id: String) {
        issueDao.deleteIssue(id)
    }

    override suspend fun createOrLinkIssueForCapture(
        capture: CaptureEntity,
        customTitle: String?,
        notes: String?
    ): IssueDeduplicationResult {
        val locationKey = capture.locationKey
        if (locationKey.isBlank()) {
            return IssueDeduplicationResult.Rejected("Missing location key for capture")
        }

        // Deduplication check: Is there an existing active (non-RESOLVED) issue at this geohash?
        val parentPrefix = if (locationKey.length > 4) locationKey.dropLast(1) else locationKey
        val existingActive = issueDao.getActiveIssueByLocationPrefix(locationKey, parentPrefix)
            ?: issueDao.getActiveIssueByLocationKey(locationKey)

        if (existingActive != null) {
            val now = System.currentTimeMillis()
            val wasAlreadyLatest = existingActive.latestCaptureId == capture.id
            if (!wasAlreadyLatest) {
                issueDao.attachLatestCapture(existingActive.id, capture.id, now)
            }
            val updated = existingActive.copy(
                latestCaptureId = capture.id,
                updatedAt = now
            )
            return IssueDeduplicationResult.LinkedToExisting(updated, wasAlreadyLatest)
        }

        // No existing active issue -> Create new OPEN issue
        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: "${capture.severity.uppercase(java.util.Locale.US)} Distress @ ${locationKey.take(8)}"

        val now = System.currentTimeMillis()
        val newIssue = IssueEntity(
            id = UUID.randomUUID().toString(),
            originCaptureId = capture.id,
            latestCaptureId = capture.id,
            locationKey = locationKey,
            title = title,
            status = IssueStatus.OPEN.name,
            assignedTo = null,
            engineerNotes = notes,
            resolutionNotes = null,
            createdAt = now,
            updatedAt = now,
            resolvedAt = null
        )

        issueDao.insertIssue(newIssue)
        return IssueDeduplicationResult.Created(newIssue)
    }
}
