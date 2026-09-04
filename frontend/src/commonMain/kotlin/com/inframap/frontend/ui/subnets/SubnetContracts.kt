@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.subnets

import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.ui.base.Paginated
import com.inframap.frontend.ui.util.UiText

data class SubnetsUiState(
    val subnets: List<Subnet> = emptyList(),
    val detectedInterfaces: List<NetworkInterface> = emptyList(),
    override val totalItems: Long = 0,
    override val isLoading: Boolean = true,
    override val errorMessage: UiText? = null,
    override val currentPage: Int = 1,
    val toastMessage: UiText? = null,
) : Paginated

data class SubnetsActions(
    val onCreateSubnetClicked: () -> Unit,
    val onAddInterfaceClicked: (NetworkInterface) -> Unit,
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
