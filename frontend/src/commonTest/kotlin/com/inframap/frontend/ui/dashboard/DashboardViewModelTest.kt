package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.usecase.dashboard.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetHealthUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeSSEClient : SSEClient {
    val events = MutableSharedFlow<SSEEvent>()
    var connectCount = 0

    override fun connect(url: String): SharedFlow<SSEEvent> {
        connectCount++
        return events.asSharedFlow()
    }

    override fun disconnect() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val sampleDevice = Device(id = "d1", hostname = "router-01", deviceType = "router", status = "active")
    private val sampleHealth = Health(status = "ok", version = "v1.2.3")
    private val sampleSource = DiscoverySource(id = "src1", name = "homelab-subnet")
    private val sampleStagingDevice = StagingDevice(id = "st1", hostname = "pending-01", deviceType = "switch")

    private fun deviceRepo(listResult: ApiResult<PaginatedList<Device>>): DeviceRepository =
        object : DeviceRepository {
            override suspend fun getDevices(
                page: Int,
                perPage: Int,
                search: String,
            ) = listResult

            override suspend fun getDeviceById(id: String) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun updateDevice(
                id: String,
                request: UpdateDeviceRequest,
            ) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
        }

    private fun stagingRepo(listResult: ApiResult<PaginatedList<StagingDevice>>): StagingRepository =
        object : StagingRepository {
            override suspend fun getStagingDevices(
                page: Int,
                perPage: Int,
            ) = listResult

            override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, requestId = "")
        }

    private fun dashboardRepo(
        healthResult: ApiResult<Health> = ApiResult.Success(sampleHealth, requestId = ""),
        sourcesResult: ApiResult<List<DiscoverySource>> = ApiResult.Success(listOf(sampleSource, sampleSource), requestId = ""),
    ): DashboardRepository =
        object : DashboardRepository {
            override suspend fun getHealth() = healthResult

            override suspend fun getStagingSummary() =
                ApiResult.Success(PaginatedList(items = listOf(sampleStagingDevice), total = 1, page = 1, perPage = 50), requestId = "")

            override suspend fun getDiscoverySources() = sourcesResult
        }

    private fun makeVm(
        getDevices: GetDevicesUseCase =
            GetDevicesUseCase(
                deviceRepo(
                    ApiResult.Success(PaginatedList(items = listOf(sampleDevice), total = 15L, page = 1, perPage = 1), requestId = ""),
                ),
            ),
        getStaging: GetStagingDevicesUseCase =
            GetStagingDevicesUseCase(
                stagingRepo(ApiResult.Success(PaginatedList(items = emptyList(), total = 3L, page = 1, perPage = 50), requestId = "")),
            ),
        getHealth: GetHealthUseCase = GetHealthUseCase(dashboardRepo()),
        getSources: GetDiscoverySourcesUseCase = GetDiscoverySourcesUseCase(dashboardRepo()),
        sseClient: SSEClient? = null,
        autoRefreshIntervalMs: Long = 0L,
        scope: CoroutineScope? = null,
    ) = DashboardViewModel(getDevices, getStaging, getHealth, getSources, sseClient, autoRefreshIntervalMs, scope)

    @Test
    fun loadDataPopulatesMetricsSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(15L, state.totalActiveDevices)
            assertEquals(3L, state.totalStagedDevices)
            assertEquals(true, state.isSystemHealthy)
            assertEquals("v1.2.3", state.systemVersion)
            assertEquals(2L, state.totalDiscoverySources)
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnDevices() =
        runTest {
            val errorDeviceRepo =
                deviceRepo(
                    ApiResult.Error(code = "DB_ERROR", message = "DB connection failed", requestId = "", httpStatus = 500),
                )
            val vm = makeVm(getDevices = GetDevicesUseCase(errorDeviceRepo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnStaging() =
        runTest {
            val errorStagingRepo =
                stagingRepo(
                    ApiResult.Error(code = "DB_ERROR", message = "Staging DB error", requestId = "", httpStatus = 500),
                )
            val vm = makeVm(getStaging = GetStagingDevicesUseCase(errorStagingRepo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnHealth() =
        runTest {
            val errorDashRepo =
                dashboardRepo(
                    healthResult = ApiResult.Error(code = "DOWN", message = "Service down", requestId = "", httpStatus = 503),
                )
            val vm =
                makeVm(
                    getHealth = GetHealthUseCase(errorDashRepo),
                    getSources = GetDiscoverySourcesUseCase(errorDashRepo),
                    scope = this,
                )

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnSources() =
        runTest {
            val errorDashRepo =
                dashboardRepo(
                    sourcesResult = ApiResult.Error(code = "DB_ERROR", message = "Sources DB error", requestId = "", httpStatus = 500),
                )
            val vm =
                makeVm(
                    getHealth = GetHealthUseCase(errorDashRepo),
                    getSources = GetDiscoverySourcesUseCase(errorDashRepo),
                    scope = this,
                )

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadDataHandlesNetworkError() =
        runTest {
            val networkStagingRepo = stagingRepo(ApiResult.NetworkError(RuntimeException("Network failure")))
            val vm = makeVm(getStaging = GetStagingDevicesUseCase(networkStagingRepo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun manualRefreshReloadsData() =
        runTest {
            var callCount = 0
            val countingDeviceRepo =
                object : DeviceRepository {
                    override suspend fun getDevices(
                        page: Int,
                        perPage: Int,
                        search: String,
                    ): ApiResult<PaginatedList<Device>> {
                        callCount++
                        return ApiResult.Success(
                            PaginatedList(items = listOf(sampleDevice), total = 15L, page = 1, perPage = 1),
                            requestId = "",
                        )
                    }

                    override suspend fun getDeviceById(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun updateDevice(
                        id: String,
                        request: UpdateDeviceRequest,
                    ) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
                }
            val vm = makeVm(getDevices = GetDevicesUseCase(countingDeviceRepo), scope = this)

            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()

            val initialCalls = callCount
            vm.refresh()
            advanceUntilIdle()

            assertTrue(callCount > initialCalls)
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun periodicAutoRefreshUpdatesHealth() =
        runTest {
            var isHealthyToggle = true
            val dynamicDashRepo =
                object : DashboardRepository {
                    override suspend fun getHealth() =
                        ApiResult.Success(Health(status = if (isHealthyToggle) "ok" else "degraded", version = "v1.2.3"), requestId = "")

                    override suspend fun getStagingSummary() =
                        ApiResult.Success(
                            PaginatedList(items = emptyList<StagingDevice>(), total = 0, page = 1, perPage = 50),
                            requestId = "",
                        )

                    override suspend fun getDiscoverySources() = ApiResult.Success(listOf(sampleSource), requestId = "")
                }

            val vm =
                makeVm(
                    getHealth = GetHealthUseCase(dynamicDashRepo),
                    getSources = GetDiscoverySourcesUseCase(dynamicDashRepo),
                    autoRefreshIntervalMs = 1000L,
                    scope = this,
                )
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            runCurrent()
            firstStateDeferred.await()
            assertEquals(true, vm.state.value.isSystemHealthy)

            isHealthyToggle = false
            val unhealthyDeferred = async { vm.state.first { it.isSystemHealthy == false } }
            advanceTimeBy(1001L)
            runCurrent()

            val state = unhealthyDeferred.await()
            vm.stopAutoRefresh()
            assertEquals(false, state.isSystemHealthy)
            vm.clear()
        }

    @Test
    fun nullSseClientAndZeroIntervalDoNotCrash() =
        runTest {
            val vm = makeVm(sseClient = null, autoRefreshIntervalMs = 0L, scope = this)
            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            stateDeferred.await()

            vm.stopAutoRefresh()
            vm.stopSseListening()
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun sseEventsTriggerMetricsReload() =
        runTest {
            val fakeSse = FakeSSEClient()
            var activeDevicesCount = 10L

            val dynamicDeviceRepo =
                object : DeviceRepository {
                    override suspend fun getDevices(
                        page: Int,
                        perPage: Int,
                        search: String,
                    ) = ApiResult.Success(
                        PaginatedList(items = emptyList<Device>(), total = activeDevicesCount, page = 1, perPage = 1),
                        requestId = "",
                    )

                    override suspend fun getDeviceById(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun updateDevice(
                        id: String,
                        request: UpdateDeviceRequest,
                    ) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
                }

            val vm = makeVm(getDevices = GetDevicesUseCase(dynamicDeviceRepo), sseClient = fakeSse, scope = this)
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()

            activeDevicesCount = 11L
            var sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 11L } }
            fakeSse.events.emit(SSEEvent.DeviceCreated(id = "e1", data = "{}"))
            advanceUntilIdle()
            assertEquals(11L, sseStateDeferred.await().totalActiveDevices)

            activeDevicesCount = 12L
            sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 12L } }
            fakeSse.events.emit(SSEEvent.DeviceUpdated(id = "e2", data = "{}"))
            advanceUntilIdle()
            assertEquals(12L, sseStateDeferred.await().totalActiveDevices)

            activeDevicesCount = 13L
            sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 13L } }
            fakeSse.events.emit(SSEEvent.TopologyUpdated(id = "e3", data = "{}"))
            advanceUntilIdle()
            assertEquals(13L, sseStateDeferred.await().totalActiveDevices)

            activeDevicesCount = 14L
            sseStateDeferred = async { vm.state.first { it.totalActiveDevices == 14L } }
            fakeSse.events.emit(SSEEvent.DiscoveryProgress(id = "e4", data = "{}"))
            advanceUntilIdle()
            assertEquals(14L, sseStateDeferred.await().totalActiveDevices)

            fakeSse.events.emit(SSEEvent.SystemNotification(id = "e5", data = "{}"))
            runCurrent()

            vm.stopSseListening()
            vm.clear()
        }

    @Test
    fun disconnectedSseEventHandlesReconnection() =
        runTest {
            val fakeSse = FakeSSEClient()
            val vm = makeVm(sseClient = fakeSse, scope = this)

            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            runCurrent()
            firstStateDeferred.await()

            assertEquals(1, fakeSse.connectCount)
            fakeSse.events.emit(SSEEvent.Disconnected())
            advanceTimeBy(5001L)
            runCurrent()

            assertEquals(2, fakeSse.connectCount)
            vm.stopSseListening()
            vm.clear()
        }

    @Test
    fun fetchHealthErrorDoesNotMutateHealthyState() =
        runTest {
            var healthFails = false
            val dynamicDashRepo =
                object : DashboardRepository {
                    override suspend fun getHealth() =
                        if (healthFails) {
                            ApiResult.Error(code = "HEALTH_ERROR", message = "Health check failed", requestId = "", httpStatus = 500)
                        } else {
                            ApiResult.Success(sampleHealth, requestId = "")
                        }

                    override suspend fun getStagingSummary() =
                        ApiResult.Success(
                            PaginatedList(items = emptyList<StagingDevice>(), total = 0, page = 1, perPage = 50),
                            requestId = "",
                        )

                    override suspend fun getDiscoverySources() = ApiResult.Success(listOf(sampleSource), requestId = "")
                }

            val vm =
                makeVm(
                    getHealth = GetHealthUseCase(dynamicDashRepo),
                    getSources = GetDiscoverySourcesUseCase(dynamicDashRepo),
                    scope = this,
                )
            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            firstStateDeferred.await()
            assertEquals(true, vm.state.value.isSystemHealthy)

            healthFails = true
            vm.fetchHealth()
            advanceUntilIdle()
            assertEquals(true, vm.state.value.isSystemHealthy)
            vm.clear()
        }

    @Test
    fun clearDisposesAllBackgroundJobs() =
        runTest {
            val fakeSse = FakeSSEClient()
            val vm = makeVm(sseClient = fakeSse, autoRefreshIntervalMs = 1000L, scope = this)

            val firstStateDeferred = async { vm.state.first { !it.isLoading } }
            runCurrent()
            firstStateDeferred.await()

            vm.clear()
            advanceUntilIdle()
        }

    @Test
    fun dashboardUiStateDefaultsAreCorrect() {
        val state = DashboardUiState()
        assertEquals(0L, state.totalActiveDevices)
        assertEquals(0L, state.totalStagedDevices)
        assertNull(state.isSystemHealthy)
        assertEquals("", state.systemVersion)
        assertEquals(0L, state.totalDiscoverySources)
        assertTrue(state.isLoading)
        assertNull(state.errorMessage)

        val copied = state.copy(totalActiveDevices = 5L, isSystemHealthy = false)
        assertEquals(5L, copied.totalActiveDevices)
        assertFalse(copied.isSystemHealthy!!)
    }
}
