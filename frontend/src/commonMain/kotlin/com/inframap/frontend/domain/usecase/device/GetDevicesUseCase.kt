package com.inframap.frontend.domain.usecase.device

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetDevicesUseCase(
    private val deviceRepository: DeviceRepository,
) : UseCase<GetDevicesUseCase.Params, ApiResult<PaginatedList<Device>>> {
    data class Params(
        val page: Int = 1,
        val perPage: Int = 10,
        val search: String = "",
    )

    override suspend fun invoke(params: Params): ApiResult<PaginatedList<Device>> =
        deviceRepository.getDevices(
            page = params.page,
            perPage = params.perPage,
            search = params.search,
        )

    suspend operator fun invoke(
        page: Int = 1,
        perPage: Int = 10,
        search: String? = null,
    ): ApiResult<PaginatedList<Device>> = invoke(Params(page = page, perPage = perPage, search = search ?: ""))
}
