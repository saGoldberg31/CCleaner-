package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

@Immutable
data class TelemetryColors(
    val isDark: Boolean,
    val healthy: Color,
    val info: Color,
    val warning: Color,
    val danger: Color,
    val gaugeTrack: Color,
    val cardElevated: Color,
    val glassFill: Color,
    val glassFillElevated: Color,
    val glassRimTop: Color,
    val glassRimBottom: Color,
    val glassOrbA: Color,
    val glassOrbB: Color,
    val glassOrbC: Color,
    val categoryPhotos: Color,
    val categoryVideos: Color,
    val categoryAudio: Color,
    val categoryDocuments: Color,
    val categoryArchives: Color,
    val categoryApps: Color,
    val categoryOther: Color,
    val categoryFree: Color
)

val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }
val LocalBatterySaverEnabled = staticCompositionLocalOf { false }

val LocalTelemetryColors = staticCompositionLocalOf {
    TelemetryColors(
        isDark = true,
        healthy = EmeraldPrimary,
        info = CyanSecondary,
        warning = AmberTertiary,
        danger = CoralError,
        gaugeTrack = DarkSurfaceContainerHigh,
        cardElevated = DarkSurfaceContainer,
        glassFill = Color(0x88162331),
        glassFillElevated = Color(0x9E1F3246),
        glassRimTop = Color(0x66FFFFFF),
        glassRimBottom = Color(0x3338BDF8),
        glassOrbA = Color(0x5500E5A3),
        glassOrbB = Color(0x5538BDF8),
        glassOrbC = Color(0x448B5CF6),
        categoryPhotos = DarkChartPhotos,
        categoryVideos = DarkChartVideos,
        categoryAudio = DarkChartAudio,
        categoryDocuments = DarkChartDocuments,
        categoryArchives = DarkChartArchives,
        categoryApps = DarkChartApps,
        categoryOther = DarkChartOther,
        categoryFree = DarkChartFree
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    error = CoralError,
    onError = CoralOnError,
    errorContainer = CoralContainer,
    onErrorContainer = CoralOnContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = LightEmeraldOnPrimary,
    primaryContainer = LightEmeraldContainer,
    onPrimaryContainer = LightEmeraldOnContainer,
    secondary = LightCyanSecondary,
    onSecondary = LightCyanOnSecondary,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightCyanOnContainer,
    tertiary = LightAmberTertiary,
    onTertiary = LightAmberOnTertiary,
    tertiaryContainer = LightAmberContainer,
    onTertiaryContainer = LightAmberOnContainer,
    error = LightCoralError,
    onError = LightCoralOnError,
    errorContainer = LightCoralContainer,
    onErrorContainer = LightCoralOnContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CleanProTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    liquidGlassEnabled: Boolean = true,
    batterySaverEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme
    val telemetryColors = if (useDark) {
        TelemetryColors(
            isDark = true,
            healthy = EmeraldPrimary,
            info = CyanSecondary,
            warning = AmberTertiary,
            danger = CoralError,
            gaugeTrack = DarkSurfaceContainerHigh,
            cardElevated = DarkSurfaceContainer,
            glassFill = Color(0x88162331),
            glassFillElevated = Color(0xA6203347),
            glassRimTop = Color(0x66FFFFFF),
            glassRimBottom = Color(0x3838BDF8),
            glassOrbA = Color(0x5200E5A3),
            glassOrbB = Color(0x5238BDF8),
            glassOrbC = Color(0x458B5CF6),
            categoryPhotos = DarkChartPhotos,
            categoryVideos = DarkChartVideos,
            categoryAudio = DarkChartAudio,
            categoryDocuments = DarkChartDocuments,
            categoryArchives = DarkChartArchives,
            categoryApps = DarkChartApps,
            categoryOther = DarkChartOther,
            categoryFree = DarkChartFree
        )
    } else {
        TelemetryColors(
            isDark = false,
            healthy = LightEmeraldPrimary,
            info = LightCyanSecondary,
            warning = LightAmberTertiary,
            danger = LightCoralError,
            gaugeTrack = LightSurfaceContainerHigh,
            cardElevated = LightSurfaceContainer,
            glassFill = Color(0xB8FFFFFF),
            glassFillElevated = Color(0xD6FFFFFF),
            glassRimTop = Color(0xEEFFFFFF),
            glassRimBottom = Color(0x4D0284C7),
            glassOrbA = Color(0x4000B884),
            glassOrbB = Color(0x4538BDF8),
            glassOrbC = Color(0x388B5CF6),
            categoryPhotos = LightChartPhotos,
            categoryVideos = LightChartVideos,
            categoryAudio = LightChartAudio,
            categoryDocuments = LightChartDocuments,
            categoryArchives = LightChartArchives,
            categoryApps = LightChartApps,
            categoryOther = LightChartOther,
            categoryFree = LightChartFree
        )
    }

    CompositionLocalProvider(
        LocalTelemetryColors provides telemetryColors,
        LocalLiquidGlassEnabled provides liquidGlassEnabled,
        LocalBatterySaverEnabled provides batterySaverEnabled
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
