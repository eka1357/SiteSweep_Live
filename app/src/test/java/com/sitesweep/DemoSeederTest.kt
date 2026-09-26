package com.sitesweep

import android.content.SharedPreferences
import com.sitesweep.capture.FrameStore
import com.sitesweep.capture.GeoTagger
import com.sitesweep.data.demo.DemoSeeder
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DemoSeederTest {

    private lateinit var fakeRepository: TestRepository
    private lateinit var fakePrefs: FakePrefs
    private lateinit var fakeFrameStore: FakeTestFrameStore
    private lateinit var seeder: DemoSeeder

    class FakePrefs : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { key?.let { data[it] = value } }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = apply { key?.let { data[it] = value } }
            override fun putLong(key: String?, value: Long) = apply { key?.let { data[it] = value } }
            override fun putFloat(key: String?, value: Float) = apply { key?.let { data[it] = value } }
            override fun putBoolean(key: String?, value: Boolean) = apply { key?.let { data[it] = value } }
            override fun remove(key: String?) = apply { key?.let { data.remove(it) } }
            override fun clear() = apply { data.clear() }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }

    class TestRepository : SiteSweepRepository {
        val sessions = mutableListOf<SessionEntity>()
        val captures = mutableListOf<CaptureEntity>()

        override suspend fun createSession(label: String): SessionEntity {
            val session = SessionEntity(id = DemoSeeder.DEMO_SESSION_ID, label = label)
            sessions.add(session)
            return session
        }
        override suspend fun createSession(session: SessionEntity): SessionEntity {
            sessions.add(session)
            return session
        }
        override suspend fun endSession(id: String) {}
        override suspend fun getSessionById(id: String) = sessions.find { it.id == id }
        override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(sessions)
        override suspend fun deleteSession(id: String) {
            sessions.removeAll { it.id == id }
            captures.removeAll { it.sessionId == id }
        }

        override suspend fun insertCapture(capture: CaptureEntity) {
            captures.add(capture)
        }
        override suspend fun getCaptureById(id: String) = captures.find { it.id == id }
        override fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>> = flowOf(captures)
        override suspend fun getCapturesByLocationKey(locationKey: String) = captures.filter { it.locationKey == locationKey }
        override fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>> = flowOf(captures)
        override fun getAllCaptures(): Flow<List<CaptureEntity>> = flowOf(captures)

        override suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {}
        override fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>> = flowOf(emptyList())
        override fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>> = flowOf(emptyList())
    }

    class FakeTestFrameStore : FrameStore(null) {
        override suspend fun saveFrame(bitmap: android.graphics.Bitmap?, captureId: String): String {
            return "/mock/storage/$captureId.jpg"
        }
        override suspend fun deleteFrame(filePath: String): Boolean = true
    }

    @Before
    fun setUp() {
        fakeRepository = TestRepository()
        fakeFrameStore = FakeTestFrameStore()
        fakePrefs = FakePrefs()

        seeder = DemoSeeder(
            context = null,
            repository = fakeRepository,
            frameStore = fakeFrameStore,
            customPrefs = fakePrefs
        )
    }

    @Test
    fun seed_createsThreeCapturesWithIncreasingSeverity() = runBlocking {
        val success = seeder.seed()
        assertTrue("Seed must return true", success)

        val captures = fakeRepository.captures
        assertEquals("Must seed exactly 3 historical captures", 3, captures.size)

        // All 3 must share the exact same demo locationKey
        val expectedLocation = GeoTagger.DEFAULT_LOCATION_KEY
        assertTrue("All captures must match demo locationKey", captures.all { it.locationKey == expectedLocation })

        // Check increasing severity
        assertEquals("STABLE", captures[0].severity)
        assertEquals("MONITOR", captures[1].severity)
        assertEquals("STRUCTURAL", captures[2].severity)

        // Check dates are ~3 weeks apart
        val threeWeeksApproxMs = 20L * 24 * 3600 * 1000L
        val diff1 = captures[1].timestamp - captures[0].timestamp
        val diff2 = captures[2].timestamp - captures[1].timestamp
        assertTrue("Captures 0 and 1 must be spaced by ~3 weeks", diff1 >= threeWeeksApproxMs)
        assertTrue("Captures 1 and 2 must be spaced by ~3 weeks", diff2 >= threeWeeksApproxMs)
    }

    @Test
    fun clear_removesSeededRecords() = runBlocking {
        seeder.seed()
        assertEquals(3, fakeRepository.captures.size)

        seeder.clear()
        assertEquals("Clearing must remove seeded captures", 0, fakeRepository.captures.size)
    }
}
