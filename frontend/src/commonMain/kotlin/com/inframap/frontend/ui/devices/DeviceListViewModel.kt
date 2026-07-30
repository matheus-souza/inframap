package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.MessageResponse
import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.DeviceListResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceListViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DeviceListUiState())
    val state: StateFlow<DeviceListUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var deleteJob: Job? = null

    init {
        loadDevices()
    }

    fun loadDevices(page: Int = 1) {
        _state.update { it.copy(isLoading = true, errorMessage = null, page = page) }
        val params = mutableMapOf("page" to page.toString(), "per_page" to "50")
        if (_state.value.searchQuery.isNotBlank()) {
            params["search"] = _state.value.searchQuery.trim()
        }

        fetchJob?.cancel()
        fetchJob =
            scope.launch {
                when (val result = apiClient.get<DeviceListResponse>("/api/v1/devices", params)) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                devices = result.data.devices,
                                total = result.data.total,
                                page = result.data.page,
                                perPage = result.data.perPage,
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message.ifEmpty { "Failed to load devices" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Network error. Failed to reach server.",
                            )
                        }
                    }
                }
            }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadDevices(page = 1)
    }

    fun confirmDeleteDevice(device: DeviceDto) {
        _state.update { it.copy(deviceToDelete = device, deleteErrorMessage = null) }
    }

    fun cancelDeleteDevice() {
        _state.update { it.copy(deviceToDelete = null, deleteErrorMessage = null) }
    }

    fun dismissDeleteError() {
        _state.update { it.copy(deleteErrorMessage = null) }
    }

    fun deleteDevice() {
        if (_state.value.isDeleting) return
        val device = _state.value.deviceToDelete ?: return

        _state.update { it.copy(isDeleting = true, deleteErrorMessage = null) }
        deleteJob?.cancel()
        deleteJob =
            scope.launch {
                when (val result = apiClient.delete<MessageResponse>("/api/v1/devices/${device.id}")) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                deviceToDelete = null,
                                isDeleting = false,
                                deleteErrorMessage = null,
                                toastMessage = "Dispositivo '${device.hostname}' excluído com sucesso.",
                            )
                        }
                        loadDevices(_state.value.page)
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                deleteErrorMessage = result.message.ifEmpty { "Failed to delete device" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                deleteErrorMessage = "Network error. Failed to delete device.",
                            )
                        }
                    }
                }
            }
    }

    fun dismissToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun clear() {
        fetchJob?.cancel()
        deleteJob?.cancel()
        fetchJob = null
        deleteJob = null
    }
}
