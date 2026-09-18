package com.inframap.frontend.domain.repository

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.ProviderHealth

interface DiscoveryRepository {
    suspend fun getSources(): ApiResult<PaginatedList<DiscoverySource>>

    suspend fun getSourceById(id: String): ApiResult<DiscoverySource>

    suspend fun createSource(request: CreateDiscoverySourceRequest): ApiResult<DiscoverySource>

    suspend fun updateSource(
        id: String,
        request: UpdateDiscoverySourceRequest,
    ): ApiResult<DiscoverySource>

    suspend fun triggerRun(sourceId: String): ApiResult<DiscoverySource>

    suspend fun deleteSource(sourceId: String): ApiResult<Unit>

    suspend fun testSourceHealth(
        id: String,
        providerId: String? = null,
    ): ApiResult<ProviderHealth>

    suspend fun getDeletionImpact(id: String): ApiResult<DiscoverySourceDeletionImpact>
}
