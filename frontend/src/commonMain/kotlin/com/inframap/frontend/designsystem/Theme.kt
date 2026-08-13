package com.inframap.frontend.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val InfraMapColorScheme: ColorScheme =
    darkColorScheme(
        primary = InfraMapElectricViolet,
        secondary = InfraMapEmeraldGreen,
        tertiary = InfraMapAmberWarm,
        background = InfraMapCanvasBg,
        surface = InfraMapSurfaceBg,
        surfaceVariant = InfraMapSurfaceElevated,
        surfaceContainerHighest = InfraMapSurfaceElevated,
        error = InfraMapRubyRed,
        errorContainer = InfraMapErrorContainer,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onTertiary = Color.Black,
        onBackground = InfraMapTextPrimary,
        onSurface = InfraMapTextPrimary,
        onSurfaceVariant = InfraMapTextSecondary,
        onError = Color.Black,
        onErrorContainer = InfraMapOnErrorContainer,
        outline = InfraMapComment,
    )

@Composable
fun InfraMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InfraMapColorScheme,
        typography = InfraMapTypography,
        content = content,
    )
}
