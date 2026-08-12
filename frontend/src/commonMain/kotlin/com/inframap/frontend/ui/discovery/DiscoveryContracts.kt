@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.discovery

import com.inframap.frontend.domain.model.DiscoverySource
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
    val deleteError: UiText? = null,
    val triggerRunError: UiText? = null,
) : Paginated

data class DiscoveryListActions(
    val onCreateSourceClicked: () -> Unit,
    val onTriggerRunClicked: (String) -> Unit,
    val onDeleteSourceClicked: (DiscoverySource) -> Unit,
    val onConfirmDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDismissDeleteError: () -> Unit,
    val onDismissToast: () -> Unit,
    val onDismissTriggerRunError: () -> Unit,
    val onRetryClicked: () -> Unit,
)

data class CreateDiscoverySourceUiState(
    val name: String = "",
    val sourceType: String = "",
    val scheduleCron: String = "",
    val configCidr: String = "",
    val enabled: Boolean = true,
    val isSubmitting: Boolean = false,
    val validationErrors: Map<String, UiText> = emptyMap(),
    val errorMessage: UiText? = null,
    val isSuccess: Boolean = false,
)

data class CreateDiscoverySourceActions(
    val onNameChanged: (String) -> Unit,
    val onSourceTypeChanged: (String) -> Unit,
    val onScheduleCronChanged: (String) -> Unit,
    val onConfigCidrChanged: (String) -> Unit,
    val onEnabledChanged: (Boolean) -> Unit,
    val onSubmitClicked: () -> Unit,
    val onCancelClicked: () -> Unit,
)
