package com.sitesweep.ui.debug

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesweep.detection.CrackClass
import com.sitesweep.detection.DelegateType
import com.sitesweep.detection.Severity
import com.sitesweep.ui.theme.PaletteInk
import com.sitesweep.ui.theme.PaletteInkElevated
import com.sitesweep.ui.theme.PaletteSafetyOrange
import com.sitesweep.ui.theme.PaletteSlate
import com.sitesweep.ui.theme.PaletteSlateBorder
import com.sitesweep.ui.theme.SeverityAmber
import com.sitesweep.ui.theme.SeverityGreen
import com.sitesweep.ui.theme.SeverityRed
import com.sitesweep.ui.theme.TextLightPrimary
import com.sitesweep.ui.theme.TextLightSecondary
import com.sitesweep.ui.theme.TextLightTertiary
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Inspection HUD showing live CameraX analysis stream, model classification,
 * confidence score, active hardware delegate, and rolling inference latency.
 *
 * Designed strictly to pitch deck palette and aesthetic rules:
 * - Industrial, field-grade tool styling
 * - No harsh gradients, no emojis, no em dashes
 * - No shutter button (capture is automated in downstream features)
 */
@Composable
fun DebugScreen(
    viewModel: DebugViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
    ) {
        // CameraX Live Preview
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // 5 FPS Throttled Analysis with STRATEGY_KEEP_ONLY_LATEST backpressure
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                viewModel.processImageProxy(imageProxy)
                            }
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("DebugScreen", "Use case binding failed", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Reticle / Center Sight Alignment (160x160 Model Input Field)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(160.dp)
                .height(160.dp)
                .border(1.dp, PaletteSafetyOrange.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
        )

        // Top Telemetry Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SITESWEEP // INFERENCE HUD",
                        color = PaletteSafetyOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = if (uiState.isFakeDetector) "ENGINE: SCRIPTED FAKE" else "ENGINE: LITERT ON-DEVICE",
                        color = TextLightPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Delegate Badge
                val delegateColor = when (uiState.activeDelegate) {
                    DelegateType.NNAPI -> SeverityGreen
                    DelegateType.GPU -> PaletteSafetyOrange
                    DelegateType.CPU -> TextLightSecondary
                }
                Box(
                    modifier = Modifier
                        .background(delegateColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                        .border(1.dp, delegateColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = uiState.activeDelegate.name,
                        color = delegateColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Bottom Telemetry Dashboard
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated.copy(alpha = 0.94f), RoundedCornerShape(4.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(4.dp))
                    .padding(16.dp)
            ) {
                // Distress Classification & Severity Band
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DETECTION CLASS",
                            color = TextLightTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.currentClass.displayName.uppercase(Locale.ROOT),
                            color = TextLightPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    val (severityBg, severityText) = when (uiState.severity) {
                        Severity.STRUCTURAL -> Pair(SeverityRed, TextLightPrimary)
                        Severity.MONITOR -> Pair(SeverityAmber, PaletteInk)
                        Severity.STABLE -> Pair(SeverityGreen, TextLightPrimary)
                    }

                    Box(
                        modifier = Modifier
                            .background(severityBg, RoundedCornerShape(2.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = uiState.severity.label,
                            color = severityText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Telemetry Metrics Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Confidence Metric
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CONFIDENCE",
                            color = TextLightTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f%%", uiState.confidence * 100f),
                            color = TextLightPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Rolling Latency Metric
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            text = "LATENCY (ROLLING)",
                            color = TextLightTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f ms", uiState.rollingLatencyMs),
                            color = PaletteSafetyOrange,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Analysis FPS (Throttled)
                    Column(modifier = Modifier.weight(0.9f)) {
                        Text(
                            text = "RATE",
                            color = TextLightTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f fps", uiState.fpsEstimate),
                            color = TextLightSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Engine Switcher Control (Field Testing Toggle)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaletteSlate, RoundedCornerShape(2.dp))
                        .clickable { viewModel.toggleDetectorType() }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.isFakeDetector) "SWITCH TO LITERT ON-DEVICE MODEL" else "SWITCH TO SCRIPTED FAKE DETECTOR",
                        color = TextLightPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
