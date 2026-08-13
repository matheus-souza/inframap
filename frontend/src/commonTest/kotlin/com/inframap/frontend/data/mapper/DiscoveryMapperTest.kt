package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiscoveryMapperTest {
    @Test
    fun toDomainMapsAllFieldsCorrectly() {
        val dto =
            DiscoverySourceDto(
                id = "src-1",
                name = "Docker Scanner",
                type = "docker",
                enabled = true,
                scheduleCron = "0 */6 * * *",
                configCidr = "172.17.0.0/16",
                lastRunAt = "2026-07-20T10:00:00Z",
                lastStatus = "completed",
                createdAt = "2026-07-01T00:00:00Z",
                updatedAt = "2026-07-20T10:05:00Z",
            )

        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals("src-1", domain.id)
        assertEquals("Docker Scanner", domain.name)
        assertEquals("docker", domain.sourceType)
        assertTrue(domain.enabled)
        assertEquals("0 */6 * * *", domain.scheduleCron)
        assertEquals("172.17.0.0/16", domain.configCidr)
        assertEquals("2026-07-20T10:00:00Z", domain.lastRunAt)
        assertEquals("completed", domain.lastStatus)
        assertEquals("2026-07-01T00:00:00Z", domain.createdAt)
        assertEquals("2026-07-20T10:05:00Z", domain.updatedAt)
    }

    @Test
    fun toPaginatedListMapsResponseCorrectly() {
        val response =
            DiscoveryListResponse(
                items =
                    listOf(
                        DiscoverySourceDto(id = "s1", name = "Source 1"),
                        DiscoverySourceDto(id = "s2", name = "Source 2"),
                    ),
                total = 5,
            )

        val list = DiscoveryMapper.toPaginatedList(response)

        assertEquals(2, list.items.size)
        assertEquals("Source 1", list.items[0].name)
        assertEquals("Source 2", list.items[1].name)
        assertEquals(5, list.total)
        assertEquals(1, list.page)
        assertEquals(2, list.perPage)
    }

    @Test
    fun toPaginatedListHandlesEmptyResponse() {
        val response = DiscoveryListResponse(items = emptyList(), total = 0)

        val list = DiscoveryMapper.toPaginatedList(response)

        assertTrue(list.items.isEmpty())
        assertEquals(0, list.total)
    }
}
