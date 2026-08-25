package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.StagingDeviceDto
import com.inframap.frontend.data.dto.StagingListResponse
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice

object StagingMapper {
    fun toDomain(dto: StagingDeviceDto): StagingDevice =
        StagingDevice(
            id = dto.id,
            hostname = dto.hostname,
            ipAddress = dto.ipAddress,
            macAddress = dto.macAddress,
            manufacturer = dto.manufacturer,
            model = dto.model,
            deviceType = dto.deviceType,
            discoverySourceId = dto.discoverySourceId,
            status = dto.status,
            createdAt = dto.createdAt,
        )

    fun toPaginatedList(response: StagingListResponse): PaginatedList<StagingDevice> =
        PaginatedList(
            items = response.devices.map { toDomain(it) },
            total = response.total,
            page = response.page,
            perPage = response.perPage,
        )
}
