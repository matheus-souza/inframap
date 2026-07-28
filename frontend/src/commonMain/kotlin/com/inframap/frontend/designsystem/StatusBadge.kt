@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class DeviceStatus {
    ACTIVE,
    OFFLINE,
    STAGED,
}

@Composable
fun InfraMapStatusBadge(
    status: DeviceStatus,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, label) =
        when (status) {
            DeviceStatus.ACTIVE -> InfraMapGreen to "Active"
            DeviceStatus.OFFLINE -> InfraMapRed to "Offline"
            DeviceStatus.STAGED -> InfraMapOrange to "Staged"
        }
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
