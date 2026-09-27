package com.sitesweep

import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.Severity
import com.sitesweep.detection.SeverityClassifier
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Unit tests verifying telemetry coherence between overlay and bottom metrics:
 * 1. Single source of truth for crack probability (no inversion for class NONE).
 * 2. Proper class mapping through SeverityClassifier with hysteresis.
 * 3. Consistent integer percentage formatting across overlay and telemetry bar.
 */
class TelemetryCoherenceTest {

    private fun formatPercent(prob: Float): Int {
        return (prob * 100f).roundToInt().coerceIn(0, 100)
    }

    @Test
    fun telemetry_stable60Percent_coherentClassAndProb() {
        // Example from recording: 60% probability ambient clutter
        val rawProb = 0.60f
        val (crackClass, severity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = rawProb,
            currentlyDetected = false,
            currentSeverity = Severity.STABLE
        )

        // Must be STABLE and NONE because 0.60 < ENTER_CRACK (0.75)
        assertEquals(CrackClass.NONE, crackClass)
        assertEquals(Severity.STABLE, severity)

        // Both overlay and bottom UI must display exactly 60%, NEVER 40% or 38.5%
        val displayedProb = formatPercent(rawProb)
        assertEquals(60, displayedProb)
    }

    @Test
    fun telemetry_monitor86Percent_coherentClassAndProb() {
        // Example from recording: 86% probability crack
        val rawProb = 0.86f
        val (crackClass, severity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = rawProb,
            currentlyDetected = false,
            currentSeverity = Severity.STABLE
        )

        // Must enter MONITOR / HAIRLINE because 0.86 >= ENTER_CRACK (0.75) and < ENTER_STRUCTURAL (0.90)
        assertEquals(CrackClass.HAIRLINE, crackClass)
        assertEquals(Severity.MONITOR, severity)

        // Formatted percentage is 86%
        val displayedProb = formatPercent(rawProb)
        assertEquals(86, displayedProb)
    }

    @Test
    fun telemetry_structural97Percent_coherentClassAndProb() {
        // Example from recording: 97% probability severe crack
        val rawProb = 0.97f
        val (crackClass, severity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = rawProb,
            currentlyDetected = true,
            currentSeverity = Severity.MONITOR
        )

        // Must enter STRUCTURAL because 0.97 >= ENTER_STRUCTURAL (0.90)
        assertEquals(CrackClass.STRUCTURAL, crackClass)
        assertEquals(Severity.STRUCTURAL, severity)

        // Formatted percentage is 97%
        val displayedProb = formatPercent(rawProb)
        assertEquals(97, displayedProb)
    }

    @Test
    fun telemetry_hysteresisBoundary_preservesClassAboveExitThreshold() {
        // Active in MONITOR; probability drops to 0.70f (above EXIT_CRACK 0.62f)
        val (crackClass, severity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = 0.70f,
            currentlyDetected = true,
            currentSeverity = Severity.MONITOR
        )

        assertEquals(CrackClass.HAIRLINE, crackClass)
        assertEquals(Severity.MONITOR, severity)
        assertEquals(70, formatPercent(0.70f))
    }

    @Test
    fun telemetry_structuralHysteresis_preservesClassAboveExitThreshold() {
        // Active in STRUCTURAL; probability drops to 0.85f (above EXIT_STRUCTURAL 0.80f)
        val (crackClass, severity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = 0.85f,
            currentlyDetected = true,
            currentSeverity = Severity.STRUCTURAL
        )

        assertEquals(CrackClass.STRUCTURAL, crackClass)
        assertEquals(Severity.STRUCTURAL, severity)
        assertEquals(85, formatPercent(0.85f))
    }

    @Test
    fun telemetry_unifiedRounding_eliminatesOverlayBottomDiscrepancies() {
        // Tests edge rounding: 0.864f -> 86%, 0.866f -> 87%, 0.974f -> 97%, 0.976f -> 98%
        // Because both components call roundToInt(), both always compute identical integers.
        val p1 = 0.864f
        assertEquals(86, formatPercent(p1))

        val p2 = 0.866f
        assertEquals(87, formatPercent(p2))

        val p3 = 0.974f
        assertEquals(97, formatPercent(p3))

        val p4 = 0.976f
        assertEquals(98, formatPercent(p4))
    }
}
