package com.sitesweep.ui.sessions

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sitesweep.ui.voice.VoiceNoteDialog

/**
 * Inspection session detail screen displaying session metadata, distress captures, and voice notes.
 * Tapping any capture navigates to RevisitScreen to query prior readings at that locationKey.
 */
@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    sessionId: String,
    onBack: () -> Unit,
    onResumeSweep: (String) -> Unit,
    onCaptureClick: (String, String) -> Unit, // locationKey, captureId
    onExportClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportStatus by viewModel.exportStatus.collectAsStateWithLifecycle()
    val session = uiState.session

    var showVoiceNoteDialog by remember { mutableStateOf(false) }
    var voiceNoteTargetCaptureId by remember { mutableStateOf<String?>(null) }

    if (showVoiceNoteDialog) {
        VoiceNoteDialog(
            title = if (voiceNoteTargetCaptureId != null) "CAPTURE NOTE" else "SESSION NOTE",
            onSave = { transcript ->
                viewModel.addVoiceNote(transcript, voiceNoteTargetCaptureId)
                showVoiceNoteDialog = false
                voiceNoteTargetCaptureId = null
            },
            onDismiss = {
                showVoiceNoteDialog = false
                voiceNoteTargetCaptureId = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(16.dp)
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
                text = "SESSION DETAILS",
                color = TextLightSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            // Export button
            Box(
                modifier = Modifier
                    .background(PaletteSafetyOrange, RoundedCornerShape(2.dp))
                    .clickable(onClick = {
                        viewModel.exportSession()
                        onExportClick(sessionId)
                    })
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

        if (exportStatus != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSafetyOrange, RoundedCornerShape(2.dp))
                    .padding(10.dp)
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
                        text = "[OK]",
                        color = TextLightSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.clickable { viewModel.clearExportStatus() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Session Metadata Card
        if (session != null) {
            val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(session.startedAt))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = session.label.uppercase(Locale.US),
                    color = PaletteSafetyOrange,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "STARTED: $dateFormatted",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "TOTAL CAPTURES: ${uiState.captures.size}",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onResumeSweep(sessionId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSlate,
                            contentColor = TextLightPrimary
                        ),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(
                            text = "RESUME SWEEP",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            voiceNoteTargetCaptureId = null
                            showVoiceNoteDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSlate,
                            contentColor = PaletteSafetyOrange
                        ),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(
                            text = "+ VOICE NOTE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (uiState.voiceNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "SESSION VOICE NOTES (${uiState.voiceNotes.size}):",
                        color = TextLightSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    uiState.voiceNotes.forEach { note ->
                        Text(
                            text = "• \"${note.transcript}\"",
                            color = TextLightPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Captures Header
        Text(
            text = "CAPTURED DISTRESS FRAMES (${uiState.captures.size})",
            color = TextLightSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "NO CAPTURES RECORDED IN THIS SESSION",
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
                    items = uiState.captures,
                    key = { it.id }
                ) { capture ->
                    val captureNotes = uiState.voiceNotes.filter { it.captureId == capture.id }
                    CaptureDetailCard(
                        capture = capture,
                        notes = captureNotes,
                        onAddNote = {
                            voiceNoteTargetCaptureId = capture.id
                            showVoiceNoteDialog = true
                        },
                        onClick = { onCaptureClick(capture.locationKey, capture.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureDetailCard(
    capture: CaptureEntity,
    notes: List<com.sitesweep.data.local.entity.VoiceNoteEntity>,
    onAddNote: () -> Unit,
    onClick: () -> Unit
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
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(68.dp, 68.dp)
                .background(PaletteSlate, RoundedCornerShape(2.dp))
                .border(1.dp, severityColor, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = bitmapState.value
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Capture thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "IMG",
                    color = TextLightTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Text(
                    text = "CONF: ${(capture.confidence * 100).toInt()}%",
                    color = TextLightSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timeFormatted,
                color = TextLightSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "LOC: ${capture.locationKey}",
                color = PaletteSafetyOrange,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )

            if (notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                notes.forEach { note ->
                    Text(
                        text = "NOTE: \"${note.transcript}\"",
                        color = TextLightPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .clickable(onClick = onAddNote)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "+ NOTE",
                    color = PaletteSafetyOrange,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "REVISIT >",
                color = PaletteSafetyOrange,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
