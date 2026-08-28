package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.SubnetDto
import com.inframap.frontend.data.dto.SubnetListResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubnetMapperTest {
    @Test
    fun toDomainMapsAllFieldsCorrectly() {
        val dto =
            SubnetDto(
                id = "sub-1",
                name = "Office LAN",
                cidr = "192.168.1.0/24",
                vlanId = 10,
                gatewayIp = "192.168.1.1",
                description = "Main network",
                discoveryEnabled = true,
                createdAt = "2026-01-01T00:00:00Z",
            )

        val domain = SubnetMapper.toDomain(dto)

        assertEquals("sub-1", domain.id)
        assertEquals("Office LAN", domain.name)
        assertEquals("192.168.1.0/24", domain.cidr)
        assertEquals(10, domain.vlanId)
        assertEquals("192.168.1.1", domain.gatewayIp)
        assertEquals("Main network", domain.description)
        assertTrue(domain.discoveryEnabled)
        assertEquals("2026-01-01T00:00:00Z", domain.createdAt)
    }

    @Test
    fun toPaginatedListMapsResponseCorrectly() {
        val response =
            SubnetListResponse(
                items = listOf(SubnetDto(id = "1", name = "S1", cidr = "10.0.0.0/8")),
                total = 1,
            )

        val list = SubnetMapper.toPaginatedList(response)

        assertEquals(1, list.items.size)
        assertEquals("S1", list.items[0].name)
    }

    @Test
    fun toSummaryMapsDtoCorrectly() {
        val dto =
            SubnetDto(
                id = "sub-1",
                name = "Office LAN",
                cidr = "192.168.1.0/24",
                discoveryEnabled = true,
            )

        val summary = SubnetMapper.toSummary(dto)

        assertEquals("sub-1", summary.id)
        assertEquals("Office LAN", summary.name)
        assertEquals("192.168.1.0/24", summary.cidr)
        assertTrue(summary.discoveryEnabled)
    }
}
