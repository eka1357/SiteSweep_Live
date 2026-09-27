package com.sitesweep.insight

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
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

/**
 * Secondary dialog displaying optional, advisory AI Insights.
 * Visually isolated from the deterministic offline inspection pipeline.
 */
@Composable
fun AiInsightDialog(
    state: AiInsightState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(PaletteInkElevated, RoundedCornerShape(2.dp))
                .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                .padding(18.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI INSIGHT",
                        color = PaletteSafetyOrange,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "AI-GENERATED ADVISORY INSIGHT",
                        color = TextLightTertiary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.4.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(PaletteSlate, RoundedCornerShape(2.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "CLOSE",
                        color = TextLightPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (state) {
                is AiInsightState.Idle -> {
                    // Ready to query
                }

                is AiInsightState.CheckingConnection -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = PaletteSafetyOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Checking connection...",
                            color = TextLightSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                is AiInsightState.Offline -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PaletteInk, RoundedCornerShape(2.dp))
                            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "INTERNET REQUIRED",
                                color = SeverityAmber,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                color = TextLightPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "SiteSweep deterministic offline inspection operates fully in Airplane Mode without internet.",
                                color = TextLightSecondary,
                                fontSize = 10.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PaletteSlate, RoundedCornerShape(2.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "DISMISS",
                                color = TextLightPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is AiInsightState.Analyzing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = PaletteSafetyOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Analyzing inspection...",
                            color = TextLightPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Querying OpenRouter with structured evidence",
                            color = TextLightTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                is AiInsightState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PaletteInk, RoundedCornerShape(2.dp))
                            .border(1.dp, SeverityRed.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = "AI INSIGHT UNAVAILABLE",
                                color = SeverityRed,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.message,
                                color = TextLightSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PaletteSlate, RoundedCornerShape(2.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "DISMISS",
                                color = TextLightSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(PaletteSafetyOrange, RoundedCornerShape(2.dp))
                                .clickable(onClick = onRetry)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "RETRY",
                                color = PaletteInk,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is AiInsightState.Success -> {
                    val insight = state.insight
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(scrollState)
                    ) {
                        InsightSectionCard(
                            title = "OBSERVATION",
                            content = insight.observation,
                            accentColor = SeverityAmber
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        InsightSectionCard(
                            title = "TREND",
                            content = insight.trend,
                            accentColor = PaletteSafetyOrange
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        InsightSectionCard(
                            title = "FOLLOW-UP",
                            content = insight.suggestedFollowUp,
                            accentColor = SeverityGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        InsightSectionCard(
                            title = "LIMITATIONS",
                            content = insight.dataLimitations,
                            accentColor = TextLightTertiary
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "NOTICE: Advisory AI synthesis only. Does not certify structural safety or replace professional engineer sign-off.",
                            color = TextLightTertiary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .background(PaletteSlate, RoundedCornerShape(2.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "DONE",
                                color = TextLightPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightSectionCard(
    title: String,
    content: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaletteInk, RoundedCornerShape(2.dp))
            .border(1.dp, PaletteSlateBorder, RoundedCornerShape(2.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = title,
                color = accentColor,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                color = TextLightPrimary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
