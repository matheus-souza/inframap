package com.inframap.frontend.ui.inventory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryStateTest {
    private val devOnline1 =
        InventoryItem(
            id = "dev-1",
            hostname = "core-router-01",
            ipAddress = "192.168.1.1",
            macAddress = "00:11:22:33:44:55",
            deviceType = "Router",
            status = InventoryStatus.ONLINE,
            subnet = "192.168.1.0/24",
            discoveryProtocol = "SNMP",
            latencyMs = 12L,
        )

    private val devOnline2 =
        InventoryItem(
            id = "dev-2",
            hostname = "dist-switch-01",
            ipAddress = "192.168.1.2",
            macAddress = "00:11:22:33:44:56",
            deviceType = "Switch",
            status = InventoryStatus.ONLINE,
            subnet = "192.168.1.0/24",
            discoveryProtocol = "LLDP",
            latencyMs = 18L,
        )

    private val devWarning =
        InventoryItem(
            id = "dev-3",
            hostname = "access-switch-02",
            ipAddress = "192.168.2.10",
            macAddress = "00:11:22:33:44:57",
            deviceType = "Switch",
            status = InventoryStatus.WARNING,
            subnet = "192.168.2.0/24",
            discoveryProtocol = "SNMP",
            latencyMs = 145L,
        )

    private val devStaging =
        InventoryItem(
            id = "dev-4",
            hostname = "unidentified-box",
            ipAddress = "10.0.0.50",
            macAddress = "AA:BB:CC:DD:EE:FF",
            deviceType = "Unknown",
            status = InventoryStatus.STAGING,
            subnet = "10.0.0.0/16",
            discoveryProtocol = "ARP",
            latencyMs = 5L,
        )

    private val devOffline =
        InventoryItem(
            id = "dev-5",
            hostname = "legacy-server",
            ipAddress = "172.16.0.99",
            macAddress = "11:22:33:44:55:66",
            deviceType = "Server",
            status = InventoryStatus.OFFLINE,
            subnet = "172.16.0.0/24",
            discoveryProtocol = "ICMP",
            latencyMs = null,
        )

    private val allSampleItems = listOf(devOnline1, devOnline2, devWarning, devStaging, devOffline)

    @Test
    fun statusCountsCalculatedCorrectly() {
        val state = InventoryUiState(items = allSampleItems)
        val counts = state.statusCounts

        assertEquals(2, counts[InventoryStatus.ONLINE])
        assertEquals(1, counts[InventoryStatus.WARNING])
        assertEquals(1, counts[InventoryStatus.STAGING])
        assertEquals(1, counts[InventoryStatus.OFFLINE])
    }

    @Test
    fun statusFilteringBehavior() {
        val state = InventoryUiState(items = allSampleItems)

        assertEquals(5, state.filteredItems.size)

        val onlineFiltered = state.copy(activeStatusFilter = InventoryStatus.ONLINE)
        assertEquals(2, onlineFiltered.filteredItems.size)
        assertTrue(onlineFiltered.filteredItems.all { it.status == InventoryStatus.ONLINE })

        val warningFiltered = state.copy(activeStatusFilter = InventoryStatus.WARNING)
        assertEquals(1, warningFiltered.filteredItems.size)
        assertEquals("dev-3", warningFiltered.filteredItems.first().id)

        val stagingFiltered = state.copy(activeStatusFilter = InventoryStatus.STAGING)
        assertEquals(1, stagingFiltered.filteredItems.size)
        assertEquals("dev-4", stagingFiltered.filteredItems.first().id)

        val offlineFiltered = state.copy(activeStatusFilter = InventoryStatus.OFFLINE)
        assertEquals(1, offlineFiltered.filteredItems.size)
        assertEquals("dev-5", offlineFiltered.filteredItems.first().id)
    }

    @Test
    fun searchQueryFilteringBehavior() {
        val state = InventoryUiState(items = allSampleItems)

        val hostnameMatch = state.copy(searchQuery = "core-router")
        assertEquals(1, hostnameMatch.filteredItems.size)
        assertEquals("dev-1", hostnameMatch.filteredItems.first().id)

        val ipMatch = state.copy(searchQuery = "10.0.0.")
        assertEquals(1, ipMatch.filteredItems.size)
        assertEquals("dev-4", ipMatch.filteredItems.first().id)

        val subnetMatch = state.copy(searchQuery = "172.16.0.0/24")
        assertEquals(1, subnetMatch.filteredItems.size)
        assertEquals("dev-5", subnetMatch.filteredItems.first().id)

        val macMatch = state.copy(searchQuery = "AA:BB:CC")
        assertEquals(1, macMatch.filteredItems.size)
        assertEquals("dev-4", macMatch.filteredItems.first().id)
    }

    @Test
    fun multiSelectionStateTracking() {
        val state = InventoryUiState(items = allSampleItems)

        assertFalse(state.isAllSelected)
        assertTrue(state.selectedIds.isEmpty())

        val selectedOne = state.copy(selectedIds = setOf("dev-1"))
        assertFalse(selectedOne.isAllSelected)
        assertEquals(1, selectedOne.selectedIds.size)

        val selectedAll = state.copy(selectedIds = setOf("dev-1", "dev-2", "dev-3", "dev-4", "dev-5"))
        assertTrue(selectedAll.isAllSelected)
        assertEquals(5, selectedAll.selectedIds.size)
    }

    @Test
    fun batchActionPayloadConstruction() {
        val emptySelectionState = InventoryUiState(items = allSampleItems)
        assertNull(emptySelectionState.createBatchAction(BatchActionType.APPROVE))
        assertNull(emptySelectionState.createBatchAction(BatchActionType.RESCAN))
        assertNull(emptySelectionState.createBatchAction(BatchActionType.DELETE))

        val selectedIds = setOf("dev-1", "dev-4")
        val stateWithSelection = InventoryUiState(items = allSampleItems, selectedIds = selectedIds)

        val approvePayload = stateWithSelection.createBatchAction(BatchActionType.APPROVE)
        assertIs<BatchActionPayload.Approve>(approvePayload)
        assertEquals(selectedIds, approvePayload.selectedIds)

        val rescanPayload = stateWithSelection.createBatchAction(BatchActionType.RESCAN)
        assertIs<BatchActionPayload.Rescan>(rescanPayload)
        assertEquals(selectedIds, rescanPayload.selectedIds)

        val deletePayload = stateWithSelection.createBatchAction(BatchActionType.DELETE)
        assertIs<BatchActionPayload.Delete>(deletePayload)
        assertEquals(selectedIds, deletePayload.selectedIds)
    }
}
