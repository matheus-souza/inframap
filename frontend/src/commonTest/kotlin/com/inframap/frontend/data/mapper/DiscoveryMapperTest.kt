package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.CollectorDto
import com.inframap.frontend.data.dto.CollectorRunDetailDto
import com.inframap.frontend.data.dto.CollectorRunSummaryDto
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDeletionImpactDto
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.dto.DiscoveryTargetDto
import com.inframap.frontend.domain.model.DiscoveryTarget
import kotlinx.serialization.json.Json
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
    fun toDomainMapsDisabledLegacySourceWithDisabledCollector() {
        val dto =
            DiscoverySourceDto(
                id = "src-disabled",
                name = "Disabled Scanner",
                type = "icmp_sweep",
                enabled = false,
            )

        val domain = DiscoveryMapper.toDomain(dto)

        assertFalse(domain.enabled)
        assertEquals(1, domain.collectors.size)
        assertFalse(domain.collectors[0].enabled)
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
    fun toDomainMapsCollectorWithConfigAndConfiguredSecrets() {
        val dto =
            CollectorDto(
                id = "col-proxmox",
                collectorType = "proxmox",
                enabled = true,
                config =
                    mapOf(
                        "api_url" to kotlinx.serialization.json.JsonPrimitive("https://pve.local:8006"),
                        "token_id" to kotlinx.serialization.json.JsonPrimitive("root@pam!token"),
                        "tls_verify" to kotlinx.serialization.json.JsonPrimitive(true),
                    ),
                configuredSecrets = listOf("token_secret"),
            )
        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals("col-proxmox", domain.id)
        assertEquals("proxmox", domain.collectorType)
        assertTrue(domain.enabled)
        assertEquals("https://pve.local:8006", domain.config["api_url"])
        assertEquals("root@pam!token", domain.config["token_id"])
        assertEquals("true", domain.config["tls_verify"])
        assertEquals(listOf("token_secret"), domain.configuredSecrets)
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

    @Test
    fun toDomainMapsDiscoverySourceDeletionImpactDtoCorrectly() {
        val dto = DiscoverySourceDeletionImpactDto(collectorsHalted = 4, devicesUnlinked = 12)
        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals(4, domain.collectorsHalted)
        assertEquals(12, domain.devicesUnlinked)
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun toDomainMapsTargetsInOrderWhenPresent() {
        val dto =
            DiscoverySourceDto(
                id = "src-targets",
                configCidr = "10.0.10.0/24",
                targets =
                    listOf(
                        DiscoveryTargetDto(
                            cidr = "10.0.10.0/24",
                            subnetId = "subnet-1",
                            subnetName = "Servers",
                            subnetDeleted = false,
                        ),
                        DiscoveryTargetDto(cidr = "192.0.2.0/24"),
                        DiscoveryTargetDto(cidr = "198.51.100.0/24", subnetId = "subnet-2", subnetDeleted = true),
                    ),
            )

        val domain = DiscoveryMapper.toDomain(dto)

        assertEquals(
            listOf(
                DiscoveryTarget(cidr = "10.0.10.0/24", subnetId = "subnet-1", subnetName = "Servers"),
                DiscoveryTarget(cidr = "192.0.2.0/24"),
                DiscoveryTarget(cidr = "198.51.100.0/24", subnetId = "subnet-2", subnetDeleted = true),
            ),
            domain.targets,
        )
    }

    @Test
    fun toDomainFallsBackToConfigCidrWhenTargetsAreAbsent() {
        val absent = DiscoveryMapper.toDomain(DiscoverySourceDto(id = "src-legacy", configCidr = "10.0.0.0/24"))
        val empty =
            DiscoveryMapper.toDomain(
                DiscoverySourceDto(id = "src-legacy", configCidr = "10.0.0.0/24", targets = emptyList()),
            )

        assertEquals(listOf(DiscoveryTarget(cidr = "10.0.0.0/24")), absent.targets)
        assertEquals(listOf(DiscoveryTarget(cidr = "10.0.0.0/24")), empty.targets)
    }

    @Test
    fun toDomainHasNoTargetsWithoutTargetsOrConfigCidr() {
        assertTrue(DiscoveryMapper.toDomain(DiscoverySourceDto(id = "src-provider")).targets.isEmpty())
        assertTrue(DiscoveryMapper.toDomain(DiscoverySourceDto(id = "src-blank", configCidr = " ")).targets.isEmpty())
    }

    @Test
    fun toDomainDropsTargetsWithoutCidr() {
        val dto =
            DiscoverySourceDto(
                id = "src-partial",
                targets = listOf(DiscoveryTargetDto(subnetId = "subnet-1"), DiscoveryTargetDto(cidr = "192.0.2.0/24")),
            )

        assertEquals(listOf(DiscoveryTarget(cidr = "192.0.2.0/24")), DiscoveryMapper.toDomain(dto).targets)
        assertNull(DiscoveryMapper.toDomain(DiscoveryTargetDto(cidr = "")))
    }

    @Test
    fun toDomainMapsRunTargetCidr() {
        val withTarget =
            DiscoveryMapper.toDomain(
                CollectorRunDetailDto(collectorType = "icmp_sweep", status = "success", targetCidr = "192.0.2.0/24"),
            )
        val withoutTarget = DiscoveryMapper.toDomain(CollectorRunDetailDto(collectorType = "mdns", status = "success"))

        assertEquals("192.0.2.0/24", withTarget.targetCidr)
        assertNull(withoutTarget.targetCidr)
    }

    @Test
    fun decodesSourcePayloadWithTargetsAndRunTargetCidr() {
        val payload =
            """
            {
                "id": "src-1", "name": "LAN", "config_cidr": "10.0.10.0/24",
                "targets": [
                    {"cidr": "10.0.10.0/24", "subnet_id": "subnet-1", "subnet_name": "Servers", "subnet_deleted": false},
                    {"cidr": "192.0.2.0/24", "subnet_id": null, "subnet_name": null, "subnet_deleted": null}
                ],
                "last_run": {
                    "status": "partial",
                    "collectors": [
                        {"collector_type": "icmp_sweep", "status": "error", "target_cidr": "192.0.2.0/24"},
                        {"collector_type": "mdns", "status": "success", "target_cidr": null}
                    ]
                }
            }
            """.trimIndent()

        val domain = DiscoveryMapper.toDomain(json.decodeFromString<DiscoverySourceDto>(payload))

        assertEquals(
            listOf(
                DiscoveryTarget(cidr = "10.0.10.0/24", subnetId = "subnet-1", subnetName = "Servers"),
                DiscoveryTarget(cidr = "192.0.2.0/24"),
            ),
            domain.targets,
        )
        val rows = assertNotNull(domain.lastRun).collectors
        assertEquals("192.0.2.0/24", rows[0].targetCidr)
        assertNull(rows[1].targetCidr)
    }

    @Test
    fun decodesLegacySourcePayloadWithoutTargets() {
        val payload =
            """
            {
                "id": "src-1", "name": "LAN", "config_cidr": "10.0.10.0/24",
                "last_run": {"status": "idle", "collectors": [{"collector_type": "icmp_sweep", "status": "success"}]}
            }
            """.trimIndent()

        val domain = DiscoveryMapper.toDomain(json.decodeFromString<DiscoverySourceDto>(payload))

        assertEquals(listOf(DiscoveryTarget(cidr = "10.0.10.0/24")), domain.targets)
        assertNull(assertNotNull(domain.lastRun).collectors.single().targetCidr)
    }
}
