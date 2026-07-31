package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.SubnetDto
import com.inframap.frontend.data.dto.SubnetListResponse
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet

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

    fun toPaginatedList(response: SubnetListResponse): PaginatedList<Subnet> =
        PaginatedList(
            items = response.items.map { toDomain(it) },
            total = response.total,
            page = 1,
            perPage = response.items.size,
        )
}
