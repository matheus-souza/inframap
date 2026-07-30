@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.dto.SubnetDto

data class SubnetsUiState(
    val subnets: List<SubnetDto> = emptyList(),
    val total: Long = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
)

data class SubnetsActions(
    val onCreateSubnetClicked: () -> Unit,
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
    val validationErrors: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
)

data class CreateSubnetActions(
    val onNameChanged: (String) -> Unit,
    val onCidrChanged: (String) -> Unit,
    val onVlanIdChanged: (String) -> Unit,
    val onGatewayIpChanged: (String) -> Unit,
    val onDescriptionChanged: (String) -> Unit,
    val onDiscoveryEnabledChanged: (Boolean) -> Unit,
    val onSubmitClicked: () -> Unit,
    val onCancelClicked: () -> Unit,
)
