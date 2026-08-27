package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {
    @Test
    fun m3PrimaryTonalTokensMatchSpec() {
        assertEquals(Color(0xFFD0BCFF), InfraMapPrimary)
        assertEquals(Color(0xFF381E72), InfraMapOnPrimary)
        assertEquals(Color(0xFF4F378B), InfraMapPrimaryContainer)
        assertEquals(Color(0xFFEADDFF), InfraMapOnPrimaryContainer)
    }

    @Test
    fun m3SecondaryTonalTokensMatchSpec() {
        assertEquals(Color(0xFF6EE7B7), InfraMapSecondary)
        assertEquals(Color(0xFF003826), InfraMapOnSecondary)
        assertEquals(Color(0xFF005138), InfraMapSecondaryContainer)
        assertEquals(Color(0xFF8CF4D3), InfraMapOnSecondaryContainer)
    }

    @Test
    fun m3TertiaryTonalTokensMatchSpec() {
        assertEquals(Color(0xFFFCD34D), InfraMapTertiary)
        assertEquals(Color(0xFF452B00), InfraMapOnTertiary)
        assertEquals(Color(0xFF633F00), InfraMapTertiaryContainer)
        assertEquals(Color(0xFFFFDF9E), InfraMapOnTertiaryContainer)
    }

    @Test
    fun m3ErrorTonalTokensMatchSpec() {
        assertEquals(Color(0xFFFFB4AB), InfraMapError)
        assertEquals(Color(0xFF690005), InfraMapOnError)
        assertEquals(Color(0xFF93000A), InfraMapErrorContainer)
        assertEquals(Color(0xFFFFDAD6), InfraMapOnErrorContainer)
    }

    @Test
    fun m3NeutralSurfaceContainerScaleMatchesSpec() {
        assertEquals(Color(0xFF121215), InfraMapSurfaceDim)
        assertEquals(Color(0xFF141318), InfraMapSurface)
        assertEquals(Color(0xFF38383F), InfraMapSurfaceBright)
        assertEquals(Color(0xFF0E0E12), InfraMapSurfaceContainerLowest)
        assertEquals(Color(0xFF18181D), InfraMapSurfaceContainerLow)
        assertEquals(Color(0xFF1E1D24), InfraMapSurfaceContainer)
        assertEquals(Color(0xFF28272F), InfraMapSurfaceContainerHigh)
        assertEquals(Color(0xFF33323C), InfraMapSurfaceContainerHighest)
    }

    @Test
    fun m3ContentAndTextTokensMatchSpec() {
        assertEquals(Color(0xFFE4E1E6), InfraMapOnSurface)
        assertEquals(Color(0xFFC8C5D0), InfraMapOnSurfaceVariant)
        assertEquals(Color(0xFF928F9A), InfraMapOutline)
        assertEquals(Color(0xFF48454E), InfraMapOutlineVariant)
    }

    @Test
    fun functionalStatusTokensMatchSpec() {
        assertEquals(Color(0xFF10B981), StatusOnline)
        assertEquals(Color(0xFFF59E0B), StatusWarning)
        assertEquals(Color(0xFFEF4444), StatusOffline)
        assertEquals(Color(0xFFA78BFA), StatusStaging)
        assertEquals(StatusOnline, statusOnline)
        assertEquals(StatusWarning, statusWarning)
        assertEquals(StatusOffline, statusAlert)
        assertEquals(StatusStaging, statusStaging)
    }

    @Test
    fun backwardCompatibleAliasesMatchSpec() {
        assertEquals(InfraMapSurfaceDim, InfraMapCanvasBg)
        assertEquals(InfraMapSurface, InfraMapSurfaceBg)
        assertEquals(InfraMapSurfaceContainerHigh, InfraMapSurfaceElevated)
        assertEquals(InfraMapOutlineVariant, InfraMapBorder)
        assertEquals(InfraMapOnSurface, InfraMapTextPrimary)
        assertEquals(InfraMapOnSurfaceVariant, InfraMapTextSecondary)
        assertEquals(InfraMapPrimary, InfraMapElectricViolet)
        assertEquals(InfraMapSecondary, InfraMapEmeraldGreen)
        assertEquals(InfraMapTertiary, InfraMapAmberWarm)
        assertEquals(InfraMapError, InfraMapRubyRed)
        assertEquals(InfraMapSurfaceDim, canvasBackground)
        assertEquals(InfraMapSurfaceContainer, surfaceContainer)
        assertEquals(InfraMapSurfaceContainerHigh, surfaceContainerHigh)
        assertEquals(InfraMapOutlineVariant, outlineSubtle)
        assertEquals(InfraMapPrimary, accentPrimary)
        assertEquals(InfraMapSurfaceDim, InfraMapBackground)
        assertEquals(InfraMapSurfaceContainerHigh, InfraMapSurfaceVariant)
        assertEquals(InfraMapOnSurface, InfraMapForeground)
        assertEquals(InfraMapPrimary, InfraMapPurple)
        assertEquals(InfraMapSecondary, InfraMapCyan)
        assertEquals(InfraMapSecondary, InfraMapGreen)
        assertEquals(InfraMapTertiary, InfraMapOrange)
        assertEquals(Color(0xFFFACC15), InfraMapYellow)
        assertEquals(InfraMapError, InfraMapRed)
        assertEquals(InfraMapOutlineVariant, InfraMapComment)
    }
}
