package com.inframap.frontend.ui.staging

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.api.MessageResponse
import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.StagingDeviceDto
import com.inframap.frontend.data.dto.StagingListResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StagingViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StagingUiState())
    val state: StateFlow<StagingUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null
    private var approveJob: Job? = null
    private var dismissJob: Job? = null

    init {
        loadStagingDevices()
    }

    fun loadStagingDevices(page: Int = 1) {
        _state.update { it.copy(isLoading = true, errorMessage = null, page = page) }
        val params = mapOf("page" to page.toString(), "per_page" to "50")

        fetchJob?.cancel()
        fetchJob =
            scope.launch {
                when (val result = apiClient.get<StagingListResponse>("/api/v1/devices/staging", params)) {
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
                                errorMessage = result.message.ifEmpty { "Falha ao carregar dispositivos em staging" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Erro de rede. Não foi possível conectar ao servidor.",
                            )
                        }
                    }
                }
            }
    }

    fun approveDevice(device: StagingDeviceDto) {
        if (_state.value.isProcessingAction) return

        _state.update {
            it.copy(
                isProcessingAction = true,
                actionDeviceId = device.id,
                actionErrorMessage = null,
            )
        }

        approveJob?.cancel()
        approveJob =
            scope.launch {
                when (val result = apiClient.post<DeviceDto>("/api/v1/devices/staging/${device.id}/approve")) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                isProcessingAction = false,
                                actionDeviceId = null,
                                actionErrorMessage = null,
                                toastMessage = "Dispositivo '${device.hostname}' aprovado com sucesso.",
                            )
                        }
                        loadStagingDevices(_state.value.page)
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isProcessingAction = false,
                                actionDeviceId = null,
                                actionErrorMessage = result.message.ifEmpty { "Falha ao aprovar dispositivo" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isProcessingAction = false,
                                actionDeviceId = null,
                                actionErrorMessage = "Erro de rede. Não foi possível aprovar o dispositivo.",
                            )
                        }
                    }
                }
            }
    }

    fun confirmDismissDevice(device: StagingDeviceDto) {
        _state.update { it.copy(deviceToDismiss = device, actionErrorMessage = null) }
    }

    fun cancelDismissDevice() {
        _state.update { it.copy(deviceToDismiss = null, actionErrorMessage = null) }
    }

    fun dismissDevice() {
        if (_state.value.isProcessingAction) return
        val device = _state.value.deviceToDismiss ?: return

        _state.update {
            it.copy(
                isProcessingAction = true,
                actionDeviceId = device.id,
                actionErrorMessage = null,
            )
        }

        dismissJob?.cancel()
        dismissJob =
            scope.launch {
                when (val result = apiClient.post<MessageResponse>("/api/v1/devices/staging/${device.id}/dismiss")) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                deviceToDismiss = null,
                                isProcessingAction = false,
                                actionDeviceId = null,
                                actionErrorMessage = null,
                                toastMessage = "Dispositivo '${device.hostname}' descartado com sucesso.",
                            )
                        }
                        loadStagingDevices(_state.value.page)
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isProcessingAction = false,
                                actionDeviceId = null,
                                actionErrorMessage = result.message.ifEmpty { "Falha ao descartar dispositivo" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isProcessingAction = false,
                                actionDeviceId = null,
                                actionErrorMessage = "Erro de rede. Não foi possível descartar o dispositivo.",
                            )
                        }
                    }
                }
            }
    }

    fun dismissActionError() {
        _state.update { it.copy(actionErrorMessage = null) }
    }

    fun dismissToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun clear() {
        fetchJob?.cancel()
        approveJob?.cancel()
        dismissJob?.cancel()
        fetchJob = null
        approveJob = null
        dismissJob = null
    }
}
