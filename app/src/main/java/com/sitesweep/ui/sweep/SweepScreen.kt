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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
    onCaptureClick: (CaptureEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

        // 3. Viewfinder Focus Reticle (Indicates center inference area)
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
                .border(1.dp, glowColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )

        val screenTopPadding = getScreenTopPadding()

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
            // Sessions navigation button
            Box(
                modifier = Modifier
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .clickable(onClick = onNavigateToSessions)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "< SESSIONS",
                    color = TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            // Session label
            Text(
                text = uiState.currentSession?.label?.uppercase(Locale.US) ?: "SWEEP ACTIVE",
                color = TextLightSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Severity Live Badge
            Box(
                modifier = Modifier
                    .background(glowColor.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                    .border(1.dp, glowColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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

        // 5. Running Capture Strip Along Bottom
        RunningCaptureStrip(
            captures = uiState.captures,
            autoCaptureState = uiState.autoCaptureState,
            onCaptureClick = onCaptureClick,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
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
    val (statusLabel, statusColor) = when (autoCaptureState) {
        AutoCaptureState.ARMED -> "AUTO-SWEEP ACTIVE" to PaletteSafetyOrange
        AutoCaptureState.DEBOUNCE_COOLDOWN -> "CAPTURED • COOLDOWN" to TextLightTertiary
        AutoCaptureState.AWAITING_CLEAR -> "PAN OFF TO RE-ARM" to SeverityAmber
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PaletteInk.copy(alpha = 0.92f))
            .border(width = 1.dp, color = PaletteSlateBorder)
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
            .width(76.dp)
            .clickable(onClick = onClick)
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(2.dp))
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(70.dp, 56.dp)
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
                "MONITOR" -> "MONITOR"
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
