package com.sitesweep.ui.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesweep.ui.theme.PaletteInk
import com.sitesweep.ui.theme.PaletteInkElevated
import com.sitesweep.ui.theme.PaletteSafetyOrange
import com.sitesweep.ui.theme.PaletteSlate
import com.sitesweep.ui.theme.PaletteSlateBorder
import com.sitesweep.ui.theme.SeverityRed
import com.sitesweep.ui.theme.TextLightPrimary
import com.sitesweep.ui.theme.TextLightSecondary
import com.sitesweep.ui.theme.TextLightTertiary
import com.sitesweep.voice.VoiceNoteRecorder

/**
 * Industrial dialog for offline voice notes attached to sessions or captures.
 * Uses on-device SpeechRecognizer with live editable transcription.
 */
@Composable
fun VoiceNoteDialog(
    title: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { VoiceNoteRecorder(context) }
    val state by recorder.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            recorder.destroy()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    color = PaletteSafetyOrange,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = if (state.isListening) "REC (OFFLINE)" else "STANDBY",
                    color = if (state.isListening) SeverityRed else TextLightTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Text input / Transcription display
            OutlinedTextField(
                value = state.transcript,
                onValueChange = { recorder.updateTranscriptManually(it) },
                placeholder = {
                    Text(
                        text = "Speak into mic or type structural distress notes...",
                        color = TextLightTertiary,
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaletteSafetyOrange,
                    unfocusedBorderColor = PaletteSlateBorder,
                    focusedTextColor = TextLightPrimary,
                    unfocusedTextColor = TextLightPrimary,
                    cursorColor = PaletteSafetyOrange
                ),
                shape = RoundedCornerShape(2.dp)
            )

            if (state.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.errorMessage ?: "",
                    color = SeverityRed,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Record / Stop toggle
                Button(
                    onClick = {
                        if (state.isListening) {
                            recorder.stopListening()
                        } else {
                            recorder.startListening()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isListening) SeverityRed else PaletteSlate,
                        contentColor = TextLightPrimary
                    ),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        text = if (state.isListening) "STOP" else "RECORD MIC",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Save button
                Button(
                    onClick = {
                        if (state.transcript.isNotBlank()) {
                            onSave(state.transcript.trim())
                        }
                    },
                    enabled = state.transcript.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaletteSafetyOrange,
                        contentColor = PaletteInk,
                        disabledContainerColor = PaletteSlate,
                        disabledContentColor = TextLightTertiary
                    ),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Text(
                        text = "SAVE NOTE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaletteInk,
                    contentColor = TextLightSecondary
                ),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth().height(34.dp)
            ) {
                Text(
                    text = "CANCEL",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
