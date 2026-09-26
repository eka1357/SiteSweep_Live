package com.sitesweep.capture

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.repository.SiteSweepRepository
import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.CrackDetectionResult
import com.sitesweep.detection.Severity
import java.util.UUID

enum class AutoCaptureState {
    ARMED,
    DEBOUNCE_COOLDOWN,
    AWAITING_CLEAR
}

/**
 * AutoCapture controller implementing the non-negotiable AGENTS.md debounce rules:
 * 1. 3-second debounce suppression after every distress capture.
 * 2. Clear-before-rearm requirement: the distress signal must clear (return to STABLE)
 *    before the system will re-arm for the next capture.
 * 3. Non-blocking geotagging from cached last-known-location.
 */
class AutoCapture(
    private val frameStore: FrameStore,
    private val geoTagger: GeoTagger,
    private val repository: SiteSweepRepository,
    private val debounceCooldownMs: Long = 3000L,
    private val onCaptureTriggered: ((CaptureEntity, Severity) -> Unit)? = null
) {

    @Volatile
    var state: AutoCaptureState = AutoCaptureState.ARMED
        private set

    @Volatile
    var lastCaptureElapsedRealtime: Long = 0L
        private set

    val isArmed: Boolean
        get() = state == AutoCaptureState.ARMED

    /**
     * Evaluates a processed frame against the debounce and clear-before-rearm state machine.
     * Executes capture and persistence on a background thread when triggered.
     * Returns the CaptureEntity if capture occurred, null otherwise.
     */
    suspend fun evaluateFrame(
        bitmap: Bitmap?,
        result: CrackDetectionResult,
        sessionId: String,
        elapsedTimeMs: Long = SystemClock.elapsedRealtime()
    ): CaptureEntity? {
        val isDistress = result.severity == Severity.MONITOR || 
                         result.severity == Severity.STRUCTURAL ||
                         result.crackClass != CrackClass.NONE

        // Update state machine transitions based on elapsed time and signal clearing
        synchronized(this) {
            when (state) {
                AutoCaptureState.DEBOUNCE_COOLDOWN -> {
                    val elapsedSinceCapture = elapsedTimeMs - lastCaptureElapsedRealtime
                    if (elapsedSinceCapture >= debounceCooldownMs) {
                        if (!isDistress) {
                            // Signal has cleared and cooldown is complete -> re-armed
                            state = AutoCaptureState.ARMED
                        } else {
                            // 3s cooldown is over, but wall still shows distress -> await clear
                            state = AutoCaptureState.AWAITING_CLEAR
                        }
                    }
                }
                AutoCaptureState.AWAITING_CLEAR -> {
                    if (!isDistress) {
                        // User panned away from crack -> clear condition satisfied, re-arm
                        state = AutoCaptureState.ARMED
                    }
                }
                AutoCaptureState.ARMED -> {
                    // Ready for next trigger
                }
            }

            if (state != AutoCaptureState.ARMED || !isDistress) {
                return null
            }

            // Trigger capture
            state = AutoCaptureState.DEBOUNCE_COOLDOWN
            lastCaptureElapsedRealtime = elapsedTimeMs
        }

        return try {
            val captureId = UUID.randomUUID().toString()
            val imagePath = frameStore.saveFrame(bitmap, captureId)
            val location = geoTagger.getCachedLocation()

            val capture = CaptureEntity(
                id = captureId,
                sessionId = sessionId,
                imagePath = imagePath,
                lat = location.latitude,
                lng = location.longitude,
                timestamp = System.currentTimeMillis(),
                severity = result.severity.label,
                confidence = result.confidence,
                locationKey = location.locationKey
            )

            repository.insertCapture(capture)
            onCaptureTriggered?.invoke(capture, result.severity)
            capture
        } catch (e: Exception) {
            Log.e("AutoCapture", "Failed to complete auto-capture: ${e.message}", e)
            null
        }
    }

    fun reset() {
        synchronized(this) {
            state = AutoCaptureState.ARMED
            lastCaptureElapsedRealtime = 0L
        }
    }
}
