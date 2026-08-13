package com.inframap.frontend.data.repository

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.map
import com.inframap.frontend.data.dto.NetworkInterfaceDto
import com.inframap.frontend.data.mapper.NetworkInterfaceMapper
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.repository.NetworkRepository

class NetworkRepositoryImpl(
    private val apiClient: ApiClient,
) : NetworkRepository {
    override suspend fun getInterfaces(): ApiResult<List<NetworkInterface>> =
        apiClient.get<List<NetworkInterfaceDto>>("/api/v1/network/interfaces").map { dtos ->
            dtos.map { NetworkInterfaceMapper.toDomain(it) }
        }
}
