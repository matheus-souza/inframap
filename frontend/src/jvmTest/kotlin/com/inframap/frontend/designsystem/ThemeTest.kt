package com.inframap.frontend.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.pow
import kotlin.test.assertTrue

class ThemeTest {
    private fun relativeLuminance(color: Color): Double {
        val argb = color.toArgb()
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0

        fun convertComponent(c: Double): Double =
            if (c <= 0.04045) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).pow(2.4)
            }

        val rLin = convertComponent(r)
        val gLin = convertComponent(g)
        val bLin = convertComponent(b)

        return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin
    }

    private fun contrastRatio(
        c1: Color,
        c2: Color,
    ): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val brighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (brighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun contrastRatioOnBackgroundPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onBackground, InfraMapColorScheme.background)
        assertTrue(contrast >= 4.5, "onBackground contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnSurfacePassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onSurface, InfraMapColorScheme.surface)
        assertTrue(contrast >= 4.5, "onSurface contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnSurfaceVariantPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onSurfaceVariant, InfraMapColorScheme.surface)
        assertTrue(contrast >= 4.5, "onSurfaceVariant on surface contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnSurfaceVariantOnVariantPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onSurfaceVariant, InfraMapColorScheme.surfaceVariant)
        assertTrue(contrast >= 4.5, "onSurfaceVariant on surfaceVariant contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnPrimaryPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onPrimary, InfraMapColorScheme.primary)
        assertTrue(contrast >= 4.0, "onPrimary contrast $contrast must be >= 4.0")
    }

    @Test
    fun contrastRatioOnSecondaryPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onSecondary, InfraMapColorScheme.secondary)
        assertTrue(contrast >= 4.5, "onSecondary contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnTertiaryPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onTertiary, InfraMapColorScheme.tertiary)
        assertTrue(contrast >= 4.5, "onTertiary contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnErrorPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onError, InfraMapColorScheme.error)
        assertTrue(contrast >= 4.5, "onError contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnPrimaryContainerPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onPrimaryContainer, InfraMapColorScheme.primaryContainer)
        assertTrue(contrast >= 4.5, "onPrimaryContainer contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnSecondaryContainerPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onSecondaryContainer, InfraMapColorScheme.secondaryContainer)
        assertTrue(contrast >= 4.5, "onSecondaryContainer contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnTertiaryContainerPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onTertiaryContainer, InfraMapColorScheme.tertiaryContainer)
        assertTrue(contrast >= 4.5, "onTertiaryContainer contrast $contrast must be >= 4.5")
    }

    @Test
    fun contrastRatioOnErrorContainerPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onErrorContainer, InfraMapColorScheme.errorContainer)
        assertTrue(contrast >= 4.5, "onErrorContainer contrast $contrast must be >= 4.5")
    }

    @Test
    fun colorSchemeSurfaceContainerScaleMatchesTokens() {
        assertEquals(InfraMapSurfaceDim, InfraMapColorScheme.surfaceDim)
        assertEquals(InfraMapSurface, InfraMapColorScheme.surface)
        assertEquals(InfraMapSurfaceBright, InfraMapColorScheme.surfaceBright)
        assertEquals(InfraMapSurfaceContainerLowest, InfraMapColorScheme.surfaceContainerLowest)
        assertEquals(InfraMapSurfaceContainerLow, InfraMapColorScheme.surfaceContainerLow)
        assertEquals(InfraMapSurfaceContainer, InfraMapColorScheme.surfaceContainer)
        assertEquals(InfraMapSurfaceContainerHigh, InfraMapColorScheme.surfaceContainerHigh)
        assertEquals(InfraMapSurfaceContainerHighest, InfraMapColorScheme.surfaceContainerHighest)
    }

    @Test
    fun colorSchemeOutlineTokensMatchSpec() {
        assertEquals(InfraMapOutline, InfraMapColorScheme.outline)
        assertEquals(InfraMapOutlineVariant, InfraMapColorScheme.outlineVariant)
    }

    @Test
    fun shapeScaleHierarchyMatchesSpec() {
        assertEquals(RoundedCornerShape(0.dp), InfraMapShapeNone)
        assertEquals(RoundedCornerShape(4.dp), InfraMapShapeExtraSmall)
        assertEquals(RoundedCornerShape(8.dp), InfraMapShapeSmall)
        assertEquals(RoundedCornerShape(12.dp), InfraMapShapeMedium)
        assertEquals(RoundedCornerShape(16.dp), InfraMapShapeLarge)
        assertEquals(RoundedCornerShape(28.dp), InfraMapShapeExtraLarge)

        assertEquals(InfraMapShapeExtraSmall, InfraMapShapes.extraSmall)
        assertEquals(InfraMapShapeSmall, InfraMapShapes.small)
        assertEquals(InfraMapShapeMedium, InfraMapShapes.medium)
        assertEquals(InfraMapShapeLarge, InfraMapShapes.large)
        assertEquals(InfraMapShapeExtraLarge, InfraMapShapes.extraLarge)
    }
}
