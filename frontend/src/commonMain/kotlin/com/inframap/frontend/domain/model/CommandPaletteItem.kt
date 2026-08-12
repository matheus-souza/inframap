package com.inframap.frontend.domain.model

import com.inframap.frontend.navigation.Route

enum class CommandPaletteCategory(
    val displayName: String,
) {
    DISPOSITIVOS("Dispositivos"),
    SUBREDES("Subredes"),
    FONTES("Fontes de Descoberta"),
    ACOES("Ações Rápidas"),
}

sealed interface CommandPaletteAction {
    data class Navigate(
        val route: Route,
    ) : CommandPaletteAction

    data object RefreshData : CommandPaletteAction
}

data class CommandPaletteItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val category: CommandPaletteCategory,
    val action: CommandPaletteAction,
)
