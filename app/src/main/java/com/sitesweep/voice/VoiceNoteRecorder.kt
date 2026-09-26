package com.sitesweep.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class VoiceRecorderState(
    val isListening: Boolean = false,
    val transcript: String = "",
    val errorMessage: String? = null,
    val isOfflineSupported: Boolean = true
)

/**
 * Controller for offline speech recognition using Android SpeechRecognizer.
 * As mandated by AGENTS.md:
 * - Operates offline via EXTRA_PREFER_OFFLINE
 * - No cloud speech APIs, zero network dependencies
 * - Graceful fallback allowing text edit/confirmation
 */
open class VoiceNoteRecorder(private val context: Context? = null) {

    private val _state = MutableStateFlow(VoiceRecorderState())
    val state: StateFlow<VoiceRecorderState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    init {
        context?.let { ctx ->
            if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                try {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                        setRecognitionListener(createListener())
                    }
                } catch (e: Exception) {
                    Log.w("VoiceNoteRecorder", "SpeechRecognizer creation failed: ${e.message}")
                }
            } else {
                _state.update { it.copy(isOfflineSupported = false) }
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.update { it.copy(isListening = true, errorMessage = null) }
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _state.update { it.copy(isListening = false) }
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    else -> "Offline recognizer error code: $error"
                }
                _state.update { it.copy(isListening = false, errorMessage = errorMsg) }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                _state.update {
                    it.copy(
                        isListening = false,
                        transcript = if (it.transcript.isBlank()) text else "${it.transcript} $text"
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                _state.update { it.copy(transcript = text) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    open fun startListening() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // Enforce on-device recognition
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            recognizer.startListening(intent)
            _state.update { it.copy(isListening = true, errorMessage = null) }
        } catch (e: Exception) {
            _state.update { it.copy(isListening = false, errorMessage = e.message) }
        }
    }

    open fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        _state.update { it.copy(isListening = false) }
    }

    fun updateTranscriptManually(newText: String) {
        _state.update { it.copy(transcript = newText) }
    }

    fun reset() {
        _state.update { VoiceRecorderState() }
    }

    open fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
