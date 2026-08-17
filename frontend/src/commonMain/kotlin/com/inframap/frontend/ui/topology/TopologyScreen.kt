package com.inframap.frontend.ui.topology

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCanvasBg
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapLoadingSkeleton
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.topology_configure_discovery
import com.inframap.frontend.generated.resources.topology_empty_subtitle
import com.inframap.frontend.generated.resources.topology_empty_title
import com.inframap.frontend.generated.resources.topology_retry
import com.inframap.frontend.generated.resources.topology_subtitle
import com.inframap.frontend.generated.resources.topology_title
import org.jetbrains.compose.resources.stringResource

@Suppress("LongMethod")
@Composable
fun TopologyScreen(
    state: TopologyState,
    actions: TopologyActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(InfraMapCanvasBg),
    ) {
        when {
            state.isLoading -> {
                InfraMapLoadingSkeleton(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                )
            }

            state.errorMessage != null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
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
                // Interactive Topology Canvas showing clean ghost preview grid
                TopologyCanvas(
                    state = state,
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                )

                // Overlay explicit empty state container with icon & CTA button
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(InfraMapCanvasBg.copy(alpha = 0.65f))
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InfraMapEmptyState(
                        icon = Icons.Filled.Radar,
                        title = stringResource(Res.string.topology_empty_title),
                        subtitle = stringResource(Res.string.topology_empty_subtitle),
                        ctaLabel = stringResource(Res.string.topology_configure_discovery),
                        onCtaClick = actions.onConfigureDiscovery,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
            }

            else -> {
                // Interactive Topology Canvas
                TopologyCanvas(
                    state = state,
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                )

                // Top Bar Title & Subtitle
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(Res.string.topology_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = stringResource(Res.string.topology_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = InfraMapTextSecondary,
                        )
                    }
                }

                // Floating Top-Center Control Bar
                CanvasToolbar(
                    activeTool = state.activeTool,
                    zoomScale = state.zoomScale,
                    showSubnetBoundaries = state.showSubnetBoundaries,
                    onToolSelected = actions.onToolSelected,
                    onZoomIn = { actions.onZoom(1.15f) },
                    onZoomOut = { actions.onZoom(0.85f) },
                    onResetZoom = actions.onResetViewport,
                    onAutoLayout = actions.onAutoLayout,
                    onToggleSubnetBoundaries = actions.onToggleSubnetBoundaries,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                )

                // Right Slide-Over Device Inspector Sheet (360px)
                AnimatedVisibility(
                    visible = state.selectedNode != null,
                    enter =
                        slideInHorizontally(
                            initialOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit =
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing),
                        ) + fadeOut(animationSpec = tween(durationMillis = 250)),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    state.selectedNode?.let { node ->
                        DeviceInspectorSheet(
                            node = node,
                            onDismiss = actions.onDismissNodeDetails,
                            onTriggerScan = actions.onTriggerScan,
                            onEditMetadata = actions.onEditMetadata,
                        )
                    }
                }
            }
        }
    }
}
