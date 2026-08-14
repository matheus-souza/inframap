package com.inframap.frontend.ui.wizard

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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupWizardViewModelTest {
    private val eth0 =
        NetworkInterface(
            name = "eth0",
            ip = "192.168.18.5",
            cidr = "192.168.18.0/24",
            mac = "aa:bb:cc:dd:ee:ff",
            gateway = "192.168.18.1",
        )

    private val wlan0 =
        NetworkInterface(
            name = "wlan0",
            ip = "10.0.0.5",
            cidr = "10.0.0.0/24",
            mac = "11:22:33:44:55:66",
            gateway = "10.0.0.1",
        )

    private val sampleSubnet =
        Subnet(id = "sub1", name = "eth0", cidr = "192.168.18.0/24")

    private val sampleSource =
        DiscoverySource(id = "src-1", name = "Full — 192.168.18.0/24", sourceType = "full")

    private fun makeVm(
        networkRepo: FakeNetworkRepository =
            FakeNetworkRepository(
                getInterfacesResult = ApiResult.Success(listOf(eth0, wlan0), requestId = ""),
            ),
        subnetRepo: FakeSubnetRepository =
            FakeSubnetRepository(
                createSubnetResult = ApiResult.Success(sampleSubnet, requestId = ""),
            ),
        discoveryRepo: FakeDiscoveryRepository =
            FakeDiscoveryRepository(
                createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
            ),
        dashboardRepo: FakeDashboardRepository = FakeDashboardRepository(),
        localStorage: FakeLocalStorage = FakeLocalStorage(),
        scope: kotlinx.coroutines.CoroutineScope? = null,
    ) = SetupWizardViewModel(
        getNetworkInterfacesUseCase = GetNetworkInterfacesUseCase(networkRepo),
        createSubnetUseCase = CreateSubnetUseCase(subnetRepo),
        createDiscoverySourceUseCase = CreateDiscoverySourceUseCase(discoveryRepo),
        triggerDiscoveryRunUseCase = TriggerDiscoveryRunUseCase(discoveryRepo),
        getDiscoverySourcesUseCase = GetDiscoverySourcesUseCase(discoveryRepo),
        getStagingSummaryUseCase = GetStagingSummaryUseCase(dashboardRepo),
        localStorage = localStorage,
        scope = scope,
    )

    // -- Step 1 tests --

    @Test
    fun initialStateIsHidden() {
        val vm = makeVm()
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, vm.state.value.currentStep)
        vm.clear()
    }

    @Test
    fun checkShouldShowMakesVisibleAndLoadsInterfaces() =
        runTest {
            val vm = makeVm(scope = this)
            vm.checkShouldShow(totalSubnets = 0, totalActiveDevices = 0)
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.isVisible)
            assertEquals(2, state.detectedInterfaces.size)
            assertEquals(setOf("192.168.18.0/24", "10.0.0.0/24"), state.selectedCidrs)
            vm.clear()
        }

    @Test
    fun checkShouldShowHidesWhenSubnetsExist() {
        val vm = makeVm()
        vm.checkShouldShow(totalSubnets = 1, totalActiveDevices = 0)
        assertFalse(vm.state.value.isVisible)
        vm.clear()
    }

    @Test
    fun checkShouldShowHidesWhenDevicesExist() {
        val vm = makeVm()
        vm.checkShouldShow(totalSubnets = 0, totalActiveDevices = 5)
        assertFalse(vm.state.value.isVisible)
        vm.clear()
    }

    @Test
    fun checkShouldShowHidesWhenDismissed() {
        val storage = FakeLocalStorage()
        storage.set(SetupWizardViewModel.KEY_WIZARD_DISMISSED, "true")
        val vm = makeVm(localStorage = storage)
        vm.checkShouldShow(totalSubnets = 0, totalActiveDevices = 0)
        assertFalse(vm.state.value.isVisible)
        vm.clear()
    }

    @Test
    fun checkShouldShowHidesWhenCompleted() {
        val storage = FakeLocalStorage()
        storage.set(SetupWizardViewModel.KEY_WIZARD_COMPLETED, "true")
        val vm = makeVm(localStorage = storage)
        vm.checkShouldShow(totalSubnets = 0, totalActiveDevices = 0)
        assertFalse(vm.state.value.isVisible)
        vm.clear()
    }

    @Test
    fun dismissSetsLocalStorageAndHides() =
        runTest {
            val storage = FakeLocalStorage()
            val vm = makeVm(localStorage = storage, scope = this)
            vm.checkShouldShow(totalSubnets = 0, totalActiveDevices = 0)
            advanceUntilIdle()
            assertTrue(vm.state.value.isVisible)

            vm.dismiss()
            assertFalse(vm.state.value.isVisible)
            assertNotNull(storage.get(SetupWizardViewModel.KEY_WIZARD_DISMISSED))
            vm.clear()
        }

    @Test
    fun completeSetsLocalStorageAndHides() {
        val storage = FakeLocalStorage()
        val vm = makeVm(localStorage = storage)
        vm.show()
        assertTrue(vm.state.value.isVisible)

        vm.complete()
        assertFalse(vm.state.value.isVisible)
        assertNotNull(storage.get(SetupWizardViewModel.KEY_WIZARD_COMPLETED))
        vm.clear()
    }

    @Test
    fun showLoadsInterfacesAndSelectsAll() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.isVisible)
            assertEquals(1, state.currentStep)
            assertEquals(2, state.detectedInterfaces.size)
            assertEquals(setOf("192.168.18.0/24", "10.0.0.0/24"), state.selectedCidrs)
            assertFalse(state.isLoading)
            vm.clear()
        }

    @Test
    fun loadInterfacesHandlesError() =
        runTest {
            val networkRepo =
                FakeNetworkRepository(
                    getInterfacesResult =
                        ApiResult.NetworkError(
                            throwable = RuntimeException("Connection refused"),
                        ),
                )
            val vm = makeVm(networkRepo = networkRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            assertTrue(state.detectedInterfaces.isEmpty())
            vm.clear()
        }

    @Test
    fun toggleInterfaceRemovesAndAdds() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(eth0)
            assertEquals(setOf("10.0.0.0/24"), vm.state.value.selectedCidrs)

            vm.toggleInterface(eth0)
            assertEquals(setOf("10.0.0.0/24", "192.168.18.0/24"), vm.state.value.selectedCidrs)
            vm.clear()
        }

    @Test
    fun nextStepCreatesSubnetsAndAdvances() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(wlan0)
            assertEquals(setOf("192.168.18.0/24"), vm.state.value.selectedCidrs)

            vm.nextStep()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(2, state.currentStep)
            assertEquals(1, state.createdSubnetCount)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun nextStepFailsOnEmptySelection() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(eth0)
            vm.toggleInterface(wlan0)
            assertTrue(
                vm.state.value.selectedCidrs
                    .isEmpty(),
            )

            vm.nextStep()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            assertEquals(1, vm.state.value.currentStep)
            vm.clear()
        }

    @Test
    fun nextStepHandlesCreateSubnetError() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    createSubnetResult =
                        ApiResult.Error(
                            code = "SERVER_ERROR",
                            message = "Internal error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.nextStep()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(1, state.currentStep)
            assertNotNull(state.errorMessage)
            assertFalse(state.isLoading)
            vm.clear()
        }

    @Test
    fun nextStepDeduplicatesByCidr() =
        runTest {
            val eth1 =
                NetworkInterface(
                    name = "eth1",
                    ip = "192.168.18.10",
                    cidr = "192.168.18.0/24",
                    mac = "ff:ee:dd:cc:bb:aa",
                    gateway = "192.168.18.1",
                )
            val networkRepo =
                FakeNetworkRepository(
                    getInterfacesResult =
                        ApiResult.Success(listOf(eth0, eth1), requestId = ""),
                )
            val vm = makeVm(networkRepo = networkRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.nextStep()
            advanceUntilIdle()

            assertEquals(2, vm.state.value.currentStep)
            assertEquals(1, vm.state.value.createdSubnetCount)
            vm.clear()
        }

    @Test
    fun nextStepIsBlockedWhileLoading() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(wlan0)
            vm.nextStep()
            vm.nextStep()
            advanceUntilIdle()

            assertEquals(2, vm.state.value.currentStep)
            assertEquals(1, vm.state.value.createdSubnetCount)
            vm.clear()
        }

    @Test
    fun previousStepDecrements() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            vm.previousStep()
            assertEquals(1, vm.state.value.currentStep)
            vm.clear()
        }

    @Test
    fun previousStepDoesNothingOnStep1() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()
            assertEquals(1, vm.state.value.currentStep)

            vm.previousStep()
            assertEquals(1, vm.state.value.currentStep)
            vm.clear()
        }

    @Test
    fun dismissErrorClearsMessage() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(eth0)
            vm.toggleInterface(wlan0)
            vm.nextStep()
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)

            vm.dismissError()
            assertNull(vm.state.value.errorMessage)
            vm.clear()
        }

    // -- Step 2 tests --

    @Test
    fun selectScanTypeUpdatesState() {
        val vm = makeVm()
        assertEquals(ScanType.FULL, vm.state.value.scanType)

        vm.selectScanType(ScanType.ICMP)
        assertEquals(ScanType.ICMP, vm.state.value.scanType)

        vm.selectScanType(ScanType.ARP_DNS)
        assertEquals(ScanType.ARP_DNS, vm.state.value.scanType)
        vm.clear()
    }

    @Test
    fun selectFrequencyUpdatesState() {
        val vm = makeVm()
        assertEquals(ScheduleFrequency.EVERY_HOUR, vm.state.value.scheduleFrequency)

        vm.selectFrequency(ScheduleFrequency.EVERY_15_MIN)
        assertEquals(ScheduleFrequency.EVERY_15_MIN, vm.state.value.scheduleFrequency)
        assertEquals("*/15 * * * *", vm.state.value.scheduleFrequency.cron)

        vm.selectFrequency(ScheduleFrequency.MANUAL)
        assertEquals(ScheduleFrequency.MANUAL, vm.state.value.scheduleFrequency)
        assertNull(vm.state.value.scheduleFrequency.cron)
        vm.clear()
    }

    @Test
    fun step2CreatesSourcesAndAdvancesToStep3() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                )
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(wlan0)
            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            vm.nextStep()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(3, state.currentStep)
            assertEquals(1, state.createdSourceIds.size)
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, discoveryRepo.createSourceCallCount)
            vm.clear()
        }

    @Test
    fun step2CreatesOneSourcePerSubnet() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                )
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)
            assertEquals(2, vm.state.value.createdSubnetCount)

            vm.nextStep()
            advanceUntilIdle()

            assertEquals(3, vm.state.value.currentStep)
            assertEquals(2, discoveryRepo.createSourceCallCount)
            assertEquals(2, vm.state.value.createdSourceIds.size)
            vm.clear()
        }

    @Test
    fun step2HandlesCreateSourceError() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Error(
                            code = "SERVER_ERROR",
                            message = "Failed",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(wlan0)
            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            vm.nextStep()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(2, state.currentStep)
            assertNotNull(state.errorMessage)
            assertFalse(state.isLoading)
            vm.clear()
        }

    @Test
    fun step2BackPreservesSelections() =
        runTest {
            val vm = makeVm(scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(wlan0)
            vm.selectScanType(ScanType.ICMP)
            vm.selectFrequency(ScheduleFrequency.EVERY_24_HOURS)
            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            vm.previousStep()
            assertEquals(1, vm.state.value.currentStep)

            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            assertEquals(ScanType.ICMP, vm.state.value.scanType)
            assertEquals(ScheduleFrequency.EVERY_24_HOURS, vm.state.value.scheduleFrequency)
            vm.clear()
        }

    @Test
    fun step2SkipsDeselectedCidrs() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                )
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)
            assertEquals(2, vm.state.value.createdSubnetCount)

            vm.previousStep()
            assertEquals(1, vm.state.value.currentStep)

            vm.toggleInterface(wlan0)
            assertEquals(setOf("192.168.18.0/24"), vm.state.value.selectedCidrs)

            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            vm.nextStep()
            advanceUntilIdle()

            assertEquals(3, vm.state.value.currentStep)
            assertEquals(1, discoveryRepo.createSourceCallCount)
            vm.clear()
        }

    @Test
    fun step2ReentrancyGuard() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                )
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            vm.show()
            advanceUntilIdle()

            vm.toggleInterface(wlan0)
            vm.nextStep()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.currentStep)

            vm.nextStep()
            vm.nextStep()
            advanceUntilIdle()

            assertEquals(3, vm.state.value.currentStep)
            assertEquals(1, discoveryRepo.createSourceCallCount)
            vm.clear()
        }

    // -- Step 3 tests --

    private fun advanceToStep3(
        discoveryRepo: FakeDiscoveryRepository =
            FakeDiscoveryRepository(
                createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
            ),
        dashboardRepo: FakeDashboardRepository = FakeDashboardRepository(),
        scope: kotlinx.coroutines.CoroutineScope,
    ): Pair<SetupWizardViewModel, FakeDiscoveryRepository> {
        val vm =
            makeVm(
                discoveryRepo = discoveryRepo,
                dashboardRepo = dashboardRepo,
                scope = scope,
            )
        vm.show()
        return vm to discoveryRepo
    }

    private suspend fun kotlinx.coroutines.test.TestScope.goToStep3(vm: SetupWizardViewModel) {
        advanceUntilIdle()
        vm.toggleInterface(wlan0)
        vm.nextStep()
        advanceUntilIdle()
        vm.nextStep()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.currentStep)
    }

    @Test
    fun startScanTriggersRunAndPollsUntilDone() =
        runTest {
            val idleSource =
                DiscoverySource(
                    id = "src-1",
                    name = "test",
                    sourceType = "full",
                    lastStatus = "idle",
                )
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                    triggerRunResult = ApiResult.Success(sampleSource, requestId = ""),
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items = listOf(idleSource),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val stagingDevice =
                StagingDevice(
                    id = "dev-1",
                    hostname = "router",
                    deviceType = "network",
                )
            val dashboardRepo =
                FakeDashboardRepository(
                    getStagingSummaryResult =
                        ApiResult.Success(
                            PaginatedList(
                                items = listOf(stagingDevice),
                                total = 1,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val (vm, repo) = advanceToStep3(discoveryRepo, dashboardRepo, this)
            goToStep3(vm)

            vm.startScan()
            advanceTimeBy(SetupWizardViewModel.POLL_INTERVAL_MS + 100)
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.scanCompleted)
            assertFalse(state.isLoading)
            assertEquals(1, state.discoveredDeviceCount)
            assertEquals(1, repo.triggerRunCallCount)
            vm.clear()
        }

    @Test
    fun startScanHandlesTriggerError() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                    triggerRunResult =
                        ApiResult.Error(
                            code = "SERVER_ERROR",
                            message = "Scan failed",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val (vm, _) = advanceToStep3(discoveryRepo, scope = this)
            goToStep3(vm)

            vm.startScan()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(3, state.currentStep)
            assertNotNull(state.errorMessage)
            assertFalse(state.isLoading)
            assertFalse(state.scanStarted)
            assertFalse(state.scanCompleted)
            vm.clear()
        }

    @Test
    fun startScanDetectsScanError() =
        runTest {
            val errorSource =
                DiscoverySource(
                    id = "src-1",
                    name = "test",
                    sourceType = "full",
                    lastStatus = "error",
                )
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                    triggerRunResult = ApiResult.Success(sampleSource, requestId = ""),
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items = listOf(errorSource),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val (vm, _) = advanceToStep3(discoveryRepo, scope = this)
            goToStep3(vm)

            vm.startScan()
            advanceTimeBy(SetupWizardViewModel.POLL_INTERVAL_MS + 100)
            advanceUntilIdle()

            val state = vm.state.value
            assertNotNull(state.errorMessage)
            assertFalse(state.isLoading)
            assertFalse(state.scanStarted)
            assertFalse(state.scanCompleted)
            vm.clear()
        }

    @Test
    fun startScanReentrancyGuard() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                    triggerRunResult = ApiResult.Success(sampleSource, requestId = ""),
                    getSourcesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items =
                                    listOf(
                                        DiscoverySource(
                                            id = "src-1",
                                            lastStatus = "idle",
                                        ),
                                    ),
                                total = 1L,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val (vm, repo) = advanceToStep3(discoveryRepo, scope = this)
            goToStep3(vm)

            vm.startScan()
            vm.startScan()
            advanceTimeBy(SetupWizardViewModel.POLL_INTERVAL_MS + 100)
            advanceUntilIdle()

            assertEquals(1, repo.triggerRunCallCount)
            vm.clear()
        }

    @Test
    fun completeSetsFlagAndHides() =
        runTest {
            val storage = FakeLocalStorage()
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                )
            val vm =
                makeVm(
                    discoveryRepo = discoveryRepo,
                    localStorage = storage,
                    scope = this,
                )
            vm.show()
            goToStep3(vm)

            vm.complete()
            assertFalse(vm.state.value.isVisible)
            assertNotNull(storage.get(SetupWizardViewModel.KEY_WIZARD_COMPLETED))
            vm.clear()
        }

    @Test
    fun backFromStep3PreservesState() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.Success(sampleSource, requestId = ""),
                )
            val (vm, _) = advanceToStep3(discoveryRepo, scope = this)
            goToStep3(vm)

            assertTrue(
                vm.state.value.createdSourceIds
                    .isNotEmpty(),
            )

            vm.previousStep()
            assertEquals(2, vm.state.value.currentStep)

            vm.nextStep()
            advanceUntilIdle()
            assertEquals(3, vm.state.value.currentStep)
            assertTrue(
                vm.state.value.createdSourceIds
                    .isNotEmpty(),
            )
            vm.clear()
        }
}
