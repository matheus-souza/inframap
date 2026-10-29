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

                    val loadedCollectors = src.collectors.map { c -> c.collectorType }.toSet()
                    updateState {
                        it.copy(
                            id = src.id,
                            name = src.name,
                            sourceType = src.sourceType,
                            selectedCollectors = loadedCollectors,
                            initialCollectors = loadedCollectors,
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

    fun toggleCollector(collectorType: String) {
        val current = state.value.selectedCollectors
        onCollectorsChanged(
            if (collectorType in current) current - collectorType else current + collectorType,
        )
    }

    /**
     * Changes the plan with the create form's rules ([DiscoveryCollectorRules]). A collector
     * switched off keeps what was typed for it, so switching it back on restores the form; it
     * is only left out of the saved plan. Its errors and warnings go with it.
     */
    fun onCollectorsChanged(collectors: Set<String>) {
        updateState { current ->
            val removed = current.selectedCollectors - collectors
            val removedErrorKeys =
                removed.flatMap { listOf(ProviderForms.labelKey(it), secretErrorKey(it)) }.toSet()
            current.copy(
                selectedCollectors = collectors,
                providerConfigs =
                    DiscoveryCollectorRules.seedDefaults(
                        current.selectedCollectors,
                        collectors,
                        current.providerConfigs,
                    ),
                activeProviderTab =
                    DiscoveryCollectorRules.providerTabAfter(
                        current.selectedCollectors,
                        collectors,
                        current.activeProviderTab,
                    ),
                targetChangedWarnings = current.targetChangedWarnings - removed,
                validationErrors =
                    (current.validationErrors - removedErrorKeys).let { errors ->
                        if (collectors.isNotEmpty()) errors - COLLECTORS_ERROR_KEY else errors
                    },
            )
        }
    }

    fun onProviderFieldChanged(
        providerId: String,
        key: String,
        value: String,
    ) {
        updateState { current ->
            val providerMap = current.providerConfigs[providerId].orEmpty() + (key to value)
            val updated = current.copy(providerConfigs = current.providerConfigs + (providerId to providerMap))
            val warning = updated.needsSecretForTargetChange(providerId)

            updated.copy(
                targetChangedWarnings = current.targetChangedWarnings + (providerId to warning),
                connectionTests = current.connectionTests - providerId,
                validationErrors =
                    if (!warning) {
                        current.validationErrors - secretErrorKey(providerId) - ProviderForms.labelKey(providerId)
                    } else {
                        current.validationErrors - ProviderForms.labelKey(providerId)
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
        val current = state.value
        val errors = mutableMapOf<String, UiText>()
        val name = current.name.trim()
        if (name.isBlank()) {
            errors["name"] = UiText.Resource(Res.string.validation_discovery_name_required)
        }

        DiscoveryCollectorRules.selectionError(current.selectedCollectors)?.let { errors[COLLECTORS_ERROR_KEY] = it }

        // Unchanged targets of an unchanged sweep are not sent, so they are not re-checked
        // either: a target left behind by a deleted Subnet must not block renaming the source.
        if (current.targetsNeedCheck) {
            DiscoveryTargetRules.checkList(current.targets, required = true)?.let {
                errors[TARGETS_ERROR_KEY] = it.message
            }
        }

        // A provider added in this edit has nothing stored, so it is held to the create rules.
        errors += DiscoveryCollectorRules.providerErrors(current.addedProviders, current.providerConfigs)

        val warnings = mutableMapOf<String, Boolean>()
        current.selectedProviders.filter { current.needsSecretForTargetChange(it) }.forEach { providerId ->
            warnings[providerId] = true
            errors[secretErrorKey(providerId)] = UiText.Resource(Res.string.error_secret_required_on_target_change)
        }

        val firstProviderWithError =
            current.selectedProviders.firstOrNull {
                ProviderForms.labelKey(it) in errors || secretErrorKey(it) in errors
            }

        updateState {
            it.copy(
                validationErrors = errors,
                targetChangedWarnings = it.targetChangedWarnings + warnings,
                activeProviderTab = firstProviderWithError ?: it.activeProviderTab,
            )
        }
        return errors.isEmpty()
    }

    fun onSubmitClicked() {
        if (!validate()) return

        updateState { it.copy(isSubmitting = true, submitError = null) }
        val currentState = state.value

        // The whole plan is sent: the backend replaces the stored collectors with this list,
        // so a collector left out is removed. A blank secret of a collector it already stores
        // is kept from the stored configuration.
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
                config = currentState.targetsConfig(),
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
        // Only a provider with a stored secret can be refused for a blank one; a provider
        // added in this edit has none for the backend to keep.
        val warnings =
            if (isSSRF) {
                currentState.selectedProviders
                    .filter { currentState.configuredSecrets[it].orEmpty().isNotEmpty() }
                    .associateWith { true }
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

private fun secretErrorKey(providerId: String): String = "secret_$providerId"

/**
 * Whether a provider the source already had points at a new host while its secret is blank.
 * Saving would then send the stored credential to a destination it was never used against,
 * which the backend refuses (`secret_required_on_target_change`). A provider added in this
 * edit has no stored credential to protect; its secret is required by the create rules.
 */
private fun EditDiscoverySourceUiState.needsSecretForTargetChange(providerId: String): Boolean {
    if (providerId !in initialCollectors) return false
    val initialTargets = initialProviderTargets[providerId].orEmpty()
    val config = providerConfigs[providerId].orEmpty()
    val targetChanged =
        ProviderForms.targetKeys(providerId).any { initialTargets[it].orEmpty() != config[it].orEmpty() }
    val secretFilled = ProviderForms.secretKeys(providerId).any { config[it].orEmpty().isNotBlank() }
    return targetChanged && !secretFilled
}

/**
 * The `config` of the update. Without `config.targets` the backend keeps the stored list, so
 * unchanged targets are left out. A plan without a network sweep scans nothing, so it is saved
 * without targets (RFC-026 §2, CONTEXT.md guideline #218): any it still stores are cleared.
 */
private fun EditDiscoverySourceUiState.targetsConfig(): DiscoverySourceConfigDto? =
    when {
        requiresCidr && targetsChanged -> DiscoverySourceConfigDto(targets = targets.toRequestTargets())
        !requiresCidr && initialTargets.isNotEmpty() -> DiscoverySourceConfigDto(targets = emptyList())
        else -> null
    }

private fun EditDiscoverySourceUiState.withError(
    key: String,
    violation: TargetRuleViolation,
): EditDiscoverySourceUiState = copy(validationErrors = validationErrors + (key to violation.message))
