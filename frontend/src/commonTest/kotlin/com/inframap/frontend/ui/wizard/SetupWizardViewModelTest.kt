package com.inframap.frontend.ui.wizard

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.fakes.FakeNetworkRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun makeVm(
        networkRepo: FakeNetworkRepository =
            FakeNetworkRepository(
                getInterfacesResult = ApiResult.Success(listOf(eth0, wlan0), requestId = ""),
            ),
        subnetRepo: FakeSubnetRepository =
            FakeSubnetRepository(
                createSubnetResult = ApiResult.Success(sampleSubnet, requestId = ""),
            ),
        localStorage: FakeLocalStorage = FakeLocalStorage(),
        scope: kotlinx.coroutines.CoroutineScope? = null,
    ) = SetupWizardViewModel(
        getNetworkInterfacesUseCase = GetNetworkInterfacesUseCase(networkRepo),
        createSubnetUseCase = CreateSubnetUseCase(subnetRepo),
        localStorage = localStorage,
        scope = scope,
    )

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
}
