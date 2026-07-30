package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.DeviceListResponse
import com.inframap.frontend.data.dto.DiscoveryListResponse
import com.inframap.frontend.data.dto.HealthDto
import com.inframap.frontend.data.dto.StagingListResponse
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val apiClient: ApiClient,
    private val sseClient: SSEClient? = null,
    private val scope: CoroutineScope,
    private val autoRefreshIntervalMs: Long = 30_000L,
) {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var fetchMetricsJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var sseJob: Job? = null

    init {
        loadData()
        startAutoRefresh()
        startSseListening()
    }

    fun loadData() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        fetchMetricsJob?.cancel()
        fetchMetricsJob =
            scope.launch {
                fetchMetrics()
            }
    }

    fun refresh() {
        loadData()
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun stopSseListening() {
        sseJob?.cancel()
        sseJob = null
    }

    fun clear() {
        fetchMetricsJob?.cancel()
        fetchMetricsJob = null
        stopAutoRefresh()
        stopSseListening()
    }

    private suspend fun fetchMetrics() =
        coroutineScope {
            val devicesDeferred = async { apiClient.get<DeviceListResponse>("/api/v1/devices") }
            val stagingDeferred = async { apiClient.get<StagingListResponse>("/api/v1/devices/staging") }
            val healthDeferred = async { apiClient.get<HealthDto>("/api/v1/health") }
            val sourcesDeferred = async { apiClient.get<DiscoveryListResponse>("/api/v1/discovery/sources") }

            val devicesResult = devicesDeferred.await()
            val stagingResult = stagingDeferred.await()
            val healthResult = healthDeferred.await()
            val sourcesResult = sourcesDeferred.await()

            val hasError =
                devicesResult is ApiResult.Error ||
                    stagingResult is ApiResult.Error ||
                    healthResult is ApiResult.Error ||
                    sourcesResult is ApiResult.Error

            val hasNetworkError =
                devicesResult is ApiResult.NetworkError ||
                    stagingResult is ApiResult.NetworkError ||
                    healthResult is ApiResult.NetworkError ||
                    sourcesResult is ApiResult.NetworkError

            if (hasNetworkError) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Network error. Failed to reach server.",
                    )
                }
                return@coroutineScope
            }

            if (hasError) {
                val errorMsg =
                    listOf(devicesResult, stagingResult, healthResult, sourcesResult)
                        .filterIsInstance<ApiResult.Error>()
                        .firstOrNull()
                        ?.message ?: "Failed to load dashboard metrics"

                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = errorMsg,
                    )
                }
                return@coroutineScope
            }

            val activeTotal = (devicesResult as? ApiResult.Success)?.data?.total ?: 0L
            val stagedTotal = (stagingResult as? ApiResult.Success)?.data?.total ?: 0L
            val healthData = (healthResult as? ApiResult.Success)?.data
            val sourcesTotal = (sourcesResult as? ApiResult.Success)?.data?.total ?: 0L

            _state.update {
                it.copy(
                    totalActiveDevices = activeTotal,
                    totalStagedDevices = stagedTotal,
                    isSystemHealthy = healthData?.status == "ok",
                    systemVersion = healthData?.version ?: "",
                    totalDiscoverySources = sourcesTotal,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }

    private fun startAutoRefresh() {
        if (autoRefreshIntervalMs <= 0) return
        autoRefreshJob?.cancel()
        autoRefreshJob =
            scope.launch {
                while (isActive) {
                    delay(autoRefreshIntervalMs)
                    fetchHealth()
                }
            }
    }

    suspend fun fetchHealth() {
        val healthResult = apiClient.get<HealthDto>("/api/v1/health")
        if (healthResult is ApiResult.Success) {
            _state.update {
                it.copy(
                    isSystemHealthy = healthResult.data.status == "ok",
                    systemVersion = healthResult.data.version,
                )
            }
        }
    }

    private fun startSseListening() {
        val client = sseClient ?: return
        sseJob?.cancel()
        sseJob =
            scope.launch {
                client.connect("/api/v1/events").collect { event ->
                    when (event) {
                        is SSEEvent.DeviceCreated,
                        is SSEEvent.DeviceUpdated,
                        is SSEEvent.TopologyUpdated,
                        is SSEEvent.DiscoveryProgress,
                        -> {
                            fetchMetrics()
                        }
                        else -> Unit
                    }
                }
            }
    }
}
