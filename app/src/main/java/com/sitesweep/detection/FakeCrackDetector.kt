package com.sitesweep.detection

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Scripted offline detector implementing [CrackDetector].
 * Allows UI, capture debounce, and session flows to be developed and tested
 * deterministically without requiring camera frames or the TFLite model.
 */
class FakeCrackDetector(
    override val activeDelegate: DelegateType = DelegateType.CPU,
    private val simulatedLatencyMs: Long = 28L
) : CrackDetector {

    private var frameCounter: Long = 0L

    // Scripted scenarios cycling through realistic field inspection conditions
    private val scriptedOutputs = listOf(
        Pair(CrackClass.NONE, 0.94f),
        Pair(CrackClass.NONE, 0.91f),
        Pair(CrackClass.HAIRLINE, 0.72f),
        Pair(CrackClass.HAIRLINE, 0.88f),
        Pair(CrackClass.STRUCTURAL, 0.78f),
        Pair(CrackClass.STRUCTURAL, 0.93f),
        Pair(CrackClass.STRUCTURAL, 0.96f),
        Pair(CrackClass.HAIRLINE, 0.65f),
        Pair(CrackClass.NONE, 0.89f)
    )

    override fun detect(bitmap: Bitmap): CrackDetectionResult {
        val startTime = SystemClock.elapsedRealtime()

        // Simulate inference execution time
        if (simulatedLatencyMs > 0) {
            SystemClock.sleep(simulatedLatencyMs)
        }

        val index = (frameCounter++ % scriptedOutputs.size).toInt()
        val (crackClass, confidence) = scriptedOutputs[index]
        val severity = SeverityClassifier.classify(crackClass, confidence)

        val latency = SystemClock.elapsedRealtime() - startTime

        return CrackDetectionResult(
            crackClass = crackClass,
            confidence = confidence,
            severity = severity,
            latencyMs = latency,
            delegateType = activeDelegate,
            timestamp = System.currentTimeMillis(),
            crackProbability = if (crackClass != CrackClass.NONE) confidence else (1.0f - confidence)
        )
    }

    override fun close() {
        // No native handles to release
    }
}
