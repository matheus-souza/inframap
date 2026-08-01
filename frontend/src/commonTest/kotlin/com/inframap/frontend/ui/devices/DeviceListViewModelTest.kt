package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

    private fun repo(
        listResult: ApiResult<PaginatedList<Device>> = ApiResult.Success(pagedDevices, requestId = ""),
        deleteResult: ApiResult<Unit> = ApiResult.Success(Unit, requestId = ""),
    ): DeviceRepository =
        object : DeviceRepository {
            override suspend fun getDevices(
                page: Int,
                perPage: Int,
                search: String,
            ) = listResult

            override suspend fun getDeviceById(id: String) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun updateDevice(
                id: String,
                request: UpdateDeviceRequest,
            ) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun deleteDevice(id: String) = deleteResult
        }

    @Test
    fun loadDevicesPopulatesListSuccessfully() =
        runTest {
            val r = repo()
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.devices.size)
            assertEquals("router-01", state.devices.first().hostname)
            vm.clear()
        }

    @Test
    fun loadDevicesHandlesApiError() =
        runTest {
            val r =
                repo(
                    listResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB Error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("DB Error", state.errorMessage?.asStringAsync())
            vm.clear()
        }

    @Test
    fun loadDevicesHandlesNetworkError() =
        runTest {
            val r = repo(listResult = ApiResult.NetworkError(RuntimeException("Network failure")))
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun searchAndPaginationStateUpdates() =
        runTest {
            val r = repo()
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onSearchQueryChanged("router")
            assertEquals("router", vm.state.value.searchQuery)

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
            vm.clear()
        }

    @Test
    fun deleteDeviceIgnoresReentrantCallsWhenDeleting() =
        runTest {
            val r = repo()
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.confirmDeleteDevice(sampleDevice)

            vm.deleteDevice()
            assertTrue(vm.state.value.isDeleting)

            vm.deleteDevice()
            assertTrue(vm.state.value.isDeleting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun deleteDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val r = repo()
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.confirmDeleteDevice(sampleDevice)
            assertEquals(
                "d1",
                vm.state.value.deviceToDelete
                    ?.id,
            )

            val deleteDeferred = async { vm.state.first { !it.isDeleting && it.toastMessage != null } }
            vm.deleteDevice()
            advanceUntilIdle()
            deleteDeferred.await()

            assertNull(vm.state.value.deviceToDelete)
            assertFalse(vm.state.value.isDeleting)
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesApiError() =
        runTest {
            val r =
                repo(
                    deleteResult =
                        ApiResult.Error(
                            code = "LOCKED",
                            message = "Cannot delete active router",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)
            advanceUntilIdle()

            vm.confirmDeleteDevice(sampleDevice)

            val stateDeferred = async { vm.state.first { it.deleteErrorMessage != null } }
            vm.deleteDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Cannot delete active router", state.deleteErrorMessage?.asStringAsync())
            assertFalse(state.isDeleting)
            assertNull(state.errorMessage)

            vm.dismissDeleteError()
            assertNull(vm.state.value.deleteErrorMessage)
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesNetworkError() =
        runTest {
            val r = repo(deleteResult = ApiResult.NetworkError(RuntimeException("Network failure")))
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)
            advanceUntilIdle()

            vm.confirmDeleteDevice(sampleDevice)

            val stateDeferred = async { vm.state.first { it.deleteErrorMessage != null } }
            vm.deleteDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNotNull(state.deleteErrorMessage)
            assertFalse(state.isDeleting)
            assertNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun cancelDeleteDeviceClearsSelection() =
        runTest {
            val r = repo()
            val vm = DeviceListViewModel(GetDevicesUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.confirmDeleteDevice(sampleDevice)
            vm.cancelDeleteDevice()
            assertNull(vm.state.value.deviceToDelete)
            vm.clear()
        }
}
