@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.discovery_status_partial
import com.inframap.frontend.generated.resources.status_active
import com.inframap.frontend.generated.resources.status_cancelled
import com.inframap.frontend.generated.resources.status_error
import com.inframap.frontend.generated.resources.status_idle
import com.inframap.frontend.generated.resources.status_offline
import com.inframap.frontend.generated.resources.status_running
import com.inframap.frontend.generated.resources.status_staged
import org.jetbrains.compose.resources.stringResource

enum class DeviceStatus {
    ACTIVE,
    OFFLINE,
    STAGED,
}

enum class SourceStatus {
    IDLE,
    RUNNING,
    PARTIAL,
    ERROR,
    CANCELLED,
}

@Composable
fun InfraMapStatusBadge(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, label) =
        when (status) {
            DeviceStatus.ACTIVE -> InfraMapGreen to stringResource(Res.string.status_active)
            DeviceStatus.OFFLINE -> InfraMapRed to stringResource(Res.string.status_offline)
            DeviceStatus.STAGED -> InfraMapOrange to stringResource(Res.string.status_staged)
        }
    StatusBadgeContent(backgroundColor = backgroundColor, label = label, modifier = modifier)
}

@Composable
fun InfraMapStatusBadge(
    status: SourceStatus,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, label) =
        when (status) {
            SourceStatus.IDLE -> InfraMapComment to stringResource(Res.string.status_idle)
            SourceStatus.RUNNING -> InfraMapGreen to stringResource(Res.string.status_running)
            SourceStatus.PARTIAL ->
                MaterialTheme.colorScheme.tertiary to stringResource(Res.string.discovery_status_partial)
            SourceStatus.ERROR -> InfraMapRed to stringResource(Res.string.status_error)
            SourceStatus.CANCELLED -> InfraMapOrange to stringResource(Res.string.status_cancelled)
        }
    StatusBadgeContent(backgroundColor = backgroundColor, label = label, modifier = modifier)
}

@Composable
private fun StatusBadgeContent(
    backgroundColor: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = backgroundColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = backgroundColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
