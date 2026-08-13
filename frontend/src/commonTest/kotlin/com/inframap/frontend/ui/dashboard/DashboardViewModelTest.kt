package com.inframap.frontend.ui.dashboard

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEEvent
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
                        PaginatedList(items = listOf(FakeDeviceRepository.DEFAULT_DEVICE), total = 15L, page = 1, perPage = 1),
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
        scope: CoroutineScope? = null,
    ) = DashboardViewModel(
        GetDevicesUseCase(deviceRepo),
        GetStagingDevicesUseCase(stagingRepo),
        GetHealthUseCase(dashRepo),
        GetDiscoverySourcesUseCase(dashRepo),
        GetSubnetsUseCase(subnetRepo),
        sseClient,
        autoRefreshIntervalMs,
        scope,
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
                assertEquals(true, state.isSystemHealthy)
                assertEquals("v1.2.3", state.systemVersion)
                assertEquals(2L, state.totalDiscoverySources)
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
                            PaginatedList(items = listOf(FakeDeviceRepository.DEFAULT_DEVICE), total = 15L, page = 1, perPage = 1),
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
    fun sseEventsTriggerMetricsReload() =
        runTest {
            val fakeSse = FakeSSEClient()
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(items = emptyList(), total = 10L, page = 1, perPage = 1),
                            requestId = "",
                        ),
                )
            val vm = makeVm(deviceRepo = deviceRepo, sseClient = fakeSse, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                deviceRepo.getDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = emptyList(), total = 11L, page = 1, perPage = 1),
                        requestId = "",
                    )
                fakeSse.events.emit(SSEEvent.DeviceCreated(id = "e1", data = "{}"))
                advanceUntilIdle()
                assertEquals(11L, expectMostRecentItem().totalActiveDevices)

                deviceRepo.getDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = emptyList(), total = 12L, page = 1, perPage = 1),
                        requestId = "",
                    )
                fakeSse.events.emit(SSEEvent.DeviceUpdated(id = "e2", data = "{}"))
                advanceUntilIdle()
                assertEquals(12L, expectMostRecentItem().totalActiveDevices)

                deviceRepo.getDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = emptyList(), total = 13L, page = 1, perPage = 1),
                        requestId = "",
                    )
                fakeSse.events.emit(SSEEvent.TopologyUpdated(id = "e3", data = "{}"))
                advanceUntilIdle()
                assertEquals(13L, expectMostRecentItem().totalActiveDevices)

                deviceRepo.getDevicesResult =
                    ApiResult.Success(
                        PaginatedList(items = emptyList(), total = 14L, page = 1, perPage = 1),
                        requestId = "",
                    )
                fakeSse.events.emit(SSEEvent.DiscoveryProgress(id = "e4", data = "{}"))
                advanceUntilIdle()
                assertEquals(14L, expectMostRecentItem().totalActiveDevices)

                fakeSse.events.emit(SSEEvent.SystemNotification(id = "e5", data = "{}"))
                runCurrent()

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
    fun clearDisposesAllBackgroundJobs() =
        runTest {
            val fakeSse = FakeSSEClient()
            val vm = makeVm(sseClient = fakeSse, autoRefreshIntervalMs = 1000L, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            vm.clear()
            advanceUntilIdle()
        }

    @Test
    fun healthFallbackPopulatesHealthWhenAuthGatedEndpointsFail() =
        runTest {
            val dashRepo = FakeDashboardRepository()
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Error(
                            code = "UNAUTHENTICATED",
                            message = "Token expired",
                            requestId = "",
                            httpStatus = 401,
                        ),
                )
            val stagingRepo =
                FakeStagingRepository(
                    getStagingDevicesResult =
                        ApiResult.Error(
                            code = "UNAUTHENTICATED",
                            message = "Token expired",
                            requestId = "",
                            httpStatus = 401,
                        ),
                )
            val vm = makeVm(deviceRepo = deviceRepo, stagingRepo = stagingRepo, dashRepo = dashRepo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()
                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                assertEquals(true, state.isSystemHealthy)
                assertEquals("v1.2.3", state.systemVersion)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun healthFallbackPreservesPreviousHealthWhenAllEndpointsFail() =
        runTest {
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(items = listOf(FakeDeviceRepository.DEFAULT_DEVICE), total = 15L, page = 1, perPage = 1),
                            requestId = "",
                        ),
                )
            val stagingRepo =
                FakeStagingRepository(
                    getStagingDevicesResult =
                        ApiResult.Success(
                            PaginatedList(items = emptyList(), total = 3L, page = 1, perPage = 50),
                            requestId = "",
                        ),
                )
            val dashRepo = FakeDashboardRepository()
            val vm = makeVm(deviceRepo = deviceRepo, stagingRepo = stagingRepo, dashRepo = dashRepo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()
                assertEquals(true, vm.state.value.isSystemHealthy)
                assertEquals("v1.2.3", vm.state.value.systemVersion)

                val networkError = ApiResult.NetworkError(RuntimeException("Network down"))
                deviceRepo.getDevicesResult = networkError
                stagingRepo.getStagingDevicesResult = networkError
                dashRepo.getHealthResult = networkError
                dashRepo.getDiscoverySourcesResult = networkError
                vm.refresh()
                skipItems(1)
                val state = awaitItem()
                assertNotNull(state.errorMessage)
                assertEquals(true, state.isSystemHealthy)
                assertEquals("v1.2.3", state.systemVersion)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun staleResponseIsDiscardedByGenerationToken() =
        runTest {
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items = listOf(FakeDeviceRepository.DEFAULT_DEVICE),
                                total = 10L,
                                page = 1,
                                perPage = 1,
                            ),
                            requestId = "",
                        ),
                    onGetDevices = { kotlinx.coroutines.delay(5000L) },
                )
            val vm = makeVm(deviceRepo = deviceRepo, scope = this)

            advanceTimeBy(1000L)
            runCurrent()

            deviceRepo.onGetDevices = null
            deviceRepo.getDevicesResult =
                ApiResult.Success(
                    PaginatedList(
                        items = listOf(FakeDeviceRepository.DEFAULT_DEVICE),
                        total = 99L,
                        page = 1,
                        perPage = 1,
                    ),
                    requestId = "",
                )
            vm.refresh()
            advanceUntilIdle()

            assertEquals(99L, vm.state.value.totalActiveDevices)
            vm.clear()
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
