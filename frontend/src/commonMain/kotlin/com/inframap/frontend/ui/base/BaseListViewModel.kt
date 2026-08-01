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
    private val defaultPerPage: Int = 50,
    scope: CoroutineScope? = null,
) : BaseViewModel<S>(initialState, scope) {
    abstract fun loadPage(
        page: Int,
        perPage: Int = defaultPerPage,
    )

    fun refresh() {
        if (currentState.isLoading) return
        loadPage(currentState.currentPage)
    }

    fun nextPage() {
        if (currentState.isLoading) return
        val totalPages = (currentState.totalItems + defaultPerPage - 1) / defaultPerPage
        if (currentState.currentPage < totalPages.toInt()) {
            loadPage(currentState.currentPage + 1)
        }
    }

    fun previousPage() {
        if (currentState.isLoading) return
        if (currentState.currentPage > 1) {
            loadPage(currentState.currentPage - 1)
        }
    }
}
