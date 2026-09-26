package com.sitesweep

import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.voice.VoiceNoteRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceNoteTest {

    @Test
    fun voiceNote_canAttachToSession() {
        val note = VoiceNoteEntity(
            sessionId = "session_123",
            captureId = null,
            transcript = "Continuous vertical hairline crack along masonry joint.",
            timestamp = System.currentTimeMillis()
        )

        assertEquals("session_123", note.sessionId)
        assertNull(note.captureId)
        assertEquals("Continuous vertical hairline crack along masonry joint.", note.transcript)
        assertNotNull(note.id)
    }

    @Test
    fun voiceNote_canAttachToSpecificCapture() {
        val note = VoiceNoteEntity(
            sessionId = "session_123",
            captureId = "capture_456",
            transcript = "Shear displacement noticeable near lintel base.",
            timestamp = System.currentTimeMillis()
        )

        assertEquals("session_123", note.sessionId)
        assertEquals("capture_456", note.captureId)
        assertEquals("Shear displacement noticeable near lintel base.", note.transcript)
    }

    @Test
    fun voiceNoteRecorder_safeExecutionOnNullContext() {
        val recorder = VoiceNoteRecorder(null)
        assertEquals("", recorder.state.value.transcript)

        recorder.updateTranscriptManually("Manual inspection observation")
        assertEquals("Manual inspection observation", recorder.state.value.transcript)

        recorder.reset()
        assertEquals("", recorder.state.value.transcript)

        // Ensure start/stop/destroy don't crash when hardware/SpeechRecognizer unavailable
        recorder.startListening()
        recorder.stopListening()
        recorder.destroy()
    }
}
