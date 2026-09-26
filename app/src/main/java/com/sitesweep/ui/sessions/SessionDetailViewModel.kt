package com.sitesweep.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionDetailUiState(
    val session: SessionEntity? = null,
    val captures: List<CaptureEntity> = emptyList(),
    val voiceNotes: List<VoiceNoteEntity> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * ViewModel managing the details of a single inspection session.
 */
class SessionDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository = (application as SiteSweepApplication).repository

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            _uiState.value = _uiState.value.copy(session = session, isLoading = false)

            launch {
                repository.getCapturesForSession(sessionId).collect { captures ->
                    _uiState.value = _uiState.value.copy(captures = captures)
                }
            }

            launch {
                repository.getVoiceNotesForSession(sessionId).collect { voiceNotes ->
                    _uiState.value = _uiState.value.copy(voiceNotes = voiceNotes)
                }
            }
        }
    }

    fun endSession() {
        val current = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.endSession(current.id)
            val updated = repository.getSessionById(current.id)
            _uiState.value = _uiState.value.copy(session = updated)
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        val current = _uiState.value.session ?: return
        viewModelScope.launch {
            repository.deleteSession(current.id)
            onDeleted()
        }
    }
}
