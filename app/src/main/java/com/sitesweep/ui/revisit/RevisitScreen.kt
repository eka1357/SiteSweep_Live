package com.sitesweep.ui.revisit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesweep.data.local.entity.CaptureEntity
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

/**
 * RevisitScreen displaying chronological captures at a fixed locationKey geohash.
 * Surfaces prior readings so the engineer can directly inspect whether a crack
 * is stable, monitoring, or widening over time.
 */
@Composable
fun RevisitScreen(
    viewModel: RevisitViewModel,
    locationKey: String,
    targetCaptureId: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(locationKey, targetCaptureId) {
        viewModel.loadLocationHistory(locationKey, targetCaptureId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val screenTopPadding = getScreenTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(top = screenTopPadding, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "< BACK",
                    color = TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "REVISIT ANALYSIS",
                color = TextLightSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Geohash badge
                Box(
                    modifier = Modifier
                        .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = locationKey.take(8),
                        color = PaletteSafetyOrange,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Judge Demo Export Button
                Box(
                    modifier = Modifier
                        .background(PaletteSafetyOrange, RoundedCornerShape(2.dp))
                        .clickable(onClick = { viewModel.exportSession() })
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "EXPORT",
                        color = PaletteInk,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Export Feedback Banner
        if (exportStatus != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSafetyOrange, RoundedCornerShape(2.dp))
                    .clickable { viewModel.clearExportStatus() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exportStatus ?: "",
                        color = PaletteSafetyOrange,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = TextLightTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Distress Trend Card
        val trendColor = when (uiState.trend) {
            TrendStatus.WIDENING -> SeverityRed
            TrendStatus.MONITORING -> SeverityAmber
            TrendStatus.STABLE -> SeverityGreen
            TrendStatus.REGRESSED -> SeverityGreen
            TrendStatus.NO_HISTORY -> TextLightSecondary
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LOCATION DRIFT AUDIT",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${uiState.captures.size} OBSERVATIONS",
                    color = TextLightTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .background(trendColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    .border(1.dp, trendColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = uiState.trend.label,
                    color = trendColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chronological History List
        Text(
            text = "CHRONOLOGICAL CAPTURE HISTORY",
            color = TextLightSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.captures.isEmpty()) {
            if (!uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO HISTORICAL CAPTURES RECORDED AT THIS GEOHASH",
                        color = TextLightTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = uiState.captures,
                    key = { _, item -> item.id }
                ) { index, capture ->
                    val isCurrent = capture.id == uiState.targetCaptureId
                    RevisitCaptureCard(
                        index = index + 1,
                        total = uiState.captures.size,
                        capture = capture,
                        isCurrent = isCurrent
                    )

                    if (index < uiState.captures.size - 1) {
                        // Industrial timeline connector showing sequential progression
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(14.dp)
                                    .background(PaletteSlateBorder)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisitCaptureCard(
    index: Int,
    total: Int,
    capture: CaptureEntity,
    isCurrent: Boolean
) {
    val severityColor = when (capture.severity.uppercase(Locale.US)) {
        "STRUCTURAL" -> SeverityRed
        "MONITOR" -> SeverityAmber
        else -> SeverityGreen
    }

    val timeFormatted = remember(capture.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(capture.timestamp))
    }

    val bitmapState = produceState<Bitmap?>(initialValue = null, capture.imagePath) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(capture.imagePath)
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) PaletteSafetyOrange else PaletteSlateBorder,
                shape = RoundedCornerShape(2.dp)
            )
            .padding(12.dp)
    ) {
        // Step Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OBSERVATION #$index OF $total",
                    color = if (isCurrent) PaletteSafetyOrange else TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(PaletteSafetyOrange, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "CURRENT",
                            color = PaletteInk,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    .border(1.dp, severityColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = capture.severity.uppercase(Locale.US),
                    color = severityColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = timeFormatted,
                color = TextLightSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "CONFIDENCE: ${(capture.confidence * 100).toInt()}%",
                color = TextLightSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Image Frame
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(PaletteSlate, RoundedCornerShape(2.dp))
                .border(1.dp, severityColor.copy(alpha = 0.5f), RoundedCornerShape(2.dp)),
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
                    text = "FRAME CAPTURE",
                    color = TextLightTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
