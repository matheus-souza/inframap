package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.SubnetDto
import com.inframap.frontend.data.dto.SubnetListResponse
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.toSummary

object SubnetMapper {
    fun toDomain(dto: SubnetDto): Subnet =
        Subnet(
            id = dto.id,
            name = dto.name,
            cidr = dto.cidr,
            vlanId = dto.vlanId,
            gatewayIp = dto.gatewayIp,
            description = dto.description,
            discoveryEnabled = dto.discoveryEnabled,
            createdAt = dto.createdAt,
        )

    fun toSummary(dto: SubnetDto): SubnetSummary = toDomain(dto).toSummary()

    fun toPaginatedList(response: SubnetListResponse): PaginatedList<Subnet> {
        val list = response.subnets
        return PaginatedList(
            items = list.map { toDomain(it) },
            total = response.total,
            page = 1,
            perPage = list.size.coerceAtLeast(1),
        )
    }
}
