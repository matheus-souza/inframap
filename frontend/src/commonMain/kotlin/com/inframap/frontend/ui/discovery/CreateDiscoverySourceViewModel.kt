package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.data.dto.DiscoverySourceConfigDto
import com.inframap.frontend.data.storage.draft.DraftForm
import com.inframap.frontend.data.storage.draft.FormDraftStore
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.toSummary
import com.inframap.frontend.domain.usecase.credentials.ListCredentialsUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.integrations.TestProviderHealthUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.discovery_error_create
import com.inframap.frontend.generated.resources.provider_test_connection_failed
import com.inframap.frontend.generated.resources.validation_discovery_name_required
import com.inframap.frontend.ui.base.BaseViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

@Suppress("TooManyFunctions")
class CreateDiscoverySourceViewModel(
    private val createSourceUseCase: CreateDiscoverySourceUseCase,
    private val listSubnetsUseCase: ListSubnetsUseCase,
    private val testProviderHealthUseCase: TestProviderHealthUseCase,
    private val listCredentialsUseCase: ListCredentialsUseCase,
    private val formDrafts: FormDraftStore,
    scope: CoroutineScope? = null,
) : BaseViewModel<CreateDiscoverySourceUiState>(CreateDiscoverySourceUiState(), scope) {
    private val pristineDraft = CreateDiscoverySourceUiState().toDraft()

    init {
        restoreDraft()
        loadSubnets()
        loadCredentials()
    }

    private fun restoreDraft() {
        val draft = formDrafts.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()) ?: return
        updateState { draft.applyTo(it) }
    }

    private inline fun editForm(crossinline reducer: (CreateDiscoverySourceUiState) -> CreateDiscoverySourceUiState) {
        updateState { reducer(it) }
        val currentDraft = state.value.toDraft()
        if (currentDraft == pristineDraft) {
            formDrafts.discard(DraftForm.CreateDiscoverySource)
        } else {
            formDrafts.save(
                DraftForm.CreateDiscoverySource,
                currentDraft,
                CreateDiscoverySourceDraft.serializer(),
            )
        }
    }

    fun discardDraft() {
        formDrafts.discard(DraftForm.CreateDiscoverySource)
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
        editForm { it.copy(name = name, validationErrors = it.validationErrors - "name") }
    }

    fun toggleCollector(collectorType: String) {
        val current = state.value.selectedCollectors
        onCollectorsChanged(
            if (collectorType in current) current - collectorType else current + collectorType,
        )
    }

    fun onCollectorsChanged(collectors: Set<String>) {
        editForm { current ->
            current.copy(
                selectedCollectors = collectors,
                providerConfigs =
                    DiscoveryCollectorRules.seedDefaults(
                        current.selectedCollectors,
                        collectors,
                        current.providerConfigs,
                    ),
                validationErrors =
                    if (collectors.isNotEmpty()) {
                        current.validationErrors - COLLECTORS_ERROR_KEY
                    } else {
                        current.validationErrors
                    },
                activeProviderTab =
                    DiscoveryCollectorRules.providerTabAfter(
                        current.selectedCollectors,
                        collectors,
                        current.activeProviderTab,
                    ),
            )
        }
    }

    fun onProviderTabSelected(providerId: String) {
        if (providerId !in state.value.selectedProviders) return
        editForm { it.copy(activeProviderTab = providerId) }
    }

    fun onScheduleCronChanged(cron: String) {
        editForm { it.copy(scheduleCron = cron) }
    }

    fun onTargetInputChanged(input: String) {
        editForm { it.copy(targetInput = input, validationErrors = it.validationErrors - TARGET_INPUT_ERROR_KEY) }
    }

    /** Appends the typed CIDR as a free-form target, or explains why it cannot be added. */
    fun onAddTargetCidr() {
        editForm { current ->
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

    /**
     * Links or unlinks a registered Subnet as a target. The first Subnet picked while the name
     * is still blank also names the plan after it.
     */
    fun onSubnetToggled(subnet: SubnetSummary) {
        editForm { current ->
            val edit = current.targets.toggledSubnet(subnet)
            val violation = edit.violation
            if (violation != null) {
                return@editForm current.withError(TARGETS_ERROR_KEY, violation)
            }
            val added = edit.targets.size > current.targets.size
            val namesPlan = added && current.name.isBlank()
            var errors = current.validationErrors - TARGETS_ERROR_KEY
            if (namesPlan) {
                errors = errors - "name"
            }
            current.copy(
                name = if (namesPlan) "Varredura ${subnet.name}" else current.name,
                targets = edit.targets,
                validationErrors = errors,
            )
        }
    }

    fun onRemoveTarget(index: Int) {
        editForm {
            it.copy(
                targets = it.targets.withoutTarget(index),
                validationErrors = it.validationErrors - TARGETS_ERROR_KEY,
            )
        }
    }

    fun onEnabledChanged(enabled: Boolean) {
        editForm { it.copy(enabled = enabled) }
    }

    fun validate(): Boolean {
        val errors = mutableMapOf<String, UiText>()
        val name = state.value.name.trim()
        val selectedCollectors = state.value.selectedCollectors

        if (name.isEmpty()) {
            errors["name"] = UiText.Resource(Res.string.validation_discovery_name_required)
        }

        DiscoveryCollectorRules.selectionError(selectedCollectors)?.let { errors[COLLECTORS_ERROR_KEY] = it }

        // Targets describe a network sweep. A plan made only of providers has no range to
        // scan, so its (hidden) target list is neither required nor sent.
        if (state.value.requiresCidr) {
            DiscoveryTargetRules.checkList(state.value.targets, required = true)?.let {
                errors[TARGETS_ERROR_KEY] = it.message
            }
        }

        val providerErrors =
            DiscoveryCollectorRules.providerErrors(state.value.selectedProviders, state.value.providerConfigs)
        errors += providerErrors

        val firstProviderWithError =
            state.value.selectedProviders.firstOrNull { ProviderForms.labelKey(it) in providerErrors }
        val targetTab = firstProviderWithError ?: state.value.activeProviderTab

        updateState { it.copy(validationErrors = errors, activeProviderTab = targetTab) }
        return errors.isEmpty()
    }

    fun createSource(onSuccess: (() -> Unit)? = null) {
        if (state.value.isSubmitting) return
        if (!validate()) return

        val stateVal = state.value

        updateState { it.copy(isSubmitting = true, errorMessage = null, isSuccess = false) }

        val config =
            if (stateVal.requiresCidr) {
                DiscoverySourceConfigDto(targets = stateVal.targets.toRequestTargets())
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
                    formDrafts.discard(DraftForm.CreateDiscoverySource)
                    updateState {
                        it.copy(
                            isSubmitting = false,
                            isSuccess = true,
                            errorMessage = null,
                        )
                    }
                    onSuccess?.invoke()
                }
                is ApiResult.Error -> onCreateRejected(result)
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

    /** A target rule the backend enforces maps to its localized message, next to the editor too. */
    private fun onCreateRejected(result: ApiResult.Error) {
        val violation = TargetRuleViolation.fromCode(result.code)
        updateState {
            it.copy(
                isSubmitting = false,
                errorMessage =
                    violation?.message ?: mapError(result, UiText.Resource(Res.string.discovery_error_create)),
                validationErrors =
                    if (violation != null) {
                        it.validationErrors + (TARGETS_ERROR_KEY to violation.message)
                    } else {
                        it.validationErrors
                    },
            )
        }
    }

    fun onProviderFieldChanged(
        providerId: String,
        key: String,
        value: String,
    ) {
        editForm { current ->
            var updated = current.providerConfigs[providerId].orEmpty() + (key to value)
            // Picking a credential must drop whatever secrets were typed before it. The
            // backend gives collector values precedence over the credential's, so a stale
            // token left behind would win and the run would authenticate with the old one.
            if (key == ProviderForms.CREDENTIAL_KEY && value.isNotBlank()) {
                updated = updated - ProviderForms.secretKeys(providerId).toSet()
            }
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

private fun CreateDiscoverySourceUiState.withError(
    key: String,
    violation: TargetRuleViolation,
): CreateDiscoverySourceUiState = copy(validationErrors = validationErrors + (key to violation.message))
