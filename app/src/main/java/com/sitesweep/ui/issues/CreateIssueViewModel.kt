package com.sitesweep.ui.issues

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.model.IssueDeduplicationResult
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class CreateIssueUiState(
    val candidateCapture: CaptureEntity? = null,
    val title: String = "",
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val creationResult: IssueDeduplicationResult? = null
)

class CreateIssueViewModel @JvmOverloads constructor(
    application: Application,
    customRepository: SiteSweepRepository? = null
) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository =
        customRepository ?: (application as SiteSweepApplication).repository

    private val _uiState = MutableStateFlow(CreateIssueUiState())
    val uiState: StateFlow<CreateIssueUiState> = _uiState.asStateFlow()

    val availableCaptures: StateFlow<List<CaptureEntity>> = repository.getAllCaptures()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadCandidateCapture(captureId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val capture = repository.getCaptureById(captureId)
            if (capture == null) {
                _uiState.update { it.copy(isLoading = false, error = "Capture not found: $captureId") }
                return@launch
            }

            val defaultTitle = "${capture.severity.uppercase(Locale.US)} Distress @ ${capture.locationKey.take(8)}"
            _uiState.update {
                it.copy(
                    candidateCapture = capture,
                    title = defaultTitle,
                    isLoading = false
                )
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _uiState.update { it.copy(title = newTitle) }
    }

    fun updateNotes(newNotes: String) {
        _uiState.update { it.copy(notes = newNotes) }
    }

    fun createIssue(onSuccess: (String) -> Unit) {
        val capture = _uiState.value.candidateCapture
        if (capture == null) {
            _uiState.update { it.copy(error = "No capture selected") }
            return
        }

        val title = _uiState.value.title.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Issue title cannot be blank") }
            return
        }

        val notes = _uiState.value.notes.trim().takeIf { it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = repository.createOrLinkIssueForCapture(
                capture = capture,
                customTitle = title,
                notes = notes
            )

            when (result) {
                is IssueDeduplicationResult.Created -> {
                    _uiState.update { it.copy(isLoading = false, creationResult = result) }
                    onSuccess(result.issue.id)
                }
                is IssueDeduplicationResult.LinkedToExisting -> {
                    _uiState.update { it.copy(isLoading = false, creationResult = result) }
                    onSuccess(result.issue.id)
                }
                is IssueDeduplicationResult.Rejected -> {
                    _uiState.update { it.copy(isLoading = false, error = result.reason) }
                }
            }
        }
    }
}
