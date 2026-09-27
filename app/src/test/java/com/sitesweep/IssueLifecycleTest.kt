package com.sitesweep

import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.model.IssueDeduplicationResult
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit test suite for the SiteSweep Issue Lifecycle and Closed-Loop Verification:
 * - Issue creation & retrieval
 * - State machine transitions & invalid transition rejection
 * - Assignment & notes update
 * - Closed-loop reinspection capture attachment & delta tracking
 * - Resolution timestamp setting & explicit reopening
 * - Spatial geohash deduplication (preventing duplicate issues for the same crack)
 * - Verification that existing entities (sessions, captures, voice notes) remain unaffected
 */
class IssueLifecycleTest {

    private lateinit var repository: InMemoryTestRepository

    private val sampleCapture1 = CaptureEntity(
        id = "cap-origin-001",
        sessionId = "sess-001",
        imagePath = "/data/user/0/com.sitesweep/files/captures/cap-origin-001.jpg",
        lat = 17.44858,
        lng = 78.37582,
        timestamp = 1727400000000L,
        severity = "STRUCTURAL",
        confidence = 0.965f,
        locationKey = "te7u0x99"
    )

    private val sampleCapture2 = CaptureEntity(
        id = "cap-reinspect-002",
        sessionId = "sess-002",
        imagePath = "/data/user/0/com.sitesweep/files/captures/cap-reinspect-002.jpg",
        lat = 17.44858,
        lng = 78.37582,
        timestamp = 1727486400000L,
        severity = "MONITOR",
        confidence = 0.720f,
        locationKey = "te7u0x99"
    )

    private val sampleCapture3 = CaptureEntity(
        id = "cap-resolved-003",
        sessionId = "sess-003",
        imagePath = "/data/user/0/com.sitesweep/files/captures/cap-resolved-003.jpg",
        lat = 17.44858,
        lng = 78.37582,
        timestamp = 1727572800000L,
        severity = "STABLE",
        confidence = 0.180f,
        locationKey = "te7u0x99"
    )

    @Before
    fun setUp() {
        repository = InMemoryTestRepository()
    }

    @Test
    fun issueCreation_fromCandidateCapture_createsOpenIssue() = runBlocking {
        repository.insertCapture(sampleCapture1)

        val result = repository.createOrLinkIssueForCapture(
            capture = sampleCapture1,
            customTitle = "Shear Crack in Column B4",
            notes = "Spalling noted along north edge"
        )

        assertTrue("Expected new issue created", result is IssueDeduplicationResult.Created)
        val created = (result as IssueDeduplicationResult.Created).issue

        assertEquals("Shear Crack in Column B4", created.title)
        assertEquals(sampleCapture1.id, created.originCaptureId)
        assertEquals(sampleCapture1.id, created.latestCaptureId)
        assertEquals(sampleCapture1.locationKey, created.locationKey)
        assertEquals(IssueStatus.OPEN.name, created.status)
        assertEquals(IssueStatus.OPEN, created.issueStatus)
        assertEquals("Spalling noted along north edge", created.engineerNotes)
        assertNull("Resolution notes must be null initially", created.resolutionNotes)
        assertNull("Resolved timestamp must be null initially", created.resolvedAt)

        val fetched = repository.getIssueById(created.id)
        assertNotNull(fetched)
        assertEquals(created.id, fetched?.id)
    }

