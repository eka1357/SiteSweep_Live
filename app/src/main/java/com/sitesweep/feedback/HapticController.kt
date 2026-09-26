package com.sitesweep.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.sitesweep.detection.Severity

/**
 * Haptic feedback controller designed for blind operation.
 * As mandated by AGENTS.md: "The user is on a ladder looking at a wall, not at the screen."
 * Provides immediately recognizable, distinct vibration waveforms per severity band.
 */
open class HapticController(private val context: Context? = null) {

    private val vibrator: Vibrator? by lazy {
        context?.let { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }

    /**
     * Distinct tactile vibration patterns per severity band:
     * - STABLE: 40ms light pulse (confirming surface integrity)
     * - MONITOR: Double pulse (100ms on, 80ms off, 100ms on) - cautionary alert
     * - STRUCTURAL: Aggressive triple pulse (200ms on, 60ms off, 200ms on, 60ms off, 350ms on) - urgent hazard
     */
    open fun triggerSeverityHaptic(severity: Severity) {
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return

            when (severity) {
                Severity.STABLE -> {
                    val effect = VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                }
                Severity.MONITOR -> {
                    val timings = longArrayOf(0, 100, 80, 100)
                    val amplitudes = intArrayOf(0, 180, 0, 220)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vib.vibrate(effect)
                }
                Severity.STRUCTURAL -> {
                    val timings = longArrayOf(0, 200, 60, 200, 60, 350)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vib.vibrate(effect)
                }
            }
        } catch (e: Exception) {
            Log.w("HapticController", "Vibration failed: ${e.message}")
        }
    }
}
