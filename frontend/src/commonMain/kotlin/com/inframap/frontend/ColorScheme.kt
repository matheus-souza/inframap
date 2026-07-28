package com.inframap.frontend

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val InfraMapPurple = Color(0xFFbd93f9)
internal val InfraMapCyan = Color(0xFF8be9fd)
internal val InfraMapBackground = Color(0xFF1e1f29)
internal val InfraMapSurface = Color(0xFF282a36)
internal val InfraMapForeground = Color(0xFFf8f8f2)
internal val InfraMapRed = Color(0xFFff5555)

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
    )
