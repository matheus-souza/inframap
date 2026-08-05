package com.inframap.frontend.ui.devices

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
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
class DeviceListViewModelTest {
    private val sampleDevice =
        Device(
            id = "d1",
            hostname = "router-01",
            deviceType = "router",
            status = "active",
        )

    private val pagedDevices =
        PaginatedList(items = listOf(sampleDevice), total = 1, page = 1, perPage = 50)

    private fun makeVm(
        repo: FakeDeviceRepository =
            FakeDeviceRepository(
                getDevicesResult = ApiResult.Success(pagedDevices, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = DeviceListViewModel(
        GetDevicesUseCase(repo),
        DeleteDeviceUseCase(repo),
        scope = scope,
    )

    @Test
    fun loadDevicesPopulatesListSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNull(state.errorMessage)
                assertEquals(1, state.devices.size)
                assertEquals("router-01", state.devices.first().hostname)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDevicesHandlesApiError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB Error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertEquals("DB Error", state.errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadDevicesHandlesNetworkError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDevicesResult = ApiResult.NetworkError(RuntimeException("Network failure")),
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
    fun searchAndPaginationStateUpdates() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onSearchQueryChanged("router")
                assertEquals("router", vm.state.value.searchQuery)

                vm.dismissToast()
                assertNull(vm.state.value.toastMessage)
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

                vm.confirmDeleteDevice(sampleDevice)

                vm.deleteDevice()
                assertTrue(vm.state.value.isDeleting)

                vm.deleteDevice()
                assertTrue(vm.state.value.isDeleting)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun deleteDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteDevice(sampleDevice)
                assertEquals(
                    "d1",
                    vm.state.value.deviceToDelete
                        ?.id,
                )

                vm.deleteDevice()
                advanceUntilIdle()

                assertNull(vm.state.value.deviceToDelete)
                assertFalse(vm.state.value.isDeleting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesApiError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDevicesResult = ApiResult.Success(pagedDevices, requestId = ""),
                    deleteDeviceResult =
                        ApiResult.Error(
                            code = "LOCKED",
                            message = "Cannot delete active router",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteDevice(sampleDevice)

                vm.deleteDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals("Cannot delete active router", state.deleteErrorMessage?.asStringAsync())
                assertFalse(state.isDeleting)
                assertNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissDeleteError()
            assertNull(vm.state.value.deleteErrorMessage)
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDeviceRepository(
                    getDevicesResult = ApiResult.Success(pagedDevices, requestId = ""),
                    deleteDeviceResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteDevice(sampleDevice)

                vm.deleteDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.deleteErrorMessage)
                assertFalse(state.isDeleting)
                assertNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun cancelDeleteDeviceClearsSelection() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteDevice(sampleDevice)
                vm.cancelDeleteDevice()
                assertNull(vm.state.value.deviceToDelete)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
