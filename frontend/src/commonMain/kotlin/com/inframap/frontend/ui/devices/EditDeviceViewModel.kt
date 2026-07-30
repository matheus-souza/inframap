package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EditDeviceViewModel(
    private val deviceId: String,
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(EditDeviceUiState(deviceId = deviceId))
    val state: StateFlow<EditDeviceUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null

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
                                hostname = result.data.hostname,
                                ipAddress = result.data.ipAddress ?: "",
                                macAddress = result.data.macAddress ?: "",
                                deviceType = result.data.deviceType,
                                status = result.data.status,
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

    fun onHostnameChanged(hostname: String) {
        _state.update {
            it.copy(
                hostname = hostname,
                validationErrors = it.validationErrors - "hostname",
            )
        }
    }

    fun onIpAddressChanged(ipAddress: String) {
        _state.update {
            it.copy(
                ipAddress = ipAddress,
                validationErrors = it.validationErrors - "ip_address",
            )
        }
    }

    fun onMacAddressChanged(macAddress: String) {
        _state.update {
            it.copy(
                macAddress = macAddress,
                validationErrors = it.validationErrors - "mac_address",
            )
        }
    }

    fun onDeviceTypeChanged(deviceType: String) {
        _state.update {
            it.copy(
                deviceType = deviceType,
                validationErrors = it.validationErrors - "device_type",
            )
        }
    }

    fun onStatusChanged(status: String) {
        _state.update {
            it.copy(
                status = status,
                validationErrors = it.validationErrors - "status",
            )
        }
    }

    fun updateDevice() {
        val current = _state.value
        val errors = mutableMapOf<String, String>()

        if (current.hostname.trim().isEmpty()) {
            errors["hostname"] = "Hostname cannot be empty"
        }

        if (errors.isNotEmpty()) {
            _state.update { it.copy(validationErrors = errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        val request =
            UpdateDeviceRequest(
                hostname = current.hostname.trim(),
                ipAddress = current.ipAddress.trim().ifEmpty { null },
                macAddress = current.macAddress.trim().ifEmpty { null },
                deviceType = current.deviceType.trim().ifEmpty { null },
                status = current.status.trim().ifEmpty { null },
            )

        scope.launch {
            when (val result = apiClient.put<DeviceDto, UpdateDeviceRequest>("/api/v1/devices/$deviceId", request)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            isSuccess = true,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = result.message.ifEmpty { "Failed to update device" },
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "Network error. Failed to update device.",
                        )
                    }
                }
            }
        }
    }

    fun clear() {
        fetchJob?.cancel()
        fetchJob = null
    }
}
