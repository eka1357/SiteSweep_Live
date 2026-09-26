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
            }
        } else {
            Log.w("VoiceAnnouncer", "TTS initialization failed with code $status")
        }
    }

    /**
     * Speaks the severity band name and NOTHING ELSE.
     * Guaranteed single-word callouts: "Stable", "Monitor", "Structural".
     */
    open fun announceSeverity(severity: Severity) {
        val word = when (severity) {
            Severity.STABLE -> "Stable"
            Severity.MONITOR -> "Monitor"
            Severity.STRUCTURAL -> "Structural"
        }

        if (isInitialized && tts != null) {
            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "severity_callout")
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
