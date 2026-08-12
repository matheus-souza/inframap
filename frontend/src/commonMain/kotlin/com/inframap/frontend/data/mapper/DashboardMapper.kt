package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.DiscoverySourceDto
import com.inframap.frontend.data.dto.HealthDto
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.Health

object DashboardMapper {
    fun toDomain(dto: HealthDto): Health =
        Health(
            status = dto.status,
            version = dto.version,
        )

    fun toDomain(dto: DiscoverySourceDto): DiscoverySource = DiscoveryMapper.toDomain(dto)
}
