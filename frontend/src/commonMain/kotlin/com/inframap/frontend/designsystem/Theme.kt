package com.inframap.frontend.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

internal val InfraMapColorScheme: ColorScheme =
    darkColorScheme(
        primary = InfraMapPurple,
        secondary = InfraMapCyan,
        tertiary = InfraMapOrange,
        background = InfraMapBackground,
        surface = InfraMapSurface,
        surfaceVariant = InfraMapSurfaceVariant,
        surfaceContainerHighest = InfraMapSurfaceContainerHighest,
        error = InfraMapRed,
        errorContainer = InfraMapErrorContainer,
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        onSecondary = androidx.compose.ui.graphics.Color.Black,
        onTertiary = androidx.compose.ui.graphics.Color.Black,
        onBackground = InfraMapForeground,
        onSurface = InfraMapForeground,
        onSurfaceVariant = InfraMapOnSurfaceVariant,
        onError = InfraMapOnError,
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
