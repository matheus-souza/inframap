package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.DeviceListResponse
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList

object DeviceMapper {
    fun toDomain(dto: DeviceDto): Device =
        Device(
            id = dto.id,
            hostname = dto.hostname,
            ipAddress = dto.ipAddress,
            macAddress = dto.macAddress,
            manufacturer = dto.manufacturer,
            model = dto.model,
            serialNumber = dto.serialNumber,
            deviceType = dto.deviceType,
            status = dto.status,
            powerState = dto.powerState?.takeIf { it.isNotBlank() },
            parentDeviceId = dto.parentDeviceId?.takeIf { it.isNotBlank() },
            metadata = dto.metadata,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )

    fun toPaginatedList(response: DeviceListResponse): PaginatedList<Device> =
        PaginatedList(
            items = response.devices.map { toDomain(it) },
            total = response.total,
            page = response.page,
            perPage = response.perPage,
        )
}
