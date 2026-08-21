package com.inframap.frontend.domain.model

import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.command_palette_category_acoes
import com.inframap.frontend.generated.resources.command_palette_category_dispositivos
import com.inframap.frontend.generated.resources.command_palette_category_navegacao
import com.inframap.frontend.generated.resources.command_palette_category_subredes
import com.inframap.frontend.navigation.Route
import org.jetbrains.compose.resources.StringResource

enum class CommandPaletteCategory(
    val titleRes: StringResource,
) {
    ACOES(Res.string.command_palette_category_acoes),
    DISPOSITIVOS(Res.string.command_palette_category_dispositivos),
    SUBREDES(Res.string.command_palette_category_subredes),
    NAVEGACAO(Res.string.command_palette_category_navegacao),
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
    val status: String? = null,
    val badge: String? = null,
)
