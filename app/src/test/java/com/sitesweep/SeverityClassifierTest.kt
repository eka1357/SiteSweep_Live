package com.sitesweep

import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.Severity
import com.sitesweep.detection.SeverityClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun emaFilter_woodGrainClutter_tenSecondsContinuous_neverTriggersDistress() {
        // 10 seconds at 5 fps = 50 frames
        // Wood grain with typical noise around 0.20-0.35, and occasional single-frame glare/texture spikes up to 0.69
        var smoothed = 0.25f
        var currentlyDistress = false
        var distressTriggerCount = 0

        val frames = listOf(
            0.22f, 0.28f, 0.25f, 0.68f, 0.24f, 0.30f, 0.32f, 0.65f, 0.21f, 0.26f, // 0-2s
            0.29f, 0.27f, 0.33f, 0.31f, 0.69f, 0.23f, 0.25f, 0.28f, 0.34f, 0.22f, // 2-4s
            0.24f, 0.29f, 0.67f, 0.25f, 0.26f, 0.31f, 0.30f, 0.28f, 0.25f, 0.66f, // 4-6s
            0.23f, 0.27f, 0.32f, 0.24f, 0.26f, 0.29f, 0.68f, 0.22f, 0.25f, 0.30f, // 6-8s
            0.28f, 0.33f, 0.27f, 0.25f, 0.69f, 0.21f, 0.26f, 0.29f, 0.28f, 0.24f  // 8-10s
        )

        for ((index, rawProb) in frames.withIndex()) {
            smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, rawProb)
            val (cls, sev) = SeverityClassifier.classifyProbabilityWithHysteresis(
                crackProbability = smoothed,
                currentlyDetected = currentlyDistress
            )
            if (cls != CrackClass.NONE || sev != Severity.STABLE) {
                distressTriggerCount++
                currentlyDistress = true
            } else {
                currentlyDistress = false
            }
            assertTrue(
                "Frame $index (raw=$rawProb, smoothed=$smoothed) falsely crossed ENTER_CRACK!",
                smoothed < SeverityClassifier.ENTER_CRACK
            )
        }

        assertEquals("Zero false alarms must occur over 10 seconds of wood grain/clutter", 0, distressTriggerCount)
    }

    @Test
    fun emaFilter_crackPoster_sustainedDistress_triggersPromptly() {
        // Starting from baseline looking at wall/desk (smoothed ~0.20f)
        var smoothed = 0.20f
        var currentlyDistress = false
        var currentSeverity = Severity.STABLE

        // Sweeping onto printed crack poster (sustained 0.88-0.90f)
        val crackFrames = listOf(0.88f, 0.90f, 0.89f, 0.91f)

        // Frame 1 (t = 0ms at 5 fps)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[0])
        var (cls, sev) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        // Frame 1 accumulates: 0.40 * 0.88 + 0.60 * 0.20 = 0.472f
        assertEquals(Severity.STABLE, sev)

        // Frame 2 (t = 200ms at 5 fps)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[1])
        val resFrame2 = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        cls = resFrame2.first
        sev = resFrame2.second
        // Frame 2: 0.40 * 0.90 + 0.60 * 0.472 = 0.643f >= ENTER_CRACK (0.60f)
        assertEquals("Frame 2 (~200ms) must promptly cross ENTER_CRACK to MONITOR", Severity.MONITOR, sev)
        currentlyDistress = true
        currentSeverity = sev

        // Frame 3 (t = 400ms at 5 fps)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[2])
        val resFrame3 = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        cls = resFrame3.first
        sev = resFrame3.second
        // Frame 3: 0.40 * 0.89 + 0.60 * 0.643 = 0.742f (>= ENTER_CRACK 0.60f, firmly MONITOR while escalating)
        assertEquals("Frame 3 (~400ms) sustains MONITOR while escalating", Severity.MONITOR, sev)
        currentSeverity = sev

        // Frame 4 (t = 600ms at 5 fps)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[3])
        val resFrame4 = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        cls = resFrame4.first
        sev = resFrame4.second
        // Frame 4: 0.40 * 0.91 + 0.60 * 0.742 = 0.809f >= ENTER_STRUCTURAL (0.78f)
        assertEquals("Frame 4 (~600ms) promptly escalates to STRUCTURAL", Severity.STRUCTURAL, sev)
    }
}
