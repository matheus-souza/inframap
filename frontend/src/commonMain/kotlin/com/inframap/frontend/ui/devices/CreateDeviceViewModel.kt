package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.device.CreateDeviceUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class CreateDeviceViewModel(
    private val createDeviceUseCase: CreateDeviceUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateDeviceUiState>(CreateDeviceUiState(), scope) {
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

    fun createDevice() {
        if (state.value.isSubmitting) return

        val current = state.value
        val errors = mutableMapOf<String, UiText>()

        if (current.hostname.trim().isEmpty()) {
            errors["hostname"] = UiText.Resource(Res.string.validation_hostname_required)
        }
        if (current.deviceType.trim().isEmpty()) {
            errors["device_type"] = UiText.Resource(Res.string.validation_device_type_required)
        }

        if (errors.isNotEmpty()) {
            updateState { it.copy(validationErrors = errors) }
            return
        }

        updateState { it.copy(isSubmitting = true, errorMessage = null) }

        launchJob("create") {
            when (
                val result =
                    createDeviceUseCase(
                        hostname = current.hostname.trim(),
                        deviceType = current.deviceType.trim(),
                        ipAddress = current.ipAddress.trim().ifEmpty { null },
                        macAddress = current.macAddress.trim().ifEmpty { null },
                    )
            ) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            createdDeviceId = result.data.id,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_create)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.devices_error_create)),
                        )
                    }
                }
            }
        }
    }
}
