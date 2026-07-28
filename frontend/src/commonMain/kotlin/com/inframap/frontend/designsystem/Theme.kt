package com.inframap.frontend.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

internal val InfraMapColorScheme: ColorScheme =
    darkColorScheme(
        primary = InfraMapPurple,
        secondary = InfraMapCyan,
        background = InfraMapBackground,
        surface = InfraMapSurface,
        error = InfraMapRed,
        onPrimary = InfraMapBackground,
        onSecondary = InfraMapBackground,
        onBackground = InfraMapForeground,
        onSurface = InfraMapForeground,
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
