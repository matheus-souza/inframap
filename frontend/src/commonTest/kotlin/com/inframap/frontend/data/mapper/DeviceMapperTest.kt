package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.DeviceListResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceMapperTest {
    @Test
    fun toDomainMapsAllFieldsCorrectly() {
        val dto =
            DeviceDto(
                id = "dev-1",
                hostname = "router-01",
                ipAddress = "192.168.1.1",
                macAddress = "AA:BB:CC:DD:EE:FF",
                manufacturer = "Cisco",
                model = "ISR 4000",
                serialNumber = "SN12345",
                deviceType = "router",
                status = "active",
                metadata = mapOf("location" to "rack-1"),
                createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-02T00:00:00Z",
            )

        val domain = DeviceMapper.toDomain(dto)

        assertEquals("dev-1", domain.id)
        assertEquals("router-01", domain.hostname)
        assertEquals("192.168.1.1", domain.ipAddress)
        assertEquals("AA:BB:CC:DD:EE:FF", domain.macAddress)
        assertEquals("Cisco", domain.manufacturer)
        assertEquals("ISR 4000", domain.model)
        assertEquals("SN12345", domain.serialNumber)
        assertEquals("router", domain.deviceType)
        assertEquals("active", domain.status)
        assertEquals(mapOf("location" to "rack-1"), domain.metadata)
        assertEquals("2026-01-01T00:00:00Z", domain.createdAt)
        assertEquals("2026-01-02T00:00:00Z", domain.updatedAt)
    }

    @Test
    fun toDomainHandlesOptionalFieldsAsNull() {
        val dto =
            DeviceDto(
                id = "dev-2",
                hostname = "switch-01",
                deviceType = "switch",
                status = "pending",
            )

        val domain = DeviceMapper.toDomain(dto)

        assertEquals("dev-2", domain.id)
        assertNull(domain.ipAddress)
        assertNull(domain.macAddress)
        assertNull(domain.manufacturer)
        assertNull(domain.model)
        assertNull(domain.serialNumber)
        assertNull(domain.metadata)
    }

    @Test
    fun toPaginatedListMapsResponseCorrectly() {
        val response =
            DeviceListResponse(
                items =
                    listOf(
                        DeviceDto(id = "1", hostname = "d1", deviceType = "server", status = "active"),
                        DeviceDto(id = "2", hostname = "d2", deviceType = "switch", status = "inactive"),
                    ),
                total = 10,
                page = 2,
                perPage = 5,
            )

        val list = DeviceMapper.toPaginatedList(response)

        assertEquals(2, list.items.size)
        assertEquals("d1", list.items[0].hostname)
        assertEquals("d2", list.items[1].hostname)
        assertEquals(10, list.total)
        assertEquals(2, list.page)
        assertEquals(5, list.perPage)
    }
}
