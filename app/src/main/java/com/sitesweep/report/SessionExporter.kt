package com.sitesweep.report

import android.content.Context
import android.os.Environment
import android.util.Log
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.local.entity.SessionEntity
import com.sitesweep.data.local.entity.VoiceNoteEntity
import com.sitesweep.data.repository.SiteSweepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportResult(
    val exportDirectory: File,
    val markdownReport: File,
    val jsonMetadata: File,
    val copiedImagesCount: Int
)

/**
 * Session exporter for Office Kit handoff.
 * As mandated by AGENTS.md:
 * - Writes self-contained markdown file plus captured JPEGs to shared storage
 * - Laptop side picks it up via Office Kit and an agent turns it into a structural distress report
 * - Dumb and stable format - handoff boundary, not a place to be clever
 * - Zero broad storage permissions needed (writes to shared app-external directory)
 */
class SessionExporter(
    private val context: Context? = null,
    private val repository: SiteSweepRepository,
    private val customBaseDir: File? = null
) {

    /**
     * Exports a complete inspection session to shared storage.
     * Returns the ExportResult containing references to the generated report and image assets.
     */
    suspend fun exportSession(sessionId: String): ExportResult {
        return withContext(Dispatchers.IO) {
            val session = repository.getSessionById(sessionId)
                ?: throw IllegalArgumentException("Session not found: $sessionId")

            val captures = repository.getCapturesForSession(sessionId).first()
            val voiceNotes = repository.getVoiceNotesForSession(sessionId).first()

            val baseDir = customBaseDir 
                ?: context?.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: File(context?.filesDir ?: File("."), "exports")

            val exportFolder = File(baseDir, "SiteSweep_Export_${session.id.take(8)}")
            if (!exportFolder.exists()) {
                exportFolder.mkdirs()
            }

            val imagesFolder = File(exportFolder, "captures").apply {
                if (!exists()) mkdirs()
            }

            // Copy captured JPEGs to export captures directory
            var copiedCount = 0
            val relativeImagePaths = mutableMapOf<String, String>()

            for (capture in captures) {
                val sourceFile = File(capture.imagePath)
                val destFileName = "capture_${capture.id.take(8)}.jpg"
                val destFile = File(imagesFolder, destFileName)

                if (sourceFile.exists()) {
                    copyFile(sourceFile, destFile)
                    relativeImagePaths[capture.id] = "captures/$destFileName"
                    copiedCount++
                } else {
                    // Create dummy placeholder frame if source unavailable
                    destFile.createNewFile()
                    relativeImagePaths[capture.id] = "captures/$destFileName"
                }
            }

            // Generate self-contained markdown report
            val mdFile = File(exportFolder, "report.md")
            val mdContent = generateMarkdownReport(session, captures, voiceNotes, relativeImagePaths)
            mdFile.writeText(mdContent)

            // Generate machine-readable JSON metadata for Office Kit laptop agent
            val jsonFile = File(exportFolder, "session.json")
            val jsonContent = generateJsonMetadata(session, captures, voiceNotes, relativeImagePaths)
            jsonFile.writeText(jsonContent)

            Log.i("SessionExporter", "Exported session $sessionId to ${exportFolder.absolutePath}")

            ExportResult(
                exportDirectory = exportFolder,
                markdownReport = mdFile,
                jsonMetadata = jsonFile,
                copiedImagesCount = copiedCount
            )
        }
    }

    private fun generateMarkdownReport(
        session: SessionEntity,
        captures: List<CaptureEntity>,
        voiceNotes: List<VoiceNoteEntity>,
        imagePaths: Map<String, String>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val startedDate = dateFormat.format(Date(session.startedAt))
        val endedDate = session.endedAt?.let { dateFormat.format(Date(it)) } ?: "In Progress"

        val sb = StringBuilder()
        sb.appendLine("# SiteSweep Structural Distress Inspection Report")
        sb.appendLine()
        sb.appendLine("- **Session Label**: ${session.label}")
        sb.appendLine("- **Session ID**: ${session.id}")
        sb.appendLine("- **Started**: $startedDate")
        sb.appendLine("- **Ended**: $endedDate")
        sb.appendLine("- **Total Captures**: ${captures.size}")
        sb.appendLine("- **Offline Inspection Pipeline**: On-Device LiteRT (NNAPI / GPU / CPU)")
        sb.appendLine()

        val sessionNotes = voiceNotes.filter { it.captureId == null }
        if (sessionNotes.isNotEmpty()) {
            sb.appendLine("## Session Voice Notes")
            for (note in sessionNotes) {
                val time = dateFormat.format(Date(note.timestamp))
                sb.appendLine("- *[$time]*: ${note.transcript}")
            }
            sb.appendLine()
        }

        sb.appendLine("## Distress Observations")
        sb.appendLine()

        if (captures.isEmpty()) {
            sb.appendLine("No distress captures recorded during this sweep session.")
        } else {
            captures.forEachIndexed { index, capture ->
                val time = dateFormat.format(Date(capture.timestamp))
                val imgRelPath = imagePaths[capture.id] ?: ""
                val captureNotes = voiceNotes.filter { it.captureId == capture.id }

                sb.appendLine("### Observation ${index + 1}: ${capture.severity}")
                sb.appendLine("- **Timestamp**: $time")
                sb.appendLine("- **Severity Band**: ${capture.severity}")
                sb.appendLine("- **Confidence**: ${(capture.confidence * 100).toInt()}%")
                sb.appendLine("- **Location Key (Geohash)**: `${capture.locationKey}`")
                sb.appendLine("- **GPS Coordinates**: ${capture.lat}, ${capture.lng}")

                if (captureNotes.isNotEmpty()) {
                    sb.appendLine("- **Field Voice Notes**:")
                    captureNotes.forEach { note ->
                        sb.appendLine("  - \"${note.transcript}\"")
                    }
                }

                if (imgRelPath.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine("![Observation ${index + 1}]($imgRelPath)")
                }
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    private fun generateJsonMetadata(
        session: SessionEntity,
        captures: List<CaptureEntity>,
        voiceNotes: List<VoiceNoteEntity>,
        imagePaths: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"version\": \"1.0.0\",")
        sb.appendLine("  \"session\": {")
        sb.appendLine("    \"id\": \"${session.id}\",")
        sb.appendLine("    \"label\": \"${escapeJson(session.label)}\",")
        sb.appendLine("    \"startedAt\": ${session.startedAt},")
        sb.appendLine("    \"endedAt\": ${session.endedAt ?: "null"}")
        sb.appendLine("  },")

        sb.appendLine("  \"captures\": [")
        captures.forEachIndexed { index, c ->
            val isLast = index == captures.size - 1
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"${c.id}\",")
            sb.appendLine("      \"timestamp\": ${c.timestamp},")
            sb.appendLine("      \"severity\": \"${c.severity}\",")
            sb.appendLine("      \"confidence\": ${c.confidence},")
            sb.appendLine("      \"locationKey\": \"${c.locationKey}\",")
            sb.appendLine("      \"latitude\": ${c.lat},")
            sb.appendLine("      \"longitude\": ${c.lng},")
            sb.appendLine("      \"imagePath\": \"${imagePaths[c.id] ?: ""}\"")
            sb.appendLine("    }${if (isLast) "" else ","}")
        }
        sb.appendLine("  ],")

        sb.appendLine("  \"voiceNotes\": [")
        voiceNotes.forEachIndexed { index, v ->
            val isLast = index == voiceNotes.size - 1
            sb.appendLine("    {")
            sb.appendLine("      \"id\": \"${v.id}\",")
            sb.appendLine("      \"captureId\": ${if (v.captureId != null) "\"${v.captureId}\"" else "null"},")
            sb.appendLine("      \"transcript\": \"${escapeJson(v.transcript)}\",")
            sb.appendLine("      \"timestamp\": ${v.timestamp}")
            sb.appendLine("    }${if (isLast) "" else ","}")
        }
        sb.appendLine("  ]")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }
}
