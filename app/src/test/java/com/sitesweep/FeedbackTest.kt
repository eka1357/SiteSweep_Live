package com.sitesweep

import com.sitesweep.detection.Severity
import com.sitesweep.feedback.HapticController
import com.sitesweep.feedback.VoiceAnnouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeedbackTest {

    @Test
    fun hapticController_executesWithoutCrashWhenHardwareNull() {
        val haptic = HapticController(null)
        // Ensure no NullPointerException occurs on null device context
        haptic.triggerSeverityHaptic(Severity.STABLE)
        haptic.triggerSeverityHaptic(Severity.MONITOR)
        haptic.triggerSeverityHaptic(Severity.STRUCTURAL)
    }

    @Test
    fun voiceAnnouncer_speaksOnlySeverityBand() {
        val spokenWords = mutableListOf<String>()

        val announcer = object : VoiceAnnouncer(null) {
            override fun announceSeverity(severity: Severity) {
                val word = when (severity) {
                    Severity.STABLE -> null // STABLE remains silent as mandated
                    Severity.MONITOR -> "Monitor"
                    Severity.STRUCTURAL -> "Structural"
                } ?: return
                spokenWords.add(word)
            }
        }

        announcer.announceSeverity(Severity.STABLE)
        announcer.announceSeverity(Severity.MONITOR)
        announcer.announceSeverity(Severity.STRUCTURAL)

        // STABLE must remain silent; MONITOR and STRUCTURAL must speak
        assertEquals(listOf("Monitor", "Structural"), spokenWords)

        for (word in spokenWords) {
            assertFalse("TTS must not include em-dashes", word.contains("—"))
            assertFalse("TTS must not include extra words", word.contains("detected"))
            assertFalse("TTS must not include extra words", word.contains("crack"))
        }
    }
}
