@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.discovery

import com.inframap.frontend.domain.model.CredentialSummary
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoverySourceDeletionImpact
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
    val configCidr: String = "",
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
    val requiresCidr: Boolean get() =
        selectedCollectors.isEmpty() || selectedCollectors.any { it !in ProviderForms.ids }
}

data class CreateDiscoverySourceActions(
    val onNameChanged: (String) -> Unit,
    val onScheduleCronChanged: (String) -> Unit,
    val onConfigCidrChanged: (String) -> Unit,
    val onEnabledChanged: (Boolean) -> Unit,
    val onSubnetSelected: (SubnetSummary) -> Unit,
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
    val scheduleCron: String = "",
    val configCidr: String = "",
    val enabled: Boolean = true,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: UiText? = null,
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

    fun hasPendingTargetOrSecretChanges(providerId: String): Boolean {
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
    val onProviderFieldChanged: (providerId: String, key: String, value: String) -> Unit,
    val onProviderTabSelected: (providerId: String) -> Unit,
    val onTestConnectionClicked: (providerId: String) -> Unit = {},
    val onSubmitClicked: () -> Unit,
    val onDeleteClicked: () -> Unit,
    val onConfirmDeleteClicked: () -> Unit,
    val onDismissDeleteDialog: () -> Unit,
    val onDismissError: () -> Unit,
    val onBackClicked: () -> Unit,
)
