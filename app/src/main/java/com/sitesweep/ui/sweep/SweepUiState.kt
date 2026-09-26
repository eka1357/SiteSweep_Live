package com.sitesweep.ui.sweep

import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.DelegateType
import com.sitesweep.detection.Severity

/**
 * UI State for the continuous camera SweepScreen.
 */
data class SweepUiState(
    val currentSeverity: Severity = Severity.STABLE,
    val currentClass: CrackClass = CrackClass.NONE,
    val confidence: Float = 0.0f,
    val crackProbability: Float = 0.0f,
    val activeDelegate: DelegateType = DelegateType.CPU,
    val currentSession: SessionEntity? = null,
    val captures: List<CaptureEntity> = emptyList(),
    val isAutoCaptureArmed: Boolean = true,
    val lastCaptureTimestamp: Long = 0L,
    val statusText: String = "Scanning"
)
