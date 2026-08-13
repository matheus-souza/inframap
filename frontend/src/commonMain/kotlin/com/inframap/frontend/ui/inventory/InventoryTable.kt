package com.inframap.frontend.ui.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val JetBrainsMonoFontFamily: FontFamily = FontFamily.Monospace

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun InventoryTable(
    items: List<InventoryItem>,
    selectedIds: Set<String>,
    onToggleSelectAll: () -> Unit,
    onToggleSelectItem: (String) -> Unit,
    onItemRescan: (InventoryItem) -> Unit,
    onItemDelete: (InventoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAllSelected = items.isNotEmpty() && items.all { it.id in selectedIds }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0xFF18181B), shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF27272A), shape = RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1F23))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(40.dp)) {
                Checkbox(
                    checked = isAllSelected,
                    onCheckedChange = { onToggleSelectAll() },
                    colors =
                        CheckboxDefaults.colors(
                            checkedColor = Color(0xFF8B5CF6),
                            uncheckedColor = Color(0xFFA1A1AA),
                        ),
                )
            }

            Text(
                text = "STATUS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.weight(1.2f),
            )

            Text(
                text = "HOSTNAME & IP",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.weight(2.5f),
            )

            Text(
                text = "TIPO",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.weight(1.2f),
            )

            Text(
                text = "SUB-REDE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.weight(1.8f),
            )

            Text(
                text = "PROTOCOLO",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.weight(1.3f),
            )

            Text(
                text = "LATÊNCIA",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.weight(1.2f),
            )

            Text(
                text = "AÇÕES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFA1A1AA),
                modifier = Modifier.width(80.dp),
            )
        }

        HorizontalDivider(color = Color(0xFF27272A))

        if (items.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhum ativo encontrado para os filtros selecionados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA1A1AA),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(items) { index, item ->
                    val isSelected = item.id in selectedIds
                    val rowBg = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.08f) else Color.Transparent

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .clickable { onToggleSelectItem(item.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.width(40.dp)) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onToggleSelectItem(item.id) },
                                colors =
                                    CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF8B5CF6),
                                        uncheckedColor = Color(0xFFA1A1AA),
                                    ),
                            )
                        }

                        Box(modifier = Modifier.weight(1.2f)) {
                            InventoryStatusPill(status = item.status)
                        }

                        Column(modifier = Modifier.weight(2.5f)) {
                            Text(
                                text = item.hostname,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Color(0xFFF4F4F5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.ipAddress,
                                style =
                                    TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        color = Color(0xFFA1A1AA),
                                    ),
                            )
                            if (item.macAddress != null) {
                                Text(
                                    text = item.macAddress,
                                    style =
                                        TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF71717A),
                                        ),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.weight(1.2f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = getDeviceTypeIcon(item.deviceType),
                                contentDescription = item.deviceType,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFA1A1AA),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.deviceType,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF4F4F5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Text(
                            text = item.subnet,
                            style =
                                TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF4F4F5),
                                ),
                            modifier = Modifier.weight(1.8f),
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF27272A),
                            modifier = Modifier.weight(1.3f),
                        ) {
                            Text(
                                text = item.discoveryProtocol,
                                style =
                                    TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF8BE9FD),
                                    ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Text(
                            text = if (item.latencyMs != null) "${item.latencyMs} ms" else "N/A",
                            style =
                                TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color =
                                        when {
                                            item.latencyMs == null -> Color(0xFFEF4444)
                                            item.latencyMs < 30 -> Color(0xFF10B981)
                                            item.latencyMs < 100 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        },
                                ),
                            modifier = Modifier.weight(1.2f),
                        )

                        Row(
                            modifier = Modifier.width(80.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(
                                onClick = { onItemRescan(item) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Re-scan",
                                    tint = Color(0xFFA1A1AA),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = { onItemDelete(item) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Excluir",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    if (index < items.lastIndex) {
                        HorizontalDivider(color = Color(0xFF27272A).copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun InventoryStatusPill(
    status: InventoryStatus,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = status.color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, status.color.copy(alpha = 0.3f)),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = status.color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun getDeviceTypeIcon(deviceType: String): ImageVector =
    when (deviceType.lowercase()) {
        "router" -> Icons.Filled.Router
        "switch", "server" -> Icons.Filled.Dns
        "firewall" -> Icons.Filled.Security
        "storage" -> Icons.Filled.Storage
        "workstation", "pc" -> Icons.Filled.Computer
        else -> Icons.Filled.Memory
    }
