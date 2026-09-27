package com.sitesweep

import android.app.Application
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.model.IssueAction
import com.sitesweep.data.model.IssueDeduplicationResult
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.data.model.allowedActions
import com.sitesweep.data.repository.SiteSweepRepository
import com.sitesweep.ui.issues.CreateIssueViewModel
import com.sitesweep.ui.issues.DashboardViewModel
import com.sitesweep.ui.issues.IssueDetailViewModel
import com.sitesweep.ui.revisit.TrendStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class EngineerDashboardTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: DashboardMockRepository
    private lateinit var mockApp: Application

    private val cap1 = CaptureEntity(
        id = "cap-c12-orig",
        sessionId = "sess-01",
        imagePath = "/data/cap1.jpg",
        lat = 17.44,
        lng = 78.37,
        timestamp = 1000L,
        severity = "STRUCTURAL",
        confidence = 0.94f,
        locationKey = "te7u0x99"
    )

    private val cap2 = CaptureEntity(
        id = "cap-c12-reinspect",
        sessionId = "sess-02",
        imagePath = "/data/cap2.jpg",
        lat = 17.44,
        lng = 78.37,
        timestamp = 2000L,
        severity = "MONITOR",
        confidence = 0.72f,
        locationKey = "te7u0x99"
    )

    private val cap3 = CaptureEntity(
        id = "cap-c12-resolved",
        sessionId = "sess-03",
        imagePath = "/data/cap3.jpg",
        lat = 17.44,
        lng = 78.37,
        timestamp = 3000L,
        severity = "STABLE",
        confidence = 0.18f,
        locationKey = "te7u0x99"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = DashboardMockRepository()
        mockApp = Application()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun dashboard_summaryCounts_computedAccurately() = runBlocking {
        // Seed 1 OPEN, 1 IN_REPAIR, 1 RESOLVED
        val issue1 = IssueEntity(
            id = "ISS-001",
            originCaptureId = cap1.id,
            latestCaptureId = cap1.id,
            locationKey = cap1.locationKey,
            title = "Column C12",
            status = IssueStatus.OPEN.name
        )
        val issue2 = IssueEntity(
            id = "ISS-002",
            originCaptureId = cap2.id,
            latestCaptureId = cap2.id,
            locationKey = "te7u0x88",
            title = "Wall W04",
            status = IssueStatus.IN_REPAIR.name,
            assignedTo = "Civil Unit"
        )
        val issue3 = IssueEntity(
            id = "ISS-003",
            originCaptureId = cap3.id,
            latestCaptureId = cap3.id,
            locationKey = "te7u0x77",
            title = "Beam B07",
            status = IssueStatus.RESOLVED.name,
            resolvedAt = 5000L,
            resolutionNotes = "Fixed"
        )

        mockRepository.insertCapture(cap1)
        mockRepository.insertCapture(cap2)
        mockRepository.insertCapture(cap3)
        mockRepository.insertIssue(issue1)
        mockRepository.insertIssue(issue2)
        mockRepository.insertIssue(issue3)

        val vm = DashboardViewModel(mockApp, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Total issues must be 3", 3, state.allIssues.size)
        assertEquals("Open count must be 1", 1, state.summary.openCount)
        assertEquals("In repair count must be 1", 1, state.summary.inRepairCount)
        assertEquals("Resolved count must be 1", 1, state.summary.resolvedCount)
        assertEquals("Assigned count must be 0", 0, state.summary.assignedCount)
        assertEquals("Reinspection count must be 0", 0, state.summary.reinspectionCount)
    }

    @Test
    fun dashboard_filterByStatus_filtersList() = runBlocking {
        val issue1 = IssueEntity(id = "ISS-01", originCaptureId = cap1.id, locationKey = "loc1", title = "T1", status = IssueStatus.OPEN.name)
        val issue2 = IssueEntity(id = "ISS-02", originCaptureId = cap2.id, locationKey = "loc2", title = "T2", status = IssueStatus.IN_REPAIR.name)

        mockRepository.insertIssue(issue1)
        mockRepository.insertIssue(issue2)

        val vm = DashboardViewModel(mockApp, mockRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.filteredIssues.size)

        vm.setFilter(IssueStatus.OPEN)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.uiState.value.filteredIssues.size)
        assertEquals("ISS-01", vm.uiState.value.filteredIssues[0].id)
    }

    @Test
    fun issueDetail_loadAndValidTransitions() = runBlocking {
        mockRepository.insertCapture(cap1)
        val issue = IssueEntity(
            id = "ISS-TEST-01",
            originCaptureId = cap1.id,
            latestCaptureId = cap1.id,
            locationKey = cap1.locationKey,
            title = "Shear Distress Column C12",
            status = IssueStatus.OPEN.name
        )
        mockRepository.insertIssue(issue)

        val vm = IssueDetailViewModel(mockApp, mockRepository)
        vm.loadIssue("ISS-TEST-01")
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = vm.uiState.value.issue
        assertNotNull(loaded)
        assertEquals("Shear Distress Column C12", loaded?.title)
        assertEquals(IssueStatus.OPEN, loaded?.issueStatus)

        // Assign
        vm.assignIssue("Epoxy Team Alpha")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(IssueStatus.ASSIGNED, mockRepository.getIssueById("ISS-TEST-01")?.issueStatus)
        assertEquals("Epoxy Team Alpha", mockRepository.getIssueById("ISS-TEST-01")?.assignedTo)

        // Start Repair
        vm.startRepair()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(IssueStatus.IN_REPAIR, mockRepository.getIssueById("ISS-TEST-01")?.issueStatus)

        // Schedule Reinspection
        vm.scheduleReinspection()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(IssueStatus.REINSPECTION, mockRepository.getIssueById("ISS-TEST-01")?.issueStatus)
    }

    @Test
    fun issueDetail_reinspectionCaptureAttachment_tracksDelta() = runBlocking {
        mockRepository.insertCapture(cap1)
        mockRepository.insertCapture(cap2)

        val issue = IssueEntity(
            id = "ISS-REINSPECT",
            originCaptureId = cap1.id,
            latestCaptureId = cap1.id,
            locationKey = cap1.locationKey,
            title = "Crack Column B2",
            status = IssueStatus.REINSPECTION.name
        )
        mockRepository.insertIssue(issue)

        val vm = IssueDetailViewModel(mockApp, mockRepository)
        vm.loadIssue("ISS-REINSPECT")
        testDispatcher.scheduler.advanceUntilIdle()

        // Attach reinspection capture (cap2: 72% hairline)
        vm.attachCapture(cap2.id)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = mockRepository.getIssueById("ISS-REINSPECT")
        assertEquals(cap2.id, updated?.latestCaptureId)
        assertEquals(cap1.id, updated?.originCaptureId)

        // Reinspection indicated persistent hairline -> Return to repair for second coat
        vm.returnToRepair(notes = "Second coat required")
        testDispatcher.scheduler.advanceUntilIdle()

        val returnedIssue = mockRepository.getIssueById("ISS-REINSPECT")
        assertEquals(IssueStatus.IN_REPAIR, returnedIssue?.issueStatus)
        assertEquals("Second coat required", returnedIssue?.engineerNotes)
    }

    @Test
    fun issueDetail_markResolved_requiresResolutionNotesAndSetsTimestamp() = runBlocking {
        mockRepository.insertCapture(cap1)
        val issue = IssueEntity(
            id = "ISS-RESOLVE-ME",
            originCaptureId = cap1.id,
            latestCaptureId = cap1.id,
            locationKey = cap1.locationKey,
            title = "Lintel Crack",
            status = IssueStatus.REINSPECTION.name
        )
        mockRepository.insertIssue(issue)

        val vm = IssueDetailViewModel(mockApp, mockRepository)
        vm.loadIssue("ISS-RESOLVE-ME")
        testDispatcher.scheduler.advanceUntilIdle()

        // Blank resolution note should fail
        vm.markResolved("   ")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull("Error message should be set for blank note", vm.uiState.value.actionError)
        assertEquals(IssueStatus.REINSPECTION, mockRepository.getIssueById("ISS-RESOLVE-ME")?.issueStatus)

        // Valid resolution note succeeds
        vm.markResolved("Epoxy pressure injected; surface ground flush.")
        testDispatcher.scheduler.advanceUntilIdle()

        val resolved = mockRepository.getIssueById("ISS-RESOLVE-ME")
        assertEquals(IssueStatus.RESOLVED, resolved?.issueStatus)
        assertEquals("Epoxy pressure injected; surface ground flush.", resolved?.resolutionNotes)
        assertNotNull(resolved?.resolvedAt)
    }

    @Test
    fun issueDetail_reopenResolvedIssue_succeedsWithAuditReason() = runBlocking {
        val issue = IssueEntity(
            id = "ISS-RESOLVED",
            originCaptureId = cap1.id,
            latestCaptureId = cap1.id,
            locationKey = cap1.locationKey,
            title = "Basement Wall",
            status = IssueStatus.RESOLVED.name,
            resolvedAt = 1000L,
            resolutionNotes = "Sealed"
        )
        mockRepository.insertIssue(issue)

        val vm = IssueDetailViewModel(mockApp, mockRepository)
        vm.loadIssue("ISS-RESOLVED")
        testDispatcher.scheduler.advanceUntilIdle()

        // Normal transition from RESOLVED to IN_REPAIR must be rejected
        vm.startRepair()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(vm.uiState.value.actionError)
        assertEquals(IssueStatus.RESOLVED, mockRepository.getIssueById("ISS-RESOLVED")?.issueStatus)

        // Explicit reopen succeeds
        vm.reopenIssue("Micro-cracking reappeared after monsoon rains")
        testDispatcher.scheduler.advanceUntilIdle()

        val reopened = mockRepository.getIssueById("ISS-RESOLVED")
        assertEquals(IssueStatus.OPEN, reopened?.issueStatus)
        assertNull(reopened?.resolvedAt)
        assertTrue(reopened?.engineerNotes?.contains("Micro-cracking reappeared") == true)
    }

    @Test
    fun createIssueViewModel_promotesCandidateCapture() = runBlocking {
        mockRepository.insertCapture(cap1)

        val vm = CreateIssueViewModel(mockApp, mockRepository)
        vm.loadCandidateCapture(cap1.id)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(cap1.id, vm.uiState.value.candidateCapture?.id)
        assertTrue(vm.uiState.value.title.isNotBlank())

        var createdId: String? = null
        vm.updateTitle("Critical Column C12 Defect")
        vm.updateNotes("Immediate stabilization required")
        vm.createIssue { newId -> createdId = newId }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(createdId)
        val created = mockRepository.getIssueById(createdId!!)
        assertEquals("Critical Column C12 Defect", created?.title)
        assertEquals("Immediate stabilization required", created?.engineerNotes)
        assertEquals(IssueStatus.OPEN, created?.issueStatus)
    }

    @Test
    fun actionVisibility_validActionsVisibleForStatus() = runBlocking {
        // OPEN: [Assign], [Start Repair]
        val openActions = IssueStatus.OPEN.allowedActions()
        assertEquals(setOf(IssueAction.ASSIGN, IssueAction.START_REPAIR), openActions)

        // ASSIGNED: [Start Repair]
        val assignedActions = IssueStatus.ASSIGNED.allowedActions()
        assertEquals(setOf(IssueAction.START_REPAIR), assignedActions)

        // IN_REPAIR: [Schedule Reinspection]
        val inRepairActions = IssueStatus.IN_REPAIR.allowedActions()
        assertEquals(setOf(IssueAction.SCHEDULE_REINSPECTION), inRepairActions)

        // REINSPECTION: [Start Reinspection], [Mark Resolved], [Return to Repair]
        val reinspectionActions = IssueStatus.REINSPECTION.allowedActions()
        assertEquals(
            setOf(IssueAction.START_REINSPECTION, IssueAction.MARK_RESOLVED, IssueAction.RETURN_TO_REPAIR),
            reinspectionActions
        )

        // RESOLVED: [Reopen Issue]
        val resolvedActions = IssueStatus.RESOLVED.allowedActions()
        assertEquals(setOf(IssueAction.REOPEN), resolvedActions)
    }

    @Test
    fun actionVisibility_invalidActionsNotShown() = runBlocking {
        // In OPEN state, scheduling reinspection or marking resolved must not be shown
        assertFalse(IssueStatus.OPEN.allowedActions().contains(IssueAction.SCHEDULE_REINSPECTION))
        assertFalse(IssueStatus.OPEN.allowedActions().contains(IssueAction.MARK_RESOLVED))
        assertFalse(IssueStatus.OPEN.allowedActions().contains(IssueAction.START_REINSPECTION))
        assertFalse(IssueStatus.OPEN.allowedActions().contains(IssueAction.REOPEN))

        // In ASSIGNED state, assigning again or marking resolved must not be shown
        assertFalse(IssueStatus.ASSIGNED.allowedActions().contains(IssueAction.ASSIGN))
        assertFalse(IssueStatus.ASSIGNED.allowedActions().contains(IssueAction.MARK_RESOLVED))
        assertFalse(IssueStatus.ASSIGNED.allowedActions().contains(IssueAction.REOPEN))

        // In IN_REPAIR state, start repair or mark resolved must not be shown
        assertFalse(IssueStatus.IN_REPAIR.allowedActions().contains(IssueAction.START_REPAIR))
        assertFalse(IssueStatus.IN_REPAIR.allowedActions().contains(IssueAction.MARK_RESOLVED))
        assertFalse(IssueStatus.IN_REPAIR.allowedActions().contains(IssueAction.START_REINSPECTION))

        // In REINSPECTION state, assign or start repair must not be shown
        assertFalse(IssueStatus.REINSPECTION.allowedActions().contains(IssueAction.ASSIGN))
        assertFalse(IssueStatus.REINSPECTION.allowedActions().contains(IssueAction.START_REPAIR))
        assertFalse(IssueStatus.REINSPECTION.allowedActions().contains(IssueAction.REOPEN))

        // In RESOLVED state, direct repairs or resolutions must not be shown
        assertFalse(IssueStatus.RESOLVED.allowedActions().contains(IssueAction.START_REPAIR))
        assertFalse(IssueStatus.RESOLVED.allowedActions().contains(IssueAction.MARK_RESOLVED))
        assertFalse(IssueStatus.RESOLVED.allowedActions().contains(IssueAction.START_REINSPECTION))
    }

    @Test
    fun issueDetail_invalidTransitionsRejectedByViewModel() = runBlocking {
        mockRepository.insertCapture(cap1)
        val issue = IssueEntity(
            id = "ISS-TRANS-TEST",
            originCaptureId = cap1.id,
            latestCaptureId = cap1.id,
            locationKey = cap1.locationKey,
            title = "Test Issue",
            status = IssueStatus.OPEN.name
        )
        mockRepository.insertIssue(issue)

        val vm = IssueDetailViewModel(mockApp, mockRepository)
        vm.loadIssue("ISS-TRANS-TEST")
        testDispatcher.scheduler.advanceUntilIdle()

        // UiState reflects OPEN allowed actions
        assertEquals(setOf(IssueAction.ASSIGN, IssueAction.START_REPAIR), vm.uiState.value.allowedActions)
        assertFalse(vm.uiState.value.allowedActions.contains(IssueAction.MARK_RESOLVED))

        // Attempting to directly schedule reinspection from OPEN must be rejected
        vm.scheduleReinspection()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull("actionError must be reported for invalid transition", vm.uiState.value.actionError)
        assertEquals(IssueStatus.OPEN, mockRepository.getIssueById("ISS-TRANS-TEST")?.issueStatus)
    }

    // --- Mock In-Memory Repository for ViewModel Unit Tests ---
    class DashboardMockRepository : SiteSweepRepository {
        private val sessions = mutableListOf<SessionEntity>()
        private val captures = mutableListOf<CaptureEntity>()
        private val issues = mutableListOf<IssueEntity>()

        private val issuesFlow = MutableStateFlow<List<IssueEntity>>(emptyList())
        private val capturesFlow = MutableStateFlow<List<CaptureEntity>>(emptyList())

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
        override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(sessions)
        override suspend fun deleteSession(id: String) {
            sessions.removeAll { it.id == id }
        }

        override suspend fun insertCapture(capture: CaptureEntity) {
            captures.removeAll { it.id == capture.id }
            captures.add(capture)
            capturesFlow.value = captures.toList()
        }

        override suspend fun getCaptureById(id: String): CaptureEntity? = captures.find { it.id == id }
        override fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>> =
            flowOf(captures.filter { it.sessionId == sessionId })

        override suspend fun getCapturesByLocationKey(locationKey: String): List<CaptureEntity> =
            captures.filter { it.locationKey == locationKey }

        override fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>> =
            flowOf(captures.filter { it.locationKey == locationKey })

        override fun getAllCaptures(): Flow<List<CaptureEntity>> = capturesFlow.asStateFlow()

        override suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {}
        override fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>> = flowOf(emptyList())
        override fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>> = flowOf(emptyList())

        override suspend fun insertIssue(issue: IssueEntity) {
            issues.removeAll { it.id == issue.id }
            issues.add(issue)
            issuesFlow.value = issues.toList()
        }

        override suspend fun updateIssue(issue: IssueEntity) {
            insertIssue(issue)
        }

        override suspend fun getIssueById(id: String): IssueEntity? = issues.find { it.id == id }
        override fun observeIssueById(id: String): Flow<IssueEntity?> =
            flowOf(issues.find { it.id == id })

        override fun getAllIssues(): Flow<List<IssueEntity>> = issuesFlow.asStateFlow()
        override fun getIssuesByStatus(status: IssueStatus): Flow<List<IssueEntity>> =
            flowOf(issues.filter { it.status == status.name })

        override suspend fun getActiveIssueByLocationKey(locationKey: String): IssueEntity? =
            issues.find { it.locationKey == locationKey && it.status != IssueStatus.RESOLVED.name }

        override suspend fun updateIssueStatus(
            id: String,
            newStatus: IssueStatus,
            resolutionNotes: String?
        ): Boolean {
            val cur = getIssueById(id) ?: return false
            if (!IssueStatus.canTransition(cur.issueStatus, newStatus)) return false
            val now = System.currentTimeMillis()
            val updated = cur.copy(
                status = newStatus.name,
                updatedAt = now,
                resolvedAt = if (newStatus == IssueStatus.RESOLVED) now else null,
                resolutionNotes = resolutionNotes ?: cur.resolutionNotes
            )
            insertIssue(updated)
            return true
        }

        override suspend fun assignIssue(id: String, assignedTo: String): Boolean {
            val cur = getIssueById(id) ?: return false
            val newStatus = if (cur.issueStatus == IssueStatus.OPEN) IssueStatus.ASSIGNED else cur.issueStatus
            val updated = cur.copy(
                assignedTo = assignedTo,
                status = newStatus.name,
                updatedAt = System.currentTimeMillis()
            )
            insertIssue(updated)
            return true
        }

        override suspend fun updateIssueNotes(
            id: String,
            engineerNotes: String?,
            resolutionNotes: String?
        ): Boolean {
            val cur = getIssueById(id) ?: return false
            val updated = cur.copy(
                engineerNotes = engineerNotes ?: cur.engineerNotes,
                resolutionNotes = resolutionNotes ?: cur.resolutionNotes,
                updatedAt = System.currentTimeMillis()
            )
            insertIssue(updated)
            return true
        }

        override suspend fun attachCaptureToIssue(issueId: String, captureId: String): Boolean {
            val cur = getIssueById(issueId) ?: return false
            val updated = cur.copy(
                latestCaptureId = captureId,
                updatedAt = System.currentTimeMillis()
            )
            insertIssue(updated)
            return true
        }

        override suspend fun reopenIssue(id: String, reason: String?): Boolean {
            val cur = getIssueById(id) ?: return false
            if (!IssueStatus.canReopen(cur.issueStatus)) return false
            val now = System.currentTimeMillis()
            val noteAppend = reason?.let { "[REOPENED]: $it" }
            val updatedNotes = listOfNotNull(cur.engineerNotes, noteAppend).joinToString("\n\n")
            val updated = cur.copy(
                status = IssueStatus.OPEN.name,
                resolvedAt = null,
                updatedAt = now,
                engineerNotes = updatedNotes
            )
            insertIssue(updated)
            return true
        }

        override suspend fun deleteIssue(id: String) {
            issues.removeAll { it.id == id }
            issuesFlow.value = issues.toList()
        }

        override suspend fun createOrLinkIssueForCapture(
            capture: CaptureEntity,
            customTitle: String?,
            notes: String?
        ): IssueDeduplicationResult {
            val existing = getActiveIssueByLocationKey(capture.locationKey)
            if (existing != null) {
                val updated = existing.copy(
                    latestCaptureId = capture.id,
                    updatedAt = System.currentTimeMillis()
                )
                insertIssue(updated)
                return IssueDeduplicationResult.LinkedToExisting(updated, false)
            }

            val newIssue = IssueEntity(
                id = UUID.randomUUID().toString(),
                originCaptureId = capture.id,
                latestCaptureId = capture.id,
                locationKey = capture.locationKey,
                title = customTitle ?: "Issue @ ${capture.locationKey}",
                status = IssueStatus.OPEN.name,
                engineerNotes = notes,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            insertIssue(newIssue)
            return IssueDeduplicationResult.Created(newIssue)
        }
    }
}
