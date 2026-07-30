@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.staging

import com.inframap.frontend.data.dto.StagingDeviceDto

data class StagingUiState(
    val devices: List<StagingDeviceDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val perPage: Int = 50,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val deviceToDismiss: StagingDeviceDto? = null,
    val actionDeviceId: String? = null,
    val isProcessingAction: Boolean = false,
    val actionErrorMessage: String? = null,
    val toastMessage: String? = null,
)

data class StagingActions(
    val onPageChanged: (Int) -> Unit,
    val onApproveClicked: (StagingDeviceDto) -> Unit,
    val onDismissClicked: (StagingDeviceDto) -> Unit,
    val onConfirmDismiss: () -> Unit,
    val onCancelDismiss: () -> Unit,
    val onDismissActionError: () -> Unit,
    val onDismissToast: () -> Unit,
    val onRetryClicked: () -> Unit,
)
