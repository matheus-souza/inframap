package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.dashboard.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetHealthUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class DashboardViewModel(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val getStagingDevicesUseCase: GetStagingDevicesUseCase,
    private val getHealthUseCase: GetHealthUseCase,
    private val getDiscoverySourcesUseCase: GetDiscoverySourcesUseCase,
    private val sseClient: SSEClient? = null,
    private val autoRefreshIntervalMs: Long = 30_000L,
    scope: CoroutineScope? = null,
) : BaseViewModel<DashboardUiState>(DashboardUiState(), scope) {
    private var autoRefreshJob: Job? = null
    private var sseJob: Job? = null

    init {
        loadData()
        startAutoRefresh()
        startSseListening()
    }

    fun loadData() {
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
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun stopSseListening() {
        sseJob?.cancel()
        sseJob = null
        sseClient?.disconnect()
    }

    override fun clear() {
        super.clear()
        stopAutoRefresh()
        stopSseListening()
    }

    @Suppress("LongMethod")
    private suspend fun fetchMetrics() =
        coroutineScope {
            val devicesDeferred = async { getDevicesUseCase(page = 1, perPage = 1) }
            val stagingDeferred = async { getStagingDevicesUseCase(page = 1, perPage = 1) }
            val healthDeferred = async { getHealthUseCase() }
            val sourcesDeferred = async { getDiscoverySourcesUseCase() }

            val devicesResult = devicesDeferred.await()
            val stagingResult = stagingDeferred.await()
            val healthResult = healthDeferred.await()
            val sourcesResult = sourcesDeferred.await()

            val results = listOf(devicesResult, stagingResult, healthResult, sourcesResult)

            if (results.any { it is ApiResult.NetworkError }) {
                handleMetricsNetworkError(devicesResult)
                return@coroutineScope
            }

            if (results.any { it is ApiResult.Error }) {
                handleMetricsApiError(results)
                return@coroutineScope
            }

            val activeTotal = (devicesResult as? ApiResult.Success)?.data?.total ?: 0L
            val stagedTotal = (stagingResult as? ApiResult.Success)?.data?.total ?: 0L
            val healthData = (healthResult as? ApiResult.Success)?.data
            val sourcesTotal = (sourcesResult as? ApiResult.Success)?.data?.size?.toLong() ?: 0L

            updateState {
                it.copy(
                    totalActiveDevices = activeTotal,
                    totalStagedDevices = stagedTotal,
                    isSystemHealthy = healthData?.isHealthy,
                    systemVersion = healthData?.version ?: "",
                    totalDiscoverySources = sourcesTotal,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }

    private fun handleMetricsNetworkError(representative: ApiResult<*>) {
        updateState {
            it.copy(
                isLoading = false,
                errorMessage = mapError(representative, UiText.Resource(Res.string.dashboard_error_load)),
            )
        }
    }

    private fun handleMetricsApiError(results: List<ApiResult<*>>) {
        val errorResult = results.filterIsInstance<ApiResult.Error>().firstOrNull()
        val errorMsg =
            errorResult?.let { mapError(it, UiText.Resource(Res.string.dashboard_error_load)) }
                ?: UiText.Resource(Res.string.dashboard_error_load)
        updateState {
            it.copy(
                isLoading = false,
                errorMessage = errorMsg,
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

    private fun startSseListening() {
        val client = sseClient ?: return
        launchJob("sse_listening") {
            while (isActive) {
                client.connect("/api/v1/events").collect { event ->
                    when (event) {
                        is SSEEvent.DeviceCreated,
                        is SSEEvent.DeviceUpdated,
                        is SSEEvent.TopologyUpdated,
                        is SSEEvent.DiscoveryProgress,
                        -> {
                            triggerFetchMetrics()
                        }
                        is SSEEvent.Disconnected -> {
                            delay(5000L)
                            startSseListening()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
