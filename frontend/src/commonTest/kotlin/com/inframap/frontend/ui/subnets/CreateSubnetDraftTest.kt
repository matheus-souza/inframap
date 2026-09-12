package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.storage.draft.DraftJson
import com.inframap.frontend.domain.model.NetworkInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateSubnetDraftTest {
    @Test
    fun toDraftCopiesEditableFieldsVerbatim() {
        val state =
            CreateSubnetUiState(
                name = "VLAN 100",
                cidr = "10.0.100.0/24",
                vlanId = "abc-raw",
                gatewayIp = "10.0.100.1",
                description = "My test subnet",
                discoveryEnabled = false,
            )

        val draft = state.toDraft(prefilledCidr = "10.0.100.0/24", prefilledName = "Suggested")

        assertEquals("VLAN 100", draft.name)
        assertEquals("10.0.100.0/24", draft.cidr)
        assertEquals("abc-raw", draft.vlanId)
        assertEquals("10.0.100.1", draft.gatewayIp)
        assertEquals("My test subnet", draft.description)
        assertFalse(draft.discoveryEnabled)
        assertEquals("10.0.100.0/24", draft.prefilledCidr)
        assertEquals("Suggested", draft.prefilledName)
    }

    @Test
    fun toDraftRecordsOrigin() {
        val state = CreateSubnetUiState(name = "Subnet A", cidr = "192.168.1.0/24")

        val draft = state.toDraft(prefilledCidr = "192.168.1.0/24", prefilledName = "eth0")

        assertEquals("192.168.1.0/24", draft.prefilledCidr)
        assertEquals("eth0", draft.prefilledName)
    }

    @Test
    fun applyToRestoresFieldsAndLeavesTransientStateUntouched() {
        val draft =
            CreateSubnetDraft(
                name = "Restored Subnet",
                cidr = "172.16.0.0/16",
                vlanId = "20",
                gatewayIp = "172.16.0.1",
                description = "Restored desc",
                discoveryEnabled = true,
                prefilledCidr = null,
                prefilledName = null,
            )

        val dummyInterface =
            NetworkInterface(
                name = "eth0",
                ip = "192.168.1.10",
                cidr = "192.168.1.0/24",
                mac = "aa:bb:cc:dd:ee:ff",
                gateway = "192.168.1.1",
            )
        val initial =
            CreateSubnetUiState(
                isSubmitting = true,
                isSuccess = true,
                showInterfaceSuggestions = false,
                detectedInterfaces = listOf(dummyInterface),
                selectedInterface = dummyInterface,
            )

        val restored = draft.applyTo(initial)

        assertEquals("Restored Subnet", restored.name)
        assertEquals("172.16.0.0/16", restored.cidr)
        assertEquals("20", restored.vlanId)
        assertEquals("172.16.0.1", restored.gatewayIp)
        assertEquals("Restored desc", restored.description)
        assertTrue(restored.discoveryEnabled)
        assertTrue(restored.restoredFromDraft)
        // Transient fields untouched
        assertTrue(restored.isSubmitting)
        assertTrue(restored.isSuccess)
        assertFalse(restored.showInterfaceSuggestions)
        assertEquals(listOf(dummyInterface), restored.detectedInterfaces)
        assertEquals(dummyInterface, restored.selectedInterface)
    }

    @Test
    fun matchesOriginOnlyForSamePrefill() {
        val draft = CreateSubnetDraft(prefilledCidr = "10.0.0.0/24", prefilledName = "eth1")

        assertTrue(draft.matchesOrigin("10.0.0.0/24", "eth1"))
        assertFalse(draft.matchesOrigin("10.0.0.0/24", "eth2"))
        assertFalse(draft.matchesOrigin("10.0.1.0/24", "eth1"))
        assertFalse(draft.matchesOrigin(null, null))

        val blankDraft = CreateSubnetDraft(prefilledCidr = null, prefilledName = null)
        assertTrue(blankDraft.matchesOrigin(null, null))
        assertFalse(blankDraft.matchesOrigin("10.0.0.0/24", null))
    }

    @Test
    fun serializedFormUsesSnakeCaseAndIncludesDefaults() {
        val draft = CreateSubnetDraft(name = "Test", cidr = "10.0.0.0/8")
        val json = DraftJson.encodeToString(CreateSubnetDraft.serializer(), draft)

        assertTrue(json.contains("\"vlan_id\":\"\""))
        assertTrue(json.contains("\"gateway_ip\":\"\""))
        assertTrue(json.contains("\"discovery_enabled\":true"))
        assertTrue(json.contains("\"prefilled_cidr\":null"))
        assertTrue(json.contains("\"prefilled_name\":null"))

        val decoded = DraftJson.decodeFromString(CreateSubnetDraft.serializer(), json)
        assertEquals(draft, decoded)
    }
}
