package com.sitesweep

import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.Severity
import com.sitesweep.detection.SeverityClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityClassifierTest {

    @Test
    fun structuralClass_mapsCorrectlyToBands() {
        // High confidence structural crack -> STRUCTURAL
        assertEquals(
            Severity.STRUCTURAL,
            SeverityClassifier.classify(CrackClass.STRUCTURAL, 0.95f)
        )
        assertEquals(
            Severity.STRUCTURAL,
            SeverityClassifier.classify(CrackClass.STRUCTURAL, 0.65f)
        )

        // Moderate confidence structural crack -> MONITOR
        assertEquals(
            Severity.MONITOR,
            SeverityClassifier.classify(CrackClass.STRUCTURAL, 0.55f)
        )

        // Low confidence structural crack -> STABLE
        assertEquals(
            Severity.STABLE,
            SeverityClassifier.classify(CrackClass.STRUCTURAL, 0.35f)
        )
    }

    @Test
    fun hairlineClass_mapsCorrectlyToBands() {
        // High confidence hairline crack -> MONITOR
        assertEquals(
            Severity.MONITOR,
            SeverityClassifier.classify(CrackClass.HAIRLINE, 0.90f)
        )

        // Normal hairline crack -> STABLE
        assertEquals(
            Severity.STABLE,
            SeverityClassifier.classify(CrackClass.HAIRLINE, 0.70f)
        )
    }

    @Test
    fun noneClass_alwaysStable() {
        assertEquals(
            Severity.STABLE,
            SeverityClassifier.classify(CrackClass.NONE, 0.99f)
        )
    }

    @Test
    fun classifyProbability_structuralBand() {
        // >= 0.75f -> STRUCTURAL distress
        val (c1, s1) = SeverityClassifier.classifyProbability(0.75f)
        assertEquals(CrackClass.STRUCTURAL, c1)
        assertEquals(Severity.STRUCTURAL, s1)

        val (c2, s2) = SeverityClassifier.classifyProbability(0.99f)
        assertEquals(CrackClass.STRUCTURAL, c2)
        assertEquals(Severity.STRUCTURAL, s2)
    }

    @Test
    fun classifyProbability_hairlineMonitorBand() {
        // >= 0.50f and < 0.75f -> HAIRLINE distress, MONITOR severity
        val (c1, s1) = SeverityClassifier.classifyProbability(0.50f)
        assertEquals(CrackClass.HAIRLINE, c1)
        assertEquals(Severity.MONITOR, s1)

        val (c2, s2) = SeverityClassifier.classifyProbability(0.74f)
        assertEquals(CrackClass.HAIRLINE, c2)
        assertEquals(Severity.MONITOR, s2)
    }

    @Test
    fun classifyProbability_belowThreshold_stableBand() {
        // < 0.50f -> NONE distress, STABLE severity
        val (c1, s1) = SeverityClassifier.classifyProbability(0.49f)
        assertEquals(CrackClass.NONE, c1)
        assertEquals(Severity.STABLE, s1)

        val (c2, s2) = SeverityClassifier.classifyProbability(0.0f)
        assertEquals(CrackClass.NONE, c2)
        assertEquals(Severity.STABLE, s2)
    }

    @Test
    fun dequantization_crackModelFormula_verifiesThresholds() {
        val scale = 0.00390625f // 1 / 256

        // raw_value = 128 -> 128 * 0.00390625 = 0.50 (exact threshold)
        val probAtThreshold = 128 * scale
        assertEquals(0.50f, probAtThreshold, 1e-5f)
        val (c128, s128) = SeverityClassifier.classifyProbability(probAtThreshold)
        assertEquals(CrackClass.HAIRLINE, c128)
        assertEquals(Severity.MONITOR, s128)

        // raw_value = 127 -> 127 * 0.00390625 = 0.49609375 (below threshold)
        val probBelowThreshold = 127 * scale
        val (c127, s127) = SeverityClassifier.classifyProbability(probBelowThreshold)
        assertEquals(CrackClass.NONE, c127)
        assertEquals(Severity.STABLE, s127)

        // raw_value = 192 -> 192 * 0.00390625 = 0.75 (structural threshold)
        val probStructural = 192 * scale
        assertEquals(0.75f, probStructural, 1e-5f)
        val (c192, s192) = SeverityClassifier.classifyProbability(probStructural)
        assertEquals(CrackClass.STRUCTURAL, c192)
        assertEquals(Severity.STRUCTURAL, s192)
    }

    @Test
    fun hysteresis_preventsBoundaryFlicker() {
        // At 0.55, if previously clear (currentlyDetected = false), does not trip false alarm (requires >= 0.60)
        val (cClear, sClear) = SeverityClassifier.classifyProbabilityWithHysteresis(0.55f, currentlyDetected = false)
        assertEquals(CrackClass.NONE, cClear)
        assertEquals(Severity.STABLE, sClear)

        // At 0.61, trips alarm
        val (cAlarm, sAlarm) = SeverityClassifier.classifyProbabilityWithHysteresis(0.61f, currentlyDetected = false)
        assertEquals(CrackClass.HAIRLINE, cAlarm)
        assertEquals(Severity.MONITOR, sAlarm)

        // At 0.50, if already in distress (currentlyDetected = true), stays in distress (requires < 0.48 to exit)
        val (cStay, sStay) = SeverityClassifier.classifyProbabilityWithHysteresis(0.50f, currentlyDetected = true)
        assertEquals(CrackClass.HAIRLINE, cStay)
        assertEquals(Severity.MONITOR, sStay)

        // At 0.45, exits distress back to clear
        val (cExit, sExit) = SeverityClassifier.classifyProbabilityWithHysteresis(0.45f, currentlyDetected = true)
        assertEquals(CrackClass.NONE, cExit)
        assertEquals(Severity.STABLE, sExit)
    }
}
