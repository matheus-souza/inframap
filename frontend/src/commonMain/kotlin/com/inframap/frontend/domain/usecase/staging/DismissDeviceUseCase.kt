package com.inframap.frontend.domain.usecase.staging

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.usecase.UseCase

class DismissDeviceUseCase(
    private val stagingRepository: StagingRepository,
) : UseCase<String, ApiResult<Unit>> {
    override suspend fun invoke(params: String): ApiResult<Unit> = stagingRepository.dismissDevice(params)
}
