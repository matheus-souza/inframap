package com.inframap.frontend.domain.usecase.staging

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.usecase.UseCase

class GetStagingDevicesUseCase(
    private val stagingRepository: StagingRepository,
) : UseCase<GetStagingDevicesUseCase.Params, ApiResult<PaginatedList<StagingDevice>>> {
    data class Params(
        val page: Int = 1,
        val perPage: Int = 10,
    )

    override suspend fun invoke(params: Params): ApiResult<PaginatedList<StagingDevice>> =
        stagingRepository.getStagingDevices(page = params.page, perPage = params.perPage)

    suspend operator fun invoke(
        page: Int = 1,
        perPage: Int = 10,
    ): ApiResult<PaginatedList<StagingDevice>> = invoke(Params(page = page, perPage = perPage))
}
