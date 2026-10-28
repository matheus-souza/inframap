package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.CollectorDto
import com.inframap.frontend.data.dto.CollectorRunDetailDto
import com.inframap.frontend.data.dto.CollectorRunSummaryDto
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.dto.DiscoveryTargetDto
import com.inframap.frontend.domain.model.CollectorRunDetail
import com.inframap.frontend.domain.model.CollectorRunSummary
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.SourceCollector
import kotlinx.serialization.json.JsonPrimitive

object DiscoveryMapper {
    fun toDomain(dto: DiscoverySourceDto): DiscoverySource {
        val mappedCollectors =
            if (dto.collectors.isNotEmpty()) {
                dto.collectors.map { toDomain(it) }
            } else if (dto.type.isNotBlank()) {
                listOf(SourceCollector(id = "", collectorType = dto.type, enabled = dto.enabled))
            } else {
                emptyList()
            }

        return DiscoverySource(
            id = dto.id,
            name = dto.name,
            sourceType = dto.type,
            enabled = dto.enabled,
            scheduleCron = dto.scheduleCron,
            configCidr = dto.configCidr,
            targets = toTargets(dto),
            collectors = mappedCollectors,
            lastRunAt = dto.lastRunAt,
            lastStatus = dto.lastStatus,
            lastRun = dto.lastRun?.let { toDomain(it) },
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }

    /**
     * The source's targets in order. A backend that predates RFC-026 sends only
     * `config_cidr`, which is read as a single target, matching the backend's own
     * compatible read (RFC-026 §1). Entries without a CIDR carry nothing to display and
     * are dropped.
     */
    private fun toTargets(dto: DiscoverySourceDto): List<DiscoveryTarget> {
        val targets = dto.targets.orEmpty().mapNotNull { toDomain(it) }
        val legacyCidr = dto.configCidr?.takeIf { it.isNotBlank() }
        return when {
            targets.isNotEmpty() -> targets
            legacyCidr != null -> listOf(DiscoveryTarget(cidr = legacyCidr))
            else -> emptyList()
        }
    }

    fun toDomain(dto: DiscoveryTargetDto): DiscoveryTarget? {
        val cidr = dto.cidr?.takeIf { it.isNotBlank() } ?: return null
        return DiscoveryTarget(
            cidr = cidr,
            subnetId = dto.subnetId,
            subnetName = dto.subnetName,
            subnetDeleted = dto.subnetDeleted ?: false,
        )
    }

    fun toDomain(dto: CollectorDto): SourceCollector {
        val stringConfig =
            dto.config?.mapValues { (_, value) ->
                if (value is JsonPrimitive) value.content else value.toString()
            } ?: emptyMap()

        return SourceCollector(
            id = dto.id,
            collectorType = dto.collectorType,
            enabled = dto.enabled,
            config = stringConfig,
            configuredSecrets = dto.configuredSecrets ?: emptyList(),
        )
    }

    fun toDomain(dto: CollectorRunSummaryDto): CollectorRunSummary =
        CollectorRunSummary(
            status = dto.status,
            collectors = dto.collectors.map { toDomain(it) },
        )

    fun toDomain(dto: CollectorRunDetailDto): CollectorRunDetail =
        CollectorRunDetail(
            collectorType = dto.collectorType,
            status = dto.status,
            devicesFound = dto.devicesFound,
            durationMs = dto.durationMs,
            errorMessage = dto.errorMessage,
            targetCidr = dto.targetCidr?.takeIf { it.isNotBlank() },
        )

    fun toPaginatedList(response: DiscoveryListResponse): PaginatedList<DiscoverySource> {
        val list = response.sources
        return PaginatedList(
            items = list.map { toDomain(it) },
            total = response.total,
            page = 1,
            perPage = list.size.coerceAtLeast(1),
        )
    }

    fun toDomain(
        dto: com.inframap.frontend.data.dto.DiscoverySourceDeletionImpactDto,
    ): com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact =
        com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact(
            collectorsHalted = dto.collectorsHalted,
            devicesUnlinked = dto.devicesUnlinked,
        )
}
