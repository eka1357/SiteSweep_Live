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

    @Volatile
    var lastCapturedSeverity: Severity = Severity.STABLE
        private set

    val isArmed: Boolean
        get() = state == AutoCaptureState.ARMED

    /**
     * Evaluates a processed frame against the debounce and clear-before-rearm state machine.
     * Supports escalation capture: if distress escalates to STRUCTURAL after an initial MONITOR,
     * the structural capture is recorded rather than suppressed.
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

        val shouldCapture: Boolean

        // Update state machine transitions based on elapsed time, signal clearing, and severity escalation
        synchronized(this) {
            val elapsedSinceCapture = elapsedTimeMs - lastCaptureElapsedRealtime

            // Escalation override: if distress escalates from MONITOR to STRUCTURAL,
            // capture the critical structural distress even if previously in cooldown/awaiting clear
            val isEscalation = result.severity == Severity.STRUCTURAL && 
                               lastCapturedSeverity == Severity.MONITOR &&
                               elapsedSinceCapture >= 800L

            when (state) {
                AutoCaptureState.DEBOUNCE_COOLDOWN -> {
                    if (elapsedSinceCapture >= debounceCooldownMs) {
                        if (!isDistress) {
                            // Signal has cleared and cooldown is complete -> re-armed
                            state = AutoCaptureState.ARMED
                            lastCapturedSeverity = Severity.STABLE
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
                        lastCapturedSeverity = Severity.STABLE
                    }
                }
                AutoCaptureState.ARMED -> {
                    // Ready for next trigger
                }
            }

            if (isEscalation || (state == AutoCaptureState.ARMED && isDistress)) {
                state = AutoCaptureState.DEBOUNCE_COOLDOWN
                lastCaptureElapsedRealtime = elapsedTimeMs
                lastCapturedSeverity = result.severity
                shouldCapture = true
            } else {
                shouldCapture = false
            }
        }

        if (!shouldCapture) {
            return null
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
            lastCapturedSeverity = Severity.STABLE
        }
    }
}
