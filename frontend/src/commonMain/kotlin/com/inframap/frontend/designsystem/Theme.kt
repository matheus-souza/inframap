package com.inframap.frontend.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

internal val InfraMapColorScheme: ColorScheme =
    darkColorScheme(
        primary = InfraMapPrimary,
        onPrimary = InfraMapOnPrimary,
        primaryContainer = InfraMapPrimaryContainer,
        onPrimaryContainer = InfraMapOnPrimaryContainer,
        secondary = InfraMapSecondary,
        onSecondary = InfraMapOnSecondary,
        secondaryContainer = InfraMapSecondaryContainer,
        onSecondaryContainer = InfraMapOnSecondaryContainer,
        tertiary = InfraMapTertiary,
        onTertiary = InfraMapOnTertiary,
        tertiaryContainer = InfraMapTertiaryContainer,
        onTertiaryContainer = InfraMapOnTertiaryContainer,
        background = InfraMapSurfaceDim,
        onBackground = InfraMapOnSurface,
        surface = InfraMapSurface,
        onSurface = InfraMapOnSurface,
        surfaceVariant = InfraMapSurfaceContainerHigh,
        onSurfaceVariant = InfraMapOnSurfaceVariant,
        surfaceTint = InfraMapPrimary,
        inverseSurface = InfraMapOnSurface,
        inverseOnSurface = InfraMapSurface,
        error = InfraMapError,
        onError = InfraMapOnError,
        errorContainer = InfraMapErrorContainer,
        onErrorContainer = InfraMapOnErrorContainer,
        outline = InfraMapOutline,
        outlineVariant = InfraMapOutlineVariant,
        surfaceBright = InfraMapSurfaceBright,
        surfaceDim = InfraMapSurfaceDim,
        surfaceContainer = InfraMapSurfaceContainer,
        surfaceContainerHigh = InfraMapSurfaceContainerHigh,
        surfaceContainerHighest = InfraMapSurfaceContainerHighest,
        surfaceContainerLow = InfraMapSurfaceContainerLow,
        surfaceContainerLowest = InfraMapSurfaceContainerLowest,
    )

@Composable
fun InfraMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = InfraMapColorScheme,
        shapes = InfraMapShapes,
        typography = InfraMapTypography,
        content = content,
    )
}
