package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.CollectorDto
import com.inframap.frontend.data.dto.CollectorRunDetailDto
import com.inframap.frontend.data.dto.CollectorRunSummaryDto
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.domain.model.CollectorRunDetail
import com.inframap.frontend.domain.model.CollectorRunSummary
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.SourceCollector

object DiscoveryMapper {
    fun toDomain(dto: DiscoverySourceDto): DiscoverySource {
        val mappedCollectors =
            if (dto.collectors.isNotEmpty()) {
                dto.collectors.map { toDomain(it) }
            } else if (dto.type.isNotBlank()) {
                listOf(SourceCollector(id = "", collectorType = dto.type, enabled = true))
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
            collectors = mappedCollectors,
            lastRunAt = dto.lastRunAt,
            lastStatus = dto.lastStatus,
            lastRun = dto.lastRun?.let { toDomain(it) },
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }

    fun toDomain(dto: CollectorDto): SourceCollector =
        SourceCollector(
            id = dto.id,
            collectorType = dto.collectorType,
            enabled = dto.enabled,
        )

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
}
