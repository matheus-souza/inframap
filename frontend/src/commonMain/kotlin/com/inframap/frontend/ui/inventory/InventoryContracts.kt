package com.inframap.frontend.ui.inventory

import androidx.compose.ui.graphics.Color

enum class InventoryStatus(
    val label: String,
    val color: Color,
) {
    ONLINE(
        label = "Online",
        color = Color(0xFF10B981),
    ),
    WARNING(
        label = "Warning",
        color = Color(0xFFF59E0B),
    ),
    STAGING(
        label = "Staging",
        color = Color(0xFF8B5CF6),
    ),
    OFFLINE(
        label = "Offline",
        color = Color(0xFFEF4444),
    ),
}

data class InventoryItem(
    val id: String,
    val hostname: String,
    val ipAddress: String,
    val macAddress: String? = null,
    val deviceType: String,
    val status: InventoryStatus,
    val subnet: String,
    val discoveryProtocol: String,
    val latencyMs: Long? = null,
)

sealed class BatchActionPayload {
    abstract val selectedIds: Set<String>

    data class Approve(
        override val selectedIds: Set<String>,
    ) : BatchActionPayload()

    data class Rescan(
        override val selectedIds: Set<String>,
    ) : BatchActionPayload()

    data class Delete(
        override val selectedIds: Set<String>,
    ) : BatchActionPayload()
}

enum class BatchActionType {
    APPROVE,
    RESCAN,
    DELETE,
}

data class InventoryActions(
    val onStatusSelected: (InventoryStatus?) -> Unit,
    val onSearchQueryChanged: (String) -> Unit,
    val onToggleSelectAll: () -> Unit,
    val onToggleSelectItem: (String) -> Unit,
    val onApproveSelected: () -> Unit,
    val onRescanSelected: () -> Unit,
    val onDeleteSelected: () -> Unit,
    val onClearSelection: () -> Unit,
    val onItemRescan: (InventoryItem) -> Unit,
    val onItemDelete: (InventoryItem) -> Unit,
)

data class InventoryUiState(
    val items: List<InventoryItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val activeStatusFilter: InventoryStatus? = null,
    val searchQuery: String = "",
) {
    val filteredItems: List<InventoryItem>
        get() =
            items.filter { item ->
                val matchesStatus = activeStatusFilter == null || item.status == activeStatusFilter
                val matchesSearch =
                    searchQuery.isBlank() ||
                        item.hostname.contains(searchQuery, ignoreCase = true) ||
                        item.ipAddress.contains(searchQuery, ignoreCase = true) ||
                        item.subnet.contains(searchQuery, ignoreCase = true) ||
                        (item.macAddress?.contains(searchQuery, ignoreCase = true) == true) ||
                        item.deviceType.contains(searchQuery, ignoreCase = true)
                matchesStatus && matchesSearch
            }

    val statusCounts: Map<InventoryStatus, Int>
        get() =
            InventoryStatus.entries.associateWith { status ->
                items.count { it.status == status }
            }

    val isAllSelected: Boolean
        get() = filteredItems.isNotEmpty() && filteredItems.all { it.id in selectedIds }

    fun createBatchAction(type: BatchActionType): BatchActionPayload? {
        if (selectedIds.isEmpty()) return null
        return when (type) {
            BatchActionType.APPROVE -> BatchActionPayload.Approve(selectedIds)
            BatchActionType.RESCAN -> BatchActionPayload.Rescan(selectedIds)
            BatchActionType.DELETE -> BatchActionPayload.Delete(selectedIds)
        }
    }
}
