package com.inframap.frontend.domain.usecase.network

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.repository.NetworkRepository
import com.inframap.frontend.domain.usecase.NoParamUseCase

class GetNetworkInterfacesUseCase(
    private val networkRepository: NetworkRepository,
) : NoParamUseCase<ApiResult<List<NetworkInterface>>> {
    override suspend fun invoke(): ApiResult<List<NetworkInterface>> = networkRepository.getInterfaces()
}
