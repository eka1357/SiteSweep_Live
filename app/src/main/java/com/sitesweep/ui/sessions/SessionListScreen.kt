package com.sitesweep.ui.sessions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspection session list screen.
 * Complies with strict AGENTS.md aesthetic guidelines:
 * - Industrial utility styling with pitch deck palette
 * - No harsh gradients, no emojis, no em dashes
 * - No colored stripe on card borders
 * - Instant rendering without skeleton loaders
 */
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onStartSweep: (String) -> Unit,
    onSessionClick: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sessionItems by viewModel.sessionItems.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val screenTopPadding = getScreenTopPadding()
    var sessionPendingDelete by remember { mutableStateOf<SessionItemUiModel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(top = screenTopPadding, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SITESWEEP",
                    color = PaletteSafetyOrange,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "OFFLINE DISTRESS INSPECTOR",
                    color = TextLightTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            // Demo settings toggle button
            Box(
                modifier = Modifier
                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "CONFIG",
                    color = TextLightSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Sweep primary action button
        Button(
            onClick = {
                scope.launch {
                    val sessionId = viewModel.createNewSession()
                    onStartSweep(sessionId)
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = PaletteSafetyOrange,
                contentColor = PaletteInk
            ),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = "START SWEEP",
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Session List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INSPECTION LOGS (${sessionItems.size})",
                color = TextLightSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (sessionItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NO PRIOR INSPECTIONS",
                        color = TextLightSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap Start Sweep to scan walls for crack distress.",
                        color = TextLightTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = sessionItems,
                    key = { it.session.id }
                ) { item ->
                    SessionRowItem(
                        item = item,
                        onClick = { onSessionClick(item.session.id) },
                        onDelete = { sessionPendingDelete = item }
                    )
                }
            }
        }

        // Industrial Confirmation Dialog for Deleting Inspection Session
        if (sessionPendingDelete != null) {
            val target = sessionPendingDelete!!
            AlertDialog(
                onDismissRequest = { sessionPendingDelete = null },
                title = {
                    Text(
                        text = "DELETE INSPECTION LOG",
                        color = PaletteSafetyOrange,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                text = {
                    Text(
                        text = "Permanently remove ${target.session.label.uppercase(Locale.US)} and all ${target.captureCount} associated captures? This action cannot be undone.",
                        color = TextLightSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteSession(target.session.id)
                            sessionPendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SeverityRed,
                            contentColor = PaletteInk
                        ),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = "DELETE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { sessionPendingDelete = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PaletteSlate,
                            contentColor = TextLightPrimary
                        ),
                        shape = RoundedCornerShape(2.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                containerColor = PaletteInkElevated,
                shape = RoundedCornerShape(2.dp)
            )
        }
    }
}

@Composable
private fun SessionRowItem(
    item: SessionItemUiModel,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(item.session.startedAt))

    val severityColor = when (item.maxSeverity) {
        "STRUCTURAL" -> SeverityRed
        "MONITOR" -> SeverityAmber
        "STABLE" -> SeverityGreen
        else -> TextLightTertiary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                text = item.session.label.uppercase(Locale.US),
                color = TextLightPrimary,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )

            // Severity tag
            Box(
                modifier = Modifier
                    .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    .border(1.dp, severityColor, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.maxSeverity,
                    color = severityColor,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$dateFormatted • ${item.captureCount} CAPTURES",
                color = TextLightSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Delete button for this log
                Box(
                    modifier = Modifier
                        .background(PaletteSlate.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "DELETE",
                        color = SeverityRed,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "VIEW >",
                    color = PaletteSafetyOrange,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
