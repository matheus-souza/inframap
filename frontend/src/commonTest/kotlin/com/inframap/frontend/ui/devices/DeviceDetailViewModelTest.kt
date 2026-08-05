package com.inframap.frontend.ui.devices

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
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
class DeviceDetailViewModelTest {
    private val sampleDevice =
        Device(
            id = "d1",
            hostname = "router-01",
            ipAddress = "192.168.1.1",
            deviceType = "router",
            status = "active",
        )

    private fun makeVm(
        repo: FakeDeviceRepository =
            FakeDeviceRepository(
                getDeviceByIdResult = ApiResult.Success(sampleDevice, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = DeviceDetailViewModel(
        "d1",
        GetDeviceByIdUseCase(repo),
        DeleteDeviceUseCase(repo),
        scope = scope,
    )

    @Test
    fun loadDevicePopulatesDetailsSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNull(state.errorMessage)
                assertEquals("router-01", state.device?.hostname)
                assertEquals("192.168.1.1", state.device?.ipAddress)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesNotFoundApiError() =
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
    fun deleteDeviceIgnoresReentrantCallsWhenDeleting() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.deleteDevice {}
                assertTrue(vm.state.value.isDeleting)

                vm.deleteDevice {}
                assertTrue(vm.state.value.isDeleting)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesApiError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDeviceByIdResult = ApiResult.Success(sampleDevice, requestId = ""),
                    deleteDeviceResult =
                        ApiResult.Error(
                            code = "LOCKED",
                            message = "Deletion locked",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.deleteDevice {}
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals("Deletion locked", state.errorMessage?.asStringAsync())
                assertFalse(state.isDeleting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDeviceByIdResult = ApiResult.Success(sampleDevice, requestId = ""),
                    deleteDeviceResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.deleteDevice {}
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.errorMessage)
                assertFalse(state.isDeleting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun openCloseAndDeleteDialogWorkflow() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.openDeleteDialog()
                assertTrue(vm.state.value.showDeleteDialog)

                vm.closeDeleteDialog()
                assertFalse(vm.state.value.showDeleteDialog)

                var navTriggered = false
                vm.deleteDevice { navTriggered = true }
                advanceUntilIdle()

                assertTrue(navTriggered)
                assertFalse(vm.state.value.isDeleting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
