@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.devices

import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.ui.base.Paginated
import com.inframap.frontend.ui.util.UiText

data class DeviceListUiState(
    val devices: List<Device> = emptyList(),
    override val totalItems: Long = 0,
    override val currentPage: Int = 1,
    val perPage: Int = 50,
    val searchQuery: String = "",
    override val isLoading: Boolean = true,
    override val errorMessage: UiText? = null,
    val deviceToDelete: Device? = null,
    val isDeleting: Boolean = false,
    val deleteErrorMessage: UiText? = null,
    val toastMessage: UiText? = null,
) : Paginated

data class DeviceListActions(
    val onSearchQueryChanged: (String) -> Unit,
    val onPageChanged: (Int) -> Unit,
    val onCreateDeviceClicked: () -> Unit,
    val onDeviceClicked: (String) -> Unit,
    val onEditDeviceClicked: (String) -> Unit,
    val onDeleteDeviceClicked: (Device) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDismissDeleteError: () -> Unit,
    val onDismissToast: () -> Unit,
    val onRetryClicked: () -> Unit,
)

data class DeviceDetailUiState(
    val device: Device? = null,
    val isLoading: Boolean = true,
    val errorMessage: UiText? = null,
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
    val deviceType: String = "",
    val isSubmitting: Boolean = false,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val errorMessage: UiText? = null,
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
    val deviceId: String,
    val hostname: String = "",
    val ipAddress: String = "",
    val macAddress: String = "",
    val deviceType: String = "",
    val status: String = "active",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val errorMessage: UiText? = null,
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
