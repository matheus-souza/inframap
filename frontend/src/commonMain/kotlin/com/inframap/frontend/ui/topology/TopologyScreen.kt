package com.inframap.frontend.ui.topology

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapLoadingSkeleton
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.model.TopologyNode
import com.inframap.frontend.ui.topology.components.TopologyCanvas
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Suppress("LongMethod")
@Composable
fun TopologyScreen(
    state: TopologyState,
    actions: TopologyActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        TopologyHeader(
            zoomScale = state.zoomScale,
            onRefresh = actions.onRefresh,
            onResetViewport = actions.onResetViewport,
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            state.isLoading -> {
                InfraMapLoadingSkeleton(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            state.errorMessage != null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.errorMessage.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        InfraMapButton(
                            text = stringResource(Res.string.topology_retry),
                            onClick = actions.onRefresh,
                        )
                    }
                }
            }

            state.graph == null || state.graph.nodes.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.topology_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                ) {
                    TopologyCanvas(
                        state = state,
                        actions = actions,
                        modifier = Modifier.fillMaxSize(),
                    )

                    state.selectedNode?.let { node ->
                        NodeDetailsCard(
                            node = node,
                            onDismiss = actions.onDismissNodeDetails,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .width(280.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopologyHeader(
    zoomScale: Float,
    onRefresh: () -> Unit,
    onResetViewport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(Res.string.topology_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.topology_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val zoomPercent = (zoomScale * 100).roundToInt()
            Text(
                text = "$zoomPercent%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onResetViewport) {
                Text(stringResource(Res.string.topology_reset_view))
            }
            Spacer(modifier = Modifier.width(8.dp))
            InfraMapButton(
                text = stringResource(Res.string.topology_refresh),
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun NodeDetailsCard(
    node: TopologyNode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfraMapCard(
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = node.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.topology_close))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Type: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = node.deviceType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Status: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val deviceStatus =
                    when (node.status.lowercase()) {
                        "active" -> DeviceStatus.ACTIVE
                        "staged" -> DeviceStatus.STAGED
                        else -> DeviceStatus.OFFLINE
                    }
                InfraMapStatusBadge(status = deviceStatus)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "ID: ${node.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
