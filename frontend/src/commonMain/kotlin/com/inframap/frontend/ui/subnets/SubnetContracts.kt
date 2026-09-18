@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.ui.base.Paginated
import com.inframap.frontend.ui.util.UiText

@Suppress("LongParameterList")
data class SubnetsUiState(
    val subnets: List<Subnet> = emptyList(),
    val detectedInterfaces: List<NetworkInterface> = emptyList(),
    override val totalItems: Long = 0,
    override val isLoading: Boolean = true,
    override val errorMessage: UiText? = null,
    override val currentPage: Int = 1,
    val toastMessage: UiText? = null,
    val subnetToDelete: Subnet? = null,
    val deleteImpact: SubnetDeletionImpact? = null,
    val isLoadingDeleteImpact: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteErrorMessage: UiText? = null,
) : Paginated

@Suppress("LongParameterList")
data class SubnetsActions(
    val onCreateSubnetClicked: () -> Unit,
    val onAddInterfaceClicked: (NetworkInterface) -> Unit,
    val onSubnetClicked: (Subnet) -> Unit = {},
    val onEditSubnetClicked: (Subnet) -> Unit = {},
    val onDeleteSubnetClicked: (Subnet) -> Unit = {},
    val onConfirmDeleteClicked: () -> Unit = {},
    val onDismissDeleteClicked: () -> Unit = {},
    val onDismissToast: () -> Unit,
    val onRetryClicked: () -> Unit,
)

data class CreateSubnetUiState(
    val name: String = "",
    val cidr: String = "",
    val vlanId: String = "",
    val gatewayIp: String = "",
    val description: String = "",
    val discoveryEnabled: Boolean = true,
    val isSubmitting: Boolean = false,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val errorMessage: UiText? = null,
    val isSuccess: Boolean = false,
    val detectedInterfaces: List<NetworkInterface> = emptyList(),
    val showInterfaceSuggestions: Boolean = true,
    /**
     * The detected interface whose values currently fill the form, highlighted in the panel.
     *
     * Held explicitly rather than derived from the fields: a second click on the highlighted
     * card drops the highlight but keeps the values, and a selection computed from the fields
     * would light the card straight back up because they still match.
     */
    val selectedInterface: NetworkInterface? = null,
    val restoredFromDraft: Boolean = false,
)

data class CreateSubnetActions(
    val onNameChanged: (String) -> Unit,
    val onCidrChanged: (String) -> Unit,
    val onVlanIdChanged: (String) -> Unit,
    val onGatewayIpChanged: (String) -> Unit,
    val onDescriptionChanged: (String) -> Unit,
    val onDiscoveryEnabledChanged: (Boolean) -> Unit,
    val onInterfaceSelected: (NetworkInterface) -> Unit,
    val onToggleSuggestions: () -> Unit,
    val onSubmitClicked: () -> Unit,
    val onCancelClicked: () -> Unit,
)

@Suppress("LongParameterList")
data class EditSubnetUiState(
    val subnetId: String,
    val name: String = "",
    val cidr: String = "",
    val vlanId: String = "",
    val gatewayIp: String = "",
    val description: String = "",
    val discoveryEnabled: Boolean = true,
    val initialCidr: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val errorMessage: UiText? = null,
    val showImpactDialog: Boolean = false,
    val impactAffectedCount: Int = 0,
    val showDeleteDialog: Boolean = false,
    val deleteImpact: SubnetDeletionImpact? = null,
    val isLoadingDeleteImpact: Boolean = false,
    val isDeleting: Boolean = false,
)

@Suppress("LongParameterList")
data class EditSubnetActions(
    val onNameChanged: (String) -> Unit,
    val onCidrChanged: (String) -> Unit,
    val onVlanIdChanged: (String) -> Unit,
    val onGatewayIpChanged: (String) -> Unit,
    val onDescriptionChanged: (String) -> Unit,
    val onDiscoveryEnabledChanged: (Boolean) -> Unit,
    val onSubmitClicked: () -> Unit,
    val onConfirmImpactClicked: () -> Unit = {},
    val onDismissImpactClicked: () -> Unit = {},
    val onDeleteClicked: () -> Unit = {},
    val onConfirmDeleteClicked: () -> Unit = {},
    val onDismissDeleteClicked: () -> Unit = {},
    val onCancelClicked: () -> Unit,
    val onRetryClicked: () -> Unit,
)
