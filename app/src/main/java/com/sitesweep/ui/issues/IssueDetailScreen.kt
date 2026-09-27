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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sitesweep.insight.AiInsightDialog
import com.sitesweep.insight.AiInsightManager
import com.sitesweep.insight.toInspectionEvidence
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sitesweep.data.local.entity.CaptureEntity
import com.sitesweep.data.model.IssueStatus
import com.sitesweep.ui.revisit.TrendStatus
import com.sitesweep.ui.theme.PaletteInk
import com.sitesweep.ui.theme.PaletteInkElevated
import com.sitesweep.ui.theme.PaletteSafetyOrange
import com.sitesweep.ui.theme.PaletteSlate
import com.sitesweep.ui.theme.PaletteSlateBorder
import com.sitesweep.ui.theme.SeverityAmber
import com.sitesweep.ui.theme.SeverityGreen
import com.sitesweep.ui.theme.SeverityRed
import com.sitesweep.ui.theme.StatusAssigned
import com.sitesweep.ui.theme.StatusInRepair
import com.sitesweep.ui.theme.StatusOpen
import com.sitesweep.ui.theme.StatusReinspection
import com.sitesweep.ui.theme.StatusResolved
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
fun IssueDetailScreen(
    viewModel: IssueDetailViewModel,
    issueId: String,
    onBack: () -> Unit,
    onStartReinspection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(issueId) {
        viewModel.loadIssue(issueId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screenTopPadding = getScreenTopPadding()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val aiInsightManager = remember { AiInsightManager(context) }
    val aiInsightState by aiInsightManager.state.collectAsStateWithLifecycle()
    var showAiInsightDialog by remember { mutableStateOf(false) }

    var showAssignDialog by remember { mutableStateOf(false) }
    var showResolveDialog by remember { mutableStateOf(false) }
    var showReopenDialog by remember { mutableStateOf(false) }
    var showReturnToRepairDialog by remember { mutableStateOf(false) }

    val issue = uiState.issue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(top = screenTopPadding, start = 16.dp, end = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
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
                    .defaultMinSize(minHeight = 36.dp)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "< DASHBOARD",
                    color = TextLightPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(PaletteSlate, RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                        .defaultMinSize(minHeight = 36.dp)
                        .clickable {
                            val evidence = uiState.toInspectionEvidence()
                            if (evidence != null) {
                                showAiInsightDialog = true
                                aiInsightManager.requestInsight(evidence, coroutineScope)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
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

                if (issue != null) {
                    val statusColor = when (issue.issueStatus) {
                        IssueStatus.OPEN -> StatusOpen
                        IssueStatus.ASSIGNED -> StatusAssigned
                        IssueStatus.IN_REPAIR -> StatusInRepair
                        IssueStatus.REINSPECTION -> StatusReinspection
                        IssueStatus.RESOLVED -> StatusResolved
                    }
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                            .border(1.dp, statusColor, RoundedCornerShape(2.dp))
                            .defaultMinSize(minHeight = 36.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = issue.status,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.actionSuccessMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SeverityGreen.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                    .border(1.dp, SeverityGreen, RoundedCornerShape(2.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = uiState.actionSuccessMessage ?: "",
                    color = SeverityGreen,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.actionError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SeverityRed.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                    .border(1.dp, SeverityRed, RoundedCornerShape(2.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = uiState.actionError ?: "",
                    color = SeverityRed,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (issue == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (uiState.isLoading) "LOADING ISSUE..." else "ISSUE NOT FOUND",
                    color = TextLightTertiary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            return
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Overview Card & Current Observation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                val displayId = if (issue.id.startsWith("ISS-", ignoreCase = true) || issue.id.length <= 10) {
                    issue.id.uppercase(Locale.US)
                } else {
                    issue.id.take(8).uppercase(Locale.US)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayId,
                        color = PaletteSafetyOrange,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LOCATION: ${issue.locationKey}",
                        color = TextLightTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = issue.title,
                    color = TextLightPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current Observation Section
                val latestCap = uiState.latestCapture ?: uiState.originCapture
                val severity = latestCap?.severity ?: "STABLE"
                val probPercent = kotlin.math.round((latestCap?.confidence ?: 0f) * 100f).toInt().coerceIn(0, 100)
                val severityColor = when (severity.uppercase(Locale.US)) {
                    "STRUCTURAL" -> SeverityRed
                    "MONITOR" -> SeverityAmber
                    else -> SeverityGreen
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaletteInk, RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "CURRENT OBSERVATION",
                        color = TextLightTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "$probPercent%",
                                color = severityColor,
                                fontSize = 22.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                    .border(1.dp, severityColor, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = severity.uppercase(Locale.US),
                                    color = severityColor,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val trendColor = when (uiState.trend) {
                            TrendStatus.WIDENING -> SeverityRed
                            TrendStatus.REGRESSED, TrendStatus.STABLE -> SeverityGreen
                            TrendStatus.MONITORING -> SeverityAmber
                            TrendStatus.NO_HISTORY -> TextLightTertiary
                        }
                        Box(
                            modifier = Modifier
                                .background(trendColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                                .border(1.dp, trendColor, RoundedCornerShape(2.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = uiState.trend.label.uppercase(Locale.US),
                                color = trendColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Closed-Loop Reinspection Verification Delta (Requirement 5 & 6)
            val prevCap = uiState.previousCaptureForReinspection
            val currCap = uiState.latestReinspectionCapture
            if (prevCap != null && currCap != null && prevCap.id != currCap.id) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                        .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "CLOSED-LOOP REINSPECTION COMPARISON",
                        color = PaletteSafetyOrange,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // BEFORE
                        val prevProb = kotlin.math.round(prevCap.confidence * 100f).toInt().coerceIn(0, 100)
                        val prevColor = when (prevCap.severity.uppercase(Locale.US)) {
                            "STRUCTURAL" -> SeverityRed
                            "MONITOR" -> SeverityAmber
                            else -> SeverityGreen
                        }
                        val prevDate = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(prevCap.timestamp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(PaletteInk, RoundedCornerShape(2.dp))
                                .border(1.dp, PaletteSlateBorder.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "BEFORE",
                                color = TextLightTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$prevProb%",
                                color = prevColor,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = prevCap.severity.uppercase(Locale.US),
                                color = prevColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = prevDate,
                                color = TextLightTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "→",
                                color = PaletteSafetyOrange,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // CURRENT
                        val currProb = kotlin.math.round(currCap.confidence * 100f).toInt().coerceIn(0, 100)
                        val currColor = when (currCap.severity.uppercase(Locale.US)) {
                            "STRUCTURAL" -> SeverityRed
                            "MONITOR" -> SeverityAmber
                            else -> SeverityGreen
                        }
                        val currDate = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(currCap.timestamp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(PaletteInk, RoundedCornerShape(2.dp))
                                .border(1.dp, PaletteSlateBorder.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "CURRENT",
                                color = TextLightTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$currProb%",
                                color = currColor,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currCap.severity.uppercase(Locale.US),
                                color = currColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currDate,
                                color = TextLightTertiary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val trendColor = when (uiState.trend) {
                        TrendStatus.WIDENING -> SeverityRed
                        TrendStatus.REGRESSED, TrendStatus.STABLE -> SeverityGreen
                        TrendStatus.MONITORING -> SeverityAmber
                        TrendStatus.NO_HISTORY -> TextLightTertiary
                    }
                    Box(
                        modifier = Modifier
                            .background(trendColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                            .border(1.dp, trendColor, RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "TREND: ${uiState.trend.label.uppercase(Locale.US)}",
                            color = trendColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. Evidence Images Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "VISUAL EVIDENCE (${uiState.historyCaptures.size})",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.historyCaptures.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val origin = uiState.originCapture
                        val latest = uiState.latestCapture

                        if (origin != null) {
                            CaptureEvidenceThumbnail(
                                label = "ORIGIN CAPTURE",
                                capture = origin,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (latest != null && latest.id != origin?.id) {
                            CaptureEvidenceThumbnail(
                                label = "LATEST CAPTURE",
                                capture = latest,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.historyCaptures.forEachIndexed { idx, cap ->
                            val label = when {
                                cap.id == issue.originCaptureId -> "ORIGIN CAPTURE"
                                cap.id == issue.latestCaptureId -> "LATEST CAPTURE"
                                else -> "INSPECTION #${idx + 1}"
                            }
                            CaptureEvidenceThumbnail(
                                label = label,
                                capture = cap,
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Chronological Observations History (Section 2)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "OBSERVATION HISTORY (${uiState.historyCaptures.size})",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.historyCaptures.isEmpty()) {
                    Text(
                        text = "No history recorded.",
                        color = TextLightTertiary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    uiState.historyCaptures.forEachIndexed { index, cap ->
                        val dateFormatted = remember(cap.timestamp) {
                            SimpleDateFormat("dd MMM", Locale.US).format(Date(cap.timestamp))
                        }
                        val fullDateFormatted = remember(cap.timestamp) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(cap.timestamp))
                        }
                        val capProb = kotlin.math.round(cap.confidence * 100f).toInt().coerceIn(0, 100)
                        val displaySeverity = when {
                            cap.severity.equals("STRUCTURAL", ignoreCase = true) -> "STRUCTURAL"
                            cap.severity.equals("MONITOR", ignoreCase = true) || cap.confidence >= 0.5f -> "HAIRLINE"
                            else -> "NONE"
                        }
                        val capColor = when (displaySeverity) {
                            "STRUCTURAL" -> SeverityRed
                            "HAIRLINE" -> SeverityAmber
                            else -> SeverityGreen
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PaletteInk, RoundedCornerShape(2.dp))
                                .border(1.dp, PaletteSlateBorder.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormatted,
                                    color = TextLightPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = fullDateFormatted,
                                    color = TextLightTertiary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$capProb% $displaySeverity",
                                    color = capColor,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "(ID: ${cap.id.take(8)})",
                                    color = TextLightTertiary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (index < uiState.historyCaptures.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "↓",
                                    color = PaletteSafetyOrange,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Engineering Record & Notes Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "ENGINEERING AUDIT TRAIL",
                    color = TextLightSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ASSIGNED TO: ${issue.assignedTo ?: "Unassigned"}",
                    color = TextLightPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                val reinspectionDateText = when {
                    issue.issueStatus == IssueStatus.RESOLVED -> {
                        issue.resolvedAt?.let { "Completed on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))}" } ?: "Resolved"
                    }
                    issue.issueStatus == IssueStatus.REINSPECTION -> {
                        val latestReins = uiState.latestReinspectionCapture
                        if (latestReins != null && latestReins.id != issue.originCaptureId) {
                            "Inspected: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(latestReins.timestamp))}"
                        } else {
                            "Awaiting reinspection sweep"
                        }
                    }
                    issue.issueStatus == IssueStatus.IN_REPAIR -> "Pending repair completion"
                    else -> "Not scheduled"
                }

                Text(
                    text = "REINSPECTION DATE: $reinspectionDateText",
                    color = if (issue.issueStatus == IssueStatus.REINSPECTION) PaletteSafetyOrange else TextLightSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (issue.issueStatus == IssueStatus.REINSPECTION) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(6.dp))

                val createdDate = remember(issue.createdAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(issue.createdAt))
                }
                Text(
                    text = "CREATED: $createdDate",
                    color = TextLightTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (!issue.engineerNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ENGINEER NOTES:",
                        color = TextLightTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = issue.engineerNotes,
                        color = TextLightPrimary,
                        fontSize = 12.sp
                    )
                }

                if (issue.issueStatus == IssueStatus.RESOLVED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StatusResolved.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                            .border(1.dp, StatusResolved, RoundedCornerShape(2.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(StatusResolved, RoundedCornerShape(1.dp))
                                )
                                Text(
                                    text = "RESOLVED BY ENGINEER",
                                    color = StatusResolved,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (issue.resolvedAt != null) {
                                val resolvedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(issue.resolvedAt))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Resolved At: $resolvedDate",
                                    color = TextLightSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (!issue.resolutionNotes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Resolution Notes: ${issue.resolutionNotes}",
                                    color = TextLightPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 6. Action Footer Bar (Context-sensitive based on IssueStatus)
        Column(modifier = Modifier.fillMaxWidth()) {
            when (issue.issueStatus) {
                IssueStatus.OPEN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAssignDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteSlate),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text(
                                text = "ASSIGN",
                                color = TextLightPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = { viewModel.startRepair() },
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text(
                                text = "START REPAIR",
                                color = PaletteInk,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                IssueStatus.ASSIGNED -> {
                    Button(
                        onClick = { viewModel.startRepair() },
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            text = "START REPAIR",
                            color = PaletteInk,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IssueStatus.IN_REPAIR -> {
                    Button(
                        onClick = { viewModel.scheduleReinspection() },
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            text = "SCHEDULE REINSPECTION",
                            color = PaletteInk,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IssueStatus.REINSPECTION -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onStartReinspection(issue.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                        ) {
                            Text(
                                text = "START REINSPECTION",
                                color = PaletteInk,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showReturnToRepairDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PaletteSlate),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text(
                                    text = "RETURN TO REPAIR",
                                    color = TextLightPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { showResolveDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusResolved),
                                shape = RoundedCornerShape(2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text(
                                    text = "MARK RESOLVED",
                                    color = TextLightPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                IssueStatus.RESOLVED -> {
                    Button(
                        onClick = { showReopenDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSlate),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text(
                            text = "REOPEN ISSUE",
                            color = TextLightPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // --- Interactive Action Dialogs ---

    if (showAssignDialog) {
        var assigneeText by remember { mutableStateOf(issue?.assignedTo ?: "") }
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = {
                Text(
                    text = "ASSIGN ISSUE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = PaletteSafetyOrange
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter engineer or maintenance team name:",
                        color = TextLightSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Civil Team A", "Structural Lead").forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                                    .clickable { assigneeText = preset }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = TextLightPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = assigneeText,
                        onValueChange = { assigneeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (assigneeText.isNotBlank()) {
                                viewModel.assignIssue(assigneeText.trim())
                                showAssignDialog = false
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaletteSafetyOrange,
                            unfocusedBorderColor = PaletteSlateBorder,
                            focusedTextColor = TextLightPrimary,
                            unfocusedTextColor = TextLightPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (assigneeText.isNotBlank()) {
                            viewModel.assignIssue(assigneeText.trim())
                            showAssignDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(text = "CONFIRM", color = PaletteInk, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignDialog = false }) {
                    Text(text = "CANCEL", color = TextLightSecondary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = PaletteInkElevated
        )
    }

    if (showResolveDialog) {
        var resolutionNotes by remember { mutableStateOf("") }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showResolveDialog = false },
            title = {
                Text(
                    text = "ENGINEER SIGN-OFF: RESOLVE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = StatusResolved
                )
            },
            text = {
                Column {
                    Text(
                        text = "Engineer confirms repair actions completed. Does not certify global structural safety.",
                        color = TextLightSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Epoxy Injected & Sealed", "Surface Stabilized").forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                                    .clickable {
                                        resolutionNotes = preset
                                        isError = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = TextLightPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolutionNotes,
                        onValueChange = {
                            resolutionNotes = it
                            isError = false
                        },
                        placeholder = { Text("e.g. Epoxy pressure-injected; crack stabilized.", color = TextLightTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (resolutionNotes.trim().isNotBlank()) {
                                viewModel.markResolved(resolutionNotes.trim())
                                showResolveDialog = false
                            } else {
                                isError = true
                            }
                        }),
                        isError = isError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusResolved,
                            unfocusedBorderColor = PaletteSlateBorder,
                            focusedTextColor = TextLightPrimary,
                            unfocusedTextColor = TextLightPrimary
                        )
                    )
                    if (isError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Resolution notes are required.",
                            color = SeverityRed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resolutionNotes.trim().isBlank()) {
                            isError = true
                        } else {
                            viewModel.markResolved(resolutionNotes.trim())
                            showResolveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusResolved),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(text = "SIGN OFF RESOLUTION", color = TextLightPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResolveDialog = false }) {
                    Text(text = "CANCEL", color = TextLightSecondary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = PaletteInkElevated
        )
    }

    if (showReturnToRepairDialog) {
        var defectNotes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReturnToRepairDialog = false },
            title = {
                Text(
                    text = "RETURN TO REPAIR",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SeverityAmber
                )
            },
            text = {
                Column {
                    Text(
                        text = "Reinspection indicated persistent distress. Add notes for repair team:",
                        color = TextLightSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Crack widening observed", "Surface debonding").forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                                    .clickable { defectNotes = preset }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = TextLightPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defectNotes,
                        onValueChange = { defectNotes = it },
                        placeholder = { Text("e.g. Additional crack propagation detected.", color = TextLightTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.returnToRepair(defectNotes.takeIf { it.isNotBlank() })
                            showReturnToRepairDialog = false
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SeverityAmber,
                            unfocusedBorderColor = PaletteSlateBorder,
                            focusedTextColor = TextLightPrimary,
                            unfocusedTextColor = TextLightPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.returnToRepair(defectNotes.takeIf { it.isNotBlank() })
                        showReturnToRepairDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeverityAmber),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(text = "RETURN TO REPAIR", color = PaletteInk, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReturnToRepairDialog = false }) {
                    Text(text = "CANCEL", color = TextLightSecondary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = PaletteInkElevated
        )
    }

    if (showReopenDialog) {
        var reopenReason by remember { mutableStateOf("") }
        var isReasonError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = {
                Text(
                    text = "REOPEN ISSUE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SeverityRed
                )
            },
            text = {
                Column {
                    Text(
                        text = "Specify reason for reopening this resolved problem:",
                        color = TextLightSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("New crack displacement", "Post-repair movement").forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .background(PaletteSlate, RoundedCornerShape(2.dp))
                                    .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                                    .clickable {
                                        reopenReason = preset
                                        isReasonError = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = TextLightPrimary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reopenReason,
                        onValueChange = {
                            reopenReason = it
                            isReasonError = false
                        },
                        placeholder = { Text("e.g. Distress returned after thermal cycle.", color = TextLightTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (reopenReason.trim().isNotBlank()) {
                                viewModel.reopenIssue(reopenReason.trim())
                                showReopenDialog = false
                            } else {
                                isReasonError = true
                            }
                        }),
                        isError = isReasonError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SeverityRed,
                            unfocusedBorderColor = PaletteSlateBorder,
                            focusedTextColor = TextLightPrimary,
                            unfocusedTextColor = TextLightPrimary
                        )
                    )
                    if (isReasonError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reason is required to reopen.",
                            color = SeverityRed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reopenReason.trim().isBlank()) {
                            isReasonError = true
                        } else {
                            viewModel.reopenIssue(reopenReason.trim())
                            showReopenDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SeverityRed),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(text = "REOPEN", color = TextLightPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) {
                    Text(text = "CANCEL", color = TextLightSecondary, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = PaletteInkElevated
        )
    }

    if (showAiInsightDialog) {
        AiInsightDialog(
            state = aiInsightState,
            onRetry = {
                val evidence = uiState.toInspectionEvidence()
                if (evidence != null) {
                    aiInsightManager.requestInsight(evidence, coroutineScope)
                }
            },
            onDismiss = {
                aiInsightManager.reset()
                showAiInsightDialog = false
            }
        )
    }
}

@Composable
private fun CaptureEvidenceThumbnail(
    label: String,
    capture: CaptureEntity,
    modifier: Modifier = Modifier
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

    Column(
        modifier = modifier
            .background(PaletteInk, RoundedCornerShape(2.dp))
            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
            .padding(8.dp)
    ) {
        Text(
            text = label,
            color = TextLightTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(PaletteSlate.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmapState.value
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "NO IMAGE",
                    color = TextLightTertiary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = capture.severity,
                color = when (capture.severity.uppercase(Locale.US)) {
                    "STRUCTURAL" -> SeverityRed
                    "MONITOR" -> SeverityAmber
                    else -> SeverityGreen
                },
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$probPercent%",
                color = TextLightPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        val evidenceTimeFormatted = remember(capture.timestamp) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(capture.timestamp))
        }
        Text(
            text = evidenceTimeFormatted,
            color = TextLightTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
