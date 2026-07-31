package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.UseCase

class CreateSubnetUseCase(
    private val subnetRepository: SubnetRepository,
) : UseCase<CreateSubnetRequest, ApiResult<Subnet>> {
    companion object {
        private val CIDR_REGEX = Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}\\/([0-9]|[12][0-9]|3[0-2])$")
        private const val MIN_VLAN_ID = 1
        private const val MAX_VLAN_ID = 4094
        private const val MAX_OCTET = 255
    }

    override suspend fun invoke(params: CreateSubnetRequest): ApiResult<Subnet> {
        val validationError = validate(params)
        if (validationError != null) {
            return validationError
        }
        return subnetRepository.createSubnet(params)
    }

    private fun validate(params: CreateSubnetRequest): ApiResult.Error? {
        val vlan = params.vlanId
        val cidr = params.cidr.trim()
        val match = CIDR_REGEX.matches(cidr)
        val validOctets =
            match &&
                cidr.substringBefore('/').split('.').all { octet ->
                    octet.toIntOrNull()?.let { it in 0..MAX_OCTET } ?: false
                }

        return when {
            params.name.isBlank() ->
                ApiResult.Error(
                    code = "INVALID_NAME",
                    message = "Subnet name cannot be empty",
                    requestId = "",
                    httpStatus = 400,
                )
            !validOctets ->
                ApiResult.Error(
                    code = "INVALID_CIDR",
                    message = "Invalid CIDR format (e.g. 192.168.1.0/24)",
                    requestId = "",
                    httpStatus = 400,
                )
            vlan != null && vlan !in MIN_VLAN_ID..MAX_VLAN_ID ->
                ApiResult.Error(
                    code = "INVALID_VLAN",
                    message = "VLAN ID must be between 1 and 4094",
                    requestId = "",
                    httpStatus = 400,
                )
            else -> null
        }
    }
}
