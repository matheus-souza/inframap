package com.inframap.frontend.ui.tour

import com.inframap.frontend.fakes.FakeLocalStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductTourViewModelTest {
    private fun makeViewModel(storage: FakeLocalStorage = FakeLocalStorage()): Pair<ProductTourViewModel, FakeLocalStorage> {
        val vm = ProductTourViewModel(localStorage = storage)
        return Pair(vm, storage)
    }

    @Test
    fun initialStateIsHidden() {
        val (vm, _) = makeViewModel()
        assertFalse(vm.state.value.isVisible)
        assertEquals(1, vm.state.value.currentStep)
        assertEquals(ProductTourStep.TOTAL_STEPS, vm.state.value.totalSteps)
        vm.clear()
    }

    @Test
    fun checkShouldShowShowsTourWhenNotCompleted() {
        val (vm, _) = makeViewModel()
        vm.checkShouldShow()
        assertTrue(vm.state.value.isVisible)
        assertEquals(1, vm.state.value.currentStep)
        assertEquals(ProductTourStep.WELCOME_NAV, vm.state.value.currentTourStep)
        vm.clear()
    }

    @Test
    fun checkShouldShowHidesTourWhenCompleted() {
        val storage = FakeLocalStorage()
        storage.set(ProductTourViewModel.KEY_TOUR_COMPLETED, "true")
        val vm = ProductTourViewModel(localStorage = storage)

        vm.checkShouldShow()
        assertFalse(vm.state.value.isVisible)
        vm.clear()
    }

    @Test
    fun startTourResetsToStepOneAndShows() {
        val storage = FakeLocalStorage()
        storage.set(ProductTourViewModel.KEY_TOUR_COMPLETED, "true")
        val vm = ProductTourViewModel(localStorage = storage)

        vm.startTour()
        assertTrue(vm.state.value.isVisible)
        assertEquals(1, vm.state.value.currentStep)
        vm.clear()
    }

    @Test
    fun nextStepAdvancesSteps() {
        val (vm, _) = makeViewModel()
        vm.startTour()

        assertEquals(1, vm.state.value.currentStep)
        assertEquals(ProductTourStep.WELCOME_NAV, vm.state.value.currentTourStep)

        vm.nextStep()
        assertEquals(2, vm.state.value.currentStep)
        assertEquals(ProductTourStep.COMMAND_PALETTE, vm.state.value.currentTourStep)

        vm.nextStep()
        assertEquals(3, vm.state.value.currentStep)
        assertEquals(ProductTourStep.DISCOVERY_SOURCES, vm.state.value.currentTourStep)

        vm.nextStep()
        assertEquals(4, vm.state.value.currentStep)
        assertEquals(ProductTourStep.STAGING_QUEUE, vm.state.value.currentTourStep)

        vm.nextStep()
        assertEquals(5, vm.state.value.currentStep)
        assertEquals(ProductTourStep.TOPOLOGY_CANVAS, vm.state.value.currentTourStep)
        vm.clear()
    }

    @Test
    fun nextStepOnLastStepCompletesTourAndPersists() {
        val (vm, storage) = makeViewModel()
        vm.startTour()

        repeat(ProductTourStep.TOTAL_STEPS - 1) {
            vm.nextStep()
        }
        assertEquals(5, vm.state.value.currentStep)
        assertTrue(vm.state.value.isVisible)

        vm.nextStep()
        assertFalse(vm.state.value.isVisible)
        assertNotNull(storage.get(ProductTourViewModel.KEY_TOUR_COMPLETED))
        vm.clear()
    }

    @Test
    fun previousStepDecrements() {
        val (vm, _) = makeViewModel()
        vm.startTour()
        vm.nextStep()
        vm.nextStep()
        assertEquals(3, vm.state.value.currentStep)

        vm.previousStep()
        assertEquals(2, vm.state.value.currentStep)
        assertEquals(ProductTourStep.COMMAND_PALETTE, vm.state.value.currentTourStep)
        vm.clear()
    }

    @Test
    fun previousStepDoesNothingOnStepOne() {
        val (vm, _) = makeViewModel()
        vm.startTour()
        assertEquals(1, vm.state.value.currentStep)

        vm.previousStep()
        assertEquals(1, vm.state.value.currentStep)
        vm.clear()
    }

    @Test
    fun dismissTourSetsLocalStorageAndHides() {
        val (vm, storage) = makeViewModel()
        vm.startTour()
        assertTrue(vm.state.value.isVisible)

        vm.dismissTour()
        assertFalse(vm.state.value.isVisible)
        assertNotNull(storage.get(ProductTourViewModel.KEY_TOUR_COMPLETED))
        vm.clear()
    }

    @Test
    fun completeTourSetsLocalStorageAndHides() {
        val (vm, storage) = makeViewModel()
        vm.startTour()
        assertTrue(vm.state.value.isVisible)

        vm.completeTour()
        assertFalse(vm.state.value.isVisible)
        assertNotNull(storage.get(ProductTourViewModel.KEY_TOUR_COMPLETED))
        vm.clear()
    }

    @Test
    fun fromStepNumberReturnsCorrectStep() {
        assertEquals(ProductTourStep.WELCOME_NAV, ProductTourStep.fromStepNumber(1))
        assertEquals(ProductTourStep.COMMAND_PALETTE, ProductTourStep.fromStepNumber(2))
        assertEquals(ProductTourStep.DISCOVERY_SOURCES, ProductTourStep.fromStepNumber(3))
        assertEquals(ProductTourStep.STAGING_QUEUE, ProductTourStep.fromStepNumber(4))
        assertEquals(ProductTourStep.TOPOLOGY_CANVAS, ProductTourStep.fromStepNumber(5))
        assertEquals(ProductTourStep.WELCOME_NAV, ProductTourStep.fromStepNumber(99))
    }
}
