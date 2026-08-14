package com.inframap.frontend.ui.tour

import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.ui.base.BaseViewModel
import kotlinx.coroutines.CoroutineScope

class ProductTourViewModel(
    private val localStorage: LocalStorage,
    scope: CoroutineScope? = null,
) : BaseViewModel<ProductTourUiState>(ProductTourUiState(), scope) {
    fun checkShouldShow() {
        val completed = localStorage.get(KEY_TOUR_COMPLETED) != null
        updateState { it.copy(isVisible = !completed, currentStep = 1) }
    }

    fun startTour() {
        updateState { it.copy(isVisible = true, currentStep = 1) }
    }

    fun nextStep() {
        if (currentState.currentStep >= ProductTourStep.TOTAL_STEPS) {
            completeTour()
        } else {
            updateState { it.copy(currentStep = it.currentStep + 1) }
        }
    }

    fun previousStep() {
        if (currentState.currentStep > 1) {
            updateState { it.copy(currentStep = it.currentStep - 1) }
        }
    }

    fun dismissTour() {
        localStorage.set(KEY_TOUR_COMPLETED, "true")
        updateState { it.copy(isVisible = false) }
    }

    fun completeTour() {
        dismissTour()
    }

    companion object {
        const val KEY_TOUR_COMPLETED = "inframap_tour_completed"
    }
}
