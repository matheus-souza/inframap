package com.inframap.frontend.ui.devices

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
import com.inframap.frontend.domain.usecase.device.UpdateDeviceUseCase
import com.inframap.frontend.fakes.FakeDeviceRepository
import kotlinx.coroutines.CoroutineScope
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
class EditDeviceViewModelTest {
    private val sampleDevice =
        Device(
            id = "d1",
            hostname = "router-01",
            ipAddress = "10.0.0.1",
            deviceType = "router",
            status = "active",
        )

    private fun makeVm(
        repo: FakeDeviceRepository =
            FakeDeviceRepository(
                getDeviceByIdResult = ApiResult.Success(sampleDevice, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = EditDeviceViewModel(
        "d1",
        GetDeviceByIdUseCase(repo),
        UpdateDeviceUseCase(repo),
        scope = scope,
    )

    @Test
    fun loadDevicePrepopulatesStateSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertEquals("router-01", state.hostname)
                assertEquals("10.0.0.1", state.ipAddress)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesApiError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDeviceByIdResult =
                        ApiResult.Error(
                            code = "NOT_FOUND",
                            message = "Device not found",
                            requestId = "",
                            httpStatus = 404,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertEquals("Device not found", state.errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDeviceByIdResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

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
    fun updateDeviceIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.updateDevice()
                assertTrue(vm.state.value.isSubmitting)

                vm.updateDevice()
                assertTrue(vm.state.value.isSubmitting)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateDeviceValidatesAndSucceeds() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onHostnameChanged("router-01-updated")
                vm.onIpAddressChanged("10.0.0.1")
                vm.onMacAddressChanged("AA:BB:CC:DD:EE:FF")
                vm.onDeviceTypeChanged("router")
                vm.onStatusChanged("active")

                vm.updateDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                assertNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateDeviceHandlesApiError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDeviceByIdResult = ApiResult.Success(sampleDevice, requestId = ""),
                    updateDeviceResult =
                        ApiResult.Error(
                            code = "DUPLICATE",
                            message = "Duplicate hostname",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onHostnameChanged("duplicate-name")
                vm.updateDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals("Duplicate hostname", state.errorMessage?.asStringAsync())
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateDeviceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDeviceByIdResult = ApiResult.Success(sampleDevice, requestId = ""),
                    updateDeviceResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.updateDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.errorMessage)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateDeviceFailsValidationWhenHostnameIsEmpty() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onHostnameChanged("")
                vm.updateDevice()

                assertTrue(
                    vm.state.value.validationErrors
                        .containsKey("hostname"),
                )
                assertFalse(vm.state.value.isSuccess)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
