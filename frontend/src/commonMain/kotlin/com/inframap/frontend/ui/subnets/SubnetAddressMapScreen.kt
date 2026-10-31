package com.inframap.frontend.ui.subnets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapEmptyState
import com.inframap.frontend.designsystem.InfraMapStatusBadge
import com.inframap.frontend.designsystem.DeviceStatus
import com.inframap.frontend.domain.model.SubnetAddressEntry
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.subnet_address_map_empty
import com.inframap.frontend.generated.resources.subnet_address_map_loading
import com.inframap.frontend.generated.resources.subnet_address_map_subtitle
import com.inframap.frontend.generated.resources.subnet_address_map_title
import com.inframap.frontend.ui.util.UiText
import org.jetbrains.compose.resources.stringResource

@Composable
fun SubnetAddressMapScreen(
    state: SubnetAddressMapUiState,
    actions: SubnetAddressMapActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(Res.string.subnet_address_map_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.subnet_address_map_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            when {
                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage.asString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.isLoading -> {
                    Text(
                        text = stringResource(Res.string.subnet_address_map_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                else -> {
                    val entries = state.addressMap?.entries ?: emptyList()
                    if (entries.isEmpty()) {
                        InfraMapEmptyState(
                            icon = Icons.Filled.Hub,
                            title = stringResource(Res.string.subnet_address_map_empty),
                            description = "",
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items = entries, key = { it.address }) { entry ->
                                AddressRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressRow(entry: SubnetAddressEntry) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        when (entry.state) {
            "free", "reserved", "conflict" -> InfraMapStatusBadge(status = DeviceStatus.ACTIVE)
            else -> InfraMapStatusBadge(status = DeviceStatus.OFFLINE)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.devices.joinToString { "${it.hostname} (${it.ipAssignment})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

data class SubnetAddressMapActions(
    val onBackClicked: () -> Unit = {},
)
