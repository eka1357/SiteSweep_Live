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
    fun manifest_doesNotContainInternetPermission() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val content = manifestFile.readText()
        assertFalse(
            "Hard constraint violation: android.permission.INTERNET found in manifest!",
            content.contains("android.permission.INTERNET")
        )
        assertFalse(
            "Hard constraint violation: ACCESS_NETWORK_STATE found in manifest!",
            content.contains("android.permission.ACCESS_NETWORK_STATE")
        )
    }

    @Test
    fun manifest_containsRequiredInspectionPermissions() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val content = manifestFile.readText()

        assertTrue("Must declare CAMERA permission", content.contains("android.permission.CAMERA"))
        assertTrue("Must declare VIBRATE permission", content.contains("android.permission.VIBRATE"))
        assertTrue("Must declare RECORD_AUDIO permission", content.contains("android.permission.RECORD_AUDIO"))
    }
}
