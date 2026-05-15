package com.tracediary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BrandGreen,
    onPrimary = SurfaceCard,
    primaryContainer = BrandGreenSoft,
    onPrimaryContainer = BrandGreenDark,
    secondary = Ink600,
    onSecondary = SurfaceCard,
    secondaryContainer = SurfaceMuted,
    onSecondaryContainer = Ink900,
    tertiary = BrandGreenPale,
    onTertiary = BrandGreenDark,
    background = SurfaceBackground,
    onBackground = Ink900,
    surface = SurfaceCard,
    onSurface = Ink900,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = Ink600,
    outline = Ink300,
    outlineVariant = SurfaceDivider
)

@Composable
fun TraceDiaryTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
