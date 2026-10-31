package com.inframap.frontend.data.mapper

import com.inframap.frontend.data.dto.CreateDnsRecordRequest
import com.inframap.frontend.data.dto.DnsRecordDto
import com.inframap.frontend.data.dto.DnsRecordListResponse
import com.inframap.frontend.data.dto.UpdateDnsRecordRequest
import com.inframap.frontend.domain.model.DnsRecord

object DnsMapper {
    fun toDomain(dto: DnsRecordDto): DnsRecord =
        DnsRecord(
            id = dto.id,
            name = dto.name,
            recordType = dto.recordType,
            deviceId = dto.deviceId,
            address = dto.address,
            target = dto.target,
            notes = dto.notes,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )

    fun toPaginatedList(dto: DnsRecordListResponse): com.inframap.frontend.domain.model.PaginatedList<DnsRecord> =
        com.inframap.frontend.domain.model.PaginatedList(
            items = (dto.items ?: emptyList()).map { toDomain(it) },
            total = dto.total.toLong(),
        )

    fun toCreateRequest(model: DnsRecord): CreateDnsRecordRequest =
        CreateDnsRecordRequest(
            name = model.name,
            recordType = model.recordType,
            deviceId = model.deviceId,
            address = model.address,
            target = model.target,
            notes = model.notes,
        )

    fun toUpdateRequest(model: DnsRecord): UpdateDnsRecordRequest =
        UpdateDnsRecordRequest(
            name = model.name,
            recordType = model.recordType,
            deviceId = model.deviceId,
            address = model.address,
            target = model.target,
            notes = model.notes,
        )
}
