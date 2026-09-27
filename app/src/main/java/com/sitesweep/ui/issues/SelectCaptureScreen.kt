package com.sitesweep.ui.issues

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun SelectCaptureScreen(
    viewModel: CreateIssueViewModel,
    onCaptureSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val captures by viewModel.availableCaptures.collectAsStateWithLifecycle()
    val screenTopPadding = getScreenTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(top = screenTopPadding, start = 16.dp, end = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
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
                text = "SELECT CANDIDATE CAPTURE",
                color = PaletteSafetyOrange,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select an existing auto-captured frame to promote into a persistent inspection issue:",
            color = TextLightSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO CAPTURES AVAILABLE IN LOCAL STORAGE",
                    color = TextLightTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = captures.sortedByDescending { it.timestamp },
                    key = { it.id }
                ) { capture ->
                    CandidateCaptureItemRow(
                        capture = capture,
                        onClick = { onCaptureSelected(capture.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateCaptureItemRow(
    capture: CaptureEntity,
    onClick: () -> Unit
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, capture.imagePath) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(capture.imagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    val probPercent = kotlin.math.round(capture.confidence * 100f).toInt().coerceIn(0, 100)
    val severityColor = when (capture.severity.uppercase(Locale.US)) {
        "STRUCTURAL" -> SeverityRed
        "MONITOR" -> SeverityAmber
        else -> SeverityGreen
    }
    val timeFormatted = remember(capture.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(capture.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(PaletteSlate.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmapState.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(text = "IMG", color = TextLightTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
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

                Text(
                    text = "PROB $probPercent%",
                    color = TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "GEO: ${capture.locationKey}",
                color = TextLightSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = timeFormatted,
                color = TextLightTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
