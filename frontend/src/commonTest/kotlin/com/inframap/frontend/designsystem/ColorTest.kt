package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {
    @Test
    fun colorSchemePrimaryMatchesDesignSpec() {
        assertEquals(Color(0xFF8b5cf6), InfraMapColorScheme.primary)
    }

    @Test
    fun colorSchemeSecondaryMatchesDesignSpec() {
        assertEquals(Color(0xFF10b981), InfraMapColorScheme.secondary)
    }

    @Test
    fun colorSchemeBackgroundMatchesDesignSpec() {
        assertEquals(Color(0xFF121214), InfraMapColorScheme.background)
    }

    @Test
    fun colorSchemeSurfaceMatchesDesignSpec() {
        assertEquals(Color(0xFF18181b), InfraMapColorScheme.surface)
    }

    @Test
    fun colorSchemeErrorMatchesDesignSpec() {
        assertEquals(Color(0xFFef4444), InfraMapColorScheme.error)
    }

    @Test
    fun colorSchemeOnBackgroundMatchesDesignSpec() {
        assertEquals(Color(0xFFf4f4f5), InfraMapColorScheme.onBackground)
    }

    @Test
    fun colorSchemeOnSurfaceMatchesDesignSpec() {
        assertEquals(Color(0xFFf4f4f5), InfraMapColorScheme.onSurface)
    }

    @Test
    fun colorSchemeOutlineMatchesDesignSpec() {
        assertEquals(Color(0xFF3f3f46), InfraMapColorScheme.outline)
    }

    @Test
    fun colorSchemeOnPrimaryMatchesDesignSpec() {
        assertEquals(Color.Black, InfraMapColorScheme.onPrimary)
    }

    @Test
    fun colorSchemeOnSecondaryMatchesDesignSpec() {
        assertEquals(Color.Black, InfraMapColorScheme.onSecondary)
    }

    @Test
    fun additionalColorConstantsMatchDesignSpec() {
        assertEquals(Color(0xFF10b981), InfraMapGreen)
        assertEquals(Color(0xFFf59e0b), InfraMapOrange)
        assertEquals(Color(0xFFfacc15), InfraMapYellow)
        assertEquals(Color(0xFF3f3f46), InfraMapComment)
    }
}
