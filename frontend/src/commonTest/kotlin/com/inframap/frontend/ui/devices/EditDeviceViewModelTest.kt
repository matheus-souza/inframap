package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
import com.inframap.frontend.domain.usecase.device.UpdateDeviceUseCase
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
class EditDeviceViewModelTest {
    private val sampleDevice =
        Device(
            id = "d1",
            hostname = "router-01",
            ipAddress = "10.0.0.1",
            deviceType = "router",
            status = "active",
        )

    private fun successRepo(updateResult: ApiResult<Device> = ApiResult.Success(sampleDevice, requestId = "")): DeviceRepository =
        object : DeviceRepository {
            override suspend fun getDevices(
                page: Int,
                perPage: Int,
                search: String,
            ) = ApiResult.Success(PaginatedList(items = listOf(sampleDevice), total = 1, page = 1, perPage = 50), requestId = "")

            override suspend fun getDeviceById(id: String) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

            override suspend fun updateDevice(
                id: String,
                request: UpdateDeviceRequest,
            ) = updateResult

            override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
        }

    @Test
    fun loadDevicePrepopulatesStateSuccessfully() =
        runTest {
            val repo = successRepo()
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(repo), UpdateDeviceUseCase(repo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertEquals("router-01", state.hostname)
            assertEquals("10.0.0.1", state.ipAddress)
            vm.clear()
        }

    @Test
    fun loadDeviceHandlesApiError() =
        runTest {
            val errorRepo =
                object : DeviceRepository {
                    override suspend fun getDevices(
                        page: Int,
                        perPage: Int,
                        search: String,
                    ) = ApiResult.Success(PaginatedList(items = emptyList<Device>(), total = 0, page = 1, perPage = 50), requestId = "")

                    override suspend fun getDeviceById(id: String) =
                        ApiResult.Error(code = "NOT_FOUND", message = "Device not found", requestId = "", httpStatus = 404)

                    override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun updateDevice(
                        id: String,
                        request: UpdateDeviceRequest,
                    ) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
                }
            val vm = EditDeviceViewModel("d999", GetDeviceByIdUseCase(errorRepo), UpdateDeviceUseCase(errorRepo), scope = this)

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
            val errorRepo =
                object : DeviceRepository {
                    override suspend fun getDevices(
                        page: Int,
                        perPage: Int,
                        search: String,
                    ) = ApiResult.Success(PaginatedList(items = emptyList<Device>(), total = 0, page = 1, perPage = 50), requestId = "")

                    override suspend fun getDeviceById(id: String) = ApiResult.NetworkError(RuntimeException("Network failure"))

                    override suspend fun createDevice(request: CreateDeviceRequest) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun updateDevice(
                        id: String,
                        request: UpdateDeviceRequest,
                    ) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
                }
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(errorRepo), UpdateDeviceUseCase(errorRepo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun updateDeviceIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val repo = successRepo()
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(repo), UpdateDeviceUseCase(repo), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.updateDevice()
            assertTrue(vm.state.value.isSubmitting)

            vm.updateDevice()
            assertTrue(vm.state.value.isSubmitting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun updateDeviceValidatesAndSucceeds() =
        runTest {
            val repo = successRepo()
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(repo), UpdateDeviceUseCase(repo), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onHostnameChanged("router-01-updated")
            vm.onIpAddressChanged("10.0.0.1")
            vm.onMacAddressChanged("AA:BB:CC:DD:EE:FF")
            vm.onDeviceTypeChanged("router")
            vm.onStatusChanged("active")

            val stateDeferred = async { vm.state.first { it.isSuccess } }
            vm.updateDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertTrue(state.isSuccess)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)

            vm.clear()
        }

    @Test
    fun updateDeviceHandlesApiError() =
        runTest {
            val repo =
                successRepo(
                    updateResult =
                        ApiResult.Error(
                            code = "DUPLICATE",
                            message = "Duplicate hostname",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(repo), UpdateDeviceUseCase(repo), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onHostnameChanged("duplicate-name")
            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.updateDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Duplicate hostname", state.errorMessage?.asStringAsync())
            assertFalse(state.isSubmitting)

            vm.clear()
        }

    @Test
    fun updateDeviceHandlesNetworkError() =
        runTest {
            val repo =
                successRepo(
                    updateResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(repo), UpdateDeviceUseCase(repo), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.updateDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNotNull(state.errorMessage)
            assertFalse(state.isSubmitting)

            vm.clear()
        }

    @Test
    fun updateDeviceFailsValidationWhenHostnameIsEmpty() =
        runTest {
            val repo = successRepo()
            val vm = EditDeviceViewModel("d1", GetDeviceByIdUseCase(repo), UpdateDeviceUseCase(repo), scope = this)
            val loadDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            loadDeferred.await()

            vm.onHostnameChanged("")
            vm.updateDevice()

            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("hostname"),
            )
            assertFalse(vm.state.value.isSuccess)

            vm.clear()
        }
}
