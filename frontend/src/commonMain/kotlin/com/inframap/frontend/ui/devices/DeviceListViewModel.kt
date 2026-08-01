package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.ui.base.BaseListViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class DeviceListViewModel(
    private val getDevicesUseCase: GetDevicesUseCase,
    private val deleteDeviceUseCase: DeleteDeviceUseCase,
    scope: CoroutineScope? = null,
) : BaseListViewModel<DeviceListUiState>(DeviceListUiState(), scope = scope) {
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
            val query = state.value.searchQuery.takeIf { it.isNotBlank() }
            val params = GetDevicesUseCase.Params(page = page, perPage = perPage, search = query ?: "")
            when (val result = getDevicesUseCase(params)) {
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
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_load)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_load)),
                        )
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        updateState { it.copy(searchQuery = query) }
        loadPage(1)
    }

    fun confirmDeleteDevice(device: Device) {
        updateState { it.copy(deviceToDelete = device, deleteErrorMessage = null) }
    }

    fun cancelDeleteDevice() {
        updateState { it.copy(deviceToDelete = null, deleteErrorMessage = null) }
    }

    fun dismissDeleteError() {
        updateState { it.copy(deleteErrorMessage = null) }
    }

    fun deleteDevice() {
        if (state.value.isDeleting) return
        val device = state.value.deviceToDelete ?: return

        updateState { it.copy(isDeleting = true, deleteErrorMessage = null) }

        launchJob("delete") {
            when (val result = deleteDeviceUseCase(device.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            deviceToDelete = null,
                            isDeleting = false,
                            deleteErrorMessage = null,
                            toastMessage = UiText.Resource(Res.string.devices_delete_success, listOf(device.hostname)),
                        )
                    }
                    loadPage(state.value.currentPage)
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            deleteErrorMessage = mapError(result, UiText.Resource(Res.string.devices_error_delete)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            deleteErrorMessage = mapError(result, UiText.Resource(Res.string.devices_error_delete)),
                        )
                    }
                }
            }
        }
    }

    fun dismissToast() {
        updateState { it.copy(toastMessage = null) }
    }
}
