package com.inframap.frontend.ui.command

import app.cash.turbine.test
import com.inframap.frontend.domain.model.CommandPaletteAction
import com.inframap.frontend.domain.model.CommandPaletteCategory
import com.inframap.frontend.domain.model.CommandPaletteItem
import com.inframap.frontend.domain.usecase.command.SearchIndexUseCase
import com.inframap.frontend.fakes.FakeDashboardRepository
import com.inframap.frontend.fakes.FakeDeviceRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import com.inframap.frontend.navigation.Route
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
    private fun makeViewModel(
        useCase: SearchIndexUseCase =
            SearchIndexUseCase(
                FakeDeviceRepository(),
                FakeSubnetRepository(),
                FakeDashboardRepository(),
            ),
        scope: CoroutineScope? = null,
    ): CommandPaletteViewModel = CommandPaletteViewModel(useCase, scope = scope)

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
    fun onQueryChangedUpdatesStateAndResults() =
        runTest {
            val vm = makeViewModel(scope = this)
            vm.open()
            advanceUntilIdle()

            vm.onQueryChanged("Dispositivo")
            advanceUntilIdle()

            assertEquals("Dispositivo", vm.state.value.query)
            assertEquals(0, vm.state.value.selectedIndex)
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
    fun navigationInEmptyResultsDoesNotCrash() =
        runTest {
            val emptyUseCase =
                SearchIndexUseCase(
                    FakeDeviceRepository(),
                    FakeSubnetRepository(),
                    FakeDashboardRepository(),
                )
            val vm = CommandPaletteViewModel(emptyUseCase, scope = this)

            // State has no results
            vm.onNextItem()
            assertEquals(0, vm.state.value.selectedIndex)

            vm.onPreviousItem()
            assertEquals(0, vm.state.value.selectedIndex)

            vm.selectCurrentItem()
            assertFalse(vm.state.value.isOpen)
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

    @Test
    fun onItemClickedDirectlyEmitsEffectAndCloses() =
        runTest {
            val vm = makeViewModel(scope = this)
            vm.open()
            advanceUntilIdle()

            val testItem =
                CommandPaletteItem(
                    id = "test-item",
                    title = "Test",
                    subtitle = "Sub",
                    category = CommandPaletteCategory.ACOES,
                    action = CommandPaletteAction.Navigate(Route.Dashboard),
                )

            vm.effects.test {
                vm.onItemClicked(testItem)

                val effect = awaitItem()
                assertTrue(effect is CommandPaletteEffect.ExecuteItem)
                assertEquals("test-item", effect.item.id)

                val closeEffect = awaitItem()
                assertTrue(closeEffect is CommandPaletteEffect.ClosePalette)
                cancelAndIgnoreRemainingEvents()
            }

            assertFalse(vm.state.value.isOpen)
            vm.clear()
        }
}
