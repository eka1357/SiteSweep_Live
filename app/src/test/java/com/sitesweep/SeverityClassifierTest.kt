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

    // -------------------------------------------------------------
    // 9 Approved Runtime Calibration Tests (Thresholds: 0.75, 0.62, 0.90, 0.80)
    // -------------------------------------------------------------

    @Test
    fun test1_stableBelow075() {
        // Below 0.75 from clear state must always be STABLE
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.74f, currentlyDetected = false)
        assertEquals(CrackClass.NONE, c1)
        assertEquals(Severity.STABLE, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.50f, currentlyDetected = false)
        assertEquals(CrackClass.NONE, c2)
        assertEquals(Severity.STABLE, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.0f, currentlyDetected = false)
        assertEquals(CrackClass.NONE, c3)
        assertEquals(Severity.STABLE, s3)
    }

    @Test
    fun test2_monitorEnteringAtOrAbove075() {
        // At or above 0.75 from clear state must enter MONITOR
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.75f, currentlyDetected = false)
        assertEquals(CrackClass.HAIRLINE, c1)
        assertEquals(Severity.MONITOR, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.78f, currentlyDetected = false)
        assertEquals(CrackClass.HAIRLINE, c2)
        assertEquals(Severity.MONITOR, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.89f, currentlyDetected = false)
        assertEquals(CrackClass.HAIRLINE, c3)
        assertEquals(Severity.MONITOR, s3)
    }

    @Test
    fun test3_monitorRemainingActiveWhileAtOrAbove062() {
        // Once active in MONITOR, remaining at or above 0.62 must keep MONITOR active
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.62f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.HAIRLINE, c1)
        assertEquals(Severity.MONITOR, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.68f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.HAIRLINE, c2)
        assertEquals(Severity.MONITOR, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.74f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.HAIRLINE, c3)
        assertEquals(Severity.MONITOR, s3)
    }

    @Test
    fun test4_monitorReturningToStableBelow062() {
        // Dropping strictly below 0.62 must exit distress back to STABLE
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.619f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.NONE, c1)
        assertEquals(Severity.STABLE, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.55f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.NONE, c2)
        assertEquals(Severity.STABLE, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.10f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.NONE, c3)
        assertEquals(Severity.STABLE, s3)
    }

    @Test
    fun test5_structuralEnteringAtOrAbove090() {
        // Probability >= 0.90 must enter STRUCTURAL
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.90f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.STRUCTURAL, c1)
        assertEquals(Severity.STRUCTURAL, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.95f, currentlyDetected = false, currentSeverity = Severity.STABLE)
        assertEquals(CrackClass.STRUCTURAL, c2)
        assertEquals(Severity.STRUCTURAL, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.99f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(CrackClass.STRUCTURAL, c3)
        assertEquals(Severity.STRUCTURAL, s3)
    }

    @Test
    fun test6_structuralRemainingActiveWhileAtOrAbove080() {
        // Once active in STRUCTURAL, probability >= 0.80 must remain in STRUCTURAL
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.80f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(CrackClass.STRUCTURAL, c1)
        assertEquals(Severity.STRUCTURAL, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.85f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(CrackClass.STRUCTURAL, c2)
        assertEquals(Severity.STRUCTURAL, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.89f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(CrackClass.STRUCTURAL, c3)
        assertEquals(Severity.STRUCTURAL, s3)
    }

    @Test
    fun test7_structuralReturningToMonitorBelow080() {
        // Dropping strictly below 0.80 from STRUCTURAL must de-escalate to MONITOR (not directly to STABLE as long as >= 0.62)
        val (c1, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(0.799f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(CrackClass.HAIRLINE, c1)
        assertEquals(Severity.MONITOR, s1)

        val (c2, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(0.70f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(CrackClass.HAIRLINE, c2)
        assertEquals(Severity.MONITOR, s2)

        val (c3, s3) = SeverityClassifier.classifyProbabilityWithHysteresis(0.62f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(CrackClass.HAIRLINE, c3)
        assertEquals(Severity.MONITOR, s3)
    }

    @Test
    fun test8_exactBoundaryValues() {
        // Exact 0.75 boundary (ENTER_CRACK)
        val (cAt75, sAt75) = SeverityClassifier.classifyProbabilityWithHysteresis(0.75f, currentlyDetected = false)
        assertEquals(Severity.MONITOR, sAt75)
        assertEquals(CrackClass.HAIRLINE, cAt75)

        val (cBelow75, sBelow75) = SeverityClassifier.classifyProbabilityWithHysteresis(0.7499f, currentlyDetected = false)
        assertEquals(Severity.STABLE, sBelow75)
        assertEquals(CrackClass.NONE, cBelow75)

        // Exact 0.62 boundary (EXIT_CRACK)
        val (cAt62, sAt62) = SeverityClassifier.classifyProbabilityWithHysteresis(0.62f, currentlyDetected = true)
        assertEquals(Severity.MONITOR, sAt62)
        assertEquals(CrackClass.HAIRLINE, cAt62)

        val (cBelow62, sBelow62) = SeverityClassifier.classifyProbabilityWithHysteresis(0.6199f, currentlyDetected = true)
        assertEquals(Severity.STABLE, sBelow62)
        assertEquals(CrackClass.NONE, cBelow62)

        // Exact 0.90 boundary (ENTER_STRUCTURAL)
        val (cAt90, sAt90) = SeverityClassifier.classifyProbabilityWithHysteresis(0.90f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(Severity.STRUCTURAL, sAt90)
        assertEquals(CrackClass.STRUCTURAL, cAt90)

        val (cBelow90, sBelow90) = SeverityClassifier.classifyProbabilityWithHysteresis(0.8999f, currentlyDetected = true, currentSeverity = Severity.MONITOR)
        assertEquals(Severity.MONITOR, sBelow90)
        assertEquals(CrackClass.HAIRLINE, cBelow90)

        // Exact 0.80 boundary (EXIT_STRUCTURAL)
        val (cAt80, sAt80) = SeverityClassifier.classifyProbabilityWithHysteresis(0.80f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(Severity.STRUCTURAL, sAt80)
        assertEquals(CrackClass.STRUCTURAL, cAt80)

        val (cBelow80, sBelow80) = SeverityClassifier.classifyProbabilityWithHysteresis(0.7999f, currentlyDetected = true, currentSeverity = Severity.STRUCTURAL)
        assertEquals(Severity.MONITOR, sBelow80)
        assertEquals(CrackClass.HAIRLINE, cBelow80)
    }

    @Test
    fun test9_noRapidStateOscillation() {
        // Simulate rapid hovering around the 0.75 boundary (e.g. 0.73 <-> 0.76)
        var currentlyDetected = false
        var currentSeverity = Severity.STABLE
        val states = mutableListOf<Severity>()

        val signal = listOf(
            0.70f, // STABLE
            0.74f, // STABLE (below 0.75)
            0.76f, // MONITOR (crosses 0.75)
            0.73f, // MONITOR (above 0.62, hysteresis prevents drop!)
            0.77f, // MONITOR (remains in monitor)
            0.72f, // MONITOR (above 0.62, still in monitor!)
            0.74f, // MONITOR
            0.60f, // STABLE (drops below 0.62, exits distress!)
            0.68f, // STABLE (above 0.62, but requires 0.75 to re-enter!)
            0.74f, // STABLE (still requires 0.75)
            0.76f  // MONITOR (re-enters)
        )

        for (prob in signal) {
            val (cls, sev) = SeverityClassifier.classifyProbabilityWithHysteresis(prob, currentlyDetected, currentSeverity)
            currentlyDetected = (cls != CrackClass.NONE)
            currentSeverity = sev
            states.add(sev)
        }

        // Expected sequence: STABLE, STABLE, MONITOR, MONITOR, MONITOR, MONITOR, MONITOR, STABLE, STABLE, STABLE, MONITOR
        val expected = listOf(
            Severity.STABLE,
            Severity.STABLE,
            Severity.MONITOR,
            Severity.MONITOR,
            Severity.MONITOR,
            Severity.MONITOR,
            Severity.MONITOR,
            Severity.STABLE,
            Severity.STABLE,
            Severity.STABLE,
            Severity.MONITOR
        )
        assertEquals(expected, states)
    }

    @Test
    fun dequantization_crackModelFormula_verifiesThresholds() {
        val scale = 0.00390625f // 1 / 256

        // raw_value = 192 -> 192 * 0.00390625 = 0.75f (at ENTER_CRACK threshold)
        val probAtThreshold = 192 * scale
        val (c192, s192) = SeverityClassifier.classifyProbability(probAtThreshold)
        assertEquals(CrackClass.HAIRLINE, c192)
        assertEquals(Severity.MONITOR, s192)

        // raw_value = 191 -> 191 * 0.00390625 = 0.74609375f (below ENTER_CRACK threshold)
        val probBelowThreshold = 191 * scale
        val (c191, s191) = SeverityClassifier.classifyProbability(probBelowThreshold)
        assertEquals(CrackClass.NONE, c191)
        assertEquals(Severity.STABLE, s191)

        // raw_value = 231 -> 231 * 0.00390625 = 0.90234375f (at ENTER_STRUCTURAL threshold)
        val probStructural = 231 * scale
        val (c231, s231) = SeverityClassifier.classifyProbability(probStructural)
        assertEquals(CrackClass.STRUCTURAL, c231)
        assertEquals(Severity.STRUCTURAL, s231)
    }

    @Test
    fun emaFilter_woodGrainClutter_tenSecondsContinuous_neverTriggersDistress() {
        // 10 seconds at 5 fps = 50 frames
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

        // Frame 1 (t = 0ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.25 = 0.5055f (< 0.75f)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[0])
        var (_, sev) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals(Severity.STABLE, sev)

        // Frame 2 (t = 200ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.5055 = 0.6716f (< 0.75f)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[1])
        var res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals(Severity.STABLE, res.second)

        // Frame 3 (t = 400ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.6716 = 0.7795f (>= ENTER_CRACK 0.75f -> prompt MONITOR)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[2])
        res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals("Frame 3 (~400ms) promptly crosses ENTER_CRACK to MONITOR", Severity.MONITOR, res.second)
        currentlyDistress = true
        currentSeverity = res.second

        // Frame 4 (t = 600ms at 5 fps): 0.35 * 0.98 + 0.65 * 0.7795 = 0.8497f (sustains MONITOR)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, crackFrames[3])
        res = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, currentlyDistress, currentSeverity)
        assertEquals("Frame 4 (~600ms) sustains MONITOR", Severity.MONITOR, res.second)
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

        // Single isolated spike (e.g. edge of laptop, cable at 0.70f)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, 0.70f)
        val (_, s1) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, false)
        assertEquals("Single frame spike smoothed to ~0.4075 must remain STABLE", Severity.STABLE, s1)
        assertTrue(smoothed < SeverityClassifier.ENTER_CRACK)

        // Frame returns to normal background (0.25f)
        smoothed = com.sitesweep.ui.sweep.SweepViewModel.computeEma(smoothed, 0.25f)
        val (_, s2) = SeverityClassifier.classifyProbabilityWithHysteresis(smoothed, false)
        assertEquals(Severity.STABLE, s2)
    }
}
