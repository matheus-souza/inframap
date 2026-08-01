package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.device.CreateDeviceUseCase
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
class CreateDeviceViewModelTest {
    private val sampleCreatedDevice =
        Device(
            id = "d100",
            hostname = "switch-core",
            deviceType = "switch",
            status = "active",
        )

    private fun repo(createResult: ApiResult<Device> = ApiResult.Success(sampleCreatedDevice, requestId = "")): DeviceRepository =
        object : DeviceRepository {
            override suspend fun getDevices(
                page: Int,
                perPage: Int,
                search: String,
            ) = ApiResult.Success(PaginatedList(items = emptyList<Device>(), total = 0, page = 1, perPage = 50), requestId = "")

            override suspend fun getDeviceById(id: String) = ApiResult.Success(sampleCreatedDevice, requestId = "")

            override suspend fun createDevice(request: CreateDeviceRequest) = createResult

            override suspend fun updateDevice(
                id: String,
                request: UpdateDeviceRequest,
            ) = ApiResult.Success(sampleCreatedDevice, requestId = "")

            override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, requestId = "")
        }

    @Test
    fun validationFailsWhenHostnameIsEmpty() =
        runTest {
            val vm = CreateDeviceViewModel(CreateDeviceUseCase(repo()), scope = this)

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
            val vm = CreateDeviceViewModel(CreateDeviceUseCase(repo()), scope = this)

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
            val vm = CreateDeviceViewModel(CreateDeviceUseCase(repo()), scope = this)
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
            val vm = CreateDeviceViewModel(CreateDeviceUseCase(repo()), scope = this)
            vm.onHostnameChanged("switch-core")
            vm.onIpAddressChanged("192.168.1.50")
            vm.onMacAddressChanged("00:11:22:33:44:55")
            vm.onDeviceTypeChanged("switch")

            val stateDeferred = async { vm.state.first { it.createdDeviceId != null } }
            vm.createDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("d100", state.createdDeviceId)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)

            vm.clear()
        }

    @Test
    fun createDeviceHandlesApiError() =
        runTest {
            val errorRepo =
                repo(
                    createResult =
                        ApiResult.Error(
                            code = "INVALID_IP",
                            message = "Invalid IP format",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = CreateDeviceViewModel(CreateDeviceUseCase(errorRepo), scope = this)
            vm.onHostnameChanged("invalid-dev")
            vm.onDeviceTypeChanged("router")

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.createDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertEquals("Invalid IP format", state.errorMessage?.asStringAsync())
            assertFalse(state.isSubmitting)
            vm.clear()
        }

    @Test
    fun createDeviceHandlesNetworkError() =
        runTest {
            val errorRepo = repo(createResult = ApiResult.NetworkError(RuntimeException("Network failure")))
            val vm = CreateDeviceViewModel(CreateDeviceUseCase(errorRepo), scope = this)
            vm.onHostnameChanged("router-net")
            vm.onDeviceTypeChanged("router")

            val stateDeferred = async { vm.state.first { it.errorMessage != null } }
            vm.createDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNotNull(state.errorMessage)
            assertFalse(state.isSubmitting)
            vm.clear()
        }
}
