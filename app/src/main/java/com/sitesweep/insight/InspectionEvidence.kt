package com.sitesweep.insight

import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.ui.issues.IssueDetailUiState

/**
 * Structured inspection evidence prepared for optional AI insight analysis.
 * Adheres strictly to offline-first and privacy guidelines:
 * - Does NOT send raw camera frames or stream continuous video.
 * - Does NOT upload images.
 * - Only packages discrete numerical, classification, temporal, and trend telemetry.
 */
data class InspectionEvidence(
    val issueId: String? = null,
    val currentCrackProbability: Float,
    val currentSeverity: String,
    val currentClass: String,
    val timestamp: Long = System.currentTimeMillis(),
    val firstObservationTimestamp: Long? = null,
    val latestObservationTimestamp: Long? = null,
    val locationKey: String? = null,
    val historicalObservations: List<HistoricalObservation> = emptyList(),
    val trendStatus: String? = null,
    val userNotes: String? = null,
    val status: String? = null,
    val resolutionContext: String? = null
)

data class HistoricalObservation(
    val timestamp: Long,
    val crackProbability: Float,
    val severity: String,
    val crackClass: String? = null
)

data class AiInsightResult(
    val observation: String,
    val trend: String,
    val suggestedFollowUp: String,
    val dataLimitations: String,
    val rawResponse: String? = null
)

sealed interface AiInsightState {
    data object Idle : AiInsightState
    data object CheckingConnection : AiInsightState
    data class Offline(val message: String = "Internet connection required for AI Insight.") : AiInsightState
    data object Analyzing : AiInsightState
    data class Success(val insight: AiInsightResult) : AiInsightState
    data class Error(val message: String) : AiInsightState
}

/**
 * Transforms an IssueDetailUiState into structured InspectionEvidence
 * without leaking raw data or images.
 */
fun IssueDetailUiState.toInspectionEvidence(): InspectionEvidence? {
    val currentIssue = issue ?: return null
    val targetCapture = latestCapture ?: originCapture ?: historyCaptures.lastOrNull() ?: return null

    val sortedHistory = historyCaptures.sortedBy { it.timestamp }
    val firstObsTime = sortedHistory.firstOrNull()?.timestamp ?: currentIssue.createdAt
    val latestObsTime = targetCapture.timestamp

    val historicalObs = sortedHistory.map { cap ->
        HistoricalObservation(
            timestamp = cap.timestamp,
            crackProbability = cap.confidence,
            severity = cap.severity,
            crackClass = when (cap.severity) {
                "STRUCTURAL" -> "STRUCTURAL"
                "MONITOR" -> "HAIRLINE"
                else -> "NONE"
            }
        )
    }

    val resContext = buildString {
        if (!currentIssue.assignedTo.isNullOrBlank()) {
            append("Assigned to: ${currentIssue.assignedTo}. ")
        }
        if (currentIssue.status == "REINSPECTION") {
            append("Status: Scheduled for reinspection. ")
        }
        if (currentIssue.resolvedAt != null) {
            append("Resolved at timestamp ${currentIssue.resolvedAt}. ")
        }
        if (!currentIssue.resolutionNotes.isNullOrBlank()) {
            append("Resolution notes: ${currentIssue.resolutionNotes}. ")
        }
    }.trim().ifBlank { null }

    val combinedNotes = currentIssue.engineerNotes?.trim()?.ifBlank { null }
    val prob = targetCapture.confidence
    val sev = targetCapture.severity
    val cls = when {
        sev.equals("STRUCTURAL", ignoreCase = true) -> "STRUCTURAL"
        sev.equals("MONITOR", ignoreCase = true) || prob >= 0.5f -> "HAIRLINE"
        else -> "NONE"
    }

    return InspectionEvidence(
        issueId = currentIssue.id,
        currentCrackProbability = prob,
        currentSeverity = sev,
        currentClass = cls,
        timestamp = targetCapture.timestamp,
        firstObservationTimestamp = firstObsTime,
        latestObservationTimestamp = latestObsTime,
        locationKey = currentIssue.locationKey,
        historicalObservations = historicalObs,
        trendStatus = trend.name,
        userNotes = combinedNotes,
        status = currentIssue.status,
        resolutionContext = resContext
    )
}
