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
        // >= 0.90f -> STRUCTURAL distress
        val (c1, s1) = SeverityClassifier.classifyProbability(0.90f)
        assertEquals(CrackClass.STRUCTURAL, c1)
        assertEquals(Severity.STRUCTURAL, s1)

        val (c2, s2) = SeverityClassifier.classifyProbability(0.99f)
        assertEquals(CrackClass.STRUCTURAL, c2)
        assertEquals(Severity.STRUCTURAL, s2)
    }

    @Test
    fun classifyProbability_hairlineMonitorBand() {
        // >= 0.78f and < 0.90f -> HAIRLINE distress, MONITOR severity
        val (c1, s1) = SeverityClassifier.classifyProbability(0.78f)
        assertEquals(CrackClass.HAIRLINE, c1)
        assertEquals(Severity.MONITOR, s1)

        val (c2, s2) = SeverityClassifier.classifyProbability(0.89f)
        assertEquals(CrackClass.HAIRLINE, c2)
        assertEquals(Severity.MONITOR, s2)
    }

    @Test
    fun classifyProbability_belowThreshold_stableBand() {
        // < 0.78f -> NONE distress, STABLE severity
        val (c1, s1) = SeverityClassifier.classifyProbability(0.77f)
        assertEquals(CrackClass.NONE, c1)
        assertEquals(Severity.STABLE, s1)

        val (c2, s2) = SeverityClassifier.classifyProbability(0.0f)
        assertEquals(CrackClass.NONE, c2)
        assertEquals(Severity.STABLE, s2)
    }

    @Test
    fun dequantization_crackModelFormula_verifiesThresholds() {
        val scale = 0.00390625f // 1 / 256

        // raw_value = 200 -> 200 * 0.00390625 = 0.78125 (at threshold)
        val probAtThreshold = 200 * scale
        val (c200, s200) = SeverityClassifier.classifyProbability(probAtThreshold)
        assertEquals(CrackClass.HAIRLINE, c200)
        assertEquals(Severity.MONITOR, s200)

        // raw_value = 199 -> 199 * 0.00390625 = 0.77734375 (below threshold)
        val probBelowThreshold = 199 * scale
        val (c199, s199) = SeverityClassifier.classifyProbability(probBelowThreshold)
        assertEquals(CrackClass.NONE, c199)
        assertEquals(Severity.STABLE, s199)

        // raw_value = 231 -> 231 * 0.00390625 = 0.90234375 (structural threshold)
        val probStructural = 231 * scale
        val (c231, s231) = SeverityClassifier.classifyProbability(probStructural)
        assertEquals(CrackClass.STRUCTURAL, c231)
        assertEquals(Severity.STRUCTURAL, s231)
    }

    @Test
    fun hysteresis_preventsBoundaryFlicker() {
        // At 0.75, if previously clear (currentlyDetected = false), does not trip false alarm (requires >= 0.78)
        val (cClear, sClear) = SeverityClassifier.classifyProbabilityWithHysteresis(0.75f, currentlyDetected = false)
        assertEquals(CrackClass.NONE, cClear)
        assertEquals(Severity.STABLE, sClear)

        // At 0.80, trips alarm (>= 0.78)
        val (cAlarm, sAlarm) = SeverityClassifier.classifyProbabilityWithHysteresis(0.80f, currentlyDetected = false)
        assertEquals(CrackClass.HAIRLINE, cAlarm)
        assertEquals(Severity.MONITOR, sAlarm)

        // At 0.70, if already in distress (currentlyDetected = true), stays in distress (requires < 0.65 to exit)
        val (cStay, sStay) = SeverityClassifier.classifyProbabilityWithHysteresis(0.70f, currentlyDetected = true)
        assertEquals(CrackClass.HAIRLINE, cStay)
        assertEquals(Severity.MONITOR, sStay)

        // At 0.60, exits distress back to clear (< 0.65)
        val (cExit, sExit) = SeverityClassifier.classifyProbabilityWithHysteresis(0.60f, currentlyDetected = true)
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
        // Starting from ambient baseline looking at clean wall/desk (~0.25f)
        var smoothed = 0.25f
        var currentlyDistress = false
        var currentSeverity = Severity.STABLE

        // Sweeping onto printed crack poster (sustained 0.98f)
        val crackFrames = listOf(0.98f, 0.98f, 0.98f, 0.98f, 0.98f, 0.98f)

        // Frame 1 (t = 0ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.25 = 0.5055f (< 0.78f)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[0])
        var (_, sev) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals(Severity.STABLE, sev)

        // Frame 2 (t = 200ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.5055 = 0.6716f (< 0.78f)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[1])
        var res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals(Severity.STABLE, res.second)

        // Frame 3 (t = 400ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.6716 = 0.7795f
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[2])
        res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)

        // Frame 4 (t = 600ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.7795 = 0.8497f (>= ENTER_CRACK 0.78f -> prompt MONITOR)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[3])
        res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals("Frame 4 (~600ms) promptly crosses ENTER_CRACK to MONITOR", Severity.MONITOR, res.second)
        currentlyDistress = true
        currentSeverity = res.second

        // Frame 5 (t = 800ms): 0.35 * 0.98 + 0.65 * 0.8497 = 0.8953f (sustains MONITOR)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[4])
        res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals("Frame 5 (~800ms) sustains MONITOR", Severity.MONITOR, res.second)
        currentSeverity = res.second

        // Frame 6 (t = 1000ms): 0.35 * 0.98 + 0.65 * 0.8953 = 0.9249f (>= ENTER_STRUCTURAL 0.90f -> STRUCTURAL)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[5])
        res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals("Frame 6 (~1000ms) promptly escalates to STRUCTURAL", Severity.STRUCTURAL, res.second)
    }

    @Test
    fun emaFilter_isolatedSpikes_neverTriggerDistress() {
        // Ambient background ~0.25f
        var smoothed = 0.25f

        // Single isolated spike (e.g. edge of laptop, cable at 0.75)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, 0.75f)
        val (_, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, false)
        assertEquals("Single frame spike smoothed to ~0.425 must remain STABLE", Severity.STABLE, s1)
        assertTrue(smoothed < SeverityClassifier.ENTER_CRACK)

        // Frame returns to normal background (0.25)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, 0.25f)
        val (_, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, false)
        assertEquals(Severity.STABLE, s2)
    }
}
