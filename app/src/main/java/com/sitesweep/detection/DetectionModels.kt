package com.sitesweep.detection

/**
 * Structural distress classification categories.
 * Binary/tertiary classes: NONE, HAIRLINE, STRUCTURAL.
 */
enum class CrackClass(val displayName: String) {
    NONE("No Distress"),
    HAIRLINE("Hairline Crack"),
    STRUCTURAL("Structural Crack")
}

/**
 * Severity bands as defined in AGENTS.md.
 * No millimetre width claims anywhere in UI. Severity bands only.
 */
enum class Severity(val label: String) {
    STABLE("STABLE"),
    MONITOR("MONITOR"),
    STRUCTURAL("STRUCTURAL")
}

/**
 * Hardware execution delegates attempted in priority order:
 * NNAPI -> GPU -> CPU.
 */
enum class DelegateType(val label: String) {
    NNAPI("NNAPI (NPU/Accelerator)"),
    GPU("GPU Delegate"),
    CPU("CPU (Multi-thread)")
}

/**
 * Immutable detection result returned from inference pipeline.
 */
data class CrackDetectionResult(
    val crackClass: CrackClass,
    val confidence: Float,
    val severity: Severity,
    val latencyMs: Long,
    val delegateType: DelegateType,
    val timestamp: Long = System.currentTimeMillis(),
    val crackProbability: Float = confidence
)
