package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.UpdateSubnetRequest
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class UpdateSubnetUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<UpdateSubnetUseCase.Params, ApiResult<Subnet>> {
    data class Params(
        val id: String,
        val request: UpdateSubnetRequest,
    )

    override suspend fun invoke(params: Params): ApiResult<Subnet> =
        subnetRepository.updateSubnet(
            id = params.id,
            request = params.request,
        )

    @Suppress("LongParameterList")
    suspend operator fun invoke(
        id: String,
        name: String,
        cidr: String,
        vlanId: Int? = null,
        gatewayIp: String? = null,
        description: String? = null,
        discoveryEnabled: Boolean? = null,
    ): ApiResult<Subnet> =
        invoke(
            Params(
                id = id,
                request =
                    UpdateSubnetRequest(
                        name = name,
                        cidr = cidr,
                        vlanId = vlanId,
                        gatewayIp = gatewayIp,
                        description = description,
                        discoveryEnabled = discoveryEnabled,
                    ),
            ),
        )
}
