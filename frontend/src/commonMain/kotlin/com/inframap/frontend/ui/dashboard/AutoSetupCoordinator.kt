package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.DiscoverySourceConfigDto
import com.inframap.frontend.data.dto.DiscoveryTargetRequestDto
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.usecase.dashboard.GetStagingSummaryUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.ui.discovery.DiscoveryTargetRules
import com.inframap.frontend.ui.discovery.toRequestTargets
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

    /**
     * Creates one Subnet per interface. Returns the id of each Subnet created here, keyed by
     * its CIDR; a CIDR already registered (409) is left out, so its target stays free-form.
     */
    @Suppress("ReturnCount")
    suspend fun createSubnets(interfaces: List<NetworkInterface>): ApiResult<Map<String, String>> {
        val subnetIds = mutableMapOf<String, String>()
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
                is ApiResult.Success -> subnetIds[iface.cidr] = result.data.id
                is ApiResult.Error ->
                    if (result.httpStatus != CONFLICT_STATUS) return result
                is ApiResult.NetworkError -> return result
            }
        }
        return ApiResult.Success(subnetIds, "")
    }

    /**
     * Creates the single "Local network sweep" source with one target per interface
     * (RFC-026 §8) and triggers its first run. Returns the ids of the sources to poll: empty
     * when there is no usable interface, or when the source already exists (409) because
     * auto-setup ran before; an existing installation is never changed.
     */
    @Suppress("ReturnCount")
    suspend fun createSourceAndTriggerScan(
        interfaces: List<NetworkInterface>,
        subnetIds: Map<String, String> = emptyMap(),
    ): ApiResult<List<String>> {
        val targets = localNetworkTargets(interfaces, subnetIds)
        if (targets.isEmpty()) return ApiResult.Success(emptyList(), "")
        val result =
            createDiscoverySourceUseCase(
                CreateDiscoverySourceRequest(
                    name = LOCAL_NETWORK_SOURCE_NAME,
                    type = "icmp_sweep",
                    enabled = true,
                    scheduleCron = "0 * * * *",
                    config = DiscoverySourceConfigDto(targets = targets),
                ),
            )
        val sourceId =
            when (result) {
                is ApiResult.Success -> result.data.id
                is ApiResult.Error ->
                    return if (result.httpStatus == CONFLICT_STATUS) ApiResult.Success(emptyList(), "") else result
                is ApiResult.NetworkError -> return result
            }
        return when (val trigger = triggerDiscoveryRunUseCase(sourceId)) {
            is ApiResult.Success -> ApiResult.Success(listOf(sourceId), "")
            is ApiResult.Error -> trigger
            is ApiResult.NetworkError -> trigger
        }
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
        /**
         * The targets of the local network source, in interface order. A target is linked to
         * the Subnet auto-setup created for its CIDR and free-form otherwise. An interface the
         * source cannot hold is skipped rather than failing the whole setup: a CIDR wider than
         * /16, one overlapping an earlier interface, or one past the 32-target or
         * 65 536-address limits of RFC-026 §2. The operator can add it by hand afterwards.
         */
        internal fun localNetworkTargets(
            interfaces: List<NetworkInterface>,
            subnetIds: Map<String, String>,
        ): List<DiscoveryTargetRequestDto> {
            val targets = mutableListOf<DiscoveryTarget>()
            for (iface in interfaces) {
                val candidate = DiscoveryTarget(cidr = iface.cidr.trim(), subnetId = subnetIds[iface.cidr])
                val violation = DiscoveryTargetRules.checkAddition(targets, candidate)
                if (violation == null) {
                    targets += candidate
                } else {
                    println("[InfraMap-AutoSetup] [WARN] Skipping ${iface.name} (${iface.cidr}): ${violation.code}")
                }
            }
            return targets.toRequestTargets()
        }

        const val KEY_DISMISSED = "inframap_auto_setup_dismissed"

        // Stored server-side as the source name, so it is not resolved from UI string resources.
        internal const val LOCAL_NETWORK_SOURCE_NAME = "Local network sweep"
        private const val POLL_INTERVAL_MS = 3000L
        private const val MAX_POLL_ATTEMPTS = 60
        private const val CONFLICT_STATUS = 409
    }
}
