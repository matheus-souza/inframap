@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.discovery

import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.ui.base.Paginated
import com.inframap.frontend.ui.util.UiText

data class DiscoveryListUiState(
    val sources: List<DiscoverySource> = emptyList(),
    override val totalItems: Long = 0,
    override val isLoading: Boolean = true,
    override val errorMessage: UiText? = null,
    override val currentPage: Int = 1,
    val toastMessage: UiText? = null,
    val sourceToDelete: DiscoverySource? = null,
    val deleteImpact: DiscoverySourceDeletionImpact? = null,
    val isLoadingDeleteImpact: Boolean = false,
    val deleteError: UiText? = null,
    val triggerRunError: UiText? = null,
) : Paginated

data class DiscoveryListActions(
    val onCreateSourceClicked: () -> Unit,
    val onEditSourceClicked: (String) -> Unit = {},
    val onTriggerRunClicked: (String) -> Unit,
    val onDeleteSourceClicked: (DiscoverySource) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDismissDeleteError: () -> Unit,
    val onDismissToast: () -> Unit,
    val onDismissTriggerRunError: () -> Unit,
    val onRetryClicked: () -> Unit,
)

/** Outcome of a "Test Connection" action, per provider. */
sealed interface ConnectionTest {
    data object Testing : ConnectionTest

    data object Healthy : ConnectionTest

    data class Failed(
        val message: UiText,
    ) : ConnectionTest
}

data class CreateDiscoverySourceUiState(
    val name: String = "",
    val selectedCollectors: Set<String> = setOf("icmp_sweep", "arp_sweep", "mdns", "reverse_dns"),
    val scheduleCron: String = "",
    /** Ordered targets of the plan (RFC-026 §1); each is free-form or linked to a Subnet. */
    val targets: List<DiscoveryTarget> = emptyList(),
    /** Free-form CIDR typed in the targets editor and not yet added. */
    val targetInput: String = "",
    val enabled: Boolean = true,
    val isSubmitting: Boolean = false,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val errorMessage: UiText? = null,
    val isSuccess: Boolean = false,
    val subnets: List<SubnetSummary> = emptyList(),
    val isLoadingSubnets: Boolean = false,
    /** Endpoint and credentials typed for each provider, keyed by provider id. */
    val providerConfigs: Map<String, Map<String, String>> = emptyMap(),
    /** Result of the last connectivity check per provider, keyed by provider id. */
    val connectionTests: Map<String, ConnectionTest> = emptyMap(),
    /** Stored credentials a provider can reference instead of carrying its secrets inline. */
    val credentials: List<CredentialSummary> = emptyList(),
    /** Active provider tab identifier when providers are selected; the screen reads [currentProviderTab]. */
    val activeProviderTab: String? = null,
    val restoredFromDraft: Boolean = false,
) {
    /** Providers the user selected, in the order they are offered. */
    val selectedProviders: List<String> get() = ProviderForms.ids.filter { it in selectedCollectors }

    /** Tabs only earn their place once there is more than one provider to switch between. */
    val showsProviderTabs: Boolean get() = selectedProviders.size >= 2

    /** The provider actually shown: the chosen one while it is still selected, else the first. */
    val currentProviderTab: String? get() =
        activeProviderTab?.takeIf { it in selectedProviders } ?: selectedProviders.firstOrNull()

    /**
     * A CIDR only describes a network sweep. A plan made purely of providers has no range to
     * scan, so requiring one would block a legitimate Docker- or Proxmox-only plan.
     */
    val requiresCidr: Boolean get() = requiresTargets(selectedCollectors)

    val targetsEditor: DiscoveryTargetsEditorState get() =
        DiscoveryTargetsEditorState(
            targets = targets,
            input = targetInput,
            subnets = subnets,
            isLoadingSubnets = isLoadingSubnets,
            inputError = validationErrors[TARGET_INPUT_ERROR_KEY],
            listError = validationErrors[TARGETS_ERROR_KEY],
        )
}

/**
 * Whether a plan needs at least one target: any network sweep does, a plan made purely of
 * providers does not (guideline #218). An empty plan is treated as needing one.
 */
internal fun requiresTargets(selectedCollectors: Set<String>): Boolean =
    selectedCollectors.isEmpty() || selectedCollectors.any { it !in ProviderForms.ids }

/** What the targets editor renders, shared by the create and edit screens. */
data class DiscoveryTargetsEditorState(
    val targets: List<DiscoveryTarget> = emptyList(),
    val input: String = "",
    val subnets: List<SubnetSummary> = emptyList(),
    val isLoadingSubnets: Boolean = false,
    val inputError: UiText? = null,
    val listError: UiText? = null,
) {
    val linkedSubnetIds: Set<String> get() = targets.mapNotNull { it.subnetId }.toSet()

    val addressCount: Long get() = DiscoveryTargetRules.addressCount(targets)

    val exceedsAddressLimit: Boolean get() = addressCount > DiscoveryTargetRules.MAX_ADDRESSES
}

data class DiscoveryTargetsEditorActions(
    val onInputChanged: (String) -> Unit = {},
    val onAddCidrClicked: () -> Unit = {},
    val onSubnetToggled: (SubnetSummary) -> Unit = {},
    val onRemoveTarget: (index: Int) -> Unit = {},
)

