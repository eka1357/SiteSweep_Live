package com.sitesweep.ui.sweep

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesweep.capture.AutoCaptureState
import com.sitesweep.data.local.entity.CaptureEntity
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
import com.sitesweep.ui.theme.getScreenTopPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Fullscreen SweepScreen satisfying AGENTS.md requirements:
 * - Fullscreen CameraX stream throttled to ~5 fps
 * - NO shutter button (capture is 100% automated downstream)
 * - Severity band shown as a dynamic perimeter edge glow
 * - Running capture strip along the bottom
 * - Professional field-instrument aesthetic with deck palette
 */
@Composable
fun SweepScreen(
    viewModel: SweepViewModel,
    sessionId: String? = null,
    onNavigateToSessions: () -> Unit = {},
    backButtonLabel: String = "< SESSIONS",
    onCaptureClick: (CaptureEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiInsightState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(sessionId) {
        viewModel.initSession(sessionId)
    }

    val view = LocalView.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (e: Exception) {
                // Ignore if camera provider already unbound
            }
            cameraExecutor.shutdown()
        }
    }

    // Dynamic edge glow parameters per AGENTS.md severity band
    val (glowColor, glowWidth) = when (uiState.currentSeverity) {
        Severity.STABLE -> Pair(SeverityGreen.copy(alpha = 0.35f), 4.dp)
        Severity.MONITOR -> Pair(SeverityAmber.copy(alpha = 0.75f), 6.dp)
        Severity.STRUCTURAL -> Pair(SeverityRed.copy(alpha = 0.95f), 8.dp)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
    ) {
        // 1. Fullscreen CameraX Stream
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Severity Edge Glow (Continuous perimeter alert)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = glowWidth, color = glowColor)
        )

        // Subtle gradient vignette inward from perimeter
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, glowColor.copy(alpha = 0.18f)),
                        radius = 1200f
                    )
                )
        )

        // 3. Viewfinder Focus Reticle (Indicates center inference area) with live detection readout
        val probPercent = kotlin.math.round(uiState.crackProbability * 100f).toInt().coerceIn(0, 100)
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.Center)
                .border(1.5.dp, glowColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
        ) {
            // Explanatory label indicating on-device inference region
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .background(PaletteInk.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ON-DEVICE INFERENCE",
                    color = TextLightTertiary,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.4.sp
                )
            }

            // Live probability readout inside reticle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .background(PaletteInk.copy(alpha = 0.88f), RoundedCornerShape(2.dp))
                    .border(1.dp, glowColor.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "CRACK PROB",
                        color = TextLightTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$probPercent%",
                        color = when (uiState.currentSeverity) {
                            Severity.STABLE -> SeverityGreen
                            Severity.MONITOR -> SeverityAmber
                            Severity.STRUCTURAL -> SeverityRed
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        val screenTopPadding = getScreenTopPadding()

        // Session elapsed timer ticker
        var elapsedSeconds by remember { mutableStateOf(0L) }
        LaunchedEffect(uiState.currentSession?.startedAt) {
            val start = uiState.currentSession?.startedAt ?: System.currentTimeMillis()
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - start) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
        val timerFormatted = remember(elapsedSeconds) {
            val mins = elapsedSeconds / 60L
            val secs = elapsedSeconds % 60L
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }

        // 4. Industrial Top HUD Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(PaletteInk.copy(alpha = 0.92f))
                .padding(top = screenTopPadding, bottom = 12.dp, start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sessions navigation button with accessible touch target
            Box(
                modifier = Modifier
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .clickable(onClick = onNavigateToSessions)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = backButtonLabel,
                    color = TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Session label + Elapsed Timer + Capture count
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = uiState.currentSession?.label?.uppercase(Locale.US) ?: "SWEEP ACTIVE",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$timerFormatted • ${uiState.captures.size} CAPTURES",
                    color = PaletteSafetyOrange,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary AI Insight trigger (clearly marked as advisory)
                Box(
                    modifier = Modifier
                        .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                        .clickable(onClick = { viewModel.requestAiInsight() })
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "AI INSIGHT",
                            color = PaletteSafetyOrange,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ADVISORY",
                            color = TextLightTertiary,
                            fontSize = 7.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Severity Live Badge (Follows classifier state cleanly without redundant percentage)
                Box(
                    modifier = Modifier
                        .widthIn(min = 90.dp)
                        .background(glowColor.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                        .border(1.dp, glowColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.currentSeverity.label,
                        color = when (uiState.currentSeverity) {
                            Severity.STABLE -> SeverityGreen
                            Severity.MONITOR -> SeverityAmber
                            Severity.STRUCTURAL -> SeverityRed
                        },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 5. Bottom Section: Live Telemetry Bar (Class, Confidence, Latency, Rate) + Running Capture Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            SweepTelemetryBar(uiState = uiState)
            RunningCaptureStrip(
                captures = uiState.captures,
                autoCaptureState = uiState.autoCaptureState,
                onCaptureClick = onCaptureClick,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (aiInsightState !is com.sitesweep.insight.AiInsightState.Idle) {
            com.sitesweep.insight.AiInsightDialog(
                state = aiInsightState,
                onRetry = { viewModel.requestAiInsight() },
                onDismiss = { viewModel.dismissAiInsight() }
            )
        }
    }
}

/**
 * Live inference telemetry bar displaying detection class, confidence, rolling latency,
 * execution rate (fps), and active hardware acceleration delegate.
 */
@Composable
private fun SweepTelemetryBar(
    uiState: SweepUiState,
    modifier: Modifier = Modifier
) {
    val delegateColor = when (uiState.activeDelegate) {
        DelegateType.NNAPI -> SeverityGreen
        DelegateType.GPU -> PaletteSafetyOrange
        DelegateType.CPU -> TextLightSecondary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PaletteInkElevated.copy(alpha = 0.95f))
            .border(width = 1.dp, color = PaletteSlateBorder)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Crack Probability (Primary Numerical Metric)
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = "CRACK PROB",
                color = TextLightTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(1.dp))
            val probPercent = kotlin.math.round(uiState.crackProbability * 100f).toInt().coerceIn(0, 100)
            val probColor = when (uiState.currentSeverity) {
                Severity.STABLE -> SeverityGreen
                Severity.MONITOR -> SeverityAmber
                Severity.STRUCTURAL -> SeverityRed
            }
            Text(
                text = "$probPercent%",
                color = probColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // 2. Class / Severity Band
        Column(modifier = Modifier.weight(1.0f)) {
            Text(
                text = "SEVERITY",
                color = TextLightTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            val classLabel = when (uiState.currentClass) {
                CrackClass.STRUCTURAL -> "STRUCTURAL"
                CrackClass.HAIRLINE -> "HAIRLINE"
                CrackClass.NONE -> "NONE"
            }
            val classColor = when (uiState.currentSeverity) {
                Severity.STABLE -> SeverityGreen
                Severity.MONITOR -> SeverityAmber
                Severity.STRUCTURAL -> SeverityRed
            }
            Text(
                text = classLabel,
                color = classColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        // 3. Latency (Rolling)
        Column(modifier = Modifier.weight(0.85f)) {
            Text(
                text = "LATENCY",
                color = TextLightTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            val latencyFormatted = if (uiState.rollingLatencyMs > 0f) {
                String.format(Locale.US, "%.0f ms", uiState.rollingLatencyMs)
            } else if (uiState.latencyMs > 0L) {
                "${uiState.latencyMs} ms"
            } else {
                "-- ms"
            }
            Text(
                text = latencyFormatted,
                color = PaletteSafetyOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // 4. Rate (FPS)
        Column(modifier = Modifier.weight(0.85f)) {
            Text(
                text = "RATE",
                color = TextLightTertiary,
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            val fpsFormatted = if (uiState.fpsEstimate > 0f) {
                String.format(Locale.US, "%.1f FPS", uiState.fpsEstimate)
            } else {
                "5.0 FPS"
            }
            Text(
                text = fpsFormatted,
                color = TextLightSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Hardware Acceleration Delegate
        Box(
            modifier = Modifier
                .background(delegateColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                .border(1.dp, delegateColor, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = uiState.activeDelegate.name,
                color = delegateColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Running capture strip pinned along the bottom of SweepScreen.
 * Displays real-time captured thumbnails chronologically with severity accents.
 */
@Composable
private fun RunningCaptureStrip(
    captures: List<CaptureEntity>,
    autoCaptureState: AutoCaptureState,
    onCaptureClick: (CaptureEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCooldown = autoCaptureState == AutoCaptureState.DEBOUNCE_COOLDOWN
    val (statusLabel, statusColor) = when (autoCaptureState) {
        AutoCaptureState.ARMED -> "AUTO-SWEEP ACTIVE" to PaletteSafetyOrange
        AutoCaptureState.DEBOUNCE_COOLDOWN -> "CAPTURED • SAVED" to PaletteSafetyOrange
        AutoCaptureState.AWAITING_CLEAR -> "PAN OFF TO RE-ARM" to SeverityAmber
    }

    val stripBorderColor = if (isCooldown) PaletteSafetyOrange else PaletteSlateBorder

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PaletteInk.copy(alpha = 0.92f))
            .border(width = 1.dp, color = stripBorderColor)
            .navigationBarsPadding()
            .padding(vertical = 8.dp)
    ) {
        // Strip Header: Count and auto-capture status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SESSION CAPTURES (${captures.size})",
                color = TextLightSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (captures.isEmpty()) {
            // Utilitarian empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO DISTRESS DETECTED YET • AUTO-CAPTURING ON TRIGGER",
                    color = TextLightTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            // Horizontal scrollable strip of captured distress frames
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = captures,
                    key = { it.id }
                ) { capture ->
                    CaptureThumbnailItem(
                        capture = capture,
                        onClick = { onCaptureClick(capture) }
                    )
                }
            }
        }
    }
}

/**
 * Individual capture thumbnail card inside the running strip.
 */
@Composable
private fun CaptureThumbnailItem(
    capture: CaptureEntity,
    onClick: () -> Unit
) {
    val borderColor = when (capture.severity.uppercase(Locale.US)) {
        "STRUCTURAL" -> SeverityRed
        "MONITOR" -> SeverityAmber
        else -> SeverityGreen
    }

    val timeFormatted = remember(capture.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(capture.timestamp))
    }

    // Load bitmap asynchronously off main thread
    val bitmapState = produceState<Bitmap?>(initialValue = null, capture.imagePath) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(capture.imagePath)
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2 // Low memory thumbnail sampling
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .width(82.dp)
            .clickable(onClick = onClick)
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(2.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(74.dp, 56.dp)
                .background(PaletteSlate, RoundedCornerShape(1.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = bitmapState.value
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Capture at $timeFormatted",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "RAW",
                    color = TextLightTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val severityLabel = when (capture.severity.uppercase(Locale.US)) {
                "STRUCTURAL" -> "STRUCT"
                "MONITOR" -> "MON"
                else -> "STABLE"
            }
            Text(
                text = severityLabel,
                color = borderColor,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = timeFormatted.take(5),
                color = TextLightSecondary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
