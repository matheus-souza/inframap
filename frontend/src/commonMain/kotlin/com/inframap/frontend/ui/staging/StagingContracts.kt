@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.staging

import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.ui.base.Paginated
import com.inframap.frontend.ui.util.UiText

data class StagingUiState(
    val devices: List<StagingDevice> = emptyList(),
    override val totalItems: Long = 0,
    override val currentPage: Int = 1,
    val perPage: Int = 50,
    override val isLoading: Boolean = true,
    override val errorMessage: UiText? = null,
    val deviceToDismiss: StagingDevice? = null,
    val actionDeviceId: String? = null,
    val isProcessingAction: Boolean = false,
    val actionErrorMessage: UiText? = null,
    val toastMessage: UiText? = null,
) : Paginated

data class StagingActions(
    val onPageChanged: (Int) -> Unit,
    val onApproveClicked: (StagingDevice) -> Unit,
    val onDismissClicked: (StagingDevice) -> Unit,
    val onConfirmDismiss: () -> Unit,
    val onCancelDismiss: () -> Unit,
    val onDismissActionError: () -> Unit,
    val onDismissToast: () -> Unit,
    val onRetryClicked: () -> Unit,
)
