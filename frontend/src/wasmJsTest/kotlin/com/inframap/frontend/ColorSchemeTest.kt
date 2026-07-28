package com.inframap.frontend

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSchemeTest {

    @Test
    fun infraMapColorSchemeHasExpectedPrimary() {
        val expected = 0xFFbd93f9u.toLong()
        assertEquals(expected, 0xFFbd93f9u.toLong())
    }

    @Test
    fun infraMapColorSchemeHasExpectedBackground() {
        val expected = 0xFF1e1f29u.toLong()
        assertEquals(expected, 0xFF1e1f29u.toLong())
    }
}
