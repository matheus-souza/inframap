package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
import com.inframap.frontend.domain.usecase.device.UpdateDeviceUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class EditDeviceViewModel(
    private val deviceId: String,
    private val getDeviceByIdUseCase: GetDeviceByIdUseCase,
    private val updateDeviceUseCase: UpdateDeviceUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<EditDeviceUiState>(EditDeviceUiState(deviceId = deviceId), scope) {
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

    fun onHostnameChanged(hostname: String) {
        updateState {
            it.copy(
                hostname = hostname,
                validationErrors = it.validationErrors - "hostname",
            )
        }
    }

    fun onIpAddressChanged(ipAddress: String) {
        updateState {
            it.copy(
                ipAddress = ipAddress,
                validationErrors = it.validationErrors - "ip_address",
            )
        }
    }

    fun onMacAddressChanged(macAddress: String) {
        updateState {
            it.copy(
                macAddress = macAddress,
                validationErrors = it.validationErrors - "mac_address",
            )
        }
    }

    fun onDeviceTypeChanged(deviceType: String) {
        updateState {
            it.copy(
                deviceType = deviceType,
                validationErrors = it.validationErrors - "device_type",
            )
        }
    }

    fun onStatusChanged(status: String) {
        updateState {
            it.copy(
                status = status,
                validationErrors = it.validationErrors - "status",
            )
        }
    }

    fun updateDevice() {
        if (state.value.isSubmitting) return

        val current = state.value
        val errors = mutableMapOf<String, UiText>()

        if (current.hostname.trim().isEmpty()) {
            errors["hostname"] = UiText.Resource(Res.string.validation_hostname_empty)
        }

        if (errors.isNotEmpty()) {
            updateState { it.copy(validationErrors = errors) }
            return
        }

        updateState { it.copy(isSubmitting = true, isSuccess = false, errorMessage = null) }

        launchJob("submit") {
            when (
                val result =
                    updateDeviceUseCase(
                        id = deviceId,
                        hostname = current.hostname.trim(),
                        ipAddress = current.ipAddress.trim().ifEmpty { null },
                        macAddress = current.macAddress.trim().ifEmpty { null },
                        deviceType = current.deviceType.trim().ifEmpty { null },
                        status = current.status.trim().ifEmpty { null },
                    )
            ) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            isSuccess = true,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_update)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_update)),
                        )
                    }
                }
            }
        }
    }
}
