package com.sitesweep.ui.sweep

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.repository.SiteSweepRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel managing the active inspection sweep session, CameraX throttled inference,
 * severity classification with hysteresis, and running capture strip.
 */
class SweepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository = (application as SiteSweepApplication).repository

    private val _uiState = MutableStateFlow(SweepUiState())
    val uiState: StateFlow<SweepUiState> = _uiState.asStateFlow()

    private val frameThrottler = FrameThrottler(targetFps = 5)
    private var smoothedProbability: Float = -1f

    private var liteRtDetector: LiteRTCrackDetector? = null
    private var fakeDetector: FakeCrackDetector? = null
    private var currentDetector: CrackDetector

    private var capturesObservationJob: Job? = null

    init {
        val fake = FakeCrackDetector()
        fakeDetector = fake

        val real = try {
            LiteRTCrackDetector(application)
        } catch (e: Throwable) {
            Log.w("SweepViewModel", "Failed to init LiteRT detector: ${e.message}. Using Fake detector.", e)
            null
        }
        liteRtDetector = real
        currentDetector = real ?: fake

        _uiState.update {
            it.copy(
                activeDelegate = currentDetector.activeDelegate,
                statusText = if (currentDetector is FakeCrackDetector) "Scripted Detector Active" else "LiteRT Engine Active"
            )
        }
    }

    /**
     * Initializes or loads the target session. If sessionId is null, creates a new session.
     */
    fun initSession(sessionId: String? = null) {
        viewModelScope.launch {
            val session = if (sessionId != null) {
                repository.getSessionById(sessionId) ?: createNewSession()
            } else {
                createNewSession()
            }

            _uiState.update { it.copy(currentSession = session) }
            observeCapturesForSession(session.id)
        }
    }

    private suspend fun createNewSession(): SessionEntity {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return repository.createSession("Sweep $timestamp")
    }

    private fun observeCapturesForSession(sessionId: String) {
        capturesObservationJob?.cancel()
        capturesObservationJob = viewModelScope.launch {
            repository.getCapturesForSession(sessionId).collect { capturesList ->
                _uiState.update { it.copy(captures = capturesList.reversed()) }
            }
        }
    }

    /**
     * Entry point for CameraX ImageAnalysis frames.
     * Evaluates throttled 5 fps window, normalizes rotation, center-crops, and runs inference.
     */
    fun processImageProxy(imageProxy: ImageProxy) {
        val elapsed = SystemClock.elapsedRealtime()
        if (!frameThrottler.shouldProcess(elapsed)) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val rawBitmap: Bitmap? = imageProxy.toBitmap()
                if (rawBitmap != null) {
                    val preparedBitmap = prepareFrameBitmap(rawBitmap, rotationDegrees)
                    val result = currentDetector.detect(preparedBitmap)
                    updateInferenceResult(result)
                }
            } catch (e: Throwable) {
                Log.e("SweepViewModel", "Inference error on frame: ${e.message}", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun prepareFrameBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

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
        val rawProb = result.crackProbability
        val smoothed = if (smoothedProbability < 0f) {
            rawProb
        } else {
            0.35f * rawProb + 0.65f * smoothedProbability
        }
        smoothedProbability = smoothed

        val currentlyDistress = _uiState.value.currentClass != CrackClass.NONE
        val (stabilizedClass, stabilizedSeverity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = smoothed,
            currentlyDetected = currentlyDistress
        )

        val displayConfidence = if (stabilizedClass != CrackClass.NONE) {
            smoothed
        } else {
            (1.0f - smoothed).coerceIn(0.0f, 1.0f)
        }

        _uiState.update {
            it.copy(
                currentClass = stabilizedClass,
                currentSeverity = stabilizedSeverity,
                confidence = displayConfidence,
                crackProbability = smoothed,
                activeDelegate = result.delegateType
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        liteRtDetector?.close()
        fakeDetector?.close()
    }
}
