package com.sitesweep.detection

import android.content.Context
import android.graphics.Bitmap

/**
 * Common interface for on-device structural crack detection.
 * Both the real LiteRT inference engine and the offline FakeCrackDetector
 * implement this contract, enabling seamless UI and pipeline development.
 */
interface CrackDetector : AutoCloseable {
    /**
     * The hardware execution delegate active for this detector instance.
     */
    val activeDelegate: DelegateType

    /**
     * Executes inference on the provided frame bitmap.
     * Guaranteed to execute entirely on-device with zero network calls.
     */
    fun detect(bitmap: Bitmap): CrackDetectionResult

    companion object {
        /**
         * Factory function to instantiate the production LiteRT detector.
         */
        operator fun invoke(
            context: Context,
            modelAssetPath: String = "crack_model.tflite"
        ): CrackDetector = LiteRTCrackDetector(context, modelAssetPath)
    }
}
