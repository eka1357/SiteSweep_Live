package com.sitesweep

import android.graphics.Bitmap
import com.sitesweep.capture.AutoCapture
import com.sitesweep.capture.AutoCaptureState
import com.sitesweep.capture.FrameStore
import com.sitesweep.capture.GeoCoordinates
import com.sitesweep.capture.GeoTagger
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.repository.SiteSweepRepository
import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.CrackDetectionResult
import com.sitesweep.detection.DelegateType
import com.sitesweep.detection.Severity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoCaptureTest {

    private lateinit var fakeRepository: FakeSiteSweepRepository
    private lateinit var fakeFrameStore: FakeFrameStore
    private lateinit var fakeGeoTagger: FakeGeoTagger
    private lateinit var autoCapture: AutoCapture

    private val dummyBitmap: Bitmap? = null

    class FakeFrameStore : FrameStore(null) {
        var saveCallCount = 0
        override suspend fun saveFrame(bitmap: Bitmap?, captureId: String): String {
            saveCallCount++
            return "/data/user/0/com.sitesweep/files/captures/capture_$captureId.jpg"
        }
    }

    class FakeGeoTagger : GeoTagger(null) {
        override fun getCachedLocation(): GeoCoordinates {
            return GeoCoordinates(17.44858, 78.37582, "tepfzp0h")
        }
    }

    class FakeSiteSweepRepository : SiteSweepRepository {
        val insertedCaptures = mutableListOf<CaptureEntity>()

        override suspend fun createSession(label: String) = SessionEntity(id = "s1", label = label)
        override suspend fun createSession(session: SessionEntity) = session
        override suspend fun endSession(id: String) {}
        override suspend fun getSessionById(id: String) = SessionEntity(id = id, label = "Test")
        override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(emptyList())
        override suspend fun deleteSession(id: String) {}

        override suspend fun insertCapture(capture: CaptureEntity) {
            insertedCaptures.add(capture)
        }
        override suspend fun getCaptureById(id: String) = insertedCaptures.find { it.id == id }
        override fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>> = flowOf(insertedCaptures)
        override suspend fun getCapturesByLocationKey(locationKey: String) = insertedCaptures.filter { it.locationKey == locationKey }
        override fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>> = flowOf(emptyList())
        override fun getAllCaptures(): Flow<List<CaptureEntity>> = flowOf(insertedCaptures)

        override suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {}
        override fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>> = flowOf(emptyList())
        override fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>> = flowOf(emptyList())
    }

    @Before
    fun setUp() {
        fakeRepository = FakeSiteSweepRepository()
        fakeFrameStore = FakeFrameStore()
        fakeGeoTagger = FakeGeoTagger()

        autoCapture = AutoCapture(
            frameStore = fakeFrameStore,
            geoTagger = fakeGeoTagger,
            repository = fakeRepository,
            debounceCooldownMs = 3000L
        )
    }

    private fun createResult(crackClass: CrackClass, severity: Severity, prob: Float): CrackDetectionResult {
        return CrackDetectionResult(
            crackClass = crackClass,
            confidence = prob,
            severity = severity,
            latencyMs = 15L,
            delegateType = DelegateType.NNAPI,
            crackProbability = prob
        )
    }

    @Test
    fun initialDistress_triggersCaptureAndPersists() = runBlocking {
        val distressResult = createResult(CrackClass.STRUCTURAL, Severity.STRUCTURAL, 0.85f)
        val capture = autoCapture.evaluateFrame(dummyBitmap, distressResult, "s1", elapsedTimeMs = 1000L)

        assertNotNull("Distress frame must trigger capture", capture)
        assertEquals(1, fakeRepository.insertedCaptures.size)
        assertEquals("STRUCTURAL", capture?.severity)
        assertEquals("tepfzp0h", capture?.locationKey)
        assertEquals(AutoCaptureState.DEBOUNCE_COOLDOWN, autoCapture.state)
        assertEquals(1, fakeFrameStore.saveCallCount)
    }

    @Test
    fun consecutiveDistressWithin3s_isSuppressedByDebounce() = runBlocking {
        val distress = createResult(CrackClass.STRUCTURAL, Severity.STRUCTURAL, 0.85f)

        // T = 1000ms: First capture
        val c1 = autoCapture.evaluateFrame(dummyBitmap, distress, "s1", elapsedTimeMs = 1000L)
        assertNotNull(c1)

        // T = 2000ms (+1s): Suppressed
        val c2 = autoCapture.evaluateFrame(dummyBitmap, distress, "s1", elapsedTimeMs = 2000L)
        assertNull("Distress within 3s must be debounced", c2)

        // T = 3500ms (+2.5s): Still suppressed
        val c3 = autoCapture.evaluateFrame(dummyBitmap, distress, "s1", elapsedTimeMs = 3500L)
        assertNull("Distress before 3s window expires must be debounced", c3)

        assertEquals("Only 1 capture should exist in DB", 1, fakeRepository.insertedCaptures.size)
    }

    @Test
    fun clearBeforeRearmRule_requiresSignalToClearBeforeNextCapture() = runBlocking {
        val distress = createResult(CrackClass.STRUCTURAL, Severity.STRUCTURAL, 0.85f)
        val stable = createResult(CrackClass.NONE, Severity.STABLE, 0.15f)

        // T = 1000ms: Initial capture
        val c1 = autoCapture.evaluateFrame(dummyBitmap, distress, "s1", elapsedTimeMs = 1000L)
        assertNotNull(c1)

        // T = 4500ms (+3.5s elapsed, but crack STILL in view)
        val c2 = autoCapture.evaluateFrame(dummyBitmap, distress, "s1", elapsedTimeMs = 4500L)
        assertNull("Must NOT re-capture if distress has not cleared", c2)
        assertEquals(AutoCaptureState.AWAITING_CLEAR, autoCapture.state)

        // T = 5000ms: Crack clears (phone panned to blank wall)
        val c3 = autoCapture.evaluateFrame(dummyBitmap, stable, "s1", elapsedTimeMs = 5000L)
        assertNull(c3)
        assertEquals(AutoCaptureState.ARMED, autoCapture.state)

        // T = 5500ms: New crack comes into view -> Successfully captures!
        val c4 = autoCapture.evaluateFrame(dummyBitmap, distress, "s1", elapsedTimeMs = 5500L)
        assertNotNull("Second distress after clearing must capture", c4)
        assertEquals(2, fakeRepository.insertedCaptures.size)
    }

    @Test
    fun stableFrame_doesNotTriggerCapture() = runBlocking {
        val stable = createResult(CrackClass.NONE, Severity.STABLE, 0.10f)
        val capture = autoCapture.evaluateFrame(dummyBitmap, stable, "s1", elapsedTimeMs = 1000L)

        assertNull("Stable surface must not trigger capture", capture)
        assertTrue(fakeRepository.insertedCaptures.isEmpty())
        assertTrue(autoCapture.isArmed)
    }
}
