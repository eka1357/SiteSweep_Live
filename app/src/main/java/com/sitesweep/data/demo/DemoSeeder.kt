package com.sitesweep.data.demo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.sitesweep.capture.FrameStore
import com.sitesweep.capture.GeoTagger
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.IssueEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DemoSeeder inserting 3 historical captures at the fixed demo locationKey.
 * Per AGENTS.md:
 * - 3 historical captures at fixed locationKey
 * - Dates 3 weeks apart
 * - Severity increasing (STABLE -> MONITOR -> STRUCTURAL)
 * - Real Room rows, same code path as live captures
 * - Real JPEGs stored in internal FrameStore
 * - Gated behind settings toggle for resetting between demo runs
 */
class DemoSeeder(
    private val context: Context? = null,
    private val repository: SiteSweepRepository,
    private val frameStore: FrameStore = FrameStore(context),
    customPrefs: SharedPreferences? = null
) {

    companion object {
        const val PREFS_NAME = "sitesweep_demo_prefs"
        const val KEY_DEMO_SEEDED = "key_demo_seeded_v3"
        const val DEMO_SESSION_ID = "session_demo_historical_01"
        const val CAPTURE_1_ID = "capture_demo_hist_01"
        const val CAPTURE_2_ID = "capture_demo_hist_02"
        const val CAPTURE_3_ID = "capture_demo_hist_03"
        const val DEMO_ISSUE_1_ID = "DEMO-ISS-01"
        const val DEMO_ISSUE_2_ID = "ISS-002"
        const val DEMO_ISSUE_3_ID = "ISS-003"
    }

    private val prefs = customPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSeeded(): Boolean = prefs?.getBoolean(KEY_DEMO_SEEDED, false) ?: false

    suspend fun seed(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure existing demo rows are cleared first to prevent duplicates
                clearInternal()

                val now = System.currentTimeMillis()
                val threeWeeksMs = 21L * 24 * 3600 * 1000L
                val timeReading1 = now - (2 * threeWeeksMs) // 6 weeks ago
                val timeReading2 = now - threeWeeksMs       // 3 weeks ago
                val timeReading3 = now - (2 * 3600 * 1000L) // earlier today

                // Create historical inspection session
                val demoSession = SessionEntity(
                    id = DEMO_SESSION_ID,
                    startedAt = timeReading1,
                    endedAt = timeReading3,
                    label = "BASELINE WALL SURVEY"
                )
                repository.createSession(demoSession) // inserts with fixed DEMO_SESSION_ID to satisfy FK

                val location = GeoTagger.DEFAULT_LOCATION_KEY
                val lat = GeoTagger.DEFAULT_LAT
                val lng = GeoTagger.DEFAULT_LNG

                // 1. Reading 1: 6 weeks ago -> STABLE (18% NONE)
                val bmp1 = createConcreteCrackBitmap(crackWidthPx = 2f, crackIntensity = 0.3f)
                val path1 = frameStore.saveFrame(bmp1, CAPTURE_1_ID)
                val capture1 = CaptureEntity(
                    id = CAPTURE_1_ID,
                    sessionId = DEMO_SESSION_ID,
                    imagePath = path1,
                    lat = lat,
                    lng = lng,
                    timestamp = timeReading1,
                    severity = "STABLE",
                    confidence = 0.18f,
                    locationKey = location
                )
                repository.insertCapture(capture1)

                // 2. Reading 2: 3 weeks ago -> MONITOR (76% HAIRLINE)
                val bmp2 = createConcreteCrackBitmap(crackWidthPx = 5f, crackIntensity = 0.65f)
                val path2 = frameStore.saveFrame(bmp2, CAPTURE_2_ID)
                val capture2 = CaptureEntity(
                    id = CAPTURE_2_ID,
                    sessionId = DEMO_SESSION_ID,
                    imagePath = path2,
                    lat = lat,
                    lng = lng,
                    timestamp = timeReading2,
                    severity = "MONITOR",
                    confidence = 0.76f,
                    locationKey = location
                )
                repository.insertCapture(capture2)

                // 3. Reading 3: Recent -> STRUCTURAL (94% STRUCTURAL)
                val bmp3 = createConcreteCrackBitmap(crackWidthPx = 10f, crackIntensity = 0.95f)
                val path3 = frameStore.saveFrame(bmp3, CAPTURE_3_ID)
                val capture3 = CaptureEntity(
                    id = CAPTURE_3_ID,
                    sessionId = DEMO_SESSION_ID,
                    imagePath = path3,
                    lat = lat,
                    lng = lng,
                    timestamp = timeReading3,
                    severity = "STRUCTURAL",
                    confidence = 0.94f,
                    locationKey = location
                )
                repository.insertCapture(capture3)

                // 4. Seed single compelling example demo issue:
                // Building A - South Shear Wall (Col C-12) [DEMO]
                val demoIssue = IssueEntity(
                    id = DEMO_ISSUE_1_ID,
                    originCaptureId = CAPTURE_1_ID,
                    latestCaptureId = CAPTURE_3_ID,
                    locationKey = location,
                    title = "Building A - South Shear Wall (Col C-12) [DEMO]",
                    status = IssueStatus.RESOLVED.name,
                    assignedTo = "Civil Unit 1 (Structural)",
                    engineerNotes = "Critical shear fracture propagating on column C-12 face. Low-pressure epoxy injection and carbon-wrap applied.",
                    resolutionNotes = "Engineer sign-off: Epoxy sealed & surface stabilized. Monitored stable.",
                    createdAt = timeReading1,
                    updatedAt = timeReading3,
                    resolvedAt = timeReading3
                )
                repository.insertIssue(demoIssue)

                prefs?.edit()?.putBoolean(KEY_DEMO_SEEDED, true)?.apply()
                true
            } catch (e: Exception) {
                Log.e("DemoSeeder", "Error seeding demo data: ${e.message}", e)
                false
            }
        }
    }

    suspend fun clear(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                clearInternal()
                prefs?.edit()?.putBoolean(KEY_DEMO_SEEDED, false)?.apply()
                true
            } catch (e: Exception) {
                Log.e("DemoSeeder", "Error clearing demo data: ${e.message}", e)
                false
            }
        }
    }

    private suspend fun clearInternal() {
        repository.deleteSession(DEMO_SESSION_ID)
        repository.deleteIssue(DEMO_ISSUE_1_ID)
        repository.deleteIssue("ISS-001")
        repository.deleteIssue(DEMO_ISSUE_2_ID)
        repository.deleteIssue(DEMO_ISSUE_3_ID)
        // Also clean up image files
        frameStore.deleteFrame(CAPTURE_1_ID)
        frameStore.deleteFrame(CAPTURE_2_ID)
        frameStore.deleteFrame(CAPTURE_3_ID)
    }

    /**
     * Generates a concrete-textured bitmap with an authentic crack fissure.
     * Guarantees realistic visual assets for the demo inspection comparison.
     */
    fun createConcreteCrackBitmap(crackWidthPx: Float, crackIntensity: Float): Bitmap? {
        return try {
            val width = 320
            val height = 320
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ?: return null
            val canvas = Canvas(bitmap)

        // Concrete slate canvas
        val bgPaint = Paint().apply {
            color = Color.rgb(65, 70, 75)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Subtle aggregate noise texture
        val noisePaint = Paint().apply {
            color = Color.rgb(55, 60, 64)
            strokeWidth = 2f
        }
        for (i in 0..400) {
            val rx = (Math.random() * width).toFloat()
            val ry = (Math.random() * height).toFloat()
            canvas.drawPoint(rx, ry, noisePaint)
        }

        // Crack path running diagonally across the masonry
        val crackPaint = Paint().apply {
            color = Color.rgb(
                (28 * (1f - crackIntensity * 0.5f)).toInt(),
                (28 * (1f - crackIntensity * 0.5f)).toInt(),
                (30 * (1f - crackIntensity * 0.5f)).toInt()
            )
            strokeWidth = crackWidthPx
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }

        val path = Path()
        path.moveTo(60f, 40f)
        path.lineTo(95f, 90f)
        path.lineTo(130f, 130f)
        path.lineTo(155f, 185f)
        path.lineTo(210f, 230f)
        path.lineTo(250f, 280f)

        canvas.drawPath(path, crackPaint)

        // Secondary tributary fissure for wider structural cracks
        if (crackWidthPx > 4f) {
            val tributaryPaint = Paint().apply {
                color = crackPaint.color
                strokeWidth = crackWidthPx * 0.5f
                style = Paint.Style.STROKE
                isAntiAlias = true
            }
            val tribPath = Path()
            tribPath.moveTo(130f, 130f)
            tribPath.lineTo(170f, 140f)
            tribPath.lineTo(200f, 160f)
            canvas.drawPath(tribPath, tributaryPaint)
        }

        return bitmap
        } catch (e: Throwable) {
            null
        }
    }
}
