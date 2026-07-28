package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class ContrastTest {
    @Test
    fun onPrimaryOverPrimaryMeetsWcagAA() {
        val ratio = contrastRatio(InfraMapColorScheme.onPrimary, InfraMapColorScheme.primary)
        assertTrue(ratio >= 4.5, "onPrimary/primary contrast ratio $ratio < 4.5 (WCAG AA)")
    }

    @Test
    fun onSecondaryOverSecondaryMeetsWcagAA() {
        val ratio = contrastRatio(InfraMapColorScheme.onSecondary, InfraMapColorScheme.secondary)
        assertTrue(ratio >= 4.5, "onSecondary/secondary contrast ratio $ratio < 4.5 (WCAG AA)")
    }

    @Test
    fun onBackgroundOverBackgroundMeetsWcagAA() {
        val ratio = contrastRatio(InfraMapColorScheme.onBackground, InfraMapColorScheme.background)
        assertTrue(ratio >= 4.5, "onBackground/background contrast ratio $ratio < 4.5 (WCAG AA)")
    }

    @Test
    fun onSurfaceOverSurfaceMeetsWcagAA() {
        val ratio = contrastRatio(InfraMapColorScheme.onSurface, InfraMapColorScheme.surface)
        assertTrue(ratio >= 4.5, "onSurface/surface contrast ratio $ratio < 4.5 (WCAG AA)")
    }

    @Test
    fun errorOverBackgroundMeetsWcagAA() {
        val ratio = contrastRatio(InfraMapColorScheme.error, InfraMapColorScheme.background)
        assertTrue(ratio >= 4.5, "error/background contrast ratio $ratio < 4.5 (WCAG AA)")
    }

    private fun contrastRatio(
        fg: Color,
        bg: Color,
    ): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
}
