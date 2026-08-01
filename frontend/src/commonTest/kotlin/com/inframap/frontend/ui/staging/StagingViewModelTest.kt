package com.inframap.frontend.ui.staging

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.usecase.staging.ApproveDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.DismissDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
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
class StagingViewModelTest {
    private val sampleStagingDevice =
        StagingDevice(
            id = "st1",
            hostname = "stg-switch-01",
            deviceType = "switch",
            status = "pending",
        )

    private val sampleDevice =
        Device(
            id = "st1",
            hostname = "stg-switch-01",
            deviceType = "switch",
            status = "active",
        )

    private val pagedResult =
        PaginatedList(items = listOf(sampleStagingDevice), total = 1, page = 1, perPage = 50)

    private val fakeGetSuccess =
        GetStagingDevicesUseCase(
            object : StagingRepository {
                override suspend fun getStagingDevices(
                    page: Int,
                    perPage: Int,
                ) = ApiResult.Success(pagedResult, requestId = "")

                override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, requestId = "")
            },
        )

    private val fakeApproveSuccess =
        ApproveDeviceUseCase(
            object : StagingRepository {
                override suspend fun getStagingDevices(
                    page: Int,
                    perPage: Int,
                ) = ApiResult.Success(pagedResult, requestId = "")

                override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, requestId = "")
            },
        )

    private val fakeDismissSuccess =
        DismissDeviceUseCase(
            object : StagingRepository {
                override suspend fun getStagingDevices(
                    page: Int,
                    perPage: Int,
                ) = ApiResult.Success(pagedResult, requestId = "")

                override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, requestId = "")
            },
        )

    @Test
    fun loadStagingDevicesPopulatesListSuccessfully() =
        runTest {
            val vm = StagingViewModel(fakeGetSuccess, fakeApproveSuccess, fakeDismissSuccess, scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.devices.size)
            assertEquals("stg-switch-01", state.devices.first().hostname)
            vm.clear()
        }

    @Test
    fun loadStagingDevicesHandlesApiError() =
        runTest {
            val repo =
                object : StagingRepository {
                    override suspend fun getStagingDevices(
                        page: Int,
                        perPage: Int,
                    ) = ApiResult.Error(code = "DB_ERROR", message = "DB Error", requestId = "", httpStatus = 500)

                    override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, requestId = "")
                }
            val vm =
                StagingViewModel(
                    GetStagingDevicesUseCase(repo),
                    ApproveDeviceUseCase(repo),
                    DismissDeviceUseCase(repo),
                    scope = this,
                )

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadStagingDevicesHandlesNetworkError() =
        runTest {
            val repo =
                object : StagingRepository {
                    override suspend fun getStagingDevices(
                        page: Int,
                        perPage: Int,
                    ) = ApiResult.NetworkError(RuntimeException("Network failure"))

                    override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, requestId = "")
                }
            val vm =
                StagingViewModel(
                    GetStagingDevicesUseCase(repo),
                    ApproveDeviceUseCase(repo),
                    DismissDeviceUseCase(repo),
                    scope = this,
                )

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun approveDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val vm = StagingViewModel(fakeGetSuccess, fakeApproveSuccess, fakeDismissSuccess, scope = this)
            advanceUntilIdle()

            val stateDeferred = async { vm.state.first { !it.isProcessingAction && it.toastMessage != null } }
            vm.approveDevice(sampleStagingDevice)
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertFalse(state.isProcessingAction)
            assertNotNull(state.toastMessage)
            vm.clear()
        }

    @Test
    fun approveDeviceIgnoresReentrantCallsWhenProcessing() =
        runTest {
            val vm = StagingViewModel(fakeGetSuccess, fakeApproveSuccess, fakeDismissSuccess, scope = this)
            advanceUntilIdle()

            vm.approveDevice(sampleStagingDevice)
            assertTrue(vm.state.value.isProcessingAction)

            vm.approveDevice(sampleStagingDevice)
            assertTrue(vm.state.value.isProcessingAction)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun dismissDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val vm = StagingViewModel(fakeGetSuccess, fakeApproveSuccess, fakeDismissSuccess, scope = this)
            advanceUntilIdle()

            vm.confirmDismissDevice(sampleStagingDevice)
            assertEquals(
                "st1",
                vm.state.value.deviceToDismiss
                    ?.id,
            )

            val stateDeferred = async { vm.state.first { !it.isProcessingAction && it.toastMessage != null } }
            vm.dismissDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNull(state.deviceToDismiss)
            assertFalse(state.isProcessingAction)
            assertNotNull(state.toastMessage)

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
            vm.clear()
        }

    @Test
    fun dismissDeviceHandlesApiError() =
        runTest {
            val repo =
                object : StagingRepository {
                    override suspend fun getStagingDevices(
                        page: Int,
                        perPage: Int,
                    ) = ApiResult.Success(pagedResult, requestId = "")

                    override suspend fun approveDevice(id: String) = ApiResult.Success(sampleDevice, requestId = "")

                    override suspend fun dismissDevice(id: String) =
                        ApiResult.Error(
                            code = "DISMISS_ERROR",
                            message = "Cannot dismiss device",
                            requestId = "",
                            httpStatus = 400,
                        )
                }
            val vm =
                StagingViewModel(
                    GetStagingDevicesUseCase(repo),
                    ApproveDeviceUseCase(repo),
                    DismissDeviceUseCase(repo),
                    scope = this,
                )
            advanceUntilIdle()

            vm.confirmDismissDevice(sampleStagingDevice)

            val stateDeferred = async { vm.state.first { it.actionErrorMessage != null } }
            vm.dismissDevice()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertNotNull(state.actionErrorMessage)
            assertFalse(state.isProcessingAction)

            vm.dismissActionError()
            assertNull(vm.state.value.actionErrorMessage)

            vm.cancelDismissDevice()
            assertNull(vm.state.value.deviceToDismiss)
            vm.clear()
        }
}
