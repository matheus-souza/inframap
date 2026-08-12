package com.inframap.frontend.ui.inventory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapTextField

@Suppress("LongMethod")
@Composable
fun InventoryScreen(
    state: InventoryUiState,
    actions: InventoryActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Inventário de Ativos",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFF4F4F5),
            )
            Text(
                text = "Visualização de alta densidade dos ativos de rede, distribuição de status e ações em lote",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFA1A1AA),
            )

            Spacer(modifier = Modifier.height(16.dp))

            InventoryStatusBar(
                statusCounts = state.statusCounts,
                activeFilter = state.activeStatusFilter,
                onStatusSelected = actions.onStatusSelected,
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfraMapTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearchQueryChanged,
                label = "Filtrar por hostname, IP, MAC ou sub-rede...",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            InventoryTable(
                items = state.filteredItems,
                selectedIds = state.selectedIds,
                onToggleSelectAll = actions.onToggleSelectAll,
                onToggleSelectItem = actions.onToggleSelectItem,
                onItemRescan = actions.onItemRescan,
                onItemDelete = actions.onItemDelete,
                modifier = Modifier.weight(1f),
            )
        }

        FloatingBatchActionBar(
            selectedCount = state.selectedIds.size,
            onApproveSelected = actions.onApproveSelected,
            onRescanSelected = actions.onRescanSelected,
            onDeleteSelected = actions.onDeleteSelected,
            onClearSelection = actions.onClearSelection,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
