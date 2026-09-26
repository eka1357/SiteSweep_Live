package com.sitesweep.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.sitesweep.detection.Severity
import java.util.Locale

/**
 * TextToSpeech announcer for hands-free audio inspection feedback.
 * Strictly adheres to AGENTS.md rule: "TTS speaks the band, nothing else."
 * Outputs only: "Stable", "Monitor", or "Structural".
 */
open class VoiceAnnouncer(
    private val context: Context? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingAnnouncement: String? = null

    init {
        context?.let { ctx ->
            try {
                tts = TextToSpeech(ctx.applicationContext, this)
            } catch (e: Exception) {
                Log.w("VoiceAnnouncer", "Unable to initialize TextToSpeech: ${e.message}")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("VoiceAnnouncer", "TTS Language not supported or missing data")
            } else {
                tts?.setSpeechRate(1.15f) // Crisp, brisk cadence for on-site inspection
                isInitialized = true
                pendingAnnouncement?.let { pending ->
                    tts?.speak(pending, TextToSpeech.QUEUE_FLUSH, null, "severity_callout")
                    pendingAnnouncement = null
                }
            }
        } else {
            Log.w("VoiceAnnouncer", "TTS initialization failed with code $status")
        }
    }

    /**
     * Speaks the severity band name and NOTHING ELSE:
     * - STABLE: Remains completely silent (no audio distraction).
     * - MONITOR: Speaks "Monitor".
     * - STRUCTURAL: Speaks "Structural" (urgent distress alert).
     */
    open fun announceSeverity(severity: Severity) {
        val word = when (severity) {
            Severity.STABLE -> null // STABLE remains silent as mandated
            Severity.MONITOR -> "Monitor"
            Severity.STRUCTURAL -> "Structural"
        } ?: return

        if (isInitialized && tts != null) {
            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "severity_callout")
        } else {
            pendingAnnouncement = word
        }
    }

    open fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w("VoiceAnnouncer", "Error shutting down TTS: ${e.message}")
        }
        tts = null
        isInitialized = false
    }
}
