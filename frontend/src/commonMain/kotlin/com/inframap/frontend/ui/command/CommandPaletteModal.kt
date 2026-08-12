@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.ui.command

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapSurfaceElevated
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.domain.model.CommandPaletteCategory
import com.inframap.frontend.domain.model.CommandPaletteItem

data class CommandPaletteActions(
    val onQueryChanged: (String) -> Unit,
    val onNextItem: () -> Unit,
    val onPreviousItem: () -> Unit,
    val onSelectItem: () -> Unit,
    val onItemClicked: (CommandPaletteItem) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
fun CommandPaletteModal(
    state: CommandPaletteUiState,
    actions: CommandPaletteActions,
) {
    if (!state.isOpen) return

    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = actions.onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(onClick = actions.onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            CommandPaletteModalContent(
                state = state,
                focusRequester = focusRequester,
                actions = actions,
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun CommandPaletteModalContent(
    state: CommandPaletteUiState,
    focusRequester: FocusRequester,
    actions: CommandPaletteActions,
) {
    Surface(
        modifier =
            Modifier
                .padding(top = 80.dp, start = 16.dp, end = 16.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, InfraMapBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false, onClick = {})
                .onPreviewKeyEvent { event ->
                    handleKeyEvent(event, actions)
                },
        color = InfraMapSurfaceBg,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            CommandPaletteSearchHeader(
                state = state,
                focusRequester = focusRequester,
                onQueryChanged = actions.onQueryChanged,
            )
            HorizontalDivider(color = InfraMapBorder, thickness = 1.dp)

            if (state.results.isEmpty() && !state.isLoading) {
                CommandPaletteEmptyState(query = state.query)
            } else {
                CommandPaletteResultsList(
                    state = state,
                    onItemClicked = actions.onItemClicked,
                )
            }
        }
    }
}

private fun handleKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    actions: CommandPaletteActions,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionDown -> {
            actions.onNextItem()
            true
        }

        Key.DirectionUp -> {
            actions.onPreviousItem()
            true
        }

        Key.Enter -> {
            actions.onSelectItem()
            true
        }

        Key.Escape -> {
            actions.onDismiss()
            true
        }

        else -> false
    }
}

@Composable
private fun CommandPaletteSearchHeader(
    state: CommandPaletteUiState,
    focusRequester: FocusRequester,
    onQueryChanged: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = InfraMapTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        TextField(
            value = state.query,
            onValueChange = onQueryChanged,
            placeholder = {
                Text(
                    text = "Buscar dispositivos, subredes, fontes ou ações...",
                    color = InfraMapTextSecondary,
                    fontSize = 14.sp,
                )
            },
            singleLine = true,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = InfraMapTextPrimary,
                    unfocusedTextColor = InfraMapTextPrimary,
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
        )
        CommandPaletteClearAndEscButtons(
            query = state.query,
            isLoading = state.isLoading,
            onClear = { onQueryChanged("") },
        )
    }
}

@Composable
private fun CommandPaletteClearAndEscButtons(
    query: String,
    isLoading: Boolean,
    onClear: () -> Unit,
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = InfraMapTextSecondary,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    if (query.isNotEmpty()) {
        IconButton(
            onClick = onClear,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = InfraMapTextSecondary,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(InfraMapBorder)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "ESC",
            color = InfraMapTextSecondary,
            fontSize = 11.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun CommandPaletteEmptyState(query: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                if (query.isBlank()) {
                    "Nenhum item disponível."
                } else {
                    "Nenhum resultado encontrado para \"$query\""
                },
            color = InfraMapTextSecondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun CommandPaletteResultsList(
    state: CommandPaletteUiState,
    onItemClicked: (CommandPaletteItem) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .padding(vertical = 8.dp),
    ) {
        var globalIndex = 0
        CommandPaletteCategory.entries.forEach { category ->
            val categoryItems = state.results.filter { it.category == category }
            if (categoryItems.isNotEmpty()) {
                item(key = "header-${category.name}") {
                    Text(
                        text = category.displayName.uppercase(),
                        color = InfraMapTextSecondary,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(categoryItems) { _, item ->
                    val currentIndex = globalIndex
                    val isSelected = currentIndex == state.selectedIndex
                    CommandPaletteItemRow(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onItemClicked(item) },
                    )
                    globalIndex++
                }
            }
        }
    }
}

@Composable
private fun CommandPaletteItemRow(
    item: CommandPaletteItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgModifier = if (isSelected) Modifier.background(InfraMapSurfaceElevated) else Modifier
    val icon: ImageVector =
        when (item.category) {
            CommandPaletteCategory.DISPOSITIVOS -> Icons.Default.Dns
            CommandPaletteCategory.SUBREDES -> Icons.Default.Lan
            CommandPaletteCategory.FONTES -> Icons.Default.Hub
            CommandPaletteCategory.ACOES -> Icons.Default.Bolt
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(bgModifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else InfraMapTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = InfraMapTextPrimary,
                fontSize = 14.sp,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!item.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    color = InfraMapTextSecondary,
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (isSelected) {
            Text(
                text = "↵",
                color = InfraMapTextSecondary,
                fontSize = 14.sp,
            )
        }
    }
}
