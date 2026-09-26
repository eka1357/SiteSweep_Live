package com.sitesweep.capture

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Stores captured inspection frames as JPEGs inside app-internal storage.
 * As mandated by AGENTS.md, all captures are strictly kept in app-internal filesDir
 * (no broad storage permissions required, fully works offline/airplane mode).
 */
open class FrameStore(private val context: Context? = null) {

    private val capturesDir: File
        get() = File(context?.filesDir ?: File("."), "captures").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    /**
     * Saves a frame bitmap as JPEG and returns its absolute path.
     * Compression quality 85 preserves distress hairline details while keeping file size small (~80-120 KB).
     */
    open suspend fun saveFrame(bitmap: Bitmap?, captureId: String = UUID.randomUUID().toString()): String {
        return withContext(Dispatchers.IO) {
            val file = File(capturesDir, "capture_$captureId.jpg")
            try {
                if (bitmap != null) {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.flush()
                    }
                } else {
                    file.createNewFile()
                }
                file.absolutePath
            } catch (e: Exception) {
                Log.e("FrameStore", "Failed to save frame JPEG: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Deletes a captured image file if it exists.
     */
    suspend fun deleteFrame(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) file.delete() else false
            } catch (e: Exception) {
                Log.w("FrameStore", "Failed to delete frame: $filePath", e)
                false
            }
        }
    }

    /**
     * Retrieves all files in the internal captures directory.
     */
    fun getAllCaptureFiles(): List<File> {
        return capturesDir.listFiles()?.toList() ?: emptyList()
    }
}
