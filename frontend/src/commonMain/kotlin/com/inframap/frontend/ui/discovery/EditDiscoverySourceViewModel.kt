package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.DiscoverySourceConfigDto
import com.inframap.frontend.data.dto.UpdateDiscoverySourceRequest
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.toSummary
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceByIdUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.TestDiscoverySourceHealthUseCase
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceParams
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
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
    private val listSubnetsUseCase: ListSubnetsUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<EditDiscoverySourceUiState>(EditDiscoverySourceUiState(id = sourceId), scope) {
    init {
        loadSource()
        loadSubnets()
    }

    /** The registered Subnets offered as targets. A failure only leaves the chips empty. */
    fun loadSubnets() {
        updateState { it.copy(isLoadingSubnets = true) }
        launchJob("load_subnets") {
            when (val result = listSubnetsUseCase()) {
                is ApiResult.Success ->
                    updateState {
                        it.copy(
                            subnets = result.data.items.map { subnet -> subnet.toSummary() },
                            isLoadingSubnets = false,
                        )
                    }
                else -> updateState { it.copy(isLoadingSubnets = false) }
            }
        }
    }

    fun loadSource() {
        updateState { it.copy(isLoading = true, loadError = null) }
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
                            targets = src.targets,
                            initialTargets = src.targets,
                            enabled = src.enabled,
                            providerConfigs = configs,
                            initialProviderTargets = initialTargets,
                            configuredSecrets = configuredSecrets,
                            isLoading = false,
                            loadError = null,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            loadError =
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

    fun onTargetInputChanged(input: String) {
        updateState { it.copy(targetInput = input, validationErrors = it.validationErrors - TARGET_INPUT_ERROR_KEY) }
    }

    /** Appends the typed CIDR as a free-form target, or explains why it cannot be added. */
    fun onAddTargetCidr() {
        updateState { current ->
            val edit = current.targets.withCidr(current.targetInput)
            val violation = edit.violation
            if (violation != null) {
                current.withError(TARGET_INPUT_ERROR_KEY, violation)
            } else {
                current.copy(
                    targets = edit.targets,
                    targetInput = "",
                    validationErrors = current.validationErrors - TARGET_INPUT_ERROR_KEY - TARGETS_ERROR_KEY,
                )
            }
        }
    }

    fun onSubnetToggled(subnet: SubnetSummary) {
        updateState { current ->
            val edit = current.targets.toggledSubnet(subnet)
            val violation = edit.violation
            if (violation != null) {
                current.withError(TARGETS_ERROR_KEY, violation)
            } else {
                current.copy(targets = edit.targets, validationErrors = current.validationErrors - TARGETS_ERROR_KEY)
            }
        }
    }

    /** Removing a target never needs the credential again (RFC-026 §5), so it only edits the list. */
    fun onRemoveTarget(index: Int) {
        updateState {
            it.copy(
                targets = it.targets.withoutTarget(index),
                validationErrors = it.validationErrors - TARGETS_ERROR_KEY,
            )
        }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, UiText>()
        val name = state.value.name.trim()
        if (name.isBlank()) {
            errors["name"] = UiText.Resource(Res.string.validation_discovery_name_required)
        }

        // Unchanged targets are not sent, so they are not re-checked either: a target left
        // behind by a deleted Subnet must not block renaming the source.
        if (state.value.requiresCidr && state.value.targetsChanged) {
            DiscoveryTargetRules.checkList(state.value.targets, required = true)?.let {
                errors[TARGETS_ERROR_KEY] = it.message
            }
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

        updateState { it.copy(isSubmitting = true, submitError = null) }
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
                // Without config.targets the backend keeps the stored list, so a source whose
                // targets did not change (or that has none to edit) leaves them untouched.
                config =
                    if (currentState.requiresCidr && currentState.targetsChanged) {
                        DiscoverySourceConfigDto(targets = currentState.targets.toRequestTargets())
                    } else {
                        null
                    },
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
                            submitError =
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

    /**
     * A refused save stays on the form: a target rule (RFC-026 §2) is shown on the targets
     * editor, anything else next to the save button. The source itself loaded fine, so this
     * never reaches the load error and its retry.
     */
    private fun handleUpdateError(result: ApiResult.Error) {
        val currentState = state.value
        val isSSRF = result.code == "secret_required_on_target_change"
        val warnings =
            if (isSSRF) {
                currentState.selectedProviders.associateWith { true }
            } else {
                emptyMap()
            }
        val violation = TargetRuleViolation.fromCode(result.code)
        val msg =
            when {
                isSSRF -> UiText.Resource(Res.string.error_secret_required_on_target_change)
                violation != null -> null
                else -> mapError(result, UiText.Resource(Res.string.edit_discovery_source_error_update))
            }
        updateState {
            it.copy(
                isSubmitting = false,
                submitError = msg,
                targetChangedWarnings = it.targetChangedWarnings + warnings,
                validationErrors =
                    if (violation != null) {
                        it.validationErrors + (TARGETS_ERROR_KEY to violation.message)
                    } else {
                        it.validationErrors
                    },
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

private fun EditDiscoverySourceUiState.withError(
    key: String,
    violation: TargetRuleViolation,
): EditDiscoverySourceUiState = copy(validationErrors = validationErrors + (key to violation.message))
