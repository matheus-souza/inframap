package com.inframap.frontend.designsystem

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class TypographyTest {
    @Test
    fun headlineLargeUsesCorrectSpec() {
        val style = InfraMapTypography.headlineLarge
        assertEquals(FontFamily.SansSerif, style.fontFamily)
        assertEquals(FontWeight.SemiBold, style.fontWeight)
        assertEquals(32.sp, style.fontSize)
    }

    @Test
    fun bodyLargeUsesCorrectSpec() {
        val style = InfraMapTypography.bodyLarge
        assertEquals(FontFamily.SansSerif, style.fontFamily)
        assertEquals(FontWeight.Normal, style.fontWeight)
        assertEquals(16.sp, style.fontSize)
    }

    @Test
    fun labelLargeUsesCorrectSpec() {
        val style = InfraMapTypography.labelLarge
        assertEquals(FontFamily.SansSerif, style.fontFamily)
        assertEquals(FontWeight.Medium, style.fontWeight)
        assertEquals(14.sp, style.fontSize)
    }

    @Test
    fun allStylesUseSansSerif() {
        val styles =
            listOf(
                InfraMapTypography.displayLarge,
                InfraMapTypography.displayMedium,
                InfraMapTypography.displaySmall,
                InfraMapTypography.headlineLarge,
                InfraMapTypography.headlineMedium,
                InfraMapTypography.headlineSmall,
                InfraMapTypography.titleLarge,
                InfraMapTypography.titleMedium,
                InfraMapTypography.titleSmall,
                InfraMapTypography.bodyLarge,
                InfraMapTypography.bodyMedium,
                InfraMapTypography.bodySmall,
                InfraMapTypography.labelLarge,
                InfraMapTypography.labelMedium,
                InfraMapTypography.labelSmall,
            )
        styles.forEach { style ->
            assertEquals(FontFamily.SansSerif, style.fontFamily, "Expected SansSerif for ${style.fontSize}")
        }
    }
}
