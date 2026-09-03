package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.toSummary
import com.inframap.frontend.domain.usecase.credentials.ListCredentialsUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.integrations.TestProviderHealthUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.discovery_error_create
import com.inframap.frontend.generated.resources.discovery_validation_collector_required
import com.inframap.frontend.generated.resources.provider_test_connection_failed
import com.inframap.frontend.generated.resources.provider_validation_docker_endpoint_required
import com.inframap.frontend.generated.resources.provider_validation_field_required
import com.inframap.frontend.generated.resources.validation_cidr_invalid
import com.inframap.frontend.generated.resources.validation_cidr_required
import com.inframap.frontend.generated.resources.validation_discovery_name_required
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class CreateDiscoverySourceViewModel(
    private val createSourceUseCase: CreateDiscoverySourceUseCase,
    private val listSubnetsUseCase: ListSubnetsUseCase,
    private val testProviderHealthUseCase: TestProviderHealthUseCase,
    private val listCredentialsUseCase: ListCredentialsUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateDiscoverySourceUiState>(CreateDiscoverySourceUiState(), scope) {
    init {
        loadSubnets()
        loadCredentials()
    }

    /**
     * Loads the stored credentials a provider can point at instead of carrying its secrets
     * inline. A failure is silent: the form still works with inline credentials, so an
     * unavailable list should not block creating a plan.
     */
    private fun loadCredentials() {
        launchJob("load_credentials") {
            when (val result = listCredentialsUseCase()) {
                is ApiResult.Success -> updateState { it.copy(credentials = result.data) }
                else -> Unit
            }
        }
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
            // Seed a newly selected provider with its defaults, so a field the operator never
            // touches still reaches the backend with its intended value.
            val seeded =
                collectors
                    .filter { it !in current.selectedCollectors }
                    .mapNotNull { id -> ProviderForms.defaults(id).takeIf { it.isNotEmpty() }?.let { id to it } }
                    .toMap()
            val errors =
                if (collectors.isNotEmpty()) {
                    current.validationErrors - "collectors"
                } else {
                    current.validationErrors
                }
            current.copy(
                selectedCollectors = collectors,
                providerConfigs = seeded + current.providerConfigs,
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

        // A CIDR describes a network sweep. A plan made only of providers has no range to
        // scan, so demanding one would block a legitimate Docker- or Proxmox-only plan.
        if (state.value.requiresCidr) {
            if (cidr.isEmpty()) {
                errors["cidr"] = UiText.Resource(Res.string.validation_cidr_required)
            } else if (!isValidCidr(cidr)) {
                errors["cidr"] = UiText.Resource(Res.string.validation_cidr_invalid)
            }
        } else if (cidr.isNotEmpty() && !isValidCidr(cidr)) {
            errors["cidr"] = UiText.Resource(Res.string.validation_cidr_invalid)
        }

        errors += providerValidationErrors(state.value)

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

        val collectors = buildCollectors(stateVal)

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

    fun onProviderFieldChanged(
        providerId: String,
        key: String,
        value: String,
    ) {
        updateState { current ->
            val updated = current.providerConfigs[providerId].orEmpty() + (key to value)
            current.copy(
                providerConfigs = current.providerConfigs + (providerId to updated),
                // Editing the configuration invalidates whatever the last check concluded.
                connectionTests = current.connectionTests - providerId,
                validationErrors = current.validationErrors - ProviderForms.labelKey(providerId),
            )
        }
    }

    fun testConnection(providerId: String) {
        val config = state.value.providerConfigs[providerId].orEmpty()
        updateState { it.copy(connectionTests = it.connectionTests + (providerId to ConnectionTest.Testing)) }

        launchJob("test_connection_$providerId") {
            val outcome =
                when (val result = testProviderHealthUseCase(providerId, config)) {
                    is ApiResult.Success ->
                        if (result.data.isHealthy) {
                            ConnectionTest.Healthy
                        } else {
                            ConnectionTest.Failed(UiText.Resource(Res.string.provider_test_connection_failed))
                        }
                    is ApiResult.Error ->
                        ConnectionTest.Failed(
                            mapError(result, UiText.Resource(Res.string.provider_test_connection_failed)),
                        )
                    is ApiResult.NetworkError ->
                        ConnectionTest.Failed(
                            mapError(result, UiText.Resource(Res.string.provider_test_connection_failed)),
                        )
                }
            updateState { it.copy(connectionTests = it.connectionTests + (providerId to outcome)) }
        }
    }

    private fun isValidCidr(cidr: String): Boolean {
        val regex = Regex("""^([0-9]{1,3}\.){3}[0-9]{1,3}\/([0-9]|[12][0-9]|3[0-2])$""")
        if (!regex.matches(cidr)) return false
        val ipPart = cidr.substringBefore("/")
        return ipPart.split(".").all { it.toIntOrNull() in 0..255 }
    }
}

/**
 * Builds the per-collector payload. Providers carry their own endpoint and credentials,
 * while network sweeps take theirs from the plan-level config.
 */
private fun buildCollectors(state: CreateDiscoverySourceUiState): List<CollectorConfigDto> =
    state.selectedCollectors.map { type ->
        CollectorConfigDto(
            type = type,
            config = state.providerConfigs[type]?.filterValues { it.isNotBlank() }?.ifEmpty { null },
        )
    }

/**
 * Validates the configuration of every selected provider.
 *
 * A connectivity check is deliberately not required to submit: the daemon may be temporarily
 * unreachable while the plan is still worth saving.
 */
private fun providerValidationErrors(state: CreateDiscoverySourceUiState): Map<String, UiText> {
    val errors = mutableMapOf<String, UiText>()
    state.selectedProviders.forEach { providerId ->
        val config = state.providerConfigs[providerId].orEmpty()
        if (ProviderForms.missingFields(providerId, config).isNotEmpty()) {
            errors[ProviderForms.labelKey(providerId)] =
                UiText.Resource(Res.string.provider_validation_field_required)
        } else if (ProviderForms.isEndpointMissing(providerId, config)) {
            errors[ProviderForms.labelKey(providerId)] =
                UiText.Resource(Res.string.provider_validation_docker_endpoint_required)
        }
    }
    return errors
}
