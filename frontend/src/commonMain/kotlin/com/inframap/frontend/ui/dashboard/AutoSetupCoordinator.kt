package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.dashboard.GetStagingSummaryUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import kotlinx.coroutines.delay

class AutoSetupCoordinator(
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    private val createSubnetUseCase: CreateSubnetUseCase,
    private val createDiscoverySourceUseCase: CreateDiscoverySourceUseCase,
    private val triggerDiscoveryRunUseCase: TriggerDiscoveryRunUseCase,
    private val getDiscoverySourcesUseCase: GetDiscoverySourcesUseCase,
    private val getStagingSummaryUseCase: GetStagingSummaryUseCase,
    private val localStorage: LocalStorage,
) {
    fun isDismissed(): Boolean = localStorage.get(KEY_DISMISSED) != null

    fun dismiss() {
        localStorage.set(KEY_DISMISSED, "true")
    }

    suspend fun detectInterfaces(): ApiResult<List<NetworkInterface>> = getNetworkInterfacesUseCase()

    @Suppress("ReturnCount")
    suspend fun createSubnets(interfaces: List<NetworkInterface>): ApiResult<Unit> {
        for (iface in interfaces) {
            val result =
                createSubnetUseCase(
                    CreateSubnetRequest(
                        name = iface.name,
                        cidr = iface.cidr,
                        gatewayIp = iface.gateway.ifEmpty { null },
                        discoveryEnabled = true,
                    ),
                )
            when (result) {
                is ApiResult.Success -> Unit
                is ApiResult.Error ->
                    if (result.httpStatus != CONFLICT_STATUS) return result
                is ApiResult.NetworkError -> return result
            }
        }
        return ApiResult.Success(Unit, "")
    }

    @Suppress("ReturnCount")
    suspend fun createSourcesAndTriggerScan(interfaces: List<NetworkInterface>): ApiResult<List<String>> {
        val sourceIds = mutableListOf<String>()
        for (iface in interfaces) {
            val result =
                createDiscoverySourceUseCase(
                    CreateDiscoverySourceRequest(
                        name = "Completo — ${iface.cidr}",
                        type = "full",
                        enabled = true,
                        scheduleCron = "0 * * * *",
                        config = mapOf("cidr" to iface.cidr),
                    ),
                )
            when (result) {
                is ApiResult.Success -> sourceIds.add(result.data.id)
                is ApiResult.Error ->
                    if (result.httpStatus != CONFLICT_STATUS) return result
                is ApiResult.NetworkError -> return result
            }
        }
        for (sourceId in sourceIds) {
            when (val result = triggerDiscoveryRunUseCase(sourceId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> return result
                is ApiResult.NetworkError -> return result
            }
        }
        return ApiResult.Success(sourceIds, "")
    }

    @Suppress("ReturnCount")
    suspend fun pollScanAndCount(sourceIds: List<String>): ApiResult<Int> {
        if (sourceIds.isEmpty()) return fetchStagingCount()
        var attempts = 0
        while (attempts < MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            attempts++
            when (val result = getDiscoverySourcesUseCase()) {
                is ApiResult.Success -> {
                    val sources = result.data.items.filter { it.id in sourceIds }
                    if (sources.any { it.lastStatus == "error" }) {
                        return ApiResult.Error(
                            code = "SCAN_ERROR",
                            message = "Erro durante a varredura.",
                            requestId = "",
                            httpStatus = 500,
                        )
                    }
                    if (sources.none { it.lastStatus == "running" }) {
                        return fetchStagingCount()
                    }
                }
                is ApiResult.Error -> return result
                is ApiResult.NetworkError -> return result
            }
        }
        return ApiResult.Error(
            code = "SCAN_TIMEOUT",
            message = "Tempo limite de varredura excedido.",
            requestId = "",
            httpStatus = 408,
        )
    }

    private suspend fun fetchStagingCount(): ApiResult<Int> =
        when (val result = getStagingSummaryUseCase()) {
            is ApiResult.Success -> ApiResult.Success(result.data.items.size, result.requestId)
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }

    companion object {
        const val KEY_DISMISSED = "inframap_auto_setup_dismissed"
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_POLL_ATTEMPTS = 60
        private const val CONFLICT_STATUS = 409
    }
}
