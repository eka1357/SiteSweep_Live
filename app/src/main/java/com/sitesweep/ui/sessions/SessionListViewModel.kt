package com.sitesweep.ui.sessions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SessionItemUiModel(
    val session: SessionEntity,
    val captureCount: Int,
    val maxSeverity: String
)

/**
 * ViewModel managing the inspection session index.
 */
class SessionListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository = (application as SiteSweepApplication).repository

    val sessionItems: StateFlow<List<SessionItemUiModel>> = combine(
        repository.getAllSessions(),
        repository.getAllCaptures()
    ) { sessions, allCaptures ->
        val capturesBySession = allCaptures.groupBy { it.sessionId }
        sessions.map { session ->
            val sessionCaptures = capturesBySession[session.id] ?: emptyList()
            val maxSeverity = determineMaxSeverity(sessionCaptures)
            SessionItemUiModel(
                session = session,
                captureCount = sessionCaptures.size,
                maxSeverity = maxSeverity
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun determineMaxSeverity(captures: List<CaptureEntity>): String {
        if (captures.isEmpty()) return "NO DISTRESS"
        val severities = captures.map { it.severity.uppercase(Locale.US) }
        return when {
            severities.contains("STRUCTURAL") -> "STRUCTURAL"
            severities.contains("MONITOR") -> "MONITOR"
            else -> "STABLE"
        }
    }

    suspend fun createNewSession(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val session = repository.createSession("Inspection $timestamp")
        return session.id
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}