    @Test
    fun stateMachine_validTransitions_allowed() {
        // OPEN -> ASSIGNED
        assertTrue(IssueStatus.canTransition(IssueStatus.OPEN, IssueStatus.ASSIGNED))
        // OPEN -> IN_REPAIR
        assertTrue(IssueStatus.canTransition(IssueStatus.OPEN, IssueStatus.IN_REPAIR))
        // ASSIGNED -> IN_REPAIR
        assertTrue(IssueStatus.canTransition(IssueStatus.ASSIGNED, IssueStatus.IN_REPAIR))
        // IN_REPAIR -> REINSPECTION
        assertTrue(IssueStatus.canTransition(IssueStatus.IN_REPAIR, IssueStatus.REINSPECTION))
        // REINSPECTION -> RESOLVED
        assertTrue(IssueStatus.canTransition(IssueStatus.REINSPECTION, IssueStatus.RESOLVED))
        // REINSPECTION -> IN_REPAIR (re-repair required)
        assertTrue(IssueStatus.canTransition(IssueStatus.REINSPECTION, IssueStatus.IN_REPAIR))

        // Operational rollbacks
        assertTrue(IssueStatus.canTransition(IssueStatus.ASSIGNED, IssueStatus.OPEN))
        assertTrue(IssueStatus.canTransition(IssueStatus.IN_REPAIR, IssueStatus.ASSIGNED))
    }

    @Test
    fun stateMachine_invalidTransitions_rejected() {
        // Direct skip from OPEN to RESOLVED
        assertFalse(IssueStatus.canTransition(IssueStatus.OPEN, IssueStatus.RESOLVED))
        // Direct jump from ASSIGNED to RESOLVED
        assertFalse(IssueStatus.canTransition(IssueStatus.ASSIGNED, IssueStatus.RESOLVED))
        // Transition from RESOLVED without explicit reopen
        assertFalse(IssueStatus.canTransition(IssueStatus.RESOLVED, IssueStatus.IN_REPAIR))
        assertFalse(IssueStatus.canTransition(IssueStatus.RESOLVED, IssueStatus.OPEN))
    }

    @Test
    fun issueLifecycle_closedLoopVerification_advancesAndResolves() = runBlocking {
        repository.insertCapture(sampleCapture1)
        val createdResult = repository.createOrLinkIssueForCapture(sampleCapture1)
        val issueId = (createdResult as IssueDeduplicationResult.Created).issue.id

        // 1. Assign to team
        val assigned = repository.assignIssue(issueId, "Epoxy Team Delta")
        assertTrue(assigned)
        var issue = repository.getIssueById(issueId)
        assertEquals(IssueStatus.ASSIGNED, issue?.issueStatus)
        assertEquals("Epoxy Team Delta", issue?.assignedTo)

        // 2. Start repair
        val started = repository.updateIssueStatus(issueId, IssueStatus.IN_REPAIR)
        assertTrue(started)
        issue = repository.getIssueById(issueId)
        assertEquals(IssueStatus.IN_REPAIR, issue?.issueStatus)

        // 3. Mark for reinspection
        val ready = repository.updateIssueStatus(issueId, IssueStatus.REINSPECTION)
        assertTrue(ready)
        issue = repository.getIssueById(issueId)
        assertEquals(IssueStatus.REINSPECTION, issue?.issueStatus)

        // 4. Attach reinspection capture (1st attempt: 72% hairline - repair incomplete)
        repository.insertCapture(sampleCapture2)
        repository.attachCaptureToIssue(issueId, sampleCapture2.id)
        issue = repository.getIssueById(issueId)
        assertEquals(sampleCapture2.id, issue?.latestCaptureId)

        // Return to repair for second coat
        val returned = repository.updateIssueStatus(issueId, IssueStatus.IN_REPAIR)
        assertTrue(returned)

        // Reinspection ready again
        repository.updateIssueStatus(issueId, IssueStatus.REINSPECTION)

        // 5. Final reinspection capture: 18% NONE
        repository.insertCapture(sampleCapture3)
        repository.attachCaptureToIssue(issueId, sampleCapture3.id)

        // 6. Mark RESOLVED with sign-off notes
        val resolved = repository.updateIssueStatus(
            id = issueId,
            newStatus = IssueStatus.RESOLVED,
            resolutionNotes = "Pressure injected epoxy resin; surface ground flush. Baseline 96% down to 18%."
        )
        assertTrue(resolved)

        val finalIssue = repository.getIssueById(issueId)
        assertEquals(IssueStatus.RESOLVED, finalIssue?.issueStatus)
        assertNotNull("Resolved timestamp must be recorded", finalIssue?.resolvedAt)
        assertEquals("Pressure injected epoxy resin; surface ground flush. Baseline 96% down to 18%.", finalIssue?.resolutionNotes)
        assertEquals(sampleCapture1.id, finalIssue?.originCaptureId)
        assertEquals(sampleCapture3.id, finalIssue?.latestCaptureId)
    }

