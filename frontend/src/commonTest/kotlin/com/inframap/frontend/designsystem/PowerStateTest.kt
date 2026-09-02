package com.inframap.frontend.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PowerStateTest {
    @Test
    fun mapsTheStatesProxmoxReports() {
        assertEquals(PowerState.RUNNING, PowerState.fromRaw("running"))
        assertEquals(PowerState.STOPPED, PowerState.fromRaw("stopped"))
        assertEquals(PowerState.PAUSED, PowerState.fromRaw("paused"))
    }

    @Test
    fun mapsDockerExitedOntoStopped() {
        // Docker reports "exited" where Proxmox reports "stopped"; both mean the same thing
        // to a reader, so they share a badge.
        assertEquals(PowerState.STOPPED, PowerState.fromRaw("exited"))
    }

    @Test
    fun isCaseAndWhitespaceTolerant() {
        assertEquals(PowerState.RUNNING, PowerState.fromRaw("  RUNNING "))
    }

    @Test
    fun anUnknownOrAbsentStateRendersNoBadge() {
        // A device no provider owns has no runtime state, and an unrecognized value must not
        // be forced into one of the three badges.
        assertNull(PowerState.fromRaw(null))
        assertNull(PowerState.fromRaw(""))
        assertNull(PowerState.fromRaw("restarting"))
    }
}
