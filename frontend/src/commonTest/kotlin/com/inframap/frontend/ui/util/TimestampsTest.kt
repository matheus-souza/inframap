package com.inframap.frontend.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TimestampsTest {
    @Test
    fun utcTimestampsDropSecondsAndFractions() {
        assertEquals("2026-10-29 19:25 UTC", Timestamps.format("2026-10-29T19:25:46.868245Z"))
        assertEquals("2026-10-29 19:25 UTC", Timestamps.format("2026-10-29T19:25Z"))
    }

    @Test
    fun anOffsetIsKeptAsGiven() {
        assertEquals("2026-10-29 16:25 -03:00", Timestamps.format("2026-10-29T16:25:46-03:00"))
    }

    @Test
    fun anythingElseIsShownUnchanged() {
        assertEquals("yesterday", Timestamps.format("yesterday"))
        assertEquals("", Timestamps.format(""))
    }
}
