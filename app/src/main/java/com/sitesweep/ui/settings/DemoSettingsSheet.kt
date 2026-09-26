package com.sitesweep.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sitesweep.SiteSweepApplication
import com.sitesweep.data.demo.DemoSeeder
import com.sitesweep.ui.theme.PaletteInk
import com.sitesweep.ui.theme.PaletteInkElevated
import com.sitesweep.ui.theme.PaletteSafetyOrange
import com.sitesweep.ui.theme.PaletteSlate
import com.sitesweep.ui.theme.PaletteSlateBorder
import com.sitesweep.ui.theme.SeverityGreen
import com.sitesweep.ui.theme.TextLightPrimary
import com.sitesweep.ui.theme.TextLightSecondary
import com.sitesweep.ui.theme.TextLightTertiary
import kotlinx.coroutines.launch

/**
 * Minimal settings dialog strictly for gating the DemoSeeder.
 * Per AGENTS.md: No settings menus beyond the demo toggle.
 */
@Composable
fun DemoSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SiteSweepApplication
    val seeder = remember { DemoSeeder(context, app.repository) }
    val scope = rememberCoroutineScope()

    var isSeeded by remember { mutableStateOf(seeder.isSeeded()) }
    var statusMessage by remember { mutableStateOf(if (isSeeded) "Seeded (3 captures)" else "Unseeded") }

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
                    text = "DEMO CONFIGURATION",
                    color = PaletteSafetyOrange,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "[X]",
                    color = TextLightSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Historical test data seeds 3 captures at the hackathon poster locationKey, spaced 3 weeks apart with widening severity.",
                color = TextLightSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SEED HISTORICAL DATA",
                        color = TextLightPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Status: $statusMessage",
                        color = if (isSeeded) SeverityGreen else TextLightTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Switch(
                    checked = isSeeded,
                    onCheckedChange = { enable ->
                        scope.launch {
                            if (enable) {
                                val success = seeder.seed()
                                isSeeded = success
                                statusMessage = if (success) "Seeded (3 captures)" else "Seed failed"
                            } else {
                                val cleared = seeder.clear()
                                isSeeded = !cleared
                                statusMessage = "Unseeded"
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PaletteSafetyOrange,
                        checkedTrackColor = PaletteInk,
                        uncheckedThumbColor = TextLightTertiary,
                        uncheckedTrackColor = PaletteInkElevated
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            seeder.seed()
                            isSeeded = true
                            statusMessage = "Re-seeded (3 captures)"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaletteSlate,
                        contentColor = PaletteSafetyOrange
                    ),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(
                        text = "RE-SEED NOW",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaletteSafetyOrange,
                        contentColor = PaletteInk
                    ),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Text(
                        text = "DONE",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
