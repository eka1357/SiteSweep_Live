package com.sitesweep.ui.revisit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * ViewModel querying prior captures at a given locationKey geohash.
 * Directly runs the indexed Room locationKey query to surface chronological drift.
 */
class RevisitViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository = (application as SiteSweepApplication).repository
    val exporter = com.sitesweep.report.SessionExporter(application, repository)
    val aiInsightManager = com.sitesweep.insight.AiInsightManager(application)
    val aiInsightState = aiInsightManager.state

    private val _uiState = MutableStateFlow(RevisitUiState())
    val uiState: StateFlow<RevisitUiState> = _uiState.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private var observationJob: Job? = null

    fun requestAiInsight(userNotes: String? = null) {
        val captures = _uiState.value.captures
        val target = captures.find { it.id == _uiState.value.targetCaptureId } ?: captures.lastOrNull()
        val previous = if (target != null) captures.filter { it.id != target.id } else emptyList()

        val evidence = com.sitesweep.insight.InspectionEvidence(
            currentCrackProbability = target?.confidence ?: 0f,
            currentSeverity = target?.severity ?: "STABLE",
            currentClass = target?.severity ?: "STABLE",
            timestamp = target?.timestamp ?: System.currentTimeMillis(),
            locationKey = _uiState.value.locationKey,
            historicalObservations = previous.map {
                com.sitesweep.insight.HistoricalObservation(
                    timestamp = it.timestamp,
                    crackProbability = it.confidence,
                    severity = it.severity
                )
            },
            trendStatus = _uiState.value.trend.label,
            userNotes = userNotes
        )
        aiInsightManager.requestInsight(evidence, viewModelScope)
    }

    fun dismissAiInsight() {
        aiInsightManager.reset()
    }

    fun exportSession(onComplete: ((java.io.File) -> Unit)? = null) {
        val currentSessionId = _uiState.value.captures.find { it.id == _uiState.value.targetCaptureId }?.sessionId
            ?: _uiState.value.captures.lastOrNull()?.sessionId

        if (currentSessionId == null) {
            _exportStatus.value = "NO CAPTURES AVAILABLE TO EXPORT"
            return
        }

        viewModelScope.launch {
            try {
                val result = exporter.exportSession(currentSessionId)
                _exportStatus.value = "EXPORTED TO: ${result.exportDirectory.name}"
                onComplete?.invoke(result.exportDirectory)
            } catch (e: Exception) {
                _exportStatus.value = "EXPORT FAILED: ${e.message}"
            }
        }
    }

    fun clearExportStatus() {
        _exportStatus.value = null
    }

    fun loadLocationHistory(locationKey: String, targetCaptureId: String? = null) {
        _uiState.value = _uiState.value.copy(
            locationKey = locationKey,
            targetCaptureId = targetCaptureId,
            isLoading = true
        )

        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observeCapturesByLocationKey(locationKey).collect { rawCaptures ->
                // Sort chronologically (oldest to newest) to display progression
                val chronological = rawCaptures.sortedBy { it.timestamp }
                val trend = TrendStatus.calculate(chronological)

                _uiState.value = _uiState.value.copy(
                    captures = chronological,
                    trend = trend,
                    isLoading = false
                )
            }
        }
    }
}
