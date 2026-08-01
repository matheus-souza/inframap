package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class DeviceDetailViewModel(
    private val deviceId: String,
    private val getDeviceByIdUseCase: GetDeviceByIdUseCase,
    private val deleteDeviceUseCase: DeleteDeviceUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<DeviceDetailUiState>(DeviceDetailUiState(), scope) {
    init {
        loadDevice()
    }

    fun loadDevice() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("fetch") {
            when (val result = getDeviceByIdUseCase(deviceId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            device = result.data,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_load_detail)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_load_detail)),
                        )
                    }
                }
            }
        }
    }

    fun openDeleteDialog() {
        updateState { it.copy(showDeleteDialog = true) }
    }

    fun closeDeleteDialog() {
        updateState { it.copy(showDeleteDialog = false) }
    }

    fun deleteDevice(onSuccess: () -> Unit) {
        if (state.value.isDeleting) return

        updateState { it.copy(isDeleting = true) }
        launchJob("delete") {
            when (val result = deleteDeviceUseCase(deviceId)) {
                is ApiResult.Success -> {
                    updateState { it.copy(isDeleting = false, showDeleteDialog = false) }
                    onSuccess()
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_delete)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_delete)),
                        )
                    }
                }
            }
        }
    }
}
