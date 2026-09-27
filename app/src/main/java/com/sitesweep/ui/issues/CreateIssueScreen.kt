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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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

@Composable
fun CreateIssueScreen(
    viewModel: CreateIssueViewModel,
    captureId: String,
    onIssueCreated: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(captureId) {
        viewModel.loadCandidateCapture(captureId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screenTopPadding = getScreenTopPadding()
    val capture = uiState.candidateCapture

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
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "< CANCEL",
                    color = TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "CREATE ISSUE",
                color = PaletteSafetyOrange,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SeverityRed.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                    .border(1.dp, SeverityRed, RoundedCornerShape(2.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = uiState.error ?: "",
                    color = SeverityRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (capture == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.isLoading) "LOADING CAPTURE..." else "CAPTURE NOT FOUND",
                    color = TextLightTertiary,
                    fontFamily = FontFamily.Monospace
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Candidate Capture Evidence Card
            CandidateCapturePreviewCard(capture = capture)

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Issue Title Field
            Text(
                text = "ISSUE TITLE",
                color = TextLightSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSafetyOrange,
                    unfocusedBorderColor = PaletteSlateBorder,
                    focusedTextColor = TextLightPrimary,
                    unfocusedTextColor = TextLightPrimary,
                    focusedContainerColor = PaletteInkElevated,
                    unfocusedContainerColor = PaletteInkElevated
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Engineer Notes Field
            Text(
                text = "ENGINEER OBSERVATION NOTES (OPTIONAL)",
                color = TextLightSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = {
                    Text(
                        text = "e.g. Shear crack near base of column C12 with minor spalling...",
                        color = TextLightTertiary,
                        fontSize = 12.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSafetyOrange,
                    unfocusedBorderColor = PaletteSlateBorder,
                    focusedTextColor = TextLightPrimary,
                    unfocusedTextColor = TextLightPrimary,
                    focusedContainerColor = PaletteInkElevated,
                    unfocusedContainerColor = PaletteInkElevated
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Action Button
        Button(
            onClick = {
                viewModel.createIssue { newIssueId ->
                    onIssueCreated(newIssueId)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "CONFIRM & CREATE ISSUE",
                color = PaletteInk,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun CandidateCapturePreviewCard(capture: CaptureEntity) {
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
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(capture.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "CANDIDATE CAPTURE EVIDENCE",
            color = PaletteSafetyOrange,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(PaletteSlate.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmapState.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Candidate capture image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "NO IMAGE AVAILABLE",
                    color = TextLightTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, severityColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = capture.severity.uppercase(Locale.US),
                        color = severityColor,
                        fontSize = 10.sp,
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

            Text(
                text = "GEO: ${capture.locationKey}",
                color = TextLightSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "CAPTURED: $timeFormatted",
            color = TextLightTertiary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
