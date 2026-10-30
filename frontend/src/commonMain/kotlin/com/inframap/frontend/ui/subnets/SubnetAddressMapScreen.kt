package com.inframap.frontend.ui.subnets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inframap.frontend.data.dto.SubnetAddressDto
import com.inframap.frontend.data.dto.SubnetAddressMapResponse
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapDetailSkeleton
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.StatusOnline
import com.inframap.frontend.designsystem.motion.m3ClickableCursor
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_back_button
import com.inframap.frontend.generated.resources.devices_retry
import com.inframap.frontend.generated.resources.edit_subnet_header_title
import com.inframap.frontend.generated.resources.subnet_addresses_conflict
import com.inframap.frontend.generated.resources.subnet_addresses_free
import com.inframap.frontend.generated.resources.subnet_addresses_gateway
import com.inframap.frontend.generated.resources.subnet_addresses_hint
import com.inframap.frontend.generated.resources.subnet_addresses_in_use
import com.inframap.frontend.generated.resources.subnet_addresses_nobody
import com.inframap.frontend.generated.resources.subnet_addresses_reserved
import com.inframap.frontend.generated.resources.subnet_addresses_title
import com.inframap.frontend.ui.devices.deviceTypeLabel
import com.inframap.frontend.ui.devices.ipAssignmentLabel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The colour of each address state, shared by the grid and its legend. */
@Composable
private fun stateColor(state: String): Color =
    when (state) {
        "in_use" -> StatusOnline.copy(alpha = 0.55f)
        "reserved" -> MaterialTheme.colorScheme.primary
        "conflict" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

private val stateLabels: List<Pair<String, StringResource>> =
    listOf(
        "free" to Res.string.subnet_addresses_free,
        "in_use" to Res.string.subnet_addresses_in_use,
        "reserved" to Res.string.subnet_addresses_reserved,
        "conflict" to Res.string.subnet_addresses_conflict,
    )

@Composable
fun SubnetAddressMapScreen(
    state: SubnetAddressMapUiState,
    actions: SubnetAddressMapActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SubnetAddressMapHeader(cidr = state.map?.cidr, actions = actions)
            Spacer(modifier = Modifier.height(20.dp))
            val map = state.map
            when {
                state.isLoading -> InfraMapDetailSkeleton(cardCount = 2)
                state.errorMessage != null ->
                    SubnetAddressMapError(state.errorMessage.asString(), actions.onRetryClicked)
                map != null -> {
                    SubnetAddressCounts(map)
                    Spacer(modifier = Modifier.height(16.dp))
                    SubnetAddressGrid(
                        map = map,
                        selectedAddress = state.selectedAddress,
                        onSelected = actions.onAddressSelected,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SubnetAddressHolders(selected = state.selected, onDeviceClicked = actions.onDeviceClicked)
                }
            }
        }
    }
}

@Composable
private fun SubnetAddressMapHeader(
    cidr: String?,
    actions: SubnetAddressMapActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_back_button),
            onClick = actions.onBackClicked,
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.subnet_addresses_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = cidr ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
        InfraMapOutlinedButton(
            text = stringResource(Res.string.edit_subnet_header_title),
            onClick = actions.onEditSubnetClicked,
            leadingIcon = Icons.Default.Edit,
        )
    }
}

@Composable
private fun SubnetAddressMapError(
    message: String,
    onRetryClicked: () -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            InfraMapOutlinedButton(text = stringResource(Res.string.devices_retry), onClick = onRetryClicked)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubnetAddressCounts(map: SubnetAddressMapResponse) {
    val counts =
        mapOf("free" to map.free, "in_use" to map.inUse, "reserved" to map.reserved, "conflict" to map.conflict)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stateLabels.forEach { (state, label) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("count_$state")) {
                Box(modifier = Modifier.size(12.dp).background(stateColor(state), RoundedCornerShape(3.dp)))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${stringResource(label)}: ${counts.getValue(state)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubnetAddressGrid(
    map: SubnetAddressMapResponse,
    selectedAddress: String?,
    onSelected: (String) -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.subnet_addresses_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                map.addresses.forEach { entry ->
                    AddressCell(
                        entry = entry,
                        isSelected = entry.address == selectedAddress,
                        onClick = { onSelected(entry.address) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddressCell(
    entry: SubnetAddressDto,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val border =
        when {
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
            entry.isGateway -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
            else -> null
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(width = 40.dp, height = 28.dp)
                .background(stateColor(entry.state), RoundedCornerShape(4.dp))
                .then(if (border != null) Modifier.border(border, RoundedCornerShape(4.dp)) else Modifier)
                .m3ClickableCursor()
                .clickable(onClick = onClick)
                .testTag("address_${entry.address}"),
    ) {
        Text(
            text = entry.address.substringAfterLast('.'),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (entry.state == "free") {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                },
        )
    }
}

@Composable
private fun SubnetAddressHolders(
    selected: SubnetAddressDto?,
    onDeviceClicked: (String) -> Unit,
) {
    if (selected == null) return
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val stateName = stateLabels.firstOrNull { it.first == selected.state }?.second
            Text(
                text = selected.address + (stateName?.let { " · ${stringResource(it)}" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selected.isGateway) {
                Text(
                    text = stringResource(Res.string.subnet_addresses_gateway),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (selected.devices.isEmpty()) {
                Text(
                    text = stringResource(Res.string.subnet_addresses_nobody),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            selected.devices.forEach { device ->
                Text(
                    text =
                        "${device.hostname.ifEmpty { device.id }} · ${deviceTypeLabel(device.deviceType)} · " +
                            ipAssignmentLabel(device.ipAssignment),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .m3ClickableCursor()
                            .clickable { onDeviceClicked(device.id) }
                            .padding(vertical = 4.dp),
                )
            }
        }
    }
}
