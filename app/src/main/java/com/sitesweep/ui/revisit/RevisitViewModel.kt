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

    private val _uiState = MutableStateFlow(RevisitUiState())
    val uiState: StateFlow<RevisitUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

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

    private fun calculateDistressTrend(captures: List<CaptureEntity>): TrendStatus {
        if (captures.size < 2) return TrendStatus.STABLE

        val severities = captures.map { it.severity.uppercase(Locale.US) }
        val first = severities.first()
        val last = severities.last()

        return when {
            last == "STRUCTURAL" && first != "STRUCTURAL" -> TrendStatus.WIDENING
            last == "STRUCTURAL" || last == "MONITOR" && first == "STABLE" -> TrendStatus.MONITORING
            severities.contains("STRUCTURAL") -> TrendStatus.WIDENING
            severities.contains("MONITOR") -> TrendStatus.MONITORING
            else -> TrendStatus.STABLE
        }
    }
}
