package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    fun contrastRatioOnErrorPassesWcagAa() {
        val contrast = contrastRatio(InfraMapColorScheme.onError, InfraMapColorScheme.error)
        assertTrue(contrast >= 4.5, "onError contrast $contrast must be >= 4.5")
    }
}
