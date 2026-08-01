package com.inframap.frontend.ui.staging

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.usecase.staging.ApproveDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.DismissDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.ui.base.BaseListViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class StagingViewModel(
    private val getStagingDevicesUseCase: GetStagingDevicesUseCase,
    private val approveDeviceUseCase: ApproveDeviceUseCase,
    private val dismissDeviceUseCase: DismissDeviceUseCase,
    scope: CoroutineScope? = null,
) : BaseListViewModel<StagingUiState>(StagingUiState(), scope = scope) {
    init {
        loadPage(1)
    }

    override fun loadPage(
        page: Int,
        perPage: Int,
    ) {
        if (state.value.isLoading && page != 1 && state.value.devices.isNotEmpty()) return

        updateState { it.copy(isLoading = true, errorMessage = null, currentPage = page) }

        launchJob("fetch") {
            when (val result = getStagingDevicesUseCase(page = page, perPage = 50)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            devices = result.data.items,
                            totalItems = result.data.total,
                            currentPage = result.data.page,
                            perPage = result.data.perPage,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.staging_error_load)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.staging_error_load)),
                        )
                    }
                }
            }
        }
    }

    fun approveDevice(device: StagingDevice) {
        if (state.value.isProcessingAction) return

        updateState {
            it.copy(
                isProcessingAction = true,
                actionDeviceId = device.id,
                actionErrorMessage = null,
            )
        }

        launchJob("approve") {
            when (val result = approveDeviceUseCase(device.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isProcessingAction = false,
                            actionDeviceId = null,
                            actionErrorMessage = null,
                            toastMessage = UiText.Resource(Res.string.staging_approve_success, listOf(device.hostname)),
                        )
                    }
                    loadPage(state.value.currentPage)
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isProcessingAction = false,
                            actionDeviceId = null,
                            actionErrorMessage = mapError(result, UiText.Resource(Res.string.staging_error_approve)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isProcessingAction = false,
                            actionDeviceId = null,
                            actionErrorMessage = mapError(result, UiText.Resource(Res.string.staging_error_approve)),
                        )
                    }
                }
            }
        }
    }

    fun confirmDismissDevice(device: StagingDevice) {
        updateState { it.copy(deviceToDismiss = device, actionErrorMessage = null) }
    }

    fun cancelDismissDevice() {
        updateState { it.copy(deviceToDismiss = null, actionErrorMessage = null) }
    }

    fun dismissDevice() {
        if (state.value.isProcessingAction) return
        val device = state.value.deviceToDismiss ?: return

        updateState {
            it.copy(
                isProcessingAction = true,
                actionDeviceId = device.id,
                actionErrorMessage = null,
            )
        }

        launchJob("dismiss") {
            when (val result = dismissDeviceUseCase(device.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            deviceToDismiss = null,
                            isProcessingAction = false,
                            actionDeviceId = null,
                            actionErrorMessage = null,
                            toastMessage = UiText.Resource(Res.string.staging_dismiss_success, listOf(device.hostname)),
                        )
                    }
                    loadPage(state.value.currentPage)
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isProcessingAction = false,
                            actionDeviceId = null,
                            actionErrorMessage = mapError(result, UiText.Resource(Res.string.staging_error_dismiss)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isProcessingAction = false,
                            actionDeviceId = null,
                            actionErrorMessage = mapError(result, UiText.Resource(Res.string.staging_error_dismiss)),
                        )
                    }
                }
            }
        }
    }

    fun dismissActionError() {
        updateState { it.copy(actionErrorMessage = null) }
    }

    fun dismissToast() {
        updateState { it.copy(toastMessage = null) }
    }
}
