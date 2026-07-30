package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.MessageResponse
import com.inframap.frontend.data.dto.DeviceDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceDetailViewModel(
    private val deviceId: String,
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DeviceDetailUiState())
    val state: StateFlow<DeviceDetailUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var deleteJob: Job? = null

    init {
        loadDevice()
    }

    fun loadDevice() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        fetchJob?.cancel()
        fetchJob =
            scope.launch {
                when (val result = apiClient.get<DeviceDto>("/api/v1/devices/$deviceId")) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                device = result.data,
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message.ifEmpty { "Failed to load device details" },
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

    fun openDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = true) }
    }

    fun closeDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteDevice(onSuccess: () -> Unit) {
        if (_state.value.isDeleting) return

        _state.update { it.copy(isDeleting = true) }
        deleteJob?.cancel()
        deleteJob =
            scope.launch {
                when (val result = apiClient.delete<MessageResponse>("/api/v1/devices/$deviceId")) {
                    is ApiResult.Success -> {
                        _state.update { it.copy(isDeleting = false, showDeleteDialog = false) }
                        onSuccess()
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                errorMessage = result.message.ifEmpty { "Failed to delete device" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                errorMessage = "Network error. Failed to delete device.",
                            )
                        }
                    }
                }
            }
    }

    fun clear() {
        fetchJob?.cancel()
        deleteJob?.cancel()
        fetchJob = null
        deleteJob = null
    }
}
