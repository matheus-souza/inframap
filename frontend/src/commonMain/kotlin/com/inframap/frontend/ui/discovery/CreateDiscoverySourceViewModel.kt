package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.toSummary
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.discovery_error_create
import com.inframap.frontend.generated.resources.discovery_validation_collector_required
import com.inframap.frontend.generated.resources.validation_cidr_invalid
import com.inframap.frontend.generated.resources.validation_cidr_required
import com.inframap.frontend.generated.resources.validation_discovery_name_required
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class CreateDiscoverySourceViewModel(
    private val createSourceUseCase: CreateDiscoverySourceUseCase,
    private val listSubnetsUseCase: ListSubnetsUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateDiscoverySourceUiState>(CreateDiscoverySourceUiState(), scope) {
    init {
        loadSubnets()
    }

    fun loadSubnets() {
        updateState { it.copy(isLoadingSubnets = true) }
        launchJob("load_subnets") {
            when (val result = listSubnetsUseCase()) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            subnets = result.data.items.map { subnet -> subnet.toSummary() },
                            isLoadingSubnets = false,
                        )
                    }
                }
                else -> {
                    updateState { it.copy(isLoadingSubnets = false) }
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        updateState { it.copy(name = name, validationErrors = it.validationErrors - "name") }
    }

    fun toggleCollector(collectorType: String) {
        val current = state.value.selectedCollectors
        onCollectorsChanged(
            if (collectorType in current) current - collectorType else current + collectorType,
        )
    }

    fun onCollectorsChanged(collectors: Set<String>) {
        updateState { current ->
            val errors =
                if (collectors.isNotEmpty()) {
                    current.validationErrors - "collectors"
                } else {
                    current.validationErrors
                }
            current.copy(
                selectedCollectors = collectors,
                validationErrors = errors,
            )
        }
    }

    fun onScheduleCronChanged(cron: String) {
        updateState { it.copy(scheduleCron = cron) }
    }

    fun onConfigCidrChanged(cidr: String) {
        updateState { it.copy(configCidr = cidr, validationErrors = it.validationErrors - "cidr") }
    }

    fun onSubnetSelected(subnet: SubnetSummary) {
        updateState { current ->
            val wasNameBlank = current.name.isBlank()
            val newName = if (wasNameBlank) "Varredura ${subnet.name}" else current.name
            var errors = current.validationErrors - "cidr"
            if (wasNameBlank) {
                errors = errors - "name"
            }
            current.copy(
                name = newName,
                configCidr = subnet.cidr,
                validationErrors = errors,
            )
        }
    }

    fun onEnabledChanged(enabled: Boolean) {
        updateState { it.copy(enabled = enabled) }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, UiText>()
        val name = state.value.name.trim()
        val cidr = state.value.configCidr.trim()
        val selectedCollectors = state.value.selectedCollectors

        if (name.isEmpty()) {
            errors["name"] = UiText.Resource(Res.string.validation_discovery_name_required)
        }

        if (selectedCollectors.isEmpty()) {
            errors["collectors"] = UiText.Resource(Res.string.discovery_validation_collector_required)
        }

        if (cidr.isEmpty()) {
            errors["cidr"] = UiText.Resource(Res.string.validation_cidr_required)
        } else if (!isValidCidr(cidr)) {
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

        val collectors = stateVal.selectedCollectors.map { CollectorConfigDto(type = it) }

        launchJob("submit") {
            when (
                val result =
                    createSourceUseCase(
                        CreateDiscoverySourceRequest(
                            name = stateVal.name.trim(),
                            enabled = stateVal.enabled,
                            scheduleCron = stateVal.scheduleCron.trim().ifEmpty { null },
                            config = config,
                            collectors = collectors,
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
