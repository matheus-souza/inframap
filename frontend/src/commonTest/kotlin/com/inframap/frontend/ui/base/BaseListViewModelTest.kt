package com.inframap.frontend.ui.base

import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class TestListState(
    override val isLoading: Boolean = false,
    override val errorMessage: UiText? = null,
    override val currentPage: Int = 1,
    override val totalItems: Long = 0,
) : Paginated

private class TestListViewModel(
    scope: CoroutineScope,
    defaultPerPage: Int = 10,
) : BaseListViewModel<TestListState>(TestListState(), scope, defaultPerPage) {
    var lastLoadedPage: Int = 0
    var lastLoadedPerPage: Int = 0

    override fun loadPage(
        page: Int,
        perPage: Int,
    ) {
        lastLoadedPage = page
        lastLoadedPerPage = perPage
        updateState { copy(currentPage = page) }
    }

    fun setTotalItems(total: Long) {
        updateState { copy(totalItems = total) }
    }
}

class BaseListViewModelTest {
    @Test
    fun loadPageTriggersCorrectParameters() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            vm.loadPage(2, 20)
            assertEquals(2, vm.lastLoadedPage)
            assertEquals(20, vm.lastLoadedPerPage)
            assertEquals(2, vm.state.value.currentPage)
            vm.clear()
        }

    @Test
    fun refreshReloadsCurrentPage() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            vm.loadPage(3)
            vm.refresh()
            assertEquals(3, vm.lastLoadedPage)
            vm.clear()
        }

    @Test
    fun nextPageNavigatesWhenNotAtEnd() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            // Set state to page 1, 25 total items => 3 pages total
            vm.loadPage(1)
            vm.setTotalItems(25)

            vm.nextPage()
            assertEquals(2, vm.lastLoadedPage)

            vm.nextPage()
            assertEquals(3, vm.lastLoadedPage)

            // Page 3 is last page, nextPage should be no-op
            vm.nextPage()
            assertEquals(3, vm.lastLoadedPage)
            vm.clear()
        }

    @Test
    fun previousPageNavigatesWhenNotAtFirstPage() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            vm.loadPage(2)

            vm.previousPage()
            assertEquals(1, vm.lastLoadedPage)

            // At page 1, previousPage should be no-op
            vm.previousPage()
            assertEquals(1, vm.lastLoadedPage)
            vm.clear()
        }
}
