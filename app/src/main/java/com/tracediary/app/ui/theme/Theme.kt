package com.tracediary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.tracediary.app.data.settings.ThemeColor

private data class AccentPalette(
    val primary: Color,
    val container: Color,
    val onContainer: Color,
    val tertiary: Color,
)

private fun ThemeColor.palette(): AccentPalette = when (this) {
    ThemeColor.Forest -> AccentPalette(BrandGreen, BrandGreenSoft, BrandGreenDark, BrandGreenPale)
    ThemeColor.MistBlue -> AccentPalette(Color(0xFF365F87), Color(0xFFE8F0F7), Color(0xFF254B69), Color(0xFFD2E3F1))
    ThemeColor.DuskPurple -> AccentPalette(Color(0xFF735792), Color(0xFFF1ECF8), Color(0xFF503A70), Color(0xFFE3D9EF))
    ThemeColor.RoseBerry -> AccentPalette(Color(0xFF98516D), Color(0xFFF8EAF0), Color(0xFF71364E), Color(0xFFEFD3DD))
    ThemeColor.Terracotta -> AccentPalette(Color(0xFF8B6040), Color(0xFFF6EDE4), Color(0xFF66462E), Color(0xFFE9DAC8))
    ThemeColor.Graphite -> AccentPalette(Color(0xFF434C5E), Color(0xFFE9ECF1), Color(0xFF2E3440), Color(0xFFD8DEE9))
}

fun colorSchemeFor(themeColor: ThemeColor) = themeColor.palette().let { accent -> lightColorScheme(
    primary = accent.primary,
    onPrimary = SurfaceCard,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    inversePrimary = accent.tertiary,
    secondary = Ink600,
    onSecondary = SurfaceCard,
    secondaryContainer = SurfaceMuted,
    onSecondaryContainer = Ink900,
    tertiary = accent.tertiary,
    onTertiary = accent.onContainer,
    tertiaryContainer = accent.container,
    onTertiaryContainer = accent.onContainer,
    background = SurfaceBackground,
    onBackground = Ink900,
    surface = SurfaceCard,
    onSurface = Ink900,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = Ink600,
    surfaceTint = accent.primary,
    inverseSurface = Ink900,
    inverseOnSurface = SurfaceCard,
    surfaceBright = SurfaceCard,
    surfaceDim = SurfaceMuted,
    surfaceContainerLowest = SurfaceCard,
    surfaceContainerLow = SurfaceBackground,
    surfaceContainer = SurfaceMuted,
    surfaceContainerHigh = SurfaceMuted,
    surfaceContainerHighest = SurfaceDivider,
    outline = Ink300,
    outlineVariant = SurfaceDivider,
) }

@Composable
fun TraceDiaryTheme(
    themeColor: ThemeColor = ThemeColor.Forest,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(themeColor),
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
