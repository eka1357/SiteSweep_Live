package com.sitesweep.detection

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Throttles frame analysis rate to ~5 fps (200ms interval).
 * Prevents thermal throttling, preserves battery life, and ensures real-time stability
 * on hardware during extended inspection sweeps.
 */
class FrameThrottler(
    val targetFps: Int = 5
) {
    private val frameIntervalMs: Long = 1000L / targetFps
    private val lastProcessedTimestamp = AtomicLong(0L)

    /**
     * Determines whether the current frame should be processed or dropped.
     * @param currentElapsedMs The current monotonic clock time in milliseconds.
     * @return true if at least [frameIntervalMs] has elapsed since the last accepted frame.
     */
    fun shouldProcess(currentElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val last = lastProcessedTimestamp.get()
        if (currentElapsedMs - last >= frameIntervalMs) {
            if (lastProcessedTimestamp.compareAndSet(last, currentElapsedMs)) {
                return true
            }
        }
        return false
    }

    /**
     * Reset the throttling timer.
     */
    fun reset() {
        lastProcessedTimestamp.set(0L)
    }
}
