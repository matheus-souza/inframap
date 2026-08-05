package com.inframap.frontend.ui.devices

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.usecase.device.CreateDeviceUseCase
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
class CreateDeviceViewModelTest {
    private val sampleCreatedDevice =
        Device(
            id = "d100",
            hostname = "switch-core",
            deviceType = "switch",
            status = "active",
        )

    private fun makeVm(
        repo: FakeDeviceRepository =
            FakeDeviceRepository(
                createDeviceResult = ApiResult.Success(sampleCreatedDevice, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = CreateDeviceViewModel(CreateDeviceUseCase(repo), scope = scope)

    @Test
    fun validationFailsWhenHostnameIsEmpty() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onHostnameChanged("")
            vm.onDeviceTypeChanged("")
            vm.createDevice()

            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("device_type"),
            )
            assertNull(vm.state.value.createdDeviceId)
            vm.clear()
        }

    @Test
    fun fieldChangeListenersClearValidationErrors() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onHostnameChanged("")
            vm.createDevice()
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )

            vm.onHostnameChanged("router-01")
            vm.onIpAddressChanged("192.168.1.1")
            vm.onMacAddressChanged("00:11:22:33:44:55")
            vm.onDeviceTypeChanged("router")

            assertEquals("router-01", vm.state.value.hostname)
            assertEquals("192.168.1.1", vm.state.value.ipAddress)
            assertEquals("00:11:22:33:44:55", vm.state.value.macAddress)
            assertEquals("router", vm.state.value.deviceType)
            assertFalse(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )
            vm.clear()
        }

    @Test
    fun createDeviceIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onHostnameChanged("switch-core")
            vm.onDeviceTypeChanged("switch")

            vm.createDevice()
            assertTrue(vm.state.value.isSubmitting)

            vm.createDevice()
            assertTrue(vm.state.value.isSubmitting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun createDeviceSucceedsWithValidPayload() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onHostnameChanged("switch-core")
            vm.onIpAddressChanged("192.168.1.50")
            vm.onMacAddressChanged("00:11:22:33:44:55")
            vm.onDeviceTypeChanged("switch")

            vm.state.test {
                skipItems(1)
                vm.createDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals("d100", state.createdDeviceId)
                assertFalse(state.isSubmitting)
                assertNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createDeviceHandlesApiError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    createDeviceResult =
                        ApiResult.Error(
                            code = "INVALID_IP",
                            message = "Invalid IP format",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)
            vm.onHostnameChanged("invalid-dev")
            vm.onDeviceTypeChanged("router")

            vm.state.test {
                skipItems(1)
                vm.createDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals("Invalid IP format", state.errorMessage?.asStringAsync())
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createDeviceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    createDeviceResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)
            vm.onHostnameChanged("router-net")
            vm.onDeviceTypeChanged("router")

            vm.state.test {
                skipItems(1)
                vm.createDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.errorMessage)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
