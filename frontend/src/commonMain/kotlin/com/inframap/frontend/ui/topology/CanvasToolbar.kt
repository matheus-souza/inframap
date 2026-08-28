package com.inframap.frontend.ui.topology

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inframap.frontend.designsystem.InfraMapBorder
import com.inframap.frontend.designsystem.InfraMapPurple
import com.inframap.frontend.designsystem.InfraMapSurfaceBg
import com.inframap.frontend.designsystem.InfraMapSurfaceElevated
import com.inframap.frontend.designsystem.InfraMapTextPrimary
import com.inframap.frontend.designsystem.InfraMapTextSecondary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.topology_toolbar_auto_layout
import com.inframap.frontend.generated.resources.topology_toolbar_hand_mode
import com.inframap.frontend.generated.resources.topology_toolbar_pointer_mode
import com.inframap.frontend.generated.resources.topology_toolbar_reset_zoom
import com.inframap.frontend.generated.resources.topology_toolbar_toggle_subnets
import com.inframap.frontend.generated.resources.topology_toolbar_zoom_in
import com.inframap.frontend.generated.resources.topology_toolbar_zoom_out
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Suppress("LongParameterList", "LongMethod")
@Composable
fun CanvasToolbar(
    activeTool: CanvasTool,
    zoomScale: Float,
    showSubnetBoundaries: Boolean,
    onToolSelected: (CanvasTool) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onAutoLayout: () -> Unit,
    onToggleSubnetBoundaries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, InfraMapBorder, RoundedCornerShape(12.dp)),
        color = InfraMapSurfaceBg,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            // Mode Selectors
            ToolIconButton(
                icon = Icons.Default.Mouse,
                contentDescription = stringResource(Res.string.topology_toolbar_pointer_mode),
                isSelected = activeTool == CanvasTool.POINTER,
                onClick = { onToolSelected(CanvasTool.POINTER) },
            )

            ToolIconButton(
                icon = Icons.Default.PanTool,
                contentDescription = stringResource(Res.string.topology_toolbar_hand_mode),
                isSelected = activeTool == CanvasTool.HAND,
                onClick = { onToolSelected(CanvasTool.HAND) },
            )

            ToolbarDivider()

            // Zoom Controls
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(Res.string.topology_toolbar_zoom_out),
                    tint = InfraMapTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = "${(zoomScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = InfraMapTextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.topology_toolbar_zoom_in),
                    tint = InfraMapTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            IconButton(
                onClick = onResetZoom,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusWeak,
                    contentDescription = stringResource(Res.string.topology_toolbar_reset_zoom),
                    tint = InfraMapTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            ToolbarDivider()

            // Subnet boundaries toggle
            ToolIconButton(
                icon = Icons.Default.GridView,
                contentDescription = stringResource(Res.string.topology_toolbar_toggle_subnets),
                isSelected = showSubnetBoundaries,
                onClick = onToggleSubnetBoundaries,
            )

            // Force-Directed Auto-Layout
            IconButton(
                onClick = onAutoLayout,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(Res.string.topology_toolbar_auto_layout),
                    tint = InfraMapPurple,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) InfraMapSurfaceElevated else Color.Transparent
    val tint = if (isSelected) InfraMapPurple else InfraMapTextSecondary

    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Spacer(modifier = Modifier.width(4.dp))
    Box(
        modifier =
            Modifier
                .width(1.dp)
                .height(20.dp)
                .background(InfraMapBorder),
    )
    Spacer(modifier = Modifier.width(4.dp))
}
