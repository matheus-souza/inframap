@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package com.inframap.frontend.ui.wizard

import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.ui.util.UiText

data class SetupWizardUiState(
    val currentStep: Int = 1,
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: UiText? = null,
    val detectedInterfaces: List<NetworkInterface> = emptyList(),
    val selectedCidrs: Set<String> = emptySet(),
    val createdSubnetCount: Int = 0,
)

data class SetupWizardActions(
    val onDismiss: () -> Unit,
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onToggleInterface: (NetworkInterface) -> Unit,
    val onDismissError: () -> Unit,
)
