package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {
    @Test
    fun colorSchemePrimaryMatchesDesignSpec() {
        assertEquals(Color(0xFFbd93f9), InfraMapColorScheme.primary)
    }

    @Test
    fun colorSchemeSecondaryMatchesDesignSpec() {
        assertEquals(Color(0xFF8be9fd), InfraMapColorScheme.secondary)
    }

    @Test
    fun colorSchemeBackgroundMatchesDesignSpec() {
        assertEquals(Color(0xFF1e1f29), InfraMapColorScheme.background)
    }

    @Test
    fun colorSchemeSurfaceMatchesDesignSpec() {
        assertEquals(Color(0xFF282a36), InfraMapColorScheme.surface)
    }

    @Test
    fun colorSchemeErrorMatchesDesignSpec() {
        assertEquals(Color(0xFFff5555), InfraMapColorScheme.error)
    }

    @Test
    fun colorSchemeOnBackgroundMatchesDesignSpec() {
        assertEquals(Color(0xFFf8f8f2), InfraMapColorScheme.onBackground)
    }

    @Test
    fun colorSchemeOnSurfaceMatchesDesignSpec() {
        assertEquals(Color(0xFFf8f8f2), InfraMapColorScheme.onSurface)
    }

    @Test
    fun colorSchemeOutlineMatchesDesignSpec() {
        assertEquals(Color(0xFF6272a4), InfraMapColorScheme.outline)
    }

    @Test
    fun colorSchemeOnPrimaryMatchesDesignSpec() {
        assertEquals(Color(0xFF1e1f29), InfraMapColorScheme.onPrimary)
    }

    @Test
    fun colorSchemeOnSecondaryMatchesDesignSpec() {
        assertEquals(Color(0xFF1e1f29), InfraMapColorScheme.onSecondary)
    }

    @Test
    fun additionalColorConstantsMatchDesignSpec() {
        assertEquals(Color(0xFF50fa7b), InfraMapGreen)
        assertEquals(Color(0xFFffb86c), InfraMapOrange)
        assertEquals(Color(0xFFf1fa8c), InfraMapYellow)
        assertEquals(Color(0xFF6272a4), InfraMapComment)
    }
}
