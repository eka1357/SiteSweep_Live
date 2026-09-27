package com.sitesweep.ui.issues

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.data.repository.SiteSweepRepository
import com.sitesweep.ui.revisit.TrendStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing Issue Detail inspection view, closed-loop verification,
 * before/after reinspection comparisons, and valid state transitions.
 */
class IssueDetailViewModel @JvmOverloads constructor(
    application: Application,
    customRepository: SiteSweepRepository? = null
) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository =
        customRepository ?: (application as SiteSweepApplication).repository

    private val _uiState = MutableStateFlow(IssueDetailUiState())
    val uiState: StateFlow<IssueDetailUiState> = _uiState.asStateFlow()

    private var issueObservationJob: Job? = null
    private var locationCapturesJob: Job? = null
    private var currentIssueId: String? = null

    fun loadIssue(issueId: String) {
        currentIssueId = issueId
        _uiState.update { it.copy(isLoading = true, actionError = null, actionSuccessMessage = null) }

        issueObservationJob?.cancel()
        issueObservationJob = viewModelScope.launch {
            repository.observeIssueById(issueId).collect { issue ->
                if (issue == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            actionError = "Issue not found"
                        )
                    }
                    return@collect
                }

                val originCap = repository.getCaptureById(issue.originCaptureId)
                val latestCap = issue.latestCaptureId?.let { repository.getCaptureById(it) } ?: originCap

                _uiState.update {
                    it.copy(
                        issue = issue,
                        originCapture = originCap,
                        latestCapture = latestCap,
                        isLoading = false
                    )
                }

                observeLocationHistory(issue.locationKey, issue.originCaptureId, issue.latestCaptureId)
            }
        }
    }

    private fun observeLocationHistory(locationKey: String, originCaptureId: String, latestCaptureId: String?) {
        locationCapturesJob?.cancel()
        locationCapturesJob = viewModelScope.launch {
            repository.observeCapturesByLocationKey(locationKey).collect { rawCaptures ->
                val chronological = rawCaptures.sortedBy { it.timestamp }
                val trend = TrendStatus.calculate(chronological)

                val prev = chronological.find { it.id == originCaptureId }
                    ?: chronological.firstOrNull()
                val latest = chronological.find { it.id == latestCaptureId }
                    ?: chronological.lastOrNull()

                _uiState.update {
                    it.copy(
                        historyCaptures = chronological,
                        trend = trend,
                        previousCaptureForReinspection = prev,
                        latestReinspectionCapture = latest
                    )
                }
            }
        }
    }

    fun assignIssue(assignedTo: String) {
        val id = currentIssueId ?: return
        if (assignedTo.isBlank()) {
            _uiState.update { it.copy(actionError = "Assignee name cannot be blank") }
            return
        }

        viewModelScope.launch {
            val success = repository.assignIssue(id, assignedTo.trim())
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Assigned to ${assignedTo.trim()}", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Failed to assign issue") }
            }
        }
    }

    fun startRepair() {
        val id = currentIssueId ?: return
        viewModelScope.launch {
            val success = repository.updateIssueStatus(id, IssueStatus.IN_REPAIR)
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Status changed to IN REPAIR", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Invalid transition to IN REPAIR") }
            }
        }
    }

    fun scheduleReinspection() {
        val id = currentIssueId ?: return
        viewModelScope.launch {
            val success = repository.updateIssueStatus(id, IssueStatus.REINSPECTION)
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Status changed to REINSPECTION", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Invalid transition to REINSPECTION") }
            }
        }
    }

    fun returnToRepair(notes: String? = null) {
        val id = currentIssueId ?: return
        viewModelScope.launch {
            val success = repository.updateIssueStatus(id, IssueStatus.IN_REPAIR)
            if (success) {
                if (!notes.isNullOrBlank()) {
                    repository.updateIssueNotes(id, engineerNotes = notes.trim())
                }
                _uiState.update { it.copy(actionSuccessMessage = "Returned to IN REPAIR for corrective action", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Invalid transition to IN REPAIR") }
            }
        }
    }

    fun markResolved(resolutionNotes: String) {
        val id = currentIssueId ?: return
        if (resolutionNotes.isBlank()) {
            _uiState.update { it.copy(actionError = "Resolution note is strictly required") }
            return
        }

        viewModelScope.launch {
            val success = repository.updateIssueStatus(
                id = id,
                newStatus = IssueStatus.RESOLVED,
                resolutionNotes = resolutionNotes.trim()
            )
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Issue marked RESOLVED by engineer", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Cannot mark resolved from current state") }
            }
        }
    }

    fun reopenIssue(reason: String) {
        val id = currentIssueId ?: return
        if (reason.isBlank()) {
            _uiState.update { it.copy(actionError = "Reopen reason is required") }
            return
        }

        viewModelScope.launch {
            val success = repository.reopenIssue(id, reason.trim())
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Issue reopened as OPEN", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Cannot reopen non-resolved issue") }
            }
        }
    }

    fun attachCapture(captureId: String) {
        val id = currentIssueId ?: return
        viewModelScope.launch {
            val success = repository.attachCaptureToIssue(id, captureId)
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Reinspection capture attached", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Failed to attach capture") }
            }
        }
    }

    fun updateNotes(engineerNotes: String) {
        val id = currentIssueId ?: return
        viewModelScope.launch {
            val success = repository.updateIssueNotes(id, engineerNotes = engineerNotes.trim())
            if (success) {
                _uiState.update { it.copy(actionSuccessMessage = "Notes updated", actionError = null) }
            } else {
                _uiState.update { it.copy(actionError = "Failed to update notes") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(actionError = null, actionSuccessMessage = null) }
    }
}
