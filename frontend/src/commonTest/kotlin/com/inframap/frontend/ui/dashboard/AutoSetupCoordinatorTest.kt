package com.inframap.frontend.ui.dashboard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.model.Subnet
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSetupCoordinatorTest {
    private val testInterface =
        NetworkInterface(
            name = "eth0",
            ip = "192.168.1.50",
            cidr = "192.168.1.0/24",
            gateway = "192.168.1.1",
            mac = "00:11:22:33:44:55",
        )

    @Test
    fun dismissStoresFlagInLocalStorage() {
        val storage = FakeLocalStorage()
        val coordinator = createCoordinator(storage = storage)

        assertFalse(coordinator.isDismissed())
        coordinator.dismiss()
        assertTrue(coordinator.isDismissed())
    }

    @Test
    fun detectInterfacesReturnsNetworkList() =
        runTest {
            val netRepo =
                FakeNetworkRepository(
                    getInterfacesResult = ApiResult.Success(listOf(testInterface), ""),
                )
            val coordinator = createCoordinator(netRepo = netRepo)

            val result = coordinator.detectInterfaces()
            assertTrue(result is ApiResult.Success)
            assertEquals(1, result.data.size)
            assertEquals("eth0", result.data[0].name)
        }

    @Test
    fun createSubnetsSucceedsForInterfaces() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    createSubnetResult =
                        ApiResult.Success(
                            Subnet(id = "sub1", name = "eth0", cidr = "192.168.1.0/24", gatewayIp = "192.168.1.1"),
                            "",
                        ),
                )
            val coordinator = createCoordinator(subnetRepo = subnetRepo)

            val result = coordinator.createSubnets(listOf(testInterface))
            assertTrue(result is ApiResult.Success)
        }

    @Test
    fun createSubnetsHandlesConflictGracefully() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    createSubnetResult = ApiResult.Error("CONFLICT", "Already exists", "", 409),
                )
            val coordinator = createCoordinator(subnetRepo = subnetRepo)

            val result = coordinator.createSubnets(listOf(testInterface))
            assertTrue(result is ApiResult.Success)
        }

    @Test
    fun createSubnetsReturnsErrorOnFailure() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    createSubnetResult = ApiResult.Error("INTERNAL", "Server error", "", 500),
                )
            val coordinator = createCoordinator(subnetRepo = subnetRepo)

            val result = coordinator.createSubnets(listOf(testInterface))
            assertTrue(result is ApiResult.Error)
        }

    @Test
    fun createSourcesAndTriggerScanReturnsSourceIds() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Success(
                            DiscoverySource(id = "ds1", name = "Scan", sourceType = "full", enabled = true),
                            "",
                        ),
                    triggerRunResult =
                        ApiResult.Success(
                            DiscoverySource(id = "ds1", name = "Scan", sourceType = "full", enabled = true),
                            "",
                        ),
                )
            val coordinator = createCoordinator(discoveryRepo = discoveryRepo)

            val result = coordinator.createSourcesAndTriggerScan(listOf(testInterface))
            assertTrue(result is ApiResult.Success)
            assertEquals(listOf("ds1"), result.data)
        }

    @Test
    fun createSourcesAndTriggerScanReturnsErrorWhenCreateFails() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Error("ERR", "Create failed", "", 500),
                )
            val coordinator = createCoordinator(discoveryRepo = discoveryRepo)

            val result = coordinator.createSourcesAndTriggerScan(listOf(testInterface))
            assertTrue(result is ApiResult.Error)
        }

    @Test
    fun createSourcesAndTriggerScanReturnsErrorWhenTriggerFails() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Success(
                            DiscoverySource(id = "ds1", name = "Scan", sourceType = "full", enabled = true),
                            "",
                        ),
                    triggerRunResult = ApiResult.Error("TRIGGER_ERR", "Failed", "", 500),
                )
            val coordinator = createCoordinator(discoveryRepo = discoveryRepo)

            val result = coordinator.createSourcesAndTriggerScan(listOf(testInterface))
            assertTrue(result is ApiResult.Error)
        }

    @Test
    fun pollScanAndCountFetchesStagingCountWhenCompleted() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items =
                                    listOf(
                                        DiscoverySource(
                                            id = "ds1",
                                            name = "Scan",
                                            sourceType = "full",
                                            enabled = true,
                                            lastStatus = "completed",
                                        ),
                                    ),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            "",
                        ),
                )
            val dashRepo =
                FakeDashboardRepository(
                    getStagingSummaryResult =
                        ApiResult.Success(
                            PaginatedList(
                                items =
                                    listOf(
                                        StagingDevice(
                                            id = "stg1",
                                            ipAddress = "192.168.1.100",
                                            hostname = "printer",
                                            deviceType = "printer",
                                        ),
                                        StagingDevice(
                                            id = "stg2",
                                            ipAddress = "192.168.1.101",
                                            hostname = "nas",
                                            deviceType = "storage",
                                        ),
                                    ),
                                total = 2L,
                                page = 1,
                                perPage = 50,
                            ),
                            "",
                        ),
                )
            val coordinator = createCoordinator(discoveryRepo = discoveryRepo, dashRepo = dashRepo)

            val result = coordinator.pollScanAndCount(listOf("ds1"))
            assertTrue(result is ApiResult.Success)
            assertEquals(2, result.data)
        }

    @Test
    fun pollScanAndCountReturnsErrorWhenSourceFails() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items =
                                    listOf(
                                        DiscoverySource(
                                            id = "ds1",
                                            name = "Scan",
                                            sourceType = "full",
                                            enabled = true,
                                            lastStatus = "error",
                                        ),
                                    ),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            "",
                        ),
                )
            val coordinator = createCoordinator(discoveryRepo = discoveryRepo)

            val result = coordinator.pollScanAndCount(listOf("ds1"))
            assertTrue(result is ApiResult.Error)
            assertEquals("SCAN_ERROR", (result as ApiResult.Error).code)
        }

    private fun createCoordinator(
        netRepo: FakeNetworkRepository = FakeNetworkRepository(),
        subnetRepo: FakeSubnetRepository = FakeSubnetRepository(),
        discoveryRepo: FakeDiscoveryRepository = FakeDiscoveryRepository(),
        dashRepo: FakeDashboardRepository = FakeDashboardRepository(),
        storage: FakeLocalStorage = FakeLocalStorage(),
    ): AutoSetupCoordinator =
        AutoSetupCoordinator(
            GetNetworkInterfacesUseCase(netRepo),
            CreateSubnetUseCase(subnetRepo),
            CreateDiscoverySourceUseCase(discoveryRepo),
            TriggerDiscoveryRunUseCase(discoveryRepo),
            GetDiscoverySourcesUseCase(discoveryRepo),
            GetStagingSummaryUseCase(dashRepo),
            storage,
        )
}