    @Test
    fun issueReopen_allowsResolvedIssueToBeReopened() = runBlocking {
        repository.insertCapture(sampleCapture1)
        val issueId = (repository.createOrLinkIssueForCapture(sampleCapture1) as IssueDeduplicationResult.Created).issue.id

        // Advance to RESOLVED
        repository.updateIssueStatus(issueId, IssueStatus.IN_REPAIR)
        repository.updateIssueStatus(issueId, IssueStatus.REINSPECTION)
        repository.updateIssueStatus(issueId, IssueStatus.RESOLVED)

        // Attempting normal transition must fail
        assertFalse(repository.updateIssueStatus(issueId, IssueStatus.IN_REPAIR))

        // Explicit reopen succeeds
        val reopened = repository.reopenIssue(issueId, reason = "Distress micro-cracking reappeared after heavy load")
        assertTrue(reopened)

        val reopenedIssue = repository.getIssueById(issueId)
        assertEquals(IssueStatus.OPEN, reopenedIssue?.issueStatus)
        assertNull("Resolved timestamp should clear upon reopen", reopenedIssue?.resolvedAt)
        assertTrue("Reopen reason should be appended to notes", reopenedIssue?.engineerNotes?.contains("micro-cracking reappeared") == true)
    }

    @Test
    fun deduplication_sameLocationKey_linksToExistingIssueInsteadOfDuplicating() = runBlocking {
        // Initial detection at te7u0x99
        repository.insertCapture(sampleCapture1)
        val firstResult = repository.createOrLinkIssueForCapture(sampleCapture1)
        assertTrue("First capture should create issue", firstResult is IssueDeduplicationResult.Created)
        val originalIssueId = (firstResult as IssueDeduplicationResult.Created).issue.id

        // Repeated capture at same locationKey (panning camera across same crack)
        val duplicateCapture = sampleCapture1.copy(
            id = "cap-dup-999",
            timestamp = sampleCapture1.timestamp + 5000L,
            confidence = 0.972f
        )
        repository.insertCapture(duplicateCapture)

        val secondResult = repository.createOrLinkIssueForCapture(duplicateCapture)
        assertTrue("Subsequent capture must link to existing issue", secondResult is IssueDeduplicationResult.LinkedToExisting)

        val linkedIssue = (secondResult as IssueDeduplicationResult.LinkedToExisting).issue
        assertEquals("Must link to same issue ID", originalIssueId, linkedIssue.id)
        assertEquals("Latest capture should be updated", duplicateCapture.id, linkedIssue.latestCaptureId)
        assertEquals("Origin capture must remain preserved", sampleCapture1.id, linkedIssue.originCaptureId)

        // Verify total issue count in repository is still 1
        var allIssuesCount = 0
        repository.getAllIssues().collect { allIssuesCount = it.size }
        assertEquals("Database must contain exactly 1 issue, not 2", 1, allIssuesCount)
    }

    @Test
    fun deduplication_afterResolution_allowsNewIssueIfNewDistressOccurs() = runBlocking {
        repository.insertCapture(sampleCapture1)
        val firstResult = repository.createOrLinkIssueForCapture(sampleCapture1)
        val firstIssueId = (firstResult as IssueDeduplicationResult.Created).issue.id

        // Resolve first issue
        repository.updateIssueStatus(firstIssueId, IssueStatus.IN_REPAIR)
        repository.updateIssueStatus(firstIssueId, IssueStatus.REINSPECTION)
        repository.updateIssueStatus(firstIssueId, IssueStatus.RESOLVED)

        // Much later, a new distinct distress appears at the same locationKey
        val newCaptureMonthsLater = sampleCapture1.copy(
            id = "cap-new-distress-2027",
            timestamp = sampleCapture1.timestamp + (90L * 24 * 3600 * 1000L)
        )
        repository.insertCapture(newCaptureMonthsLater)

        val newResult = repository.createOrLinkIssueForCapture(newCaptureMonthsLater)
        assertTrue("Resolved issue allows new open issue to be created", newResult is IssueDeduplicationResult.Created)
        val newIssueId = (newResult as IssueDeduplicationResult.Created).issue.id

        assertNotEquals("New issue ID must be distinct", firstIssueId, newIssueId)
    }

