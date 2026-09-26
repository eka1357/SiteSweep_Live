package com.sitesweep

import com.sitesweep.detection.FrameThrottler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameThrottlerTest {

    @Test
    fun throttler_enforcesTargetFpsInterval() {
        val throttler = FrameThrottler(targetFps = 5) // 200ms per frame

        // First frame should always be accepted
        assertTrue(throttler.shouldProcess(currentElapsedMs = 1000L))

        // Frame after 50ms should be dropped
        assertFalse(throttler.shouldProcess(currentElapsedMs = 1050L))

        // Frame after 150ms should be dropped
        assertFalse(throttler.shouldProcess(currentElapsedMs = 1150L))

        // Frame after 199ms should be dropped
        assertFalse(throttler.shouldProcess(currentElapsedMs = 1199L))

        // Frame after >= 200ms should be accepted
        assertTrue(throttler.shouldProcess(currentElapsedMs = 1200L))

        // Immediate subsequent frame should be dropped
        assertFalse(throttler.shouldProcess(currentElapsedMs = 1210L))

        // Next 200ms elapsed frame accepted
        assertTrue(throttler.shouldProcess(currentElapsedMs = 1405L))
    }
}
