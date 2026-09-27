package com.sitesweep.ui.revisit

import com.sitesweep.data.local.entity.CaptureEntity

enum class TrendStatus(val label: String) {
    STABLE("STABLE • NO NOTABLE DRIFT"),
    MONITORING("MONITORING • MINOR DEGRADATION"),
    WIDENING("ESCALATING • CRACK WIDENING DETECTED"),
    REGRESSED("REGRESSED • NO FURTHER GROWTH"),
    NO_HISTORY("NO PRIOR OBSERVATIONS AT THIS SPOT");

    companion object {
        fun calculate(captures: List<CaptureEntity>): TrendStatus {
            if (captures.isEmpty()) return NO_HISTORY
            fun rank(severity: String) = when (severity.uppercase(java.util.Locale.US)) {
                "STRUCTURAL" -> 2
                "MONITOR" -> 1
                else -> 0
            }
            val ranks = captures.sortedBy { it.timestamp }.map { rank(it.severity) }
            if (ranks.size == 1) {
                return when (ranks[0]) {
                    2 -> WIDENING
                    1 -> MONITORING
                    else -> STABLE
                }
            }
            val first = ranks.first()
            val last = ranks.last()
            return when {
                last > first -> WIDENING
                last < first -> REGRESSED
                else -> when (last) {
                    2 -> WIDENING
                    1 -> MONITORING
                    else -> STABLE
                }
            }
        }
    }
}

data class RevisitUiState(
    val locationKey: String = "",
    val targetCaptureId: String? = null,
    val captures: List<CaptureEntity> = emptyList(),
    val trend: TrendStatus = TrendStatus.STABLE,
    val isLoading: Boolean = true
)
