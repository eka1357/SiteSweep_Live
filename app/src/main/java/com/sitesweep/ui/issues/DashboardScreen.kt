package com.sitesweep.ui.issues

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onIssueClick: (String) -> Unit,
    onStartSweep: () -> Unit,
    onCreateIssue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screenTopPadding = getScreenTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(top = screenTopPadding, start = 16.dp, end = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        // 1. Navigation & Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(PaletteSlate, RoundedCornerShape(2.dp))
                        .clickable(onClick = onBack)
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
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ENGINEER DASHBOARD",
                        color = PaletteSafetyOrange,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "OFFLINE ISSUE LIFECYCLE",
                        color = TextLightTertiary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(PaletteSafetyOrange, RoundedCornerShape(2.dp))
                    .clickable(onClick = onCreateIssue)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "+ NEW ISSUE",
                    color = PaletteInk,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Metrics Summary Cards Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryMetricCard(
                title = "OPEN",
                count = uiState.summary.openCount,
                isSelected = uiState.selectedFilter == IssueStatus.OPEN,
                accentColor = StatusOpen,
                onClick = {
                    viewModel.setFilter(if (uiState.selectedFilter == IssueStatus.OPEN) null else IssueStatus.OPEN)
                }
            )
            SummaryMetricCard(
                title = "ASSIGNED",
                count = uiState.summary.assignedCount,
                isSelected = uiState.selectedFilter == IssueStatus.ASSIGNED,
                accentColor = StatusAssigned,
                onClick = {
                    viewModel.setFilter(if (uiState.selectedFilter == IssueStatus.ASSIGNED) null else IssueStatus.ASSIGNED)
                }
            )
            SummaryMetricCard(
                title = "IN REPAIR",
                count = uiState.summary.inRepairCount,
                isSelected = uiState.selectedFilter == IssueStatus.IN_REPAIR,
                accentColor = StatusInRepair,
                onClick = {
                    viewModel.setFilter(if (uiState.selectedFilter == IssueStatus.IN_REPAIR) null else IssueStatus.IN_REPAIR)
                }
            )
            SummaryMetricCard(
                title = "REINSPECTION",
                count = uiState.summary.reinspectionCount,
                isSelected = uiState.selectedFilter == IssueStatus.REINSPECTION,
                accentColor = StatusReinspection,
                onClick = {
                    viewModel.setFilter(if (uiState.selectedFilter == IssueStatus.REINSPECTION) null else IssueStatus.REINSPECTION)
                }
            )
            SummaryMetricCard(
                title = "RESOLVED",
                count = uiState.summary.resolvedCount,
                isSelected = uiState.selectedFilter == IssueStatus.RESOLVED,
                accentColor = StatusResolved,
                onClick = {
                    viewModel.setFilter(if (uiState.selectedFilter == IssueStatus.RESOLVED) null else IssueStatus.RESOLVED)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                label = "ALL (${uiState.allIssues.size})",
                isSelected = uiState.selectedFilter == null,
                onClick = { viewModel.setFilter(null) }
            )
            IssueStatus.entries.forEach { status ->
                val count = when (status) {
                    IssueStatus.OPEN -> uiState.summary.openCount
                    IssueStatus.ASSIGNED -> uiState.summary.assignedCount
                    IssueStatus.IN_REPAIR -> uiState.summary.inRepairCount
                    IssueStatus.REINSPECTION -> uiState.summary.reinspectionCount
                    IssueStatus.RESOLVED -> uiState.summary.resolvedCount
                }
                FilterChip(
                    label = "${status.name} ($count)",
                    isSelected = uiState.selectedFilter == status,
                    onClick = { viewModel.setFilter(status) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Section Label
        val sectionTitle = if (uiState.selectedFilter == null) {
            "ACTIVE ISSUES (${uiState.filteredIssues.size})"
        } else {
            "${uiState.selectedFilter?.name} ISSUES (${uiState.filteredIssues.size})"
        }
        Text(
            text = sectionTitle,
            color = TextLightSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Issues List or Empty State
        if (uiState.filteredIssues.isEmpty()) {
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
                        text = "No inspection issues yet.",
                        color = TextLightPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create an issue from an existing capture or start a camera sweep.",
                        color = TextLightTertiary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCreateIssue,
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                text = "Create Issue from Capture",
                                color = PaletteInk,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onStartSweep,
                            colors = ButtonDefaults.buttonColors(containerColor = PaletteSlate),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                text = "Start Sweep",
                                color = TextLightPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = uiState.filteredIssues,
                    key = { it.id }
                ) { issueItem ->
                    IssueRowCard(
                        issue = issueItem,
                        onClick = { onIssueClick(issueItem.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    count: Int,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) accentColor else PaletteSlateBorder,
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accentColor, RoundedCornerShape(1.dp))
            )
            Text(
                text = title,
                color = if (isSelected) TextLightPrimary else TextLightTertiary,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = count.toString(),
            color = accentColor,
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) PaletteSafetyOrange else PaletteSlate,
                RoundedCornerShape(2.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) PaletteInk else TextLightSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun IssueRowCard(
    issue: IssueItemUiModel,
    onClick: () -> Unit
) {
    val statusColor = when (issue.status) {
        IssueStatus.OPEN -> StatusOpen
        IssueStatus.ASSIGNED -> StatusAssigned
        IssueStatus.IN_REPAIR -> StatusInRepair
        IssueStatus.REINSPECTION -> StatusReinspection
        IssueStatus.RESOLVED -> StatusResolved
    }

    val severityColor = when (issue.severity.uppercase(Locale.US)) {
        "STRUCTURAL" -> SeverityRed
        "MONITOR" -> SeverityAmber
        else -> SeverityGreen
    }

    val timeFormatted = remember(issue.lastInspectedTimestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(issue.lastInspectedTimestamp))
    }

    val probPercent = kotlin.math.round(issue.crackProbability * 100f).toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteInkElevated, RoundedCornerShape(2.dp))
            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Top row: ID, Status chip, Geohash
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = displayId,
                    color = PaletteSafetyOrange,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, statusColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = issue.status.name,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "GEO: ${issue.locationKey}",
                color = TextLightTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = issue.title,
            color = TextLightPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!issue.assignedTo.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Assigned: ${issue.assignedTo}",
                color = TextLightSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Metrics Row: Severity, Probability, Trend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(severityColor.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                        .border(1.dp, severityColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = issue.severity.uppercase(Locale.US),
                        color = severityColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "$probPercent%",
                    color = when (issue.severity.uppercase(Locale.US)) {
                        "STRUCTURAL" -> SeverityRed
                        "MONITOR" -> SeverityAmber
                        else -> SeverityGreen
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "•",
                    color = TextLightTertiary,
                    fontSize = 10.sp
                )

                Text(
                    text = when (issue.trend) {
                        TrendStatus.WIDENING -> "WIDENING"
                        TrendStatus.REGRESSED -> "REGRESSED"
                        TrendStatus.MONITORING -> "MONITORING"
                        TrendStatus.STABLE -> "STABLE"
                        TrendStatus.NO_HISTORY -> "SINGLE"
                    },
                    color = when (issue.trend) {
                        TrendStatus.WIDENING -> SeverityRed
                        TrendStatus.REGRESSED, TrendStatus.STABLE -> SeverityGreen
                        TrendStatus.MONITORING -> SeverityAmber
                        TrendStatus.NO_HISTORY -> TextLightTertiary
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = timeFormatted,
                color = TextLightTertiary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
