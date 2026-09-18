package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.repository.DiscoveryRepository
import com.inframap.frontend.domain.usecase.UseCase

data class UpdateDiscoverySourceParams(
    val id: String,
    val request: UpdateDiscoverySourceRequest,
)

class UpdateDiscoverySourceUseCase(
    private val discoveryRepository: DiscoveryRepository,
) : UseCase<UpdateDiscoverySourceParams, ApiResult<DiscoverySource>> {
    override suspend fun invoke(params: UpdateDiscoverySourceParams): ApiResult<DiscoverySource> {
        val validationError =
            when {
                params.id.isBlank() ->
                    ApiResult.Error(
                        code = "INVALID_ID",
                        message = "ID da fonte de descoberta é obrigatório",
                        requestId = "",
                        httpStatus = 400,
                    )
                params.request.name.isBlank() ->
                    ApiResult.Error(
                        code = "INVALID_NAME",
                        message = "Nome da fonte de descoberta é obrigatório",
                        requestId = "",
                        httpStatus = 400,
                    )
                else -> null
            }
        return validationError ?: discoveryRepository.updateSource(params.id, params.request)
    }
}