    @Test
    fun existingEntities_sessionsCapturesVoiceNotes_remainUnaffected() = runBlocking {
        val session = SessionEntity(id = "sess-100", label = "FOUNDATION AUDIT")
        repository.createSession(session)

        val capture = CaptureEntity(
            id = "cap-100",
            sessionId = "sess-100",
            imagePath = "/data/test.jpg",
            lat = 17.0,
            lng = 78.0,
            timestamp = 1000L,
            severity = "MONITOR",
            confidence = 0.82f,
            locationKey = "te7u0000"
        )
        repository.insertCapture(capture)

        val voiceNote = VoiceNoteEntity(
            id = "vn-100",
            sessionId = "sess-100",
            captureId = "cap-100",
            transcript = "Visible settlement hairline crack"
        )
        repository.insertVoiceNote(voiceNote)

        // Existing entity queries must function identically
        val fetchedSession = repository.getSessionById("sess-100")
        val fetchedCapture = repository.getCaptureById("cap-100")

        assertEquals("FOUNDATION AUDIT", fetchedSession?.label)
        assertEquals("MONITOR", fetchedCapture?.severity)
        assertEquals(0.82f, fetchedCapture?.confidence ?: 0f, 0.001f)
    }

    // --- In-Memory Repository Implementation for Deterministic Unit Testing ---
    class InMemoryTestRepository : SiteSweepRepository {
        val sessions = mutableListOf<SessionEntity>()
        val captures = mutableListOf<CaptureEntity>()
        val voiceNotes = mutableListOf<VoiceNoteEntity>()
        val issues = mutableListOf<IssueEntity>()

        override suspend fun createSession(label: String): SessionEntity {
            val s = SessionEntity(id = UUID.randomUUID().toString(), label = label)
            sessions.add(s)
            return s
        }

        override suspend fun createSession(session: SessionEntity): SessionEntity {
            sessions.add(session)
            return session
        }

        override suspend fun endSession(id: String) {}

        override suspend fun getSessionById(id: String): SessionEntity? = sessions.find { it.id == id }

        override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(sessions.toList())

        override suspend fun deleteSession(id: String) {
            sessions.removeAll { it.id == id }
        }

        override suspend fun insertCapture(capture: CaptureEntity) {
            captures.removeAll { it.id == capture.id }
            captures.add(capture)
        }

        override suspend fun getCaptureById(id: String): CaptureEntity? = captures.find { it.id == id }

        override fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>> =
            flowOf(captures.filter { it.sessionId == sessionId })

        override suspend fun getCapturesByLocationKey(locationKey: String): List<CaptureEntity> =
            captures.filter { it.locationKey == locationKey }

        override fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>> =
            flowOf(captures.filter { it.locationKey == locationKey })

        override fun getAllCaptures(): Flow<List<CaptureEntity>> = flowOf(captures.toList())

        override suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {
            voiceNotes.add(voiceNote)
        }

        override fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>> =
            flowOf(voiceNotes.filter { it.sessionId == sessionId })

        override fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>> =
            flowOf(voiceNotes.filter { it.captureId == captureId })

        // Issue Operations
        override suspend fun insertIssue(issue: IssueEntity) {
            issues.removeAll { it.id == issue.id }
            issues.add(issue)
        }

        override suspend fun updateIssue(issue: IssueEntity) {
            insertIssue(issue)
        }

        override suspend fun getIssueById(id: String): IssueEntity? = issues.find { it.id == id }

        override fun observeIssueById(id: String): Flow<IssueEntity?> =
            flowOf(issues.find { it.id == id })

