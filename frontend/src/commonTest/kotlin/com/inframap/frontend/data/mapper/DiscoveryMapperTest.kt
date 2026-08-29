package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.CollectorDto
import com.inframap.frontend.data.dto.CollectorRunDetailDto
import com.inframap.frontend.data.dto.CollectorRunSummaryDto
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryMapperTest {
    @Test
    fun toDomainMapsAllFieldsCorrectlyWithCollectorsFallback() {
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
        assertEquals(1, domain.collectors.size)
        assertEquals("", domain.collectors[0].id)
        assertEquals("docker", domain.collectors[0].collectorType)
        assertTrue(domain.collectors[0].enabled)
        assertNull(domain.lastRun)
    }

    @Test
    fun toDomainMapsExplicitCollectorsListCorrectly() {
        val collectorsDto =
            listOf(
                CollectorDto(id = "col-1", collectorType = "icmp_sweep", enabled = true),
                CollectorDto(id = "col-2", collectorType = "arp_sweep", enabled = false),
                CollectorDto(id = "col-3", collectorType = "mdns", enabled = true),
            )
        val dto =
            DiscoverySourceDto(
                id = "src-2",
                name = "Multi-Scan Source",
                type = "icmp_sweep",
                enabled = true,
                _collectors = collectorsDto,
            )

        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals(3, domain.collectors.size)
        assertEquals("col-1", domain.collectors[0].id)
        assertEquals("icmp_sweep", domain.collectors[0].collectorType)
        assertTrue(domain.collectors[0].enabled)

        assertEquals("col-2", domain.collectors[1].id)
        assertEquals("arp_sweep", domain.collectors[1].collectorType)
        assertFalse(domain.collectors[1].enabled)

        assertEquals("col-3", domain.collectors[2].id)
        assertEquals("mdns", domain.collectors[2].collectorType)
        assertTrue(domain.collectors[2].enabled)
    }

    @Test
    fun toDomainMapsNewOnlyPayloadCorrectly() {
        val collectorsDto =
            listOf(
                CollectorDto(id = "col-docker", collectorType = "docker", enabled = true),
                CollectorDto(id = "col-proxmox", collectorType = "proxmox", enabled = true),
            )
        val dto =
            DiscoverySourceDto(
                id = "src-new",
                name = "New Plan Source",
                type = "",
                _collectors = collectorsDto,
            )

        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals("", domain.sourceType)
        assertEquals(2, domain.collectors.size)
        assertEquals("col-docker", domain.collectors[0].id)
        assertEquals("docker", domain.collectors[0].collectorType)
        assertEquals("col-proxmox", domain.collectors[1].id)
        assertEquals("proxmox", domain.collectors[1].collectorType)
    }

    @Test
    fun toDomainMapsLastRunCorrectly() {
        val lastRunDto =
            CollectorRunSummaryDto(
                status = "partial",
                _collectors =
                    listOf(
                        CollectorRunDetailDto(
                            collectorType = "icmp_sweep",
                            status = "success",
                            devicesFound = 5,
                            durationMs = 1200L,
                        ),
                        CollectorRunDetailDto(
                            collectorType = "snmp",
                            status = "error",
                            devicesFound = 0,
                            durationMs = 500L,
                            errorMessage = "SNMP timeout",
                        ),
                    ),
            )
        val dto =
            DiscoverySourceDto(
                id = "src-run",
                name = "Source with Run",
                type = "icmp_sweep",
                lastRun = lastRunDto,
            )

        val domain = DiscoveryMapper.toDomain(dto)
        val lastRun = domain.lastRun

        assertNotNull(lastRun)
        assertEquals("partial", lastRun.status)
        assertEquals(2, lastRun.collectors.size)
        assertEquals("icmp_sweep", lastRun.collectors[0].collectorType)
        assertEquals("success", lastRun.collectors[0].status)
        assertEquals(5, lastRun.collectors[0].devicesFound)
        assertEquals(1200L, lastRun.collectors[0].durationMs)
        assertNull(lastRun.collectors[0].errorMessage)

        assertEquals("snmp", lastRun.collectors[1].collectorType)
        assertEquals("error", lastRun.collectors[1].status)
        assertEquals(0, lastRun.collectors[1].devicesFound)
        assertEquals(500L, lastRun.collectors[1].durationMs)
        assertEquals("SNMP timeout", lastRun.collectors[1].errorMessage)
    }

    @Test
    fun toDomainHandlesEmptyCollectorsAndEmptyType() {
        val dto =
            DiscoverySourceDto(
                id = "src-3",
                name = "Empty Source",
                type = "",
            )

        val domain = DiscoveryMapper.toDomain(dto)

        assertTrue(domain.collectors.isEmpty())
    }

    @Test
    fun toDomainMapsSingleCollectorDtoCorrectly() {
        val dto = CollectorDto(id = "col-x", collectorType = "snmp", enabled = true)
        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals("col-x", domain.id)
        assertEquals("snmp", domain.collectorType)
        assertTrue(domain.enabled)
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
