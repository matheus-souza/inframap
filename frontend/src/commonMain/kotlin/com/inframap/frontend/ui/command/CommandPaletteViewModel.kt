package com.inframap.frontend.ui.command

import com.inframap.frontend.domain.model.CommandPaletteItem
import com.inframap.frontend.domain.usecase.command.SearchIndexUseCase
import com.inframap.frontend.ui.base.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class CommandPaletteUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<CommandPaletteItem> = emptyList(),
    val selectedIndex: Int = 0,
    val isLoading: Boolean = false,
)

sealed interface CommandPaletteEffect {
    data class ExecuteItem(
        val item: CommandPaletteItem,
    ) : CommandPaletteEffect

    data object ClosePalette : CommandPaletteEffect
}

class CommandPaletteViewModel(
    private val searchIndexUseCase: SearchIndexUseCase,
    scope: CoroutineScope? = null,
) : BaseViewModel<CommandPaletteUiState>(CommandPaletteUiState(), scope = scope) {
    private val _effects = MutableSharedFlow<CommandPaletteEffect>(extraBufferCapacity = 64)
    val effects: SharedFlow<CommandPaletteEffect> = _effects.asSharedFlow()

    fun open() {
        updateState { it.copy(isOpen = true, query = "", selectedIndex = 0) }
        search("")
    }

    fun close() {
        updateState { it.copy(isOpen = false, query = "", selectedIndex = 0) }
        _effects.tryEmit(CommandPaletteEffect.ClosePalette)
    }

    fun toggle() {
        if (state.value.isOpen) {
            close()
        } else {
            open()
        }
    }

    fun onQueryChanged(query: String) {
        updateState { it.copy(query = query, selectedIndex = 0) }
        search(query)
    }

    fun onNextItem() {
        val total = state.value.results.size
        if (total == 0) return
        val next = (state.value.selectedIndex + 1) % total
        updateState { it.copy(selectedIndex = next) }
    }

    fun onPreviousItem() {
        val total = state.value.results.size
        if (total == 0) return
        val prev = if (state.value.selectedIndex <= 0) total - 1 else state.value.selectedIndex - 1
        updateState { it.copy(selectedIndex = prev) }
    }

    fun selectCurrentItem() {
        val current = state.value.results.getOrNull(state.value.selectedIndex) ?: return
        onItemClicked(current)
    }

    fun onItemClicked(item: CommandPaletteItem) {
        _effects.tryEmit(CommandPaletteEffect.ExecuteItem(item))
        close()
    }

    private fun search(query: String) {
        updateState { it.copy(isLoading = true) }
        launchJob("search", cancelPrevious = true) {
            val items = searchIndexUseCase(query)
            updateState { state ->
                val newIndex = if (items.isEmpty()) 0 else state.selectedIndex.coerceIn(0, items.size - 1)
                state.copy(
                    results = items,
                    selectedIndex = newIndex,
                    isLoading = false,
                )
            }
        }
    }
}
