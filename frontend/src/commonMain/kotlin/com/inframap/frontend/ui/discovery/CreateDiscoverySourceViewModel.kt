package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class CreateDiscoverySourceViewModel(
    private val createSourceUseCase: CreateDiscoverySourceUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateDiscoverySourceUiState>(CreateDiscoverySourceUiState(), scope) {
    fun onNameChanged(name: String) {
        updateState { it.copy(name = name, validationErrors = it.validationErrors - "name") }
    }

    fun onSourceTypeChanged(sourceType: String) {
        updateState { it.copy(sourceType = sourceType, validationErrors = it.validationErrors - "type") }
    }

    fun onScheduleCronChanged(cron: String) {
        updateState { it.copy(scheduleCron = cron) }
    }

    fun onConfigCidrChanged(cidr: String) {
        updateState { it.copy(configCidr = cidr, validationErrors = it.validationErrors - "cidr") }
    }

    fun onEnabledChanged(enabled: Boolean) {
        updateState { it.copy(enabled = enabled) }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, UiText>()
        val name = state.value.name.trim()
        val sourceType = state.value.sourceType
        val cidr = state.value.configCidr.trim()

        if (name.isEmpty()) {
            errors["name"] = UiText.Resource(Res.string.validation_discovery_name_required)
        }

        if (sourceType.isEmpty()) {
            errors["type"] = UiText.Resource(Res.string.validation_discovery_type_required)
        }

        if (cidr.isNotEmpty() && !isValidCidr(cidr)) {
            errors["cidr"] = UiText.Resource(Res.string.validation_cidr_invalid)
        }

        updateState { it.copy(validationErrors = errors) }
        return errors.isEmpty()
    }

    fun createSource(onSuccess: (() -> Unit)? = null) {
        if (state.value.isSubmitting) return
        if (!validate()) return

        val stateVal = state.value

        updateState { it.copy(isSubmitting = true, errorMessage = null, isSuccess = false) }

        val config =
            if (stateVal.configCidr.trim().isNotEmpty()) {
                mapOf("cidr" to stateVal.configCidr.trim())
            } else {
                null
            }

        launchJob("submit") {
            when (
                val result =
                    createSourceUseCase(
                        CreateDiscoverySourceRequest(
                            name = stateVal.name.trim(),
                            type = stateVal.sourceType,
                            enabled = stateVal.enabled,
                            scheduleCron = stateVal.scheduleCron.trim().ifEmpty { null },
                            config = config,
                        ),
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
                    onSuccess?.invoke()
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.discovery_error_create)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.discovery_error_create)),
                        )
                    }
                }
            }
        }
    }

    private fun isValidCidr(cidr: String): Boolean {
        val regex = Regex("""^([0-9]{1,3}\.){3}[0-9]{1,3}\/([0-9]|[12][0-9]|3[0-2])$""")
        if (!regex.matches(cidr)) return false
        val ipPart = cidr.substringBefore("/")
        return ipPart.split(".").all { it.toIntOrNull() in 0..255 }
    }
}
