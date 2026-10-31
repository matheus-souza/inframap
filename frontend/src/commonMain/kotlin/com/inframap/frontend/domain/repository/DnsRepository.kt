package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDnsRecordRequest
import com.inframap.frontend.data.dto.DeleteDnsRecordResponse
import com.inframap.frontend.data.dto.DnsRecordDto
import com.inframap.frontend.data.dto.UpdateDnsRecordRequest
import com.inframap.frontend.domain.model.DnsRecord
import com.inframap.frontend.domain.model.PaginatedList

interface DnsRepository {
    suspend fun getDnsRecords(): ApiResult<PaginatedList<DnsRecord>>

    suspend fun createDnsRecord(request: CreateDnsRecordRequest): ApiResult<DnsRecord>

    suspend fun updateDnsRecord(
        id: String,
        request: UpdateDnsRecordRequest,
    ): ApiResult<DnsRecord>

    suspend fun deleteDnsRecord(id: String): ApiResult<DeleteDnsRecordResponse>
}
