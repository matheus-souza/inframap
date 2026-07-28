package com.inframap.frontend

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSchemeTest {

    @Test
    fun colorSchemePrimaryMatchesDesignSpec() {
        assertEquals(Color(0xFFbd93f9), InfraMapColorScheme.primary)
    }

    @Test
    fun colorSchemeBackgroundMatchesDesignSpec() {
        assertEquals(Color(0xFF1e1f29), InfraMapColorScheme.background)
    }

    @Test
    fun colorSchemeSecondaryMatchesDesignSpec() {
        assertEquals(Color(0xFF8be9fd), InfraMapColorScheme.secondary)
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
}
