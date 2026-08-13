package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.NetworkInterfaceDto
import com.inframap.frontend.domain.model.NetworkInterface

object NetworkInterfaceMapper {
    fun toDomain(dto: NetworkInterfaceDto): NetworkInterface =
        NetworkInterface(
            name = dto.name,
            ip = dto.ip,
            cidr = dto.cidr,
            mac = dto.mac,
            gateway = dto.gateway,
        )
}
