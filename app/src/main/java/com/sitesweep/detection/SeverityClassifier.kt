package com.sitesweep.detection

/**
 * Maps crack classification and raw model confidence to discrete severity bands.
 * As mandated by AGENTS.md, all threshold logic is centralized here so that
 * sensitivities can be tuned during hackathon trials without touching UI or camera code.
 */
object SeverityClassifier {

    // Tunable confidence thresholds with dual hysteresis to prevent false alarms on cables/furniture
    const val CRACK_THRESHOLD: Float = 0.50f
    var structuralThreshold: Float = 0.65f
    var monitorThreshold: Float = 0.50f
    var hairlineMonitorThreshold: Float = 0.85f
    var structuralProbabilityThreshold: Float = 0.75f

    const val ENTER_CRACK: Float = 0.60f
    const val EXIT_CRACK: Float = 0.48f
    const val ENTER_STRUCTURAL: Float = 0.78f
    const val EXIT_STRUCTURAL: Float = 0.72f

    /**
     * Classifies single-value crack probability (0.0 to 1.0) as output by crack_model.tflite.
     * Threshold: >= 0.5 = crack detected.
     */
    fun classifyProbability(crackProbability: Float): Pair<CrackClass, Severity> {
        return when {
            crackProbability >= structuralProbabilityThreshold -> Pair(CrackClass.STRUCTURAL, Severity.STRUCTURAL)
            crackProbability >= CRACK_THRESHOLD -> Pair(CrackClass.HAIRLINE, Severity.MONITOR)
            else -> Pair(CrackClass.NONE, Severity.STABLE)
        }
    }

    /**
     * Classifies crack probability with hysteresis deadband on BOTH the crack and structural
     * decision boundaries to prevent rapid flickering and false alarms on cables/clutter.
     */
    fun classifyProbabilityWithHysteresis(
        crackProbability: Float,
        currentlyDetected: Boolean = false,
        currentSeverity: Severity = Severity.STABLE
    ): Pair<CrackClass, Severity> {
        val isStructuralActive = currentSeverity == Severity.STRUCTURAL
        val activeStructural = if (isStructuralActive) EXIT_STRUCTURAL else ENTER_STRUCTURAL
        val activeCrack = if (currentlyDetected) EXIT_CRACK else ENTER_CRACK

        return when {
            crackProbability >= activeStructural -> Pair(CrackClass.STRUCTURAL, Severity.STRUCTURAL)
            crackProbability >= activeCrack -> Pair(CrackClass.HAIRLINE, Severity.MONITOR)
            else -> Pair(CrackClass.NONE, Severity.STABLE)
        }
    }

    /**
     * Map model output to the three non-negotiable severity bands:
     * - STABLE: Low probability distress or high-confidence hairline with no opening
     * - MONITOR: Moderate confidence distress or marked hairline
     * - STRUCTURAL: High confidence structural crack requiring immediate alert
     */
    fun classify(crackClass: CrackClass, confidence: Float): Severity {
        return when (crackClass) {
            CrackClass.STRUCTURAL -> {
                when {
                    confidence >= structuralThreshold -> Severity.STRUCTURAL
                    confidence >= monitorThreshold -> Severity.MONITOR
                    else -> Severity.STABLE
                }
            }
            CrackClass.HAIRLINE -> {
                when {
                    confidence >= hairlineMonitorThreshold -> Severity.MONITOR
                    else -> Severity.STABLE
                }
            }
            CrackClass.NONE -> Severity.STABLE
        }
    }
}
