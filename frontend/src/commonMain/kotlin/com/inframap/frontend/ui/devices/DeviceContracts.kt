@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.dto.DeviceDto

data class DeviceListUiState(
    val devices: List<DeviceDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val perPage: Int = 50,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deviceToDelete: DeviceDto? = null,
    val isDeleting: Boolean = false,
    val toastMessage: String? = null,
)

data class DeviceListActions(
    val onSearchQueryChanged: (String) -> Unit,
    val onPageChanged: (Int) -> Unit,
    val onCreateDeviceClicked: () -> Unit,
    val onDeviceClicked: (String) -> Unit,
    val onEditDeviceClicked: (String) -> Unit,
    val onDeleteDeviceClicked: (DeviceDto) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onRetryClicked: () -> Unit,
)

data class DeviceDetailUiState(
    val device: DeviceDto? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showDeleteDialog: Boolean = false,
    val isDeleting: Boolean = false,
)

data class DeviceDetailActions(
    val onBackClicked: () -> Unit,
    val onEditClicked: (String) -> Unit,
    val onDeleteClicked: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onRetryClicked: () -> Unit,
)

data class CreateDeviceUiState(
    val hostname: String = "",
    val ipAddress: String = "",
    val macAddress: String = "",
    val deviceType: String = "router",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val validationErrors: Map<String, String> = emptyMap(),
    val createdDeviceId: String? = null,
)

data class CreateDeviceActions(
    val onHostnameChanged: (String) -> Unit,
    val onIpAddressChanged: (String) -> Unit,
    val onMacAddressChanged: (String) -> Unit,
    val onDeviceTypeChanged: (String) -> Unit,
    val onSubmitClicked: () -> Unit,
    val onCancelClicked: () -> Unit,
)

data class EditDeviceUiState(
    val deviceId: String = "",
    val hostname: String = "",
    val ipAddress: String = "",
    val macAddress: String = "",
    val deviceType: String = "router",
    val status: String = "active",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val validationErrors: Map<String, String> = emptyMap(),
    val isSuccess: Boolean = false,
)

data class EditDeviceActions(
    val onHostnameChanged: (String) -> Unit,
    val onIpAddressChanged: (String) -> Unit,
    val onMacAddressChanged: (String) -> Unit,
    val onDeviceTypeChanged: (String) -> Unit,
    val onStatusChanged: (String) -> Unit,
    val onSubmitClicked: () -> Unit,
    val onCancelClicked: () -> Unit,
    val onRetryClicked: () -> Unit,
)
