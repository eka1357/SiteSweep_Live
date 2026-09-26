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
import com.sitesweep.capture.AutoCapture
import com.sitesweep.capture.FrameStore
import com.sitesweep.capture.GeoTagger
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
import com.sitesweep.feedback.HapticController
import com.sitesweep.feedback.VoiceAnnouncer
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
 * severity classification with hysteresis, automated capture with 3-second debounce,
 * and running capture strip.
 */
class SweepViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SiteSweepRepository = (application as SiteSweepApplication).repository
    val frameStore = FrameStore(application)
    val geoTagger = GeoTagger(application)

    private val _uiState = MutableStateFlow(SweepUiState())
    val uiState: StateFlow<SweepUiState> = _uiState.asStateFlow()

    private val frameThrottler = FrameThrottler(targetFps = 5)
    private var smoothedProbability: Float = -1f
    private val latencyHistory = java.util.ArrayDeque<Long>(15)
    private val frameTimestamps = java.util.ArrayDeque<Long>(10)

    private var liteRtDetector: LiteRTCrackDetector? = null
    private var fakeDetector: FakeCrackDetector? = null
    private var currentDetector: CrackDetector

    private var capturesObservationJob: Job? = null

    val hapticController = HapticController(application)
    val voiceAnnouncer = VoiceAnnouncer(application)

    private var lastAnnouncedSeverity: Severity = Severity.STABLE

    /**
     * Triggers distinct audio and haptic feedback on severity state transitions:
     * - STRUCTURAL: Immediate aggressive alert (voice "Structural" + triple pulse)
     * - MONITOR: Cautionary alert (voice "Monitor" + double pulse)
     * - STABLE: Remains silent, resets detection episode
     */
    private fun checkDistressFeedback(severity: Severity) {
        when (severity) {
            Severity.STRUCTURAL -> {
                if (lastAnnouncedSeverity != Severity.STRUCTURAL) {
                    lastAnnouncedSeverity = Severity.STRUCTURAL
                    voiceAnnouncer.announceSeverity(Severity.STRUCTURAL)
                    hapticController.triggerSeverityHaptic(Severity.STRUCTURAL)
                }
            }
            Severity.MONITOR -> {
                if (lastAnnouncedSeverity == Severity.STABLE) {
                    lastAnnouncedSeverity = Severity.MONITOR
                    voiceAnnouncer.announceSeverity(Severity.MONITOR)
                    hapticController.triggerSeverityHaptic(Severity.MONITOR)
                }
            }
            Severity.STABLE -> {
                if (lastAnnouncedSeverity != Severity.STABLE) {
                    lastAnnouncedSeverity = Severity.STABLE
                    hapticController.triggerSeverityHaptic(Severity.STABLE)
                }
            }
        }
    }

    // AutoCapture instance with 3-second debounce and clear-before-rearm rule
    var autoCapture: AutoCapture = AutoCapture(
        frameStore = frameStore,
        geoTagger = geoTagger,
        repository = repository,
        debounceCooldownMs = 3000L,
        onCaptureTriggered = { capture, severity ->
            // Re-enforce feedback if not already announced by real-time transition
            if (severity == Severity.STRUCTURAL && lastAnnouncedSeverity != Severity.STRUCTURAL) {
                lastAnnouncedSeverity = Severity.STRUCTURAL
                hapticController.triggerSeverityHaptic(severity)
                voiceAnnouncer.announceSeverity(severity)
            } else if (severity == Severity.MONITOR && lastAnnouncedSeverity == Severity.STABLE) {
                lastAnnouncedSeverity = Severity.MONITOR
                hapticController.triggerSeverityHaptic(severity)
                voiceAnnouncer.announceSeverity(severity)
            }
            _onCaptureTriggeredListener?.invoke(capture, severity)
        }
    )

    private var _onCaptureTriggeredListener: ((CaptureEntity, Severity) -> Unit)? = null

    fun setOnCaptureTriggeredListener(listener: (CaptureEntity, Severity) -> Unit) {
        _onCaptureTriggeredListener = listener
    }

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
        // Reset state so old session cooldowns or smoothed probabilities do not leak
        autoCapture.reset()
        smoothedProbability = -1f
        lastAnnouncedSeverity = Severity.STABLE
        frameThrottler.reset()
        synchronized(latencyHistory) { latencyHistory.clear() }
        synchronized(frameTimestamps) { frameTimestamps.clear() }

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

    /**
     * Marks the currently active sweep session as ended with timestamp.
     */
    fun endActiveSession() {
        val id = _uiState.value.currentSession?.id ?: return
        viewModelScope.launch {
            repository.endSession(id)
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
            var rawBitmap: Bitmap? = null
            var preparedBitmap: Bitmap? = null
            try {
                val raw = imageProxy.toBitmap()
                rawBitmap = raw
                preparedBitmap = prepareFrameBitmap(raw, rotationDegrees)
                val rawResult = currentDetector.detect(preparedBitmap)
                val stabilizedResult = evaluateStabilizedResult(rawResult)
                Log.i("SweepPipeline", "RAW: ${rawResult.crackProbability} | SMOOTH: ${stabilizedResult.crackProbability} | CLASS: ${stabilizedResult.crackClass} | SEV: ${stabilizedResult.severity}")
                
                updateInferenceResult(stabilizedResult)
                checkDistressFeedback(stabilizedResult.severity)

                // Automated capture evaluation on active session
                val currentSessionId = _uiState.value.currentSession?.id
                if (currentSessionId != null) {
                    autoCapture.evaluateFrame(
                        bitmap = preparedBitmap,
                        result = stabilizedResult,
                        sessionId = currentSessionId,
                        elapsedTimeMs = elapsed
                    )
                }

                _uiState.update {
                    it.copy(
                        isAutoCaptureArmed = autoCapture.isArmed,
                        autoCaptureState = autoCapture.state,
                        lastCaptureTimestamp = autoCapture.lastCaptureElapsedRealtime
                    )
                }
            } catch (e: Throwable) {
                Log.e("SweepViewModel", "Inference error on frame: ${e.message}", e)
            } finally {
                imageProxy.close()
                if (rawBitmap != null && rawBitmap !== preparedBitmap && !rawBitmap.isRecycled) {
                    rawBitmap.recycle()
                }
                if (preparedBitmap != null && !preparedBitmap.isRecycled) {
                    preparedBitmap.recycle()
                }
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

        val cropped = if (rotated.width == size && rotated.height == size) {
            rotated
        } else {
            Bitmap.createBitmap(rotated, x, y, size, size)
        }

        if (rotated !== bitmap && rotated !== cropped && !rotated.isRecycled) {
            rotated.recycle()
        }

        return cropped
    }

    private fun evaluateStabilizedResult(result: CrackDetectionResult): CrackDetectionResult {
        val rawProb = result.crackProbability
        val smoothed = computeEma(smoothedProbability, rawProb)
        smoothedProbability = smoothed

        val currentlyDistress = _uiState.value.currentClass != CrackClass.NONE
        val (stabilizedClass, stabilizedSeverity) = SeverityClassifier.classifyProbabilityWithHysteresis(
            crackProbability = smoothed,
            currentlyDetected = currentlyDistress,
            currentSeverity = _uiState.value.currentSeverity
        )

        val displayConfidence = if (stabilizedClass != CrackClass.NONE) {
            smoothed
        } else {
            (1.0f - smoothed).coerceIn(0.0f, 1.0f)
        }

        return result.copy(
            crackClass = stabilizedClass,
            severity = stabilizedSeverity,
            confidence = displayConfidence,
            crackProbability = smoothed
        )
    }

    private fun updateInferenceResult(stabilizedResult: CrackDetectionResult) {
        val now = SystemClock.elapsedRealtime()

        val rollingLatency = synchronized(latencyHistory) {
            if (latencyHistory.size >= 15) {
                latencyHistory.removeFirst()
            }
            latencyHistory.addLast(stabilizedResult.latencyMs)
            if (latencyHistory.isNotEmpty()) latencyHistory.average().toFloat() else 0f
        }

        val fps = synchronized(frameTimestamps) {
            if (frameTimestamps.size >= 10) {
                frameTimestamps.removeFirst()
            }
            frameTimestamps.addLast(now)
            if (frameTimestamps.size >= 2) {
                val durationSec = (frameTimestamps.last() - frameTimestamps.first()) / 1000f
                if (durationSec > 0f) (frameTimestamps.size - 1) / durationSec else 0f
            } else 0f
        }

        _uiState.update {
            it.copy(
                currentClass = stabilizedResult.crackClass,
                currentSeverity = stabilizedResult.severity,
                confidence = stabilizedResult.confidence,
                crackProbability = stabilizedResult.crackProbability,
                activeDelegate = stabilizedResult.delegateType,
                latencyMs = stabilizedResult.latencyMs,
                rollingLatencyMs = rollingLatency,
                fpsEstimate = fps
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceAnnouncer.shutdown()
        liteRtDetector?.close()
        fakeDetector?.close()
    }

    companion object {
        /**
         * Pure symmetric EMA smoothing factor (0.35f).
         * Restored from known-good baseline (commit 1136717).
         *
         * Mathematical rationale:
         * Asymmetric attack/release (e.g. attack 0.85/0.40, release 0.25) behaves as a peak-detector
         * charge pump on natural texture fluctuations (wood grain, desk clutter, cable edges).
         * Because release is slower than attack, normal texture variance ratchets the smoothed probability
         * upward over consecutive frames until it falsely breaches ENTER_CRACK.
         *
         * Pure symmetric smoothing (0.35f on both rise and fall):
         * 1. Accurately tracks the central tendency of background textures (averaging ~0.20-0.35),
         *    keeping wood grain and clutter firmly below ENTER_CRACK (0.60f) even over 10+ seconds.
         * 2. On sustained crack posters (0.88-0.92), reaches MONITOR at frame 2 (~200ms) and
         *    escalates to STRUCTURAL at frame 4 (~600ms), delivering prompt, reliable detection.
         */
        const val EMA_ALPHA = 0.35f

        fun computeEma(
            currentSmoothed: Float,
            rawProb: Float,
            alpha: Float = EMA_ALPHA
        ): Float {
            if (currentSmoothed < 0f) {
                // Guard against startup spikes: do not jump directly to rawProb on frame 1
                return (alpha * rawProb).coerceIn(0f, 1f)
            }
            return (alpha * rawProb + (1.0f - alpha) * currentSmoothed).coerceIn(0f, 1f)
        }
    }
}
