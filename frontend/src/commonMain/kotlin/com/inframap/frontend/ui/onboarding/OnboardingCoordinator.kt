package com.inframap.frontend.ui.onboarding

import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.ui.tour.ProductTourViewModel
import com.inframap.frontend.ui.wizard.SetupWizardViewModel

class OnboardingCoordinator(
    private val localStorage: LocalStorage,
) {
    fun shouldShowWizard(
        totalSubnets: Long,
        totalActiveDevices: Long,
    ): Boolean {
        val isFreshInstall = totalSubnets == 0L && totalActiveDevices == 0L
        val dismissed = localStorage.get(SetupWizardViewModel.KEY_WIZARD_DISMISSED) != null
        val completed = localStorage.get(SetupWizardViewModel.KEY_WIZARD_COMPLETED) != null
        return isFreshInstall && !dismissed && !completed
    }

    fun shouldShowTour(): Boolean {
        val tourCompleted = localStorage.get(ProductTourViewModel.KEY_TOUR_COMPLETED) != null
        val wizardActive = isWizardActive()
        return !tourCompleted && !wizardActive
    }

    fun onWizardCompleted() {
        localStorage.set(SetupWizardViewModel.KEY_WIZARD_COMPLETED, "true")
    }

    private fun isWizardActive(): Boolean {
        val dismissed = localStorage.get(SetupWizardViewModel.KEY_WIZARD_DISMISSED) != null
        val completed = localStorage.get(SetupWizardViewModel.KEY_WIZARD_COMPLETED) != null
        return !dismissed && !completed
    }
}
