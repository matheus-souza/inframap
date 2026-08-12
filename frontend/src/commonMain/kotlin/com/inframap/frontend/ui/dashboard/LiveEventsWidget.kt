package com.inframap.frontend.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapCyan
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.designsystem.StatusOfflineColor
import com.inframap.frontend.designsystem.StatusOnlineColor
import com.inframap.frontend.designsystem.StatusStagingColor
import com.inframap.frontend.designsystem.StatusWarningColor

@Composable
fun LiveEventsWidget(
    events: List<DashboardEventItem>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .border(1.dp, InfraMapBorder, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
        color = InfraMapSurfaceBg,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusOnlineColor)
                                .semantics { contentDescription = "SSE Live indicator dot" },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Live SSE Stream",
                        style = MaterialTheme.typography.titleMedium,
                        color = InfraMapTextPrimary,
                    )
                }
                Text(
                    text = "${events.size} eventos",
                    style = MaterialTheme.typography.labelSmall,
                    color = InfraMapTextSecondary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (events.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Aguardando eventos em tempo real via SSE stream...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InfraMapTextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(events, key = { it.id }) { item ->
                        EventRowItem(event = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRowItem(event: DashboardEventItem) {
    val pillColor =
        when (event.eventType) {
            "DiscoveryProgress" -> StatusWarningColor
            "DeviceCreated" -> StatusOnlineColor
            "DeviceUpdated" -> InfraMapCyan
            "TopologyUpdated", "StagingUpdated" -> StatusStagingColor
            "Connected" -> StatusOnlineColor
            "Disconnected" -> StatusOfflineColor
            else -> InfraMapTextSecondary
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = event.timestamp,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = InfraMapTextSecondary,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = pillColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = event.eventType,
                style = MaterialTheme.typography.labelSmall,
                color = pillColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = event.message,
            style = MaterialTheme.typography.bodySmall,
            color = InfraMapTextPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
