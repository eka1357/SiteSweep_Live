package com.sitesweep.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fixed height allowance for the HackTracker overlay telemetry bar on loaner devices.
 * Ensures top bar elements (Session label, timer, severity badge, CONFIG/EXPORT buttons)
 * never render under or get clipped by the status bar or the HackTracker overlay bar.
 */
val HACK_TRACKER_OVERLAY_BAR_HEIGHT: Dp = 52.dp

/**
 * Returns the total top padding required on every screen:
 * Status bar inset PLUS the height of the HackTracker overlay bar.
 */
@Composable
fun getScreenTopPadding(): Dp {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // If statusBarPadding is not yet measured (e.g. 0dp), supply a safe baseline of 36dp
    val effectiveStatusBar = if (statusBarPadding > 0.dp) statusBarPadding else 36.dp
    return effectiveStatusBar + HACK_TRACKER_OVERLAY_BAR_HEIGHT
}

private val SiteSweepDarkColorScheme = darkColorScheme(
    primary = PaletteSafetyOrange,
    onPrimary = PaletteInk,
    primaryContainer = PaletteSlate,
    onPrimaryContainer = TextLightPrimary,
    background = PaletteInk,
    onBackground = TextLightPrimary,
    surface = PaletteInkElevated,
    onSurface = TextLightPrimary,
    surfaceVariant = PaletteSlate,
    onSurfaceVariant = TextLightSecondary,
    outline = PaletteSlateBorder
)

@Composable
fun SiteSweepTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SiteSweepDarkColorScheme,
        typography = SiteSweepTypography,
        content = content
    )
}

