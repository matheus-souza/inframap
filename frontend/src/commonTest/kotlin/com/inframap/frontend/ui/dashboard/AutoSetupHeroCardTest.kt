package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.ui.util.UiText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoSetupHeroCardTest {
    @Test
    fun defaultStateIsIdleAndHidden() {
        val state = AutoSetupState()
        assertFalse(state.isVisible)
        assertEquals(AutoSetupPhase.IDLE, state.phase)
        assertEquals(0, state.discoveredDeviceCount)
        assertTrue(state.detectedInterfaces.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun visibleStateWithDetectedInterfaces() {
        val ifaces =
            listOf(
                NetworkInterface(name = "eth0", ip = "192.168.1.50", cidr = "192.168.1.0/24", mac = "00:11:22:33:44:55"),
                NetworkInterface(name = "wlan0", ip = "10.0.0.100", cidr = "10.0.0.0/24", mac = "AA:BB:CC:DD:EE:FF"),
            )
        val state =
            AutoSetupState(
                isVisible = true,
                detectedInterfaces = ifaces,
                phase = AutoSetupPhase.IDLE,
            )

        assertTrue(state.isVisible)
        assertEquals(2, state.detectedInterfaces.size)
        assertEquals("eth0", state.detectedInterfaces[0].name)
        assertEquals("192.168.1.0/24", state.detectedInterfaces[0].cidr)
    }

    @Test
    fun phaseTransitionsAreTrackedCorrectly() {
        val creatingSubnets = AutoSetupState(phase = AutoSetupPhase.CREATING_SUBNETS)
        assertEquals(AutoSetupPhase.CREATING_SUBNETS, creatingSubnets.phase)

        val creatingSources = AutoSetupState(phase = AutoSetupPhase.CREATING_SOURCES)
        assertEquals(AutoSetupPhase.CREATING_SOURCES, creatingSources.phase)

        val scanning = AutoSetupState(phase = AutoSetupPhase.SCANNING)
        assertEquals(AutoSetupPhase.SCANNING, scanning.phase)

        val completed = AutoSetupState(phase = AutoSetupPhase.COMPLETED, discoveredDeviceCount = 7)
        assertEquals(AutoSetupPhase.COMPLETED, completed.phase)
        assertEquals(7, completed.discoveredDeviceCount)
    }

    @Test
    fun errorStateCapturesMessage() {
        val state =
            AutoSetupState(
                isVisible = true,
                errorMessage = UiText.DynamicString("Failed to probe interfaces"),
            )
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage is UiText.DynamicString)
        assertEquals("Failed to probe interfaces", (state.errorMessage as UiText.DynamicString).value)
    }
}
