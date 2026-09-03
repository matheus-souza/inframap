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
import com.inframap.frontend.generated.resources.power_state_paused
import com.inframap.frontend.generated.resources.power_state_running
import com.inframap.frontend.generated.resources.power_state_stopped
import com.inframap.frontend.generated.resources.status_active
import com.inframap.frontend.generated.resources.status_cancelled
import com.inframap.frontend.generated.resources.status_error
import com.inframap.frontend.generated.resources.status_idle
import com.inframap.frontend.generated.resources.status_offline
import com.inframap.frontend.generated.resources.status_partial
import com.inframap.frontend.generated.resources.status_running
import com.inframap.frontend.generated.resources.status_staged
import org.jetbrains.compose.resources.stringResource

enum class DeviceStatus {
    ACTIVE,
    OFFLINE,
    STAGED,
}

/**
 * Runtime state a provider reports for a workload.
 *
 * Deliberately separate from [DeviceStatus]: a stopped container is still an actively
 * discovered device, so the two are shown side by side rather than collapsed into one.
 */
enum class PowerState {
    RUNNING,
    STOPPED,
    PAUSED,
    ;

    companion object {
        /**
         * Maps a provider's reported state onto the badge, returning null for anything
         * unrecognized so an unexpected value renders nothing instead of a wrong badge.
         * Docker reports "exited" where Proxmox reports "stopped".
         */
        fun fromRaw(raw: String?): PowerState? =
            when (raw?.trim()?.lowercase()) {
                "running" -> RUNNING
                "stopped", "exited" -> STOPPED
                "paused" -> PAUSED
                else -> null
            }
    }
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
fun InfraMapPowerStateBadge(
    powerState: PowerState,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, label) =
        when (powerState) {
            PowerState.RUNNING -> InfraMapGreen to stringResource(Res.string.power_state_running)
            PowerState.STOPPED -> InfraMapComment to stringResource(Res.string.power_state_stopped)
            PowerState.PAUSED -> InfraMapYellow to stringResource(Res.string.power_state_paused)
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
                MaterialTheme.colorScheme.tertiary to stringResource(Res.string.status_partial)
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
