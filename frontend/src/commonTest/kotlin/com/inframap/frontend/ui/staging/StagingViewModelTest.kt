package com.inframap.frontend.ui.staging

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.usecase.staging.ApproveDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.DismissDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.fakes.FakeStagingRepository
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
class StagingViewModelTest {
    private val sampleStagingDevice =
        StagingDevice(
            id = "st1",
            hostname = "stg-switch-01",
            deviceType = "switch",
            status = "pending",
        )

    private val pagedResult =
        PaginatedList(items = listOf(sampleStagingDevice), total = 1, page = 1, perPage = 50)

    private fun makeVm(
        repo: FakeStagingRepository =
            FakeStagingRepository(
                getStagingDevicesResult = ApiResult.Success(pagedResult, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = StagingViewModel(
        GetStagingDevicesUseCase(repo),
        ApproveDeviceUseCase(repo),
        DismissDeviceUseCase(repo),
        scope = scope,
    )

    @Test
    fun loadStagingDevicesPopulatesListSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNull(state.errorMessage)
                assertEquals(1, state.devices.size)
                assertEquals("stg-switch-01", state.devices.first().hostname)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadStagingDevicesHandlesApiError() =
        runTest {
            val repo =
                FakeStagingRepository(
                    getStagingDevicesResult =
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
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadStagingDevicesHandlesNetworkError() =
        runTest {
            val repo =
                FakeStagingRepository(
                    getStagingDevicesResult = ApiResult.NetworkError(RuntimeException("Network failure")),
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
    fun approveDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.approveDevice(sampleStagingDevice)
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.isProcessingAction)
                assertNotNull(state.toastMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun approveDeviceIgnoresReentrantCallsWhenProcessing() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.approveDevice(sampleStagingDevice)
                assertTrue(vm.state.value.isProcessingAction)

                vm.approveDevice(sampleStagingDevice)
                assertTrue(vm.state.value.isProcessingAction)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun dismissDeviceWorkflowCompletesSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDismissDevice(sampleStagingDevice)
                assertEquals(
                    "st1",
                    vm.state.value.deviceToDismiss
                        ?.id,
                )

                vm.dismissDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNull(state.deviceToDismiss)
                assertFalse(state.isProcessingAction)
                assertNotNull(state.toastMessage)
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
            vm.clear()
        }

    @Test
    fun dismissDeviceHandlesApiError() =
        runTest {
            val repo =
                FakeStagingRepository(
                    getStagingDevicesResult = ApiResult.Success(pagedResult, requestId = ""),
                    dismissDeviceResult =
                        ApiResult.Error(
                            code = "DISMISS_ERROR",
                            message = "Cannot dismiss device",
                            requestId = "",
                            httpStatus = 400,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDismissDevice(sampleStagingDevice)

                vm.dismissDevice()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.actionErrorMessage)
                assertFalse(state.isProcessingAction)
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissActionError()
            assertNull(vm.state.value.actionErrorMessage)

            vm.cancelDismissDevice()
            assertNull(vm.state.value.deviceToDismiss)
            vm.clear()
        }
}
