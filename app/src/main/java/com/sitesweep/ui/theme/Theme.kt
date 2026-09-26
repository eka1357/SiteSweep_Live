package com.sitesweep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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
