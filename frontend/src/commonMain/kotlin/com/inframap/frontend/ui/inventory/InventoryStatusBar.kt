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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun InventoryStatusBar(
    statusCounts: Map<InventoryStatus, Int>,
    activeFilter: InventoryStatus?,
    onStatusSelected: (InventoryStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalCount = statusCounts.values.sum()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = Color(0xFF18181B),
                    shape = RoundedCornerShape(12.dp),
                ).border(
                    width = 1.dp,
                    color = Color(0xFF27272A),
                    shape = RoundedCornerShape(12.dp),
                ).padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STATUS DISTRIBUTION",
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                color = Color(0xFFA1A1AA),
            )

            if (activeFilter != null) {
                Surface(
                    onClick = { onStatusSelected(null) },
                    shape = RoundedCornerShape(16.dp),
                    color = activeFilter.color.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, activeFilter.color),
                ) {
                    Text(
                        text = "Filtered by: ${activeFilter.label} ✕",
                        style = MaterialTheme.typography.labelSmall,
                        color = activeFilter.color,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else {
                Text(
                    text = "$totalCount Total Devices",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA1A1AA),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF27272A)),
        ) {
            if (totalCount == 0) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(Color(0xFF27272A)),
                )
            } else {
                InventoryStatus.entries.forEach { status ->
                    val count = statusCounts[status] ?: 0
                    if (count > 0) {
                        val weight = count.toFloat() / totalCount.toFloat()
                        val isSelected = activeFilter == status
                        Box(
                            modifier =
                                Modifier
                                    .weight(weight)
                                    .height(12.dp)
                                    .background(
                                        if (activeFilter == null || isSelected) {
                                            status.color
                                        } else {
                                            status.color.copy(alpha = 0.3f)
                                        },
                                    ).clickable {
                                        onStatusSelected(if (isSelected) null else status)
                                    },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InventoryStatus.entries.forEach { status ->
                val count = statusCounts[status] ?: 0
                val percentage = if (totalCount > 0) (count * 100) / totalCount else 0
                val isSelected = activeFilter == status

                val chipBg = if (isSelected) status.color.copy(alpha = 0.15f) else Color.Transparent
                val chipBorder = if (isSelected) status.color else Color(0xFF27272A)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                onStatusSelected(if (isSelected) null else status)
                            }.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(status.color),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFFF4F4F5),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$count ($percentage%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA1A1AA),
                    )
                }
            }
        }
    }
}
