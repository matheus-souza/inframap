package com.inframap.frontend.domain.usecase.dns

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DnsRecord
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DnsRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetDnsRecordsUseCase(
    private val dnsRepository: DnsRepository,
) : UseCase<Unit, ApiResult<PaginatedList<DnsRecord>>> {
    override suspend fun invoke(params: Unit): ApiResult<PaginatedList<DnsRecord>> {
        return dnsRepository.getDnsRecords()
    }
}
