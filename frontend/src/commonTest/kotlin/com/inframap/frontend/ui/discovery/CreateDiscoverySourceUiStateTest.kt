package com.inframap.frontend.ui.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateDiscoverySourceUiStateTest {
    @Test
    fun networkOnlyCollectorsDoNotShowTabsAndHaveNoCurrentTab() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("icmp_sweep", "arp_sweep"),
            )

        assertFalse(state.showsProviderTabs)
        assertNull(state.currentProviderTab)
    }

    @Test
    fun singleProviderDoesNotShowTabsAndIsCurrentTab() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("icmp_sweep", "docker"),
            )

        assertFalse(state.showsProviderTabs)
        assertEquals("docker", state.currentProviderTab)
    }

    @Test
    fun twoProvidersWithNullActiveTabShowsTabsAndDefaultsToFirstSelected() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("proxmox", "docker"),
                activeProviderTab = null,
            )

        assertTrue(state.showsProviderTabs)
        assertEquals("proxmox", state.currentProviderTab)
    }

    @Test
    fun twoProvidersWithExplicitActiveTabFollowsActiveTab() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("proxmox", "docker"),
                activeProviderTab = "docker",
            )

        assertTrue(state.showsProviderTabs)
        assertEquals("docker", state.currentProviderTab)
    }

    @Test
    fun activeTabPointsToDeselectedProviderFallsBackToFirstSelected() {
        val state =
            CreateDiscoverySourceUiState(
                selectedCollectors = setOf("proxmox"),
                activeProviderTab = "docker",
            )

        assertFalse(state.showsProviderTabs)
        assertEquals("proxmox", state.currentProviderTab)
    }
}
