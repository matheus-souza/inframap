package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceByIdUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.TestDiscoverySourceHealthUseCase
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceParams
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.delete_discovery_source_error
import com.inframap.frontend.generated.resources.edit_discovery_source_error_load
import com.inframap.frontend.generated.resources.edit_discovery_source_error_update
import com.inframap.frontend.generated.resources.error_secret_required_on_target_change
import com.inframap.frontend.generated.resources.provider_test_connection_failed
import com.inframap.frontend.generated.resources.validation_discovery_name_required
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions")
class EditDiscoverySourceViewModel(
    private val sourceId: String,
    private val getSourceByIdUseCase: GetDiscoverySourceByIdUseCase,
    private val updateDiscoverySourceUseCase: UpdateDiscoverySourceUseCase,
    private val deleteDiscoverySourceUseCase: DeleteDiscoverySourceUseCase,
    private val getDiscoverySourceDeletionImpactUseCase: GetDiscoverySourceDeletionImpactUseCase,
    private val testDiscoverySourceHealthUseCase: TestDiscoverySourceHealthUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<EditDiscoverySourceUiState>(EditDiscoverySourceUiState(id = sourceId), scope) {
    init {
        loadSource()
    }

    fun loadSource() {
        updateState { it.copy(isLoading = true, errorMessage = null) }
        launchJob("load_source") {
            when (val result = getSourceByIdUseCase(sourceId)) {
                is ApiResult.Success -> {
                    val src = result.data
                    val configs = mutableMapOf<String, Map<String, String>>()
                    val initialTargets = mutableMapOf<String, Map<String, String>>()
                    val configuredSecrets = mutableMapOf<String, Set<String>>()

                    src.collectors.forEach { col ->
                        val colType = col.collectorType
                        configs[colType] = col.config
                        configuredSecrets[colType] = col.configuredSecrets.toSet()

                        val targetKeys = ProviderForms.targetKeys(colType)
                        if (targetKeys.isNotEmpty()) {
                            initialTargets[colType] = col.config.filterKeys { it in targetKeys }
                        }
                    }

                    updateState {
                        it.copy(
                            id = src.id,
                            name = src.name,
                            sourceType = src.sourceType,
                            selectedCollectors = src.collectors.map { c -> c.collectorType }.toSet(),
                            scheduleCron = src.scheduleCron.orEmpty(),
                            configCidr = src.configCidr.orEmpty(),
                            enabled = src.enabled,
                            providerConfigs = configs,
                            initialProviderTargets = initialTargets,
                            configuredSecrets = configuredSecrets,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage =
                                mapError(
                                    result,
                                    UiText.Resource(Res.string.edit_discovery_source_error_load),
                                ),
                        )
                    }
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        updateState { it.copy(name = name, validationErrors = it.validationErrors - "name") }
    }

    fun onScheduleCronChanged(cron: String) {
        updateState { it.copy(scheduleCron = cron) }
    }

    fun onEnabledChanged(enabled: Boolean) {
        updateState { it.copy(enabled = enabled) }
    }

    fun onProviderFieldChanged(
        providerId: String,
        key: String,
        value: String,
    ) {
        updateState { current ->
            val providerMap = current.providerConfigs[providerId].orEmpty().toMutableMap()
            providerMap[key] = value
            val newProviderConfigs = current.providerConfigs + (providerId to providerMap)

            val targetKeys = ProviderForms.targetKeys(providerId)
            val secretKeys = ProviderForms.secretKeys(providerId)
            val initialTargets = current.initialProviderTargets[providerId].orEmpty()

            val targetChanged =
                targetKeys.any { tKey ->
                    val initVal = initialTargets[tKey].orEmpty()
                    val curVal = providerMap[tKey].orEmpty()
                    initVal != curVal
                }

            val secretFilled =
                secretKeys.any { sKey ->
                    providerMap[sKey].orEmpty().isNotBlank()
                }

            val warning = targetChanged && !secretFilled
            val newWarnings = current.targetChangedWarnings + (providerId to warning)

            current.copy(
                providerConfigs = newProviderConfigs,
                targetChangedWarnings = newWarnings,
                connectionTests = current.connectionTests - providerId,
                validationErrors =
                    if (!warning) {
                        current.validationErrors - "secret_$providerId"
                    } else {
                        current.validationErrors
                    },
            )
        }
    }

    fun onProviderTabSelected(providerId: String) {
        updateState { it.copy(activeProviderTab = providerId) }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, UiText>()
        val name = state.value.name.trim()
        if (name.isBlank()) {
            errors["name"] = UiText.Resource(Res.string.validation_discovery_name_required)
        }

        val warnings = mutableMapOf<String, Boolean>()
        state.value.selectedProviders.forEach { providerId ->
            val targetKeys = ProviderForms.targetKeys(providerId)
            val secretKeys = ProviderForms.secretKeys(providerId)
            val initialTargets = state.value.initialProviderTargets[providerId].orEmpty()
            val currentConfig = state.value.providerConfigs[providerId].orEmpty()

            val targetChanged =
                targetKeys.any { tKey ->
                    val initVal = initialTargets[tKey].orEmpty()
                    val curVal = currentConfig[tKey].orEmpty()
                    initVal != curVal
                }

            val secretFilled =
                secretKeys.any { sKey ->
                    currentConfig[sKey].orEmpty().isNotBlank()
                }

            if (targetChanged && !secretFilled) {
                warnings[providerId] = true
                errors["secret_$providerId"] = UiText.Resource(Res.string.error_secret_required_on_target_change)
            }
        }

        updateState {
            it.copy(
                validationErrors = errors,
                targetChangedWarnings = it.targetChangedWarnings + warnings,
            )
        }
        return errors.isEmpty()
    }

    fun onSubmitClicked() {
        if (!validate()) return

        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        val currentState = state.value

        val collectors =
            currentState.selectedCollectors.map { colType ->
                val cfg = currentState.providerConfigs[colType]?.filterValues { it.isNotBlank() }?.ifEmpty { null }
                CollectorConfigDto(
                    type = colType,
                    config = cfg,
                )
            }

        val req =
            UpdateDiscoverySourceRequest(
                name = currentState.name.trim(),
                type = currentState.sourceType,
                enabled = currentState.enabled,
                scheduleCron = currentState.scheduleCron.trim().ifEmpty { null },
                collectors = collectors,
            )

        launchJob("update_source") {
            when (val result = updateDiscoverySourceUseCase(UpdateDiscoverySourceParams(sourceId, req))) {
                is ApiResult.Success -> {
                    updateState { it.copy(isSubmitting = false, isSuccess = true) }
                }
                is ApiResult.Error -> handleUpdateError(result)
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            errorMessage =
                                mapError(
                                    result,
                                    UiText.Resource(Res.string.edit_discovery_source_error_update),
                                ),
                        )
                    }
                }
            }
        }
    }

    private fun handleUpdateError(result: ApiResult.Error) {
        val currentState = state.value
        val isSSRF = result.code == "secret_required_on_target_change"
        val warnings =
            if (isSSRF) {
                currentState.selectedProviders.associateWith { true }
            } else {
                emptyMap()
            }
        val msg =
            if (isSSRF) {
                UiText.Resource(Res.string.error_secret_required_on_target_change)
            } else {
                mapError(result, UiText.Resource(Res.string.edit_discovery_source_error_update))
            }
        updateState {
            it.copy(
                isSubmitting = false,
                errorMessage = msg,
                targetChangedWarnings = it.targetChangedWarnings + warnings,
            )
        }
    }

    fun onDeleteClicked() {
        updateState {
            it.copy(
                showDeleteDialog = true,
                isLoadingDeleteImpact = true,
                deleteImpact = null,
                deleteError = null,
            )
        }
        launchJob("fetch_deletion_impact") {
            when (val result = getDiscoverySourceDeletionImpactUseCase(sourceId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                            deleteImpact = result.data,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                        )
                    }
                }
            }
        }
    }

    fun onConfirmDeleteClicked() {
        if (state.value.isDeleting) return
        updateState { it.copy(isDeleting = true, deleteError = null) }

        launchJob("delete_source") {
            when (val result = deleteDiscoverySourceUseCase(sourceId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                            deleteImpact = null,
                            isDeleted = true,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            deleteError = mapError(result, UiText.Resource(Res.string.delete_discovery_source_error)),
                        )
                    }
                }
            }
        }
    }

    fun onDismissDeleteDialog() {
        updateState {
            it.copy(
                showDeleteDialog = false,
                deleteImpact = null,
                isLoadingDeleteImpact = false,
                deleteError = null,
            )
        }
    }

    fun onDismissError() {
        updateState { it.copy(errorMessage = null) }
    }

    fun testConnection(providerId: String) {
        val currentState = state.value
        if (currentState.id.isBlank() || currentState.hasPendingTargetOrSecretChanges(providerId)) {
            return
        }

        updateState {
            it.copy(connectionTests = it.connectionTests + (providerId to ConnectionTest.Testing))
        }

        launchJob("test_connection_$providerId") {
            val outcome =
                when (val result = testDiscoverySourceHealthUseCase(currentState.id, providerId)) {
                    is ApiResult.Success ->
                        if (result.data.isHealthy) {
                            ConnectionTest.Healthy
                        } else {
                            val msg = result.data.message
                            if (!msg.isNullOrBlank()) {
                                ConnectionTest.Failed(UiText.DynamicString(msg))
                            } else {
                                ConnectionTest.Failed(UiText.Resource(Res.string.provider_test_connection_failed))
                            }
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
            updateState {
                it.copy(connectionTests = it.connectionTests + (providerId to outcome))
            }
        }
    }
}
