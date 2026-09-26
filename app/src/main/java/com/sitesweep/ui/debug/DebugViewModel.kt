package com.sitesweep.ui.debug

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.CrackDetectionResult
import com.sitesweep.detection.CrackDetector
import com.sitesweep.detection.DelegateType
import com.sitesweep.detection.FakeCrackDetector
import com.sitesweep.detection.FrameThrottler
import com.sitesweep.detection.LiteRTCrackDetector
import com.sitesweep.detection.Severity
import com.sitesweep.detection.SeverityClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class DebugUiState(
    val currentClass: CrackClass = CrackClass.NONE,
    val confidence: Float = 0.0f,
    val crackProbability: Float = 0.0f,
    val rawProbability: Float = 0.0f,
    val severity: Severity = Severity.STABLE,
    val activeDelegate: DelegateType = DelegateType.CPU,
    val currentLatencyMs: Long = 0L,
    val rollingLatencyMs: Float = 0.0f,
    val fpsEstimate: Float = 0.0f,
    val isFakeDetector: Boolean = false,
    val totalFramesAnalyzed: Long = 0L,
    val statusMessage: String = "Ready"
)

/**
 * ViewModel managing the throttled 5 fps inference pipeline and debug telemetry.
 * Incorporates rotation normalization, center-cropping, temporal smoothing,
 * and hysteresis to ensure rock-solid, jitter-free detections.
 */
class DebugViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    private val frameThrottler = FrameThrottler(targetFps = 5)
    private val latencyHistory = ArrayDeque<Long>(15)
    private val frameTimestamps = ArrayDeque<Long>(10)

    private var smoothedProbability: Float = -1f

    private var liteRtDetector: LiteRTCrackDetector? = null
    private var fakeDetector: FakeCrackDetector? = null
    private var currentDetector: CrackDetector

    init {
        val fake = FakeCrackDetector()
        fakeDetector = fake

        val real = try {
            LiteRTCrackDetector(application)
        } catch (e: Throwable) {
            Log.w("DebugViewModel", "Failed to init LiteRT detector: ${e.message}. Using Fake detector as initial.", e)
            null
        }
        liteRtDetector = real

        currentDetector = real ?: fake
        val isFake = currentDetector is FakeCrackDetector

        _uiState.update {
            it.copy(
                activeDelegate = currentDetector.activeDelegate,
                isFakeDetector = isFake,
                statusMessage = if (isFake) "Fake Scripted Detector Active" else "LiteRT Offline Engine Active"
            )
        }
    }

    /**
     * Toggles between the real LiteRT model and the scripted FakeCrackDetector.
     */
    fun toggleDetectorType() {
        val nextIsFake = !uiState.value.isFakeDetector
        smoothedProbability = -1f // Reset temporal filter on switch
        if (nextIsFake) {
            val fake = fakeDetector ?: FakeCrackDetector().also { fakeDetector = it }
            currentDetector = fake
            _uiState.update {
                it.copy(
                    isFakeDetector = true,
                    activeDelegate = fake.activeDelegate,
                    statusMessage = "Switched to Fake Scripted Detector"
                )
            }
        } else {
            val real = liteRtDetector ?: try {
                LiteRTCrackDetector(getApplication()).also { liteRtDetector = it }
            } catch (e: Throwable) {
                Log.e("DebugViewModel", "Cannot switch to LiteRT: ${e.message}", e)
                null
            }

            if (real != null) {
                currentDetector = real
                _uiState.update {
                    it.copy(
                        isFakeDetector = false,
                        activeDelegate = real.activeDelegate,
                        statusMessage = "Switched to LiteRT Engine (${real.activeDelegate.label})"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(statusMessage = "LiteRT model unavailable on this device")
                }
            }
        }
    }

    /**
     * Entry point for CameraX ImageAnalysis frames.
     * Evaluates throttling window (~5 fps), corrects rotation, center-crops, and runs offline inference.
     */
    fun processImageProxy(imageProxy: ImageProxy) {
        val elapsed = SystemClock.elapsedRealtime()
        if (!frameThrottler.shouldProcess(elapsed)) {
            // Drop frame to preserve battery and respect 5 fps constraint
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val rawBitmap: Bitmap? = imageProxy.toBitmap()
                if (rawBitmap != null) {
                    // Correct orientation and extract center square matching the reticle
                    val preparedBitmap = prepareFrameBitmap(rawBitmap, rotationDegrees)
                    val result = currentDetector.detect(preparedBitmap)
                    updateInferenceResult(result)
                }
            } catch (e: Throwable) {
                Log.e("DebugViewModel", "Inference error on frame: ${e.message}", e)
            } finally {
                // Always close ImageProxy in finally block
                imageProxy.close()
            }
        }
    }

    /**
     * Normalizes camera frame orientation and performs a center crop.
     * Prevents aspect-ratio squashing distortion and isolates the area within the viewfinder reticle.
     */
    private fun prepareFrameBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        // 1. Rotate to upright orientation if required
        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        // 2. Center crop square (matches preview reticle and preserves 1:1 aspect ratio)
        val size = minOf(rotated.width, rotated.height)
        val x = (rotated.width - size) / 2
        val y = (rotated.height - size) / 2

        return if (rotated.width == size && rotated.height == size) {
            rotated
        } else {
            Bitmap.createBitmap(rotated, x, y, size, size)
        }
    }

    private fun updateInferenceResult(result: CrackDetectionResult) {
        val now = SystemClock.elapsedRealtime()

        // 1. Latency tracking
        synchronized(latencyHistory) {
            if (latencyHistory.size >= 15) {
                latencyHistory.removeFirst()
            }
            latencyHistory.addLast(result.latencyMs)
        }

        val rollingLatency = synchronized(latencyHistory) {
            if (latencyHistory.isNotEmpty()) latencyHistory.average().toFloat() else 0f
        }

        // 2. FPS tracking
        synchronized(frameTimestamps) {
            if (frameTimestamps.size >= 10) {
                frameTimestamps.removeFirst()
            }
            frameTimestamps.addLast(now)
        }

        val fps = synchronized(frameTimestamps) {
            if (frameTimestamps.size >= 2) {
                val durationSec = (frameTimestamps.last() - frameTimestamps.first()) / 1000f
                if (durationSec > 0f) (frameTimestamps.size - 1) / durationSec else 0f
            } else 0f
        }

        // 3. Temporal probability smoothing (Exponential Moving Average)
        val rawProb = result.crackProbability
        val smoothed = if (smoothedProbability < 0f) {
            rawProb
        } else {
            // 35% new measurement + 65% historical filter: eliminates frame-to-frame flicker
            0.35f * rawProb + 0.65f * smoothedProbability
        }
        smoothedProbability = smoothed

        // 4. Hysteresis classification to prevent rapid boundary flipping
        val currentlyDistress = _uiState.value.currentClass != CrackClass.NONE
        val (stabilizedClass, stabilizedSeverity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = smoothed,
            currentlyDetected = currentlyDistress
        )

        // Confidence reflects certainty of current state
        val displayConfidence = if (stabilizedClass != CrackClass.NONE) {
            smoothed
        } else {
            (1.0f - smoothed).coerceIn(0.0f, 1.0f)
        }

        _uiState.update {
            it.copy(
                currentClass = stabilizedClass,
                confidence = displayConfidence,
                crackProbability = smoothed,
                rawProbability = rawProb,
                severity = stabilizedSeverity,
                activeDelegate = result.delegateType,
                currentLatencyMs = result.latencyMs,
                rollingLatencyMs = rollingLatency,
                fpsEstimate = fps,
                totalFramesAnalyzed = it.totalFramesAnalyzed + 1
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        liteRtDetector?.close()
        fakeDetector?.close()
    }
}
