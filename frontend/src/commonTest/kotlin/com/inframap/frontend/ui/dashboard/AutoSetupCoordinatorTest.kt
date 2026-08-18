package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.usecase.dashboard.GetStagingSummaryUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.fakes.FakeDashboardRepository
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.fakes.FakeNetworkRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutoSetupCoordinatorTest {
    private fun create(
        networkRepo: FakeNetworkRepository = FakeNetworkRepository(),
        subnetRepo: FakeSubnetRepository = FakeSubnetRepository(),
        discoveryRepo: FakeDiscoveryRepository = FakeDiscoveryRepository(),
        dashRepo: FakeDashboardRepository = FakeDashboardRepository(),
        localStorage: FakeLocalStorage = FakeLocalStorage(),
    ) = AutoSetupCoordinator(
        GetNetworkInterfacesUseCase(networkRepo),
        CreateSubnetUseCase(subnetRepo),
        CreateDiscoverySourceUseCase(discoveryRepo),
        TriggerDiscoveryRunUseCase(discoveryRepo),
        GetDiscoverySourcesUseCase(discoveryRepo),
        GetStagingSummaryUseCase(dashRepo),
        localStorage,
    )

    @Test
    fun isDismissedReturnsFalseByDefault() {
        val coordinator = create()
        assertFalse(coordinator.isDismissed())
    }

    @Test
    fun dismissSetsStorageKey() {
        val storage = FakeLocalStorage()
        val coordinator = create(localStorage = storage)
        coordinator.dismiss()
        assertTrue(coordinator.isDismissed())
    }

    @Test
    fun detectInterfacesReturnsNetworkInterfaces() =
        runTest {
            val coordinator = create()
            val result = coordinator.detectInterfaces()
            assertIs<ApiResult.Success<List<NetworkInterface>>>(result)
            assertEquals(1, result.data.size)
            assertEquals("eth0", result.data[0].name)
        }

    @Test
    fun createSubnetsSucceedsForValidInterfaces() =
        runTest {
            val coordinator = create()
            val iface =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.1.5",
                    cidr = "192.168.1.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                    gateway = "192.168.1.1",
                )
            val result = coordinator.createSubnets(listOf(iface))
            assertIs<ApiResult.Success<Unit>>(result)
        }

    @Test
    fun createSubnetsSkipsConflictErrors() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    createSubnetResult =
                        ApiResult.Error(
                            code = "CONFLICT",
                            message = "Already exists",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val coordinator = create(subnetRepo = subnetRepo)
            val iface =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.1.5",
                    cidr = "192.168.1.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                )
            val result = coordinator.createSubnets(listOf(iface))
            assertIs<ApiResult.Success<Unit>>(result)
        }

    @Test
    fun createSubnetsReturnsNonConflictError() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    createSubnetResult =
                        ApiResult.Error(
                            code = "INTERNAL",
                            message = "Server error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val coordinator = create(subnetRepo = subnetRepo)
            val iface =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.1.5",
                    cidr = "192.168.1.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                )
            val result = coordinator.createSubnets(listOf(iface))
            assertIs<ApiResult.Error>(result)
        }

    @Test
    fun createSourcesAndTriggerScanSucceeds() =
        runTest {
            val coordinator = create()
            val iface =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.1.5",
                    cidr = "192.168.1.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                )
            val result = coordinator.createSourcesAndTriggerScan(listOf(iface))
            assertIs<ApiResult.Success<List<String>>>(result)
        }

    @Test
    fun pollScanAndCountSucceeds() =
        runTest {
            val completedSource =
                DiscoverySource(
                    id = "src-1",
                    name = "Test",
                    sourceType = "full",
                    enabled = true,
                    lastStatus = "completed",
                )
            val discoveryRepo =
                FakeDiscoveryRepository(
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items = listOf(completedSource),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val coordinator = create(discoveryRepo = discoveryRepo)
            val result = coordinator.pollScanAndCount(listOf("src-1"))
            assertIs<ApiResult.Success<Int>>(result)
        }

    @Test
    fun pollScanAndCountReturnsEmptySourcesCount() =
        runTest {
            val coordinator = create()
            val result = coordinator.pollScanAndCount(emptyList())
            assertIs<ApiResult.Success<Int>>(result)
        }

    @Test
    fun createSourcesReturnsErrorOnCreateFailure() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Error(
                            code = "ERR",
                            message = "Failed",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val coordinator = create(discoveryRepo = discoveryRepo)
            val iface =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.1.5",
                    cidr = "192.168.1.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                )
            val result = coordinator.createSourcesAndTriggerScan(listOf(iface))
            assertIs<ApiResult.Error>(result)
        }

    @Test
    fun createSourcesReturnsErrorOnTriggerFailure() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    triggerRunResult =
                        ApiResult.Error(
                            code = "ERR",
                            message = "Trigger failed",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val coordinator = create(discoveryRepo = discoveryRepo)
            val iface =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.1.5",
                    cidr = "192.168.1.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                )
            val result = coordinator.createSourcesAndTriggerScan(listOf(iface))
            assertIs<ApiResult.Error>(result)
        }

    @Test
    fun fetchStagingCountPropagatesError() =
        runTest {
            val dashRepo =
                FakeDashboardRepository(
                    getStagingSummaryResult =
                        ApiResult.Error(
                            code = "ERR",
                            message = "Staging unavailable",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val completedSource =
                DiscoverySource(
                    id = "src-1",
                    name = "Test",
                    sourceType = "full",
                    enabled = true,
                    lastStatus = "completed",
                )
            val discoveryRepo =
                FakeDiscoveryRepository(
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items = listOf(completedSource),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val coordinator = create(discoveryRepo = discoveryRepo, dashRepo = dashRepo)
            val result = coordinator.pollScanAndCount(listOf("src-1"))
            assertIs<ApiResult.Error>(result)
        }
}
