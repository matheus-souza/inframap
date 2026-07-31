package com.inframap.frontend.ui.base

import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

interface Paginated {
    val isLoading: Boolean
    val errorMessage: UiText?
    val currentPage: Int
    val totalItems: Long
}

abstract class BaseListViewModel<S : Paginated>(
    initialState: S,
    scope: CoroutineScope,
    private val defaultPerPage: Int = 50,
) : BaseViewModel<S>(initialState, scope) {
    abstract fun loadPage(
        page: Int,
        perPage: Int = defaultPerPage,
    )

    fun refresh() = loadPage(currentState.currentPage)

    fun nextPage() {
        val totalPages = (currentState.totalItems + defaultPerPage - 1) / defaultPerPage
        if (currentState.currentPage < totalPages.toInt()) {
            loadPage(currentState.currentPage + 1)
        }
    }

    fun previousPage() {
        if (currentState.currentPage > 1) {
            loadPage(currentState.currentPage - 1)
        }
    }
}
