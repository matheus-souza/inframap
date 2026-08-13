package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList

object DiscoveryMapper {
    fun toDomain(dto: DiscoverySourceDto): DiscoverySource =
        DiscoverySource(
            id = dto.id,
            name = dto.name,
            sourceType = dto.type,
            enabled = dto.enabled,
            scheduleCron = dto.scheduleCron,
            configCidr = dto.configCidr,
            lastRunAt = dto.lastRunAt,
            lastStatus = dto.lastStatus,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )

    fun toPaginatedList(response: DiscoveryListResponse): PaginatedList<DiscoverySource> =
        PaginatedList(
            items = response.items.map { toDomain(it) },
            total = response.total,
            page = 1,
            perPage = response.items.size,
        )
}