        override fun getAllIssues(): Flow<List<IssueEntity>> = flowOf(issues.toList())

        override fun getIssuesByStatus(status: IssueStatus): Flow<List<IssueEntity>> =
            flowOf(issues.filter { it.status == status.name })

        override suspend fun getActiveIssueByLocationKey(locationKey: String): IssueEntity? {
            val prefix = if (locationKey.length > 4) locationKey.dropLast(1) else locationKey
            return issues.find {
                (it.locationKey == locationKey || it.locationKey.startsWith(prefix)) &&
                        it.status != IssueStatus.RESOLVED.name
            }
        }

        override suspend fun updateIssueStatus(
            id: String,
            newStatus: IssueStatus,
            resolutionNotes: String?
        ): Boolean {
            val current = getIssueById(id) ?: return false
            if (!IssueStatus.canTransition(current.issueStatus, newStatus)) {
                return false
            }
            val now = System.currentTimeMillis()
            val resolvedAt = if (newStatus == IssueStatus.RESOLVED) now else null
            val updated = current.copy(
                status = newStatus.name,
                updatedAt = now,
                resolvedAt = resolvedAt,
                resolutionNotes = resolutionNotes ?: current.resolutionNotes
            )
            insertIssue(updated)
            return true
        }

        override suspend fun assignIssue(id: String, assignedTo: String): Boolean {
            val current = getIssueById(id) ?: return false
            val newStatus = if (current.issueStatus == IssueStatus.OPEN) IssueStatus.ASSIGNED else current.issueStatus
            val now = System.currentTimeMillis()
            val updated = current.copy(
                assignedTo = assignedTo,
                status = newStatus.name,
                updatedAt = now
            )
            insertIssue(updated)
            return true
        }

        override suspend fun updateIssueNotes(
            id: String,
            engineerNotes: String?,
            resolutionNotes: String?
        ): Boolean {
            val current = getIssueById(id) ?: return false
            val now = System.currentTimeMillis()
            val updated = current.copy(
                engineerNotes = engineerNotes ?: current.engineerNotes,
                resolutionNotes = resolutionNotes ?: current.resolutionNotes,
                updatedAt = now
            )
            insertIssue(updated)
            return true
        }

        override suspend fun attachCaptureToIssue(issueId: String, captureId: String): Boolean {
            val current = getIssueById(issueId) ?: return false
            val now = System.currentTimeMillis()
            val updated = current.copy(
                latestCaptureId = captureId,
                updatedAt = now
            )
            insertIssue(updated)
            return true
        }

        override suspend fun reopenIssue(id: String, reason: String?): Boolean {
            val current = getIssueById(id) ?: return false
            if (!IssueStatus.canReopen(current.issueStatus)) {
                return false
            }
            val now = System.currentTimeMillis()
            val noteAppend = reason?.let { "[REOPENED]: $it" }
            val updatedNotes = listOfNotNull(current.engineerNotes, noteAppend).joinToString("\n\n")

            val updated = current.copy(
                status = IssueStatus.OPEN.name,
                updatedAt = now,
                resolvedAt = null,
                engineerNotes = updatedNotes
            )
            insertIssue(updated)
            return true
        }

        override suspend fun deleteIssue(id: String) {
            issues.removeAll { it.id == id }
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

            val existingActive = getActiveIssueByLocationKey(locationKey)
            if (existingActive != null) {
                val now = System.currentTimeMillis()
                val wasAlreadyLatest = existingActive.latestCaptureId == capture.id
                val updated = existingActive.copy(
                    latestCaptureId = capture.id,
                    updatedAt = now
                )
                insertIssue(updated)
                return IssueDeduplicationResult.LinkedToExisting(updated, wasAlreadyLatest)
            }

            val title = customTitle?.takeIf { it.isNotBlank() }
                ?: "${capture.severity} Distress @ ${locationKey.take(8)}"

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
            insertIssue(newIssue)
            return IssueDeduplicationResult.Created(newIssue)
        }
    }
}
