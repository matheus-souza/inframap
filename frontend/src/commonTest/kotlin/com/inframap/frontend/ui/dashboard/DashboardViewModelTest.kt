package com.inframap.frontend.ui.dashboard

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.usecase.dashboard.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetHealthUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.fakes.FakeDashboardRepository
import com.inframap.frontend.fakes.FakeDeviceRepository
import com.inframap.frontend.fakes.FakeSSEClient
import com.inframap.frontend.fakes.FakeStagingRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private fun makeVm(
        deviceRepo: FakeDeviceRepository =
            FakeDeviceRepository(
                getDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = listOf(FakeDeviceRepository.DEFAULT_DEVICE), total = 15L, page = 1, perPage = 5),
                        requestId = "",
                    ),
            ),
        stagingRepo: FakeStagingRepository =
            FakeStagingRepository(
                getStagingDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = emptyList(), total = 3L, page = 1, perPage = 50),
                        requestId = "",
                    ),
            ),
        dashRepo: FakeDashboardRepository = FakeDashboardRepository(),
        subnetRepo: FakeSubnetRepository = FakeSubnetRepository(),
        sseClient: FakeSSEClient? = null,
        autoRefreshIntervalMs: Long = 0L,
        timestampProvider: () -> String = { "19:20:15" },
        scope: CoroutineScope? = null,
    ) = DashboardViewModel(
        getDevicesUseCase = GetDevicesUseCase(deviceRepo),
        getStagingDevicesUseCase = GetStagingDevicesUseCase(stagingRepo),
        getHealthUseCase = GetHealthUseCase(dashRepo),
        getDiscoverySourcesUseCase = GetDiscoverySourcesUseCase(dashRepo),
        sseClient = sseClient,
        getSubnetsUseCase = GetSubnetsUseCase(subnetRepo),
        autoRefreshIntervalMs = autoRefreshIntervalMs,
        timestampProvider = timestampProvider,
        scope = scope,
    )

    @Test
    fun loadDataPopulatesMetricsSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNull(state.errorMessage)
                assertEquals(15L, state.totalActiveDevices)
                assertEquals(3L, state.totalStagedDevices)
                assertEquals(1L, state.totalSubnetsMonitored)
                assertEquals(100, state.onlinePercentage)
                assertEquals(true, state.isSystemHealthy)
                assertEquals("v1.2.3", state.systemVersion)
                assertEquals(2L, state.totalDiscoverySources)
                assertEquals(1, state.recentDevices.size)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnDevices() =
        runTest {
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB connection failed",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(deviceRepo = deviceRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnStaging() =
        runTest {
            val stagingRepo =
                FakeStagingRepository(
                    getStagingDevicesResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "Staging DB error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(stagingRepo = stagingRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnHealth() =
        runTest {
            val dashRepo =
                FakeDashboardRepository(
                    getHealthResult =
                        ApiResult.Error(
                            code = "DOWN",
                            message = "Service down",
                            requestId = "",
                            httpStatus = 503,
                        ),
                )
            val vm = makeVm(dashRepo = dashRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDataHandlesApiErrorOnSources() =
        runTest {
            val dashRepo =
                FakeDashboardRepository(
                    getDiscoverySourcesResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "Sources DB error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(dashRepo = dashRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDataHandlesNetworkError() =
        runTest {
            val stagingRepo =
                FakeStagingRepository(
                    getStagingDevicesResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(stagingRepo = stagingRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun manualRefreshReloadsData() =
        runTest {
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(items = listOf(FakeDeviceRepository.DEFAULT_DEVICE), total = 15L, page = 1, perPage = 5),
                            requestId = "",
                        ),
                )
            val vm = makeVm(deviceRepo = deviceRepo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()
                val initialCalls = deviceRepo.getDevicesCallCount

                vm.refresh()
                skipItems(1)
                awaitItem()

                assertTrue(deviceRepo.getDevicesCallCount > initialCalls)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun periodicAutoRefreshUpdatesHealth() =
        runTest {
            val dashRepo = FakeDashboardRepository()
            val vm = makeVm(dashRepo = dashRepo, autoRefreshIntervalMs = 1000L, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()
                assertEquals(true, vm.state.value.isSystemHealthy)

                dashRepo.getHealthResult =
                    ApiResult.Success(
                        Health(status = "degraded", version = "v1.2.3"),
                        requestId = "",
                    )
                advanceTimeBy(1001L)
                runCurrent()

                val state = expectMostRecentItem()
                vm.stopAutoRefresh()
                assertEquals(false, state.isSystemHealthy)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun nullSseClientAndZeroIntervalDoNotCrash() =
        runTest {
            val vm = makeVm(sseClient = null, autoRefreshIntervalMs = 0L, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()
                vm.stopAutoRefresh()
                vm.stopSseListening()
                assertFalse(vm.state.value.isLoading)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun sseEventsTriggerMetricsReloadAndAppendsLiveEvents() =
        runTest {
            val fakeSse = FakeSSEClient()
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(items = emptyList(), total = 10L, page = 1, perPage = 5),
                            requestId = "",
                        ),
                )
            val vm = makeVm(deviceRepo = deviceRepo, sseClient = fakeSse, timestampProvider = { "19:20:15" }, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                deviceRepo.getDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = emptyList(), total = 11L, page = 1, perPage = 5),
                        requestId = "",
                    )
                fakeSse.events.emit(SSEEvent.DeviceCreated(id = "e1", data = "Device 192.168.1.10 added"))
                advanceUntilIdle()

                val state1 = expectMostRecentItem()
                assertEquals(11L, state1.totalActiveDevices)
                assertEquals(1, state1.liveEvents.size)
                assertEquals("DeviceCreated", state1.liveEvents.first().eventType)
                assertEquals("19:20:15", state1.liveEvents.first().timestamp)
                assertEquals("Device 192.168.1.10 added", state1.liveEvents.first().message)

                fakeSse.events.emit(SSEEvent.DiscoveryProgress(id = "e2", data = "Scan 192.168.1.0/24 in progress"))
                advanceUntilIdle()

                val state2 = expectMostRecentItem()
                assertEquals(DiscoveryEngineStatus.RUNNING, state2.discoveryEngineStatus)
                assertEquals(2, state2.liveEvents.size)
                assertEquals("DiscoveryProgress", state2.liveEvents.first().eventType)

                vm.stopSseListening()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun disconnectedSseEventHandlesReconnection() =
        runTest {
            val fakeSse = FakeSSEClient()
            val vm = makeVm(sseClient = fakeSse, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                assertEquals(1, fakeSse.connectCount)
                fakeSse.events.emit(SSEEvent.Disconnected())
                advanceTimeBy(5001L)
                runCurrent()

                assertEquals(2, fakeSse.connectCount)
                vm.stopSseListening()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun fetchHealthErrorDoesNotMutateHealthyState() =
        runTest {
            val dashRepo = FakeDashboardRepository()
            val vm = makeVm(dashRepo = dashRepo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()
                assertEquals(true, vm.state.value.isSystemHealthy)

                dashRepo.getHealthResult =
                    ApiResult.Error(
                        code = "HEALTH_ERROR",
                        message = "Health check failed",
                        requestId = "",
                        httpStatus = 500,
                    )
                vm.fetchHealth()
                advanceUntilIdle()
                assertEquals(true, vm.state.value.isSystemHealthy)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun kpiCountersAndOnlinePercentageCalculatedCorrectly() =
        runTest {
            val activeDevice = Device(id = "d1", hostname = "router-01", deviceType = "ROUTER", status = "ACTIVE")
            val offlineDevice = Device(id = "d2", hostname = "switch-02", deviceType = "SWITCH", status = "OFFLINE")
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(items = listOf(activeDevice, offlineDevice), total = 10L, page = 1, perPage = 5),
                            requestId = "",
                        ),
                )
            val vm = makeVm(deviceRepo = deviceRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertEquals(10L, state.totalActiveDevices)
                assertEquals(5L, state.onlineDevicesCount)
                assertEquals(50, state.onlinePercentage)
                assertEquals(2, state.recentDevices.size)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun liveEventsListCapsAtMax50Items() =
        runTest {
            val fakeSse = FakeSSEClient()
            val vm = makeVm(sseClient = fakeSse, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                repeat(60) { index ->
                    fakeSse.events.emit(SSEEvent.SystemNotification(id = "e_$index", data = "Event $index"))
                    runCurrent()
                }

                val state = expectMostRecentItem()
                assertEquals(50, state.liveEvents.size)
                assertEquals("Event 59", state.liveEvents.first().message)

                vm.stopSseListening()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun dashboardUiStateDefaultsAreCorrect() {
        val state = DashboardUiState()
        assertEquals(0L, state.totalActiveDevices)
        assertEquals(0L, state.onlineDevicesCount)
        assertEquals(0L, state.totalSubnetsMonitored)
        assertEquals(DiscoveryEngineStatus.IDLE, state.discoveryEngineStatus)
        assertEquals(0L, state.totalStagedDevices)
        assertTrue(state.recentDevices.isEmpty())
        assertTrue(state.liveEvents.isEmpty())
        assertNull(state.isSystemHealthy)
        assertEquals("", state.systemVersion)
        assertEquals(0L, state.totalDiscoverySources)
        assertTrue(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(0, state.onlinePercentage)

        val copied = state.copy(totalActiveDevices = 10L, onlineDevicesCount = 8L)
        assertEquals(10L, copied.totalActiveDevices)
        assertEquals(80, copied.onlinePercentage)
    }
}
