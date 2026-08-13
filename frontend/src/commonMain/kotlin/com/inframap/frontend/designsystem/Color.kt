package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color

// Excalidraw Dark Slate Core Color Palette (RFC-019 / ADR-006)
val InfraMapCanvasBg = Color(0xFF121214) // Deep Charcoal Black
val InfraMapSurfaceBg = Color(0xFF18181b) // Container Surface
val InfraMapSurfaceElevated = Color(0xFF27272a) // Elevated Surfaces & Borders
val InfraMapBorder = Color(0xFF27272a) // Borders
val InfraMapTextPrimary = Color(0xFFf4f4f5) // High-contrast body & header text
val InfraMapTextSecondary = Color(0xFFa1a1aa) // Subtitles & Icons

val InfraMapElectricViolet = Color(0xFF8b5cf6) // Primary / Active Accent (#8b5cf6)
val InfraMapEmeraldGreen = Color(0xFF10b981) // Secondary / Status Online (#10b981)
val InfraMapAmberWarm = Color(0xFFf59e0b) // Tertiary / Status Warning (#f59e0b)
val InfraMapRubyRed = Color(0xFFef4444) // Error / Status Offline (#ef4444)

// Functional Status Tokens
val StatusOnline = InfraMapEmeraldGreen
val StatusWarning = InfraMapAmberWarm
val StatusOffline = InfraMapRubyRed
val StatusStaging = InfraMapElectricViolet

// Convenience & backward-compatible aliases
internal val InfraMapBackground = InfraMapCanvasBg
internal val InfraMapSurface = InfraMapSurfaceBg
internal val InfraMapSurfaceVariant = InfraMapSurfaceElevated
internal val InfraMapOnSurfaceVariant = InfraMapTextSecondary
internal val InfraMapForeground = InfraMapTextPrimary

internal val InfraMapPurple = InfraMapElectricViolet
internal val InfraMapCyan = InfraMapEmeraldGreen
internal val InfraMapGreen = InfraMapEmeraldGreen
internal val InfraMapOrange = InfraMapAmberWarm
internal val InfraMapYellow = Color(0xFFfacc15)
internal val InfraMapRed = InfraMapRubyRed
internal val InfraMapComment = Color(0xFF3f3f46)

internal val InfraMapErrorContainer = Color(0xFF450a0a)
internal val InfraMapOnErrorContainer = Color(0xFFfca5a5)
internal val InfraMapOnError = InfraMapCanvasBg
internal val InfraMapSurfaceContainerHighest = InfraMapSurfaceElevated
