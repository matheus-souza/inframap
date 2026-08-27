package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.jvm.JvmName

// Material Design 3 Tonal Accents & Containers (RFC-022 / ADR-010)
val InfraMapPrimary = Color(0xFFD0BCFF)
val InfraMapOnPrimary = Color(0xFF381E72)
val InfraMapPrimaryContainer = Color(0xFF4F378B)
val InfraMapOnPrimaryContainer = Color(0xFFEADDFF)

val InfraMapSecondary = Color(0xFF6EE7B7)
val InfraMapOnSecondary = Color(0xFF003826)
val InfraMapSecondaryContainer = Color(0xFF005138)
val InfraMapOnSecondaryContainer = Color(0xFF8CF4D3)

val InfraMapTertiary = Color(0xFFFCD34D)
val InfraMapOnTertiary = Color(0xFF452B00)
val InfraMapTertiaryContainer = Color(0xFF633F00)
val InfraMapOnTertiaryContainer = Color(0xFFFFDF9E)

val InfraMapError = Color(0xFFFFB4AB)
val InfraMapOnError = Color(0xFF690005)
val InfraMapErrorContainer = Color(0xFF93000A)
val InfraMapOnErrorContainer = Color(0xFFFFDAD6)

// Material Design 3 Neutral Surface Container Scale
val InfraMapSurfaceDim = Color(0xFF121215)
val InfraMapSurface = Color(0xFF141318)
val InfraMapSurfaceBright = Color(0xFF38383F)
val InfraMapSurfaceContainerLowest = Color(0xFF0E0E12)
val InfraMapSurfaceContainerLow = Color(0xFF18181D)
val InfraMapSurfaceContainer = Color(0xFF1E1D24)
val InfraMapSurfaceContainerHigh = Color(0xFF28272F)
val InfraMapSurfaceContainerHighest = Color(0xFF33323C)

// Material Design 3 Content & Text Tokens
val InfraMapOnSurface = Color(0xFFE4E1E6)
val InfraMapOnSurfaceVariant = Color(0xFFC8C5D0)
val InfraMapOutline = Color(0xFF928F9A)
val InfraMapOutlineVariant = Color(0xFF48454E)

// Functional Status Tokens
val StatusOnline = Color(0xFF10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusOffline = Color(0xFFEF4444)
val StatusStaging = Color(0xFFA78BFA)

// Backward-compatible aliases for existing references
val InfraMapCanvasBg = InfraMapSurfaceDim
val InfraMapSurfaceBg = InfraMapSurface
val InfraMapSurfaceElevated = InfraMapSurfaceContainerHigh
val InfraMapBorder = InfraMapOutlineVariant
val InfraMapTextPrimary = InfraMapOnSurface
val InfraMapTextSecondary = InfraMapOnSurfaceVariant

val InfraMapElectricViolet = InfraMapPrimary
val InfraMapEmeraldGreen = InfraMapSecondary
val InfraMapAmberWarm = InfraMapTertiary
val InfraMapRubyRed = InfraMapError

// Legacy / T37 Tokens
val canvasBackground = InfraMapSurfaceDim
val surfaceContainer = InfraMapSurfaceContainer
val surfaceContainerHigh = InfraMapSurfaceContainerHigh
val outlineSubtle = InfraMapOutlineVariant
val accentPrimary = InfraMapPrimary

@get:JvmName("getStatusOnlineLowercase")
val statusOnline = StatusOnline

@get:JvmName("getStatusWarningLowercase")
val statusWarning = StatusWarning

val statusAlert = StatusOffline

@get:JvmName("getStatusStagingLowercase")
val statusStaging = StatusStaging

internal val InfraMapBackground = InfraMapSurfaceDim
internal val InfraMapSurfaceVariant = InfraMapSurfaceContainerHigh
internal val InfraMapForeground = InfraMapOnSurface

internal val InfraMapPurple = InfraMapPrimary
internal val InfraMapCyan = InfraMapSecondary
internal val InfraMapGreen = InfraMapSecondary
internal val InfraMapOrange = InfraMapTertiary
internal val InfraMapYellow = Color(0xFFFACC15)
internal val InfraMapRed = InfraMapError
internal val InfraMapComment = InfraMapOutlineVariant
