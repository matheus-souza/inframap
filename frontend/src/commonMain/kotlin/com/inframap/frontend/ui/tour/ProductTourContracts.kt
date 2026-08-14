package com.inframap.frontend.ui.tour

data class ProductTourUiState(
    val isVisible: Boolean = false,
    val currentStep: Int = 1,
    val totalSteps: Int = ProductTourStep.TOTAL_STEPS,
) {
    val currentTourStep: ProductTourStep
        get() = ProductTourStep.fromStepNumber(currentStep)
}

data class ProductTourActions(
    val onDismiss: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onRestart: () -> Unit = {},
)
