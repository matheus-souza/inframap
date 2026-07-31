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
    var loadPageCallCount: Int = 0

    override fun loadPage(
        page: Int,
        perPage: Int,
    ) {
        loadPageCallCount++
        lastLoadedPage = page
        lastLoadedPerPage = perPage
        updateState { copy(currentPage = page) }
    }

    fun setTotalItems(total: Long) {
        updateState { copy(totalItems = total) }
    }

    fun setLoading(loading: Boolean) {
        updateState { copy(isLoading = loading) }
    }
}

class BaseListViewModelTest {
    @Test
    fun loadPageTriggersCorrectParameters() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            vm.loadPage(2, 20)
            assertEquals(1, vm.loadPageCallCount)
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
            assertEquals(1, vm.loadPageCallCount)
            vm.refresh()
            assertEquals(2, vm.loadPageCallCount)
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
            assertEquals(1, vm.loadPageCallCount)

            vm.nextPage()
            assertEquals(2, vm.loadPageCallCount)
            assertEquals(2, vm.lastLoadedPage)

            vm.nextPage()
            assertEquals(3, vm.loadPageCallCount)
            assertEquals(3, vm.lastLoadedPage)

            // Page 3 is last page, nextPage should be no-op
            vm.nextPage()
            assertEquals(3, vm.loadPageCallCount)
            assertEquals(3, vm.lastLoadedPage)
            vm.clear()
        }

    @Test
    fun previousPageNavigatesWhenNotAtFirstPage() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            vm.loadPage(2)
            assertEquals(1, vm.loadPageCallCount)

            vm.previousPage()
            assertEquals(2, vm.loadPageCallCount)
            assertEquals(1, vm.lastLoadedPage)

            // At page 1, previousPage should be no-op
            vm.previousPage()
            assertEquals(2, vm.loadPageCallCount)
            assertEquals(1, vm.lastLoadedPage)
            vm.clear()
        }

    @Test
    fun navigationMethodsAreNoOpWhenLoading() =
        runTest {
            val vm = TestListViewModel(this, defaultPerPage = 10)
            vm.loadPage(1)
            assertEquals(1, vm.loadPageCallCount)
            vm.setTotalItems(30)
            vm.setLoading(true)

            vm.refresh()
            assertEquals(1, vm.loadPageCallCount)

            vm.nextPage()
            assertEquals(1, vm.loadPageCallCount)

            vm.previousPage()
            assertEquals(1, vm.loadPageCallCount)
            vm.clear()
        }
}
