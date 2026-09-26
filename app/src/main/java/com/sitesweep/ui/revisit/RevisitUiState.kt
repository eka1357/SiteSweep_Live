package com.sitesweep.ui.revisit

import com.sitesweep.data.local.entity.CaptureEntity

enum class TrendStatus(val label: String) {
    STABLE("STABLE • NO NOTABLE DRIFT"),
    MONITORING("MONITORING • MINOR DEGRADATION"),
    WIDENING("ESCALATING • CRACK WIDENING DETECTED")
}

data class RevisitUiState(
    val locationKey: String = "",
    val targetCaptureId: String? = null,
    val captures: List<CaptureEntity> = emptyList(),
    val trend: TrendStatus = TrendStatus.STABLE,
    val isLoading: Boolean = true
)
