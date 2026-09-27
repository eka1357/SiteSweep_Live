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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel powering the Engineer Operations Dashboard.
 * Reactively combines local Room issues and captures into aggregated summaries and active issue models.
 */
class DashboardViewModel @JvmOverloads constructor(
    application: Application,
    customRepository: SiteSweepRepository? = null
) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository =
        customRepository ?: (application as SiteSweepApplication).repository

    private val _selectedFilter = MutableStateFlow<IssueStatus?>(null)
    val selectedFilter: StateFlow<IssueStatus?> = _selectedFilter

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllIssues(),
        repository.getAllCaptures(),
        _selectedFilter
    ) { issues, captures, filter ->
        buildDashboardState(issues, captures, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(isLoading = true)
    )

    fun setFilter(status: IssueStatus?) {
        _selectedFilter.value = status
    }

    private fun buildDashboardState(
        issues: List<IssueEntity>,
        captures: List<CaptureEntity>,
        filter: IssueStatus?
    ): DashboardUiState {
        val capturesMap = captures.associateBy { it.id }
        val capturesByLocation = captures.groupBy { it.locationKey }

        val issueItems = issues.map { issue ->
            val latestCap = capturesMap[issue.latestCaptureId] ?: capturesMap[issue.originCaptureId]
            val locationCaptures = capturesByLocation[issue.locationKey]?.sortedBy { it.timestamp } ?: emptyList()
            val trend = TrendStatus.calculate(locationCaptures)
            val severity = latestCap?.severity ?: "STABLE"
            val crackProb = latestCap?.confidence ?: 0f
            val timestamp = latestCap?.timestamp ?: issue.updatedAt

            IssueItemUiModel(
                id = issue.id,
                title = issue.title,
                locationKey = issue.locationKey,
                status = issue.issueStatus,
                severity = severity,
                crackProbability = crackProb,
                trend = trend,
                lastInspectedTimestamp = timestamp,
                assignedTo = issue.assignedTo,
                originCaptureId = issue.originCaptureId,
                latestCaptureId = issue.latestCaptureId
            )
        }

        val sortedIssueItems = issueItems.sortedWith(
            compareBy<IssueItemUiModel> { it.status == IssueStatus.RESOLVED }
                .thenByDescending { it.severity == "STRUCTURAL" }
                .thenByDescending { it.crackProbability }
                .thenByDescending { it.lastInspectedTimestamp }
        )

        val summary = DashboardSummary(
            openCount = issues.count { it.issueStatus == IssueStatus.OPEN },
            assignedCount = issues.count { it.issueStatus == IssueStatus.ASSIGNED },
            inRepairCount = issues.count { it.issueStatus == IssueStatus.IN_REPAIR },
            reinspectionCount = issues.count { it.issueStatus == IssueStatus.REINSPECTION },
            resolvedCount = issues.count { it.issueStatus == IssueStatus.RESOLVED }
        )

        val filtered = if (filter == null) {
            sortedIssueItems
        } else {
            sortedIssueItems.filter { it.status == filter }
        }

        return DashboardUiState(
            summary = summary,
            allIssues = sortedIssueItems,
            filteredIssues = filtered,
            selectedFilter = filter,
            isLoading = false
        )
    }
}