data class CreateDiscoverySourceActions(
    val onNameChanged: (String) -> Unit,
    val onScheduleCronChanged: (String) -> Unit,
    val onEnabledChanged: (Boolean) -> Unit,
    val targets: DiscoveryTargetsEditorActions = DiscoveryTargetsEditorActions(),
    val onCollectorsChanged: (Set<String>) -> Unit = {},
    val onProviderFieldChanged: (providerId: String, key: String, value: String) -> Unit = { _, _, _ -> },
    val onTestConnectionClicked: (providerId: String) -> Unit = {},
    val onProviderTabSelected: (providerId: String) -> Unit = {},
    val onSubmitClicked: () -> Unit,
    val onCancelClicked: () -> Unit,
)

data class EditDiscoverySourceUiState(
    val id: String = "",
    val name: String = "",
    val sourceType: String = "",
    val selectedCollectors: Set<String> = emptySet(),
    /**
     * Collectors the source had when loaded. Only these have a configuration stored on the
     * server, so only these can keep a secret left blank.
     */
    val initialCollectors: Set<String> = emptySet(),
    val scheduleCron: String = "",
    val targets: List<DiscoveryTarget> = emptyList(),
    /** Targets as loaded; the update carries `config.targets` only when they changed. */
    val initialTargets: List<DiscoveryTarget> = emptyList(),
    val targetInput: String = "",
    val subnets: List<SubnetSummary> = emptyList(),
    val isLoadingSubnets: Boolean = false,
    val enabled: Boolean = true,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    /** Why the source could not be loaded; the screen offers a retry instead of the form. */
    val loadError: UiText? = null,
    /**
     * Why the last save was refused, shown next to the save button. A rule a single field
     * breaks (a target rule, RFC-026 §2) is shown on that field instead.
     */
    val submitError: UiText? = null,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val providerConfigs: Map<String, Map<String, String>> = emptyMap(),
    val initialProviderTargets: Map<String, Map<String, String>> = emptyMap(),
    val configuredSecrets: Map<String, Set<String>> = emptyMap(),
    val targetChangedWarnings: Map<String, Boolean> = emptyMap(),
    val connectionTests: Map<String, ConnectionTest> = emptyMap(),
    val activeProviderTab: String? = null,
    val showDeleteDialog: Boolean = false,
    val deleteImpact: DiscoverySourceDeletionImpact? = null,
    val isLoadingDeleteImpact: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: UiText? = null,
    val isDeleted: Boolean = false,
) {
    val selectedProviders: List<String> get() = ProviderForms.ids.filter { it in selectedCollectors }
    val showsProviderTabs: Boolean get() = selectedProviders.size >= 2
    val currentProviderTab: String? get() =
        activeProviderTab?.takeIf { it in selectedProviders } ?: selectedProviders.firstOrNull()

    /**
     * Selected providers the source did not have when loaded. Nothing is stored for them, so
     * their required fields, secrets included, must be filled in as on the create form.
     */
    val addedProviders: List<String> get() = selectedProviders.filter { it !in initialCollectors }

    /** See [CreateDiscoverySourceUiState.requiresCidr]; the editor is hidden when false. */
    val requiresCidr: Boolean get() = requiresTargets(selectedCollectors)

    /**
     * Whether the targets must be checked before saving: when they changed, or when the plan
     * gained its first network sweep, which now scans them.
     */
    val targetsNeedCheck: Boolean get() =
        requiresCidr && (targetsChanged || !requiresTargets(initialCollectors))

    /** Whether the operator added, removed, reordered or replaced a target since loading. */
    val targetsChanged: Boolean get() =
        targets.map { it.cidr to it.subnetId } != initialTargets.map { it.cidr to it.subnetId }

    val targetsEditor: DiscoveryTargetsEditorState get() =
        DiscoveryTargetsEditorState(
            targets = targets,
            input = targetInput,
            subnets = subnets,
            isLoadingSubnets = isLoadingSubnets,
            inputError = validationErrors[TARGET_INPUT_ERROR_KEY],
            listError = validationErrors[TARGETS_ERROR_KEY],
        )

    /**
     * Whether the provider's form differs from what is stored, so a connection test (which runs
     * against the stored configuration) would not check what the operator sees. A provider
     * added in this edit has nothing stored yet.
     */
    fun hasPendingTargetOrSecretChanges(providerId: String): Boolean {
        if (providerId !in initialCollectors) return true
        val targetKeys = ProviderForms.targetKeys(providerId)
        val secretKeys = ProviderForms.secretKeys(providerId)
        val initialTargets = initialProviderTargets[providerId].orEmpty()
        val currentConfig = providerConfigs[providerId].orEmpty()

        val targetChanged =
            targetKeys.any { tKey ->
                initialTargets[tKey].orEmpty() != currentConfig[tKey].orEmpty()
            }
        val secretEntered =
            secretKeys.any { sKey ->
                currentConfig[sKey].orEmpty().isNotBlank()
            }
        return targetChanged || secretEntered
    }
}

data class EditDiscoverySourceActions(
    val onNameChanged: (String) -> Unit,
    val onScheduleCronChanged: (String) -> Unit,
    val onEnabledChanged: (Boolean) -> Unit,
    val onCollectorsChanged: (Set<String>) -> Unit = {},
    val onProviderFieldChanged: (providerId: String, key: String, value: String) -> Unit,
    val onProviderTabSelected: (providerId: String) -> Unit,
    val targets: DiscoveryTargetsEditorActions = DiscoveryTargetsEditorActions(),
    val onTestConnectionClicked: (providerId: String) -> Unit = {},
    val onSubmitClicked: () -> Unit,
    val onDeleteClicked: () -> Unit,
    val onConfirmDeleteClicked: () -> Unit,
    val onDismissDeleteDialog: () -> Unit,
    val onRetryLoad: () -> Unit,
    val onBackClicked: () -> Unit,
)
