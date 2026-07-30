package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.DeviceDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreateDeviceViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CreateDeviceUiState())
    val state: StateFlow<CreateDeviceUiState> = _state.asStateFlow()

    private var createJob: Job? = null

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

    fun createDevice() {
        if (_state.value.isSubmitting) return

        val current = _state.value
        val errors = mutableMapOf<String, String>()

        if (current.hostname.trim().isEmpty()) {
            errors["hostname"] = "Hostname is required"
        }
        if (current.deviceType.trim().isEmpty()) {
            errors["device_type"] = "Device type is required"
        }

        if (errors.isNotEmpty()) {
            _state.update { it.copy(validationErrors = errors) }
            return
        }

        _state.update { it.copy(isSubmitting = true, errorMessage = null) }

        val request =
            CreateDeviceRequest(
                hostname = current.hostname.trim(),
                ipAddress = current.ipAddress.trim().ifEmpty { null },
                macAddress = current.macAddress.trim().ifEmpty { null },
                deviceType = current.deviceType.trim(),
            )

        createJob?.cancel()
        createJob =
            scope.launch {
                when (val result = apiClient.post<DeviceDto, CreateDeviceRequest>("/api/v1/devices", request)) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                createdDeviceId = result.data.id,
                                errorMessage = null,
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = result.message.ifEmpty { "Failed to create device" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = "Network error. Failed to create device.",
                            )
                        }
                    }
                }
            }
    }

    fun clear() {
        createJob?.cancel()
        createJob = null
    }
}
