package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
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
class DeviceDetailViewModelTest {
    private val sampleDevice =
        Device(
            id = "d1",
            hostname = "router-01",
            ipAddress = "192.168.1.1",
            deviceType = "router",
            status = "active",
        )

    private fun repo(
        getResult: ApiResult<Device> = ApiResult.Success(sampleDevice, requestId = ""),
        deleteResult: ApiResult<Unit> = ApiResult.Success(Unit, requestId = ""),
    ): DeviceRepository =
        object : DeviceRepository {
            override suspend fun getDevices(
                page: Int,
                perPage: Int,
                search: String,
            ) = ApiResult.Success(PaginatedList(items = listOf(sampleDevice), total = 1, page = 1, perPage = 50), requestId = "")

            override suspend fun getDeviceById(id: String) = getResult

            override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun updateDevice(
                id: String,
                request: UpdateDeviceRequest,
            ) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun deleteDevice(id: String) = deleteResult
        }

    @Test
    fun loadDevicePopulatesDetailsSuccessfully() =
        runTest {
            val r = repo()
            val vm = DeviceDetailViewModel("d1", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals("router-01", state.device?.hostname)
            assertEquals("192.168.1.1", state.device?.ipAddress)
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesNotFoundApiError() =
        runTest {
            val r =
                repo(
                    getResult =
                        ApiResult.Error(
                            code = "NOT_FOUND",
                            message = "Device not found",
                            requestId = "",
                            httpStatus = 404,
                        ),
                )
            val vm = DeviceDetailViewModel("unknown", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("Device not found", state.errorMessage?.asStringAsync())
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesNetworkError() =
        runTest {
            val r = repo(getResult = ApiResult.NetworkError(RuntimeException("Network failure")))
            val vm = DeviceDetailViewModel("d1", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun deleteDeviceIgnoresReentrantCallsWhenDeleting() =
        runTest {
            val r = repo()
            val vm = DeviceDetailViewModel("d1", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.deleteDevice {}
            assertTrue(vm.state.value.isDeleting)

            vm.deleteDevice {}
            assertTrue(vm.state.value.isDeleting)

            advanceUntilIdle()
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
                            message = "Deletion locked",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = DeviceDetailViewModel("d1", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.deleteDevice {}
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Deletion locked", state.errorMessage?.asStringAsync())
            assertFalse(state.isDeleting)
            vm.clear()
        }

    @Test
    fun deleteDeviceHandlesNetworkError() =
        runTest {
            val r = repo(deleteResult = ApiResult.NetworkError(RuntimeException("Network failure")))
            val vm = DeviceDetailViewModel("d1", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.deleteDevice {}
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNotNull(state.errorMessage)
            assertFalse(state.isDeleting)
            vm.clear()
        }

    @Test
    fun openCloseAndDeleteDialogWorkflow() =
        runTest {
            val r = repo()
            val vm = DeviceDetailViewModel("d1", GetDeviceByIdUseCase(r), DeleteDeviceUseCase(r), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.openDeleteDialog()
            assertTrue(vm.state.value.showDeleteDialog)

            vm.closeDeleteDialog()
            assertFalse(vm.state.value.showDeleteDialog)

            var navTriggered = false
            val deleteDeferred = async { vm.state.first { !it.isDeleting } }
            vm.deleteDevice { navTriggered = true }
            advanceUntilIdle()
            deleteDeferred.await()

            assertTrue(navTriggered)
            assertFalse(vm.state.value.isDeleting)
            vm.clear()
        }
}
