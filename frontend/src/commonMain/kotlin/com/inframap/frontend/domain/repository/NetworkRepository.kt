package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.NetworkInterface

interface NetworkRepository {
    suspend fun getInterfaces(): ApiResult<List<NetworkInterface>>
}
