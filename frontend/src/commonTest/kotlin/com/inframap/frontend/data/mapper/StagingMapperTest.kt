package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.StagingDeviceDto
import com.inframap.frontend.data.dto.StagingListResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class StagingMapperTest {
    @Test
    fun toDomainMapsAllFieldsCorrectly() {
        val dto =
            StagingDeviceDto(
                id = "stg-1",
                hostname = "discovered-host",
                ipAddress = "10.0.0.5",
                macAddress = "00:11:22:33:44:55",
                manufacturer = "Dell",
                model = "PowerEdge",
                deviceType = "server",
                discoverySourceId = "src-1",
                status = "pending",
                createdAt = "2026-01-01T00:00:00Z",
            )

        val domain = StagingMapper.toDomain(dto)

        assertEquals("stg-1", domain.id)
        assertEquals("discovered-host", domain.hostname)
        assertEquals("10.0.0.5", domain.ipAddress)
        assertEquals("00:11:22:33:44:55", domain.macAddress)
        assertEquals("Dell", domain.manufacturer)
        assertEquals("PowerEdge", domain.model)
        assertEquals("server", domain.deviceType)
        assertEquals("src-1", domain.discoverySourceId)
        assertEquals("pending", domain.status)
        assertEquals("2026-01-01T00:00:00Z", domain.createdAt)
    }

    @Test
    fun toPaginatedListMapsResponseCorrectly() {
        val response =
            StagingListResponse(
                items = listOf(StagingDeviceDto(id = "s1", hostname = "host1", deviceType = "server")),
                total = 1,
                page = 1,
                perPage = 10,
            )

        val list = StagingMapper.toPaginatedList(response)

        assertEquals(1, list.items.size)
        assertEquals("host1", list.items[0].hostname)
        assertEquals(1, list.total)
    }
}
