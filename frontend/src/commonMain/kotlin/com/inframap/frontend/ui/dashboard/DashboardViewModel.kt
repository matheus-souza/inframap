package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.usecase.dashboard.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetHealthUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.dashboard_error_load
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive

@Suppress("TooManyFunctions")
class DashboardViewModel(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val getStagingDevicesUseCase: GetStagingDevicesUseCase,
    private val getHealthUseCase: GetHealthUseCase,
    private val getDiscoverySourcesUseCase: GetDiscoverySourcesUseCase,
    private val getSubnetsUseCase: GetSubnetsUseCase,
    private val autoSetupCoordinator: AutoSetupCoordinator,
    private val sseClient: SSEClient? = null,
    private val autoRefreshIntervalMs: Long = 30_000L,
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
        updateState { it.copy(isLoading = true, errorMessage = null, isErrorDismissed = false) }
        triggerFetchMetrics()
    }

    fun refresh() {
        loadData()
    }

    fun dismissError() {
        updateState { it.copy(isErrorDismissed = true) }
    }

    fun dismissAutoSetup() {
        autoSetupCoordinator.dismiss()
        updateState { it.copy(autoSetup = it.autoSetup.copy(isVisible = false)) }
    }

    fun startAutoSetup() {
        val interfaces = currentState.autoSetup.detectedInterfaces
        if (interfaces.isEmpty()) return
        updateState {
            it.copy(autoSetup = it.autoSetup.copy(phase = AutoSetupPhase.CREATING_SUBNETS, errorMessage = null))
        }
        launchJob("auto_setup") { executeAutoSetup(interfaces) }
    }

    private suspend fun executeAutoSetup(interfaces: List<com.inframap.frontend.domain.model.NetworkInterface>) {
        when (val result = autoSetupCoordinator.createSubnets(interfaces)) {
            is ApiResult.Success -> Unit
            else -> {
                updateAutoSetupError(mapError(result))
                return
            }
        }

        updateState {
            it.copy(autoSetup = it.autoSetup.copy(phase = AutoSetupPhase.CREATING_SOURCES))
        }

        val sourceIds =
            when (val result = autoSetupCoordinator.createSourcesAndTriggerScan(interfaces)) {
                is ApiResult.Success -> result.data
                else -> {
                    updateAutoSetupError(mapError(result))
                    return
                }
            }

        updateState {
            it.copy(autoSetup = it.autoSetup.copy(phase = AutoSetupPhase.SCANNING))
        }

        when (val result = autoSetupCoordinator.pollScanAndCount(sourceIds)) {
            is ApiResult.Success -> {
                updateState {
                    it.copy(
                        autoSetup =
                            it.autoSetup.copy(
                                phase = AutoSetupPhase.COMPLETED,
                                discoveredDeviceCount = result.data,
                            ),
                    )
                }
                triggerFetchMetrics()
            }
            else -> updateAutoSetupError(mapError(result))
        }
    }

    private fun updateAutoSetupError(errorMsg: UiText) {
        updateState {
            it.copy(autoSetup = it.autoSetup.copy(phase = AutoSetupPhase.IDLE, errorMessage = errorMsg))
        }
    }

    fun checkAutoSetup() {
        if (currentState.totalSubnets > 0 || autoSetupCoordinator.isDismissed()) return
        launchJob("detect_interfaces") {
            when (val result = autoSetupCoordinator.detectInterfaces()) {
                is ApiResult.Success ->
                    if (result.data.isNotEmpty()) {
                        updateState {
                            it.copy(
                                autoSetup =
                                    AutoSetupState(
                                        isVisible = true,
                                        detectedInterfaces = result.data,
                                    ),
                            )
                        }
                    }
                else -> Unit
            }
        }
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

    @Suppress("LongMethod", "TooGenericExceptionCaught", "SwallowedException")
    private suspend fun fetchMetrics() {
        val generation = metricsGeneration
        try {
            coroutineScope {
                val devicesDeferred = async { getDevicesUseCase(page = 1, perPage = 1) }
                val stagingDeferred = async { getStagingDevicesUseCase(page = 1, perPage = 1) }
                val healthDeferred = async { getHealthUseCase() }
                val sourcesDeferred = async { getDiscoverySourcesUseCase() }
                val subnetsDeferred = async { getSubnetsUseCase() }

                val devicesResult = devicesDeferred.await()
                val stagingResult = stagingDeferred.await()
                val healthResult = healthDeferred.await()
                val sourcesResult = sourcesDeferred.await()
                val subnetsResult = subnetsDeferred.await()

                if (generation != metricsGeneration) {
                    updateState { it.copy(isLoading = false) }
                    return@coroutineScope
                }

                val results = listOf(devicesResult, stagingResult, healthResult, sourcesResult, subnetsResult)

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

                val activeTotal = (devicesResult as? ApiResult.Success)?.data?.total ?: 0L
                val stagedTotal = (stagingResult as? ApiResult.Success)?.data?.total ?: 0L
                val healthData = (healthResult as? ApiResult.Success)?.data
                val sourcesTotal = (sourcesResult as? ApiResult.Success)?.data?.size?.toLong() ?: 0L
                val subnetsTotal = (subnetsResult as? ApiResult.Success)?.data?.total ?: 0L

                updateState {
                    it.copy(
                        totalActiveDevices = activeTotal,
                        totalStagedDevices = stagedTotal,
                        totalSubnets = subnetsTotal,
                        isSystemHealthy = healthData?.isHealthy,
                        systemVersion = healthData?.version ?: "",
                        totalDiscoverySources = sourcesTotal,
                        isLoading = false,
                        errorMessage = null,
                    )
                }

                if (subnetsTotal == 0L) checkAutoSetup()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            updateState {
                it.copy(
                    isLoading = false,
                    errorMessage = UiText.Resource(Res.string.dashboard_error_load),
                )
            }
        } finally {
            updateState { it.copy(isLoading = false) }
        }
    }

    private fun handleMetricsError(
        errorMsg: UiText,
        healthResult: ApiResult<*>,
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

    private fun startSseListening() {
        val client = sseClient ?: return
        launchJob("sse_listening") {
            while (isActive) {
                client
                    .connect("/api/v1/events/stream")
                    .takeWhile { it !is SSEEvent.Disconnected }
                    .collect { event ->
                        when (event) {
                            is SSEEvent.DeviceCreated,
                            is SSEEvent.DeviceUpdated,
                            is SSEEvent.TopologyUpdated,
                            is SSEEvent.DiscoveryProgress,
                            -> triggerFetchMetrics()
                            else -> Unit
                        }
                    }
                client.disconnect()
                delay(5000L)
            }
        }
    }
}
