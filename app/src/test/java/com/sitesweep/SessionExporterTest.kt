package com.sitesweep

import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.repository.SiteSweepRepository
import com.sitesweep.report.SessionExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionExporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeRepository: FakeExportRepository
    private lateinit var exporter: SessionExporter

    class FakeExportRepository(
        private val session: SessionEntity,
        private val captures: List<CaptureEntity>,
        private val voiceNotes: List<VoiceNoteEntity>
    ) : SiteSweepRepository {
        override suspend fun createSession(label: String) = session
        override suspend fun endSession(id: String) {}
        override suspend fun getSessionById(id: String) = if (id == session.id) session else null
        override fun getAllSessions(): Flow<List<SessionEntity>> = flowOf(listOf(session))
        override suspend fun deleteSession(id: String) {}

        override suspend fun insertCapture(capture: CaptureEntity) {}
        override suspend fun getCaptureById(id: String) = captures.find { it.id == id }
        override fun getCapturesForSession(sessionId: String): Flow<List<CaptureEntity>> = flowOf(captures)
        override suspend fun getCapturesByLocationKey(locationKey: String) = captures.filter { it.locationKey == locationKey }
        override fun observeCapturesByLocationKey(locationKey: String): Flow<List<CaptureEntity>> = flowOf(captures)
        override fun getAllCaptures(): Flow<List<CaptureEntity>> = flowOf(captures)

        override suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity) {}
        override fun getVoiceNotesForSession(sessionId: String): Flow<List<VoiceNoteEntity>> = flowOf(voiceNotes)
        override fun getVoiceNotesForCapture(captureId: String): Flow<List<VoiceNoteEntity>> = flowOf(voiceNotes.filter { it.captureId == captureId })
    }

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("storage")

        val imageFile1 = tempFolder.newFile("test_cap1.jpg").apply { writeText("JPEG_DATA_1") }
        val imageFile2 = tempFolder.newFile("test_cap2.jpg").apply { writeText("JPEG_DATA_2") }

        val testSession = SessionEntity(
            id = "sess_export_001",
            label = "South Elevation Masonry",
            startedAt = 1727337600000L,
            endedAt = 1727341200000L
        )

        val testCaptures = listOf(
            CaptureEntity(
                id = "cap_exp_01",
                sessionId = "sess_export_001",
                imagePath = imageFile1.absolutePath,
                lat = 17.44858,
                lng = 78.37582,
                timestamp = 1727338000000L,
                severity = "MONITOR",
                confidence = 0.74f,
                locationKey = "tepfzp0h"
            ),
            CaptureEntity(
                id = "cap_exp_02",
                sessionId = "sess_export_001",
                imagePath = imageFile2.absolutePath,
                lat = 17.44860,
                lng = 78.37585,
                timestamp = 1727339500000L,
                severity = "STRUCTURAL",
                confidence = 0.92f,
                locationKey = "tepfzp0h"
            )
        )

        val testVoiceNotes = listOf(
            VoiceNoteEntity(
                id = "vn_exp_01",
                sessionId = "sess_export_001",
                captureId = "cap_exp_02",
                transcript = "Horizontal displacement 3mm above floor level.",
                timestamp = 1727339600000L
            )
        )

        fakeRepository = FakeExportRepository(testSession, testCaptures, testVoiceNotes)
        exporter = SessionExporter(
            context = null,
            repository = fakeRepository,
            customBaseDir = rootDir
        )
    }

    @Test
    fun exportSession_writesMarkdownJsonAndBundledJpegs() = runBlocking {
        val result = exporter.exportSession("sess_export_001")

        assertTrue("Export directory must exist", result.exportDirectory.exists())
        assertTrue("Markdown report must exist", result.markdownReport.exists())
        assertTrue("JSON metadata must exist", result.jsonMetadata.exists())
        assertEquals("Both JPEGs must be copied", 2, result.copiedImagesCount)

        val mdContent = result.markdownReport.readText()
        assertTrue("Report must contain session label", mdContent.contains("South Elevation Masonry"))
        assertTrue("Report must contain observation 1", mdContent.contains("### Observation 1: MONITOR"))
        assertTrue("Report must contain observation 2", mdContent.contains("### Observation 2: STRUCTURAL"))
        assertTrue("Report must reference bundled image path", mdContent.contains("![Observation 1](captures/capture_cap_exp_.jpg)"))
        assertTrue("Report must include voice note transcript", mdContent.contains("Horizontal displacement 3mm above floor level."))

        val jsonContent = result.jsonMetadata.readText()
        assertTrue("JSON must contain version", jsonContent.contains("\"version\": \"1.0.0\""))
        assertTrue("JSON must list captures", jsonContent.contains("\"severity\": \"STRUCTURAL\""))
    }
}
