package com.sitesweep.ui.issues

import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.model.IssueAction
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.data.model.allowedActions
import com.sitesweep.ui.revisit.TrendStatus

/**
 * UI presentation model for an Issue in the Engineer Dashboard list.
 */
data class IssueItemUiModel(
    val id: String,
    val title: String,
    val locationKey: String,
    val status: IssueStatus,
    val severity: String,
    val crackProbability: Float,
    val trend: TrendStatus,
    val lastInspectedTimestamp: Long,
    val assignedTo: String?,
    val originCaptureId: String,
    val latestCaptureId: String?
)

/**
 * Summary counts displayed in top-level dashboard metrics cards.
 */
data class DashboardSummary(
    val openCount: Int = 0,
    val assignedCount: Int = 0,
    val inRepairCount: Int = 0,
    val reinspectionCount: Int = 0,
    val resolvedCount: Int = 0
) {
    val totalCount: Int
        get() = openCount + assignedCount + inRepairCount + reinspectionCount + resolvedCount
}

/**
 * State for the main Engineer Dashboard.
 */
data class DashboardUiState(
    val summary: DashboardSummary = DashboardSummary(),
    val allIssues: List<IssueItemUiModel> = emptyList(),
    val filteredIssues: List<IssueItemUiModel> = emptyList(),
    val selectedFilter: IssueStatus? = null,
    val isLoading: Boolean = false
)

/**
 * State for the Issue Detail view supporting closed-loop verification.
 */
data class IssueDetailUiState(
    val issue: IssueEntity? = null,
    val originCapture: CaptureEntity? = null,
    val latestCapture: CaptureEntity? = null,
    val historyCaptures: List<CaptureEntity> = emptyList(),
    val trend: TrendStatus = TrendStatus.STABLE,
    val isLoading: Boolean = true,
    val actionError: String? = null,
    val actionSuccessMessage: String? = null,
    val previousCaptureForReinspection: CaptureEntity? = null,
    val latestReinspectionCapture: CaptureEntity? = null
) {
    val allowedActions: Set<IssueAction>
        get() = issue?.issueStatus?.allowedActions() ?: emptySet()
}
