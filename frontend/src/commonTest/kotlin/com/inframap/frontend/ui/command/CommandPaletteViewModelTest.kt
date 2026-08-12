package com.inframap.frontend.ui.command

import app.cash.turbine.test
import com.inframap.frontend.domain.usecase.command.SearchIndexUseCase
import com.inframap.frontend.fakes.FakeDashboardRepository
import com.inframap.frontend.fakes.FakeDeviceRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommandPaletteViewModelTest {
    private fun makeViewModel(scope: CoroutineScope? = null): CommandPaletteViewModel {
        val searchUseCase =
            SearchIndexUseCase(
                FakeDeviceRepository(),
                FakeSubnetRepository(),
                FakeDashboardRepository(),
            )
        return CommandPaletteViewModel(searchUseCase, scope = scope)
    }

    @Test
    fun initialModeIsClosed() {
        val vm = makeViewModel()
        assertFalse(vm.state.value.isOpen)
        assertEquals("", vm.state.value.query)
        assertEquals(0, vm.state.value.selectedIndex)
        vm.clear()
    }

    @Test
    fun openAndCloseTogglesState() =
        runTest {
            val vm = makeViewModel(scope = this)

            vm.open()
            advanceUntilIdle()

            assertTrue(vm.state.value.isOpen)
            assertTrue(
                vm.state.value.results
                    .isNotEmpty(),
            )

            vm.close()
            assertFalse(vm.state.value.isOpen)
            vm.clear()
        }

    @Test
    fun toggleSwitchesState() =
        runTest {
            val vm = makeViewModel(scope = this)

            vm.toggle()
            advanceUntilIdle()
            assertTrue(vm.state.value.isOpen)

            vm.toggle()
            assertFalse(vm.state.value.isOpen)
            vm.clear()
        }

    @Test
    fun arrowKeysNavigateSelection() =
        runTest {
            val vm = makeViewModel(scope = this)
            vm.open()
            advanceUntilIdle()

            val itemCount = vm.state.value.results.size
            assertTrue(itemCount > 1)

            assertEquals(0, vm.state.value.selectedIndex)

            vm.onNextItem()
            assertEquals(1, vm.state.value.selectedIndex)

            vm.onPreviousItem()
            assertEquals(0, vm.state.value.selectedIndex)

            vm.onPreviousItem()
            assertEquals(itemCount - 1, vm.state.value.selectedIndex)
            vm.clear()
        }

    @Test
    fun selectingItemEmitsEffectAndClosesModal() =
        runTest {
            val vm = makeViewModel(scope = this)
            vm.open()
            advanceUntilIdle()

            val firstItem =
                vm.state.value.results
                    .first()

            vm.effects.test {
                vm.selectCurrentItem()

                val effect = awaitItem()
                assertTrue(effect is CommandPaletteEffect.ExecuteItem)
                assertEquals(firstItem.id, effect.item.id)

                val closeEffect = awaitItem()
                assertTrue(closeEffect is CommandPaletteEffect.ClosePalette)
                cancelAndIgnoreRemainingEvents()
            }

            assertFalse(vm.state.value.isOpen)
            vm.clear()
        }
}
