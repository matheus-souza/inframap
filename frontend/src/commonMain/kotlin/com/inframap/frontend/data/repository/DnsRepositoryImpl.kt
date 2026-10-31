package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.CreateDnsRecordRequest
import com.inframap.frontend.data.dto.DeleteDnsRecordResponse
import com.inframap.frontend.data.dto.DnsRecordDto
import com.inframap.frontend.data.dto.DnsRecordListResponse
import com.inframap.frontend.data.dto.UpdateDnsRecordRequest
import com.inframap.frontend.data.mapper.DnsMapper
import com.inframap.frontend.domain.model.DnsRecord
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DnsRepository

class DnsRepositoryImpl(
    private val apiClient: ApiClient,
) : DnsRepository {
    override suspend fun getDnsRecords(): ApiResult<PaginatedList<DnsRecord>> {
        val result = apiClient.get<DnsRecordListResponse>("/api/v1/dns/records")
        return when (result) {
            is ApiResult.Success ->
                ApiResult.Success(
                    data = DnsMapper.toPaginatedList(result.data),
                    requestId = result.requestId,
                )
            is ApiResult.Error ->
                ApiResult.Error(
                    code = result.code,
                    message = result.message,
                    requestId = result.requestId,
                    httpStatus = result.httpStatus,
                    detailsJson = result.detailsJson,
                )
            is ApiResult.NetworkError ->
                ApiResult.NetworkError(
                    throwable = result.throwable,
                )
        }
    }

    override suspend fun createDnsRecord(request: CreateDnsRecordRequest): ApiResult<DnsRecord> =
        apiClient
            .post<DnsRecordDto, CreateDnsRecordRequest>("/api/v1/dns/records", request)
            .let { result: ApiResult<DnsRecordDto> -> result.map { DnsMapper.toDomain(it) } }

    override suspend fun updateDnsRecord(
        id: String,
        request: UpdateDnsRecordRequest,
    ): ApiResult<DnsRecord> =
        apiClient
            .put<DnsRecordDto, UpdateDnsRecordRequest>("/api/v1/dns/records/$id", request)
            .let { result: ApiResult<DnsRecordDto> -> result.map { DnsMapper.toDomain(it) } }

    override suspend fun deleteDnsRecord(id: String): ApiResult<DeleteDnsRecordResponse> =
        apiClient.delete<DeleteDnsRecordResponse>("/api/v1/dns/records/$id")
}
