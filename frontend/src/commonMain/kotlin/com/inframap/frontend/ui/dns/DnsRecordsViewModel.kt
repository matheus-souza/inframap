package com.inframap.frontend.ui.dns

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DnsRecord
import com.inframap.frontend.domain.usecase.dns.GetDnsRecordsUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.dns_records_error_load
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class DnsRecordsViewModel(
    private val getDnsRecordsUseCase: GetDnsRecordsUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<DnsRecordsUiState>(DnsRecordsUiState(), scope = scope) {
    init {
        load()
    }

    fun load() {
        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("fetch") {
            when (val result = getDnsRecordsUseCase(Unit)) {
                is ApiResult.Success -> {
                    val records =
                        result.data.items?.map { dto ->
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
                        } ?: emptyList()
                    updateState {
                        it.copy(
                            records = records,
                            totalItems = result.data.total.toInt(),
                            currentPage = result.data.page,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.dns_records_error_load)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.dns_records_error_load)),
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        load()
    }
}
