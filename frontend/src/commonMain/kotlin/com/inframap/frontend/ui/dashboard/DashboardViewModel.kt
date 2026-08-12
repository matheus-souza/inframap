package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.dashboard.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetHealthUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import com.inframap.frontend.ui.util.getCurrentTimeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Suppress("LongParameterList")
class DashboardViewModel(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val getStagingDevicesUseCase: GetStagingDevicesUseCase,
    private val getHealthUseCase: GetHealthUseCase,
    private val getDiscoverySourcesUseCase: GetDiscoverySourcesUseCase,
    private val sseClient: SSEClient? = null,
    private val getSubnetsUseCase: GetSubnetsUseCase? = null,
    private val autoRefreshIntervalMs: Long = 30_000L,
    private val timestampProvider: () -> String = { getCurrentTimeString() },
    scope: CoroutineScope? = null,
) : BaseViewModel<DashboardUiState>(DashboardUiState(), scope) {
    private var metricsGeneration = 0L

    init {
        loadData()
        startAutoRefresh()
        startSseListening()
    }

    fun loadData() {
        metricsGeneration++
        updateState { it.copy(isLoading = true, errorMessage = null) }
        triggerFetchMetrics()
    }

    fun refresh() {
        loadData()
    }

    private fun triggerFetchMetrics() {
        launchJob("fetch_metrics") {
            fetchMetrics()
        }
    }

    fun stopAutoRefresh() {
        cancelJob("auto_refresh")
    }

    fun stopSseListening() {
        cancelJob("sse_listening")
        sseClient?.disconnect()
    }

    @Suppress("LongMethod")
    private suspend fun fetchMetrics() {
        val generation = metricsGeneration
        coroutineScope {
            val devicesDeferred = async { getDevicesUseCase(page = 1, perPage = 5) }
            val stagingDeferred = async { getStagingDevicesUseCase(page = 1, perPage = 1) }
            val healthDeferred = async { getHealthUseCase() }
            val sourcesDeferred = async { getDiscoverySourcesUseCase() }
            val subnetsDeferred = async { getSubnetsUseCase?.invoke() }

            val devicesResult = devicesDeferred.await()
            val stagingResult = stagingDeferred.await()
            val healthResult = healthDeferred.await()
            val sourcesResult = sourcesDeferred.await()
            val subnetsResult = subnetsDeferred.await()

            if (generation != metricsGeneration) return@coroutineScope

            val results = listOfNotNull(devicesResult, stagingResult, healthResult, sourcesResult, subnetsResult)

            val networkError = results.firstOrNull { it is ApiResult.NetworkError }
            if (networkError != null) {
                handleMetricsError(
                    mapError(networkError, UiText.Resource(Res.string.dashboard_error_load)),
                    healthResult,
                )
                return@coroutineScope
            }

            val errorResult = results.firstOrNull { it is ApiResult.Error }
            if (errorResult != null) {
                handleMetricsError(
                    mapError(errorResult, UiText.Resource(Res.string.dashboard_error_load)),
                    healthResult,
                )
                return@coroutineScope
            }

            val devicesData = (devicesResult as? ApiResult.Success)?.data
            val activeTotal = devicesData?.total ?: 0L
            val recentList = devicesData?.items ?: emptyList()
            val onlineCount =
                if (recentList.isNotEmpty()) {
                    val activeCount =
                        recentList.count {
                            it.status.equals("ACTIVE", ignoreCase = true) ||
                                it.status.equals("ONLINE", ignoreCase = true)
                        }
                    (activeTotal * activeCount) / recentList.size
                } else {
                    activeTotal
                }

            val stagedTotal = (stagingResult as? ApiResult.Success)?.data?.total ?: 0L
            val healthData = (healthResult as? ApiResult.Success)?.data
            val sourcesTotal = (sourcesResult as? ApiResult.Success)?.data?.size?.toLong() ?: 0L
            val subnetsData = (subnetsResult as? ApiResult.Success)?.data
            val subnetsTotal = subnetsData?.total ?: subnetsData?.items?.size?.toLong() ?: sourcesTotal

            updateState {
                it.copy(
                    totalActiveDevices = activeTotal,
                    onlineDevicesCount = onlineCount,
                    totalSubnetsMonitored = subnetsTotal,
                    totalStagedDevices = stagedTotal,
                    recentDevices = recentList,
                    isSystemHealthy = healthData?.isHealthy,
                    systemVersion = healthData?.version ?: "",
                    totalDiscoverySources = sourcesTotal,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun handleMetricsError(
        errorMsg: UiText,
        healthResult: ApiResult<*>?,
    ) {
        val healthData =
            (healthResult as? ApiResult.Success)
                ?.data as? com.inframap.frontend.domain.model.Health
        updateState {
            it.copy(
                isLoading = false,
                errorMessage = errorMsg,
                isSystemHealthy = healthData?.isHealthy ?: it.isSystemHealthy,
                systemVersion = healthData?.version ?: it.systemVersion,
            )
        }
    }

    private fun startAutoRefresh() {
        if (autoRefreshIntervalMs <= 0) return
        launchJob("auto_refresh") {
            while (isActive) {
                delay(autoRefreshIntervalMs)
                fetchHealth()
            }
        }
    }

    suspend fun fetchHealth() {
        when (val healthResult = getHealthUseCase()) {
            is ApiResult.Success -> {
                updateState {
                    it.copy(
                        isSystemHealthy = healthResult.data.isHealthy,
                        systemVersion = healthResult.data.version,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun addLiveEvent(
        eventType: String,
        message: String,
    ) {
        val timestamp = timestampProvider()
        val eventId = "evt_${metricsGeneration}_${state.value.liveEvents.size + 1}"
        val newEvent =
            DashboardEventItem(
                id = eventId,
                timestamp = timestamp,
                eventType = eventType,
                message = message,
            )
        updateState {
            it.copy(
                liveEvents = (listOf(newEvent) + it.liveEvents).take(MAX_LIVE_EVENTS),
            )
        }
    }

    private fun startSseListening() {
        val client = sseClient ?: return
        launchJob("sse_listening") {
            while (isActive) {
                client.connect("/api/v1/events").collect { event ->
                    handleSseEvent(event)
                }
            }
        }
    }

    private suspend fun handleSseEvent(event: SSEEvent) {
        when (event) {
            is SSEEvent.DiscoveryProgress -> {
                addLiveEvent("DiscoveryProgress", event.data.ifBlank { "Discovery scan in progress..." })
                updateState { it.copy(discoveryEngineStatus = DiscoveryEngineStatus.RUNNING) }
                triggerFetchMetrics()
            }
            is SSEEvent.DeviceCreated -> {
                addLiveEvent("DeviceCreated", event.data.ifBlank { "New device registered on network" })
                triggerFetchMetrics()
            }
            is SSEEvent.DeviceUpdated -> {
                addLiveEvent("DeviceUpdated", event.data.ifBlank { "Device state or metadata updated" })
                triggerFetchMetrics()
            }
            is SSEEvent.TopologyUpdated -> {
                addLiveEvent("TopologyUpdated", event.data.ifBlank { "Topology graph map updated" })
                triggerFetchMetrics()
            }
            is SSEEvent.SystemNotification -> {
                addLiveEvent("SystemNotification", event.data.ifBlank { "System event logged" })
            }
            is SSEEvent.Connected -> {
                addLiveEvent("Connected", "Connected to live SSE stream")
            }
            is SSEEvent.Disconnected -> {
                addLiveEvent("Disconnected", "Disconnected from live SSE stream")
                delay(RECONNECT_DELAY_MS)
                startSseListening()
            }
            is SSEEvent.Unknown -> {
                addLiveEvent(event.type, event.data.ifBlank { "System event" })
            }
        }
    }

    companion object {
        private const val MAX_LIVE_EVENTS = 50
        private const val RECONNECT_DELAY_MS = 5000L
    }
}
