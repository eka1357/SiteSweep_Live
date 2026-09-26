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

    private val _uiState = MutableStateFlow(RevisitUiState())
    val uiState: StateFlow<RevisitUiState> = _uiState.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    private var observationJob: Job? = null

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
                val trend = calculateDistressTrend(chronological)

                _uiState.value = _uiState.value.copy(
                    captures = chronological,
                    trend = trend,
                    isLoading = false
                )
            }
        }
    }

    private fun rank(severity: String) = when (severity.uppercase(Locale.US)) {
        "STRUCTURAL" -> 2
        "MONITOR" -> 1
        else -> 0
    }

    private fun calculateDistressTrend(captures: List<CaptureEntity>): TrendStatus {
        if (captures.isEmpty()) return TrendStatus.NO_HISTORY

        val ranks = captures.sortedBy { it.timestamp }.map { rank(it.severity) }
        if (ranks.size == 1) {
            return when (ranks[0]) {
                2 -> TrendStatus.WIDENING
                1 -> TrendStatus.MONITORING
                else -> TrendStatus.STABLE
            }
        }

        val first = ranks.first()
        val last = ranks.last()
        return when {
            last > first -> TrendStatus.WIDENING
            last < first -> TrendStatus.REGRESSED
            else -> when (last) {
                2 -> TrendStatus.WIDENING
                1 -> TrendStatus.MONITORING
                else -> TrendStatus.STABLE
            }
        }
    }
}
