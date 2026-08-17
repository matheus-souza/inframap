package com.inframap.frontend.ui.onboarding

import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.ui.tour.ProductTourViewModel
import com.inframap.frontend.ui.wizard.SetupWizardViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingCoordinatorTest {
    private fun createCoordinator(storage: FakeLocalStorage = FakeLocalStorage()) = Pair(storage, OnboardingCoordinator(storage))

    @Test
    fun freshInstallShowsWizard() {
        val (_, coordinator) = createCoordinator()
        assertTrue(coordinator.shouldShowWizard(totalSubnets = 0, totalActiveDevices = 0))
    }

    @Test
    fun existingDataDoesNotShowWizard() {
        val (_, coordinator) = createCoordinator()
        assertFalse(coordinator.shouldShowWizard(totalSubnets = 1, totalActiveDevices = 0))
    }

    @Test
    fun dismissedWizardDoesNotShow() {
        val (storage, coordinator) = createCoordinator()
        storage.set(SetupWizardViewModel.KEY_WIZARD_DISMISSED, "true")
        assertFalse(coordinator.shouldShowWizard(totalSubnets = 0, totalActiveDevices = 0))
    }

    @Test
    fun completedWizardDoesNotShow() {
        val (storage, coordinator) = createCoordinator()
        storage.set(SetupWizardViewModel.KEY_WIZARD_COMPLETED, "true")
        assertFalse(coordinator.shouldShowWizard(totalSubnets = 0, totalActiveDevices = 0))
    }

    @Test
    fun tourNotShownWhenWizardIsActive() {
        val (_, coordinator) = createCoordinator()
        assertFalse(coordinator.shouldShowTour())
    }

    @Test
    fun tourShownAfterWizardCompleted() {
        val (storage, coordinator) = createCoordinator()
        storage.set(SetupWizardViewModel.KEY_WIZARD_COMPLETED, "true")
        assertTrue(coordinator.shouldShowTour())
    }

    @Test
    fun tourShownAfterWizardDismissed() {
        val (storage, coordinator) = createCoordinator()
        storage.set(SetupWizardViewModel.KEY_WIZARD_DISMISSED, "true")
        assertTrue(coordinator.shouldShowTour())
    }

    @Test
    fun tourNotShownWhenAlreadyCompleted() {
        val (storage, coordinator) = createCoordinator()
        storage.set(SetupWizardViewModel.KEY_WIZARD_COMPLETED, "true")
        storage.set(ProductTourViewModel.KEY_TOUR_COMPLETED, "true")
        assertFalse(coordinator.shouldShowTour())
    }

    @Test
    fun onWizardCompletedSetsStorageKey() {
        val (storage, coordinator) = createCoordinator()
        coordinator.onWizardCompleted()
        assertTrue(storage.get(SetupWizardViewModel.KEY_WIZARD_COMPLETED) != null)
    }
}
