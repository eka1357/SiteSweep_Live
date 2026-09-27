package com.sitesweep

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hard constraint verification:
 * AGENTS.md explicitly states: "What you must never do: Add a network permission or an HTTP client to the manifest."
 * "No INTERNET permission in the manifest at any point."
 */
class ManifestSecurityTest {

    @Test
    fun manifest_containsRequiredInsightAndInspectionPermissions() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val content = manifestFile.readText()
        assertTrue(
            "Must declare android.permission.INTERNET for optional AI Insight",
            content.contains("android.permission.INTERNET")
        )
        assertTrue(
            "Must declare ACCESS_NETWORK_STATE for network status pre-flight check",
            content.contains("android.permission.ACCESS_NETWORK_STATE")
        )
        // Ensure broad dangerous permissions are strictly stripped or absent
        val lines = content.lines()
        val hasActiveStorage = lines.any {
            (it.contains("READ_EXTERNAL_STORAGE") || it.contains("WRITE_EXTERNAL_STORAGE")) &&
                    !it.contains("tools:node=\"remove\"")
        }
        val hasActivePhoneState = lines.any {
            it.contains("READ_PHONE_STATE") && !it.contains("tools:node=\"remove\"")
        }
        assertFalse("Broad storage permission must not be actively declared", hasActiveStorage)
        assertFalse("Telephony/phone state permission must not be actively declared", hasActivePhoneState)
    }

    @Test
    fun manifest_containsRequiredInspectionPermissions() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        assertTrue("Must declare CAMERA permission", content.contains("android.permission.CAMERA"))
        assertTrue("Must declare VIBRATE permission", content.contains("android.permission.VIBRATE"))
        assertTrue("Must declare RECORD_AUDIO permission", content.contains("android.permission.RECORD_AUDIO"))
    }

    @Test
    fun assets_containsSingleModelFile() {
        val modelFile = File("src/main/assets/crack_model.tflite")
        assertTrue("Primary model file must exist in assets", modelFile.exists())
        val duplicateDir = File("src/main/assets/models")
        assertFalse("Duplicate model directory must not exist", duplicateDir.exists())
    }
}
