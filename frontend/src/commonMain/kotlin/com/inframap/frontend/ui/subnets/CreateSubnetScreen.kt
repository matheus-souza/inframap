package com.inframap.frontend.ui.subnets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapCheckboxRow
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.create_subnet_cidr_label
import com.inframap.frontend.generated.resources.create_subnet_description_label
import com.inframap.frontend.generated.resources.create_subnet_discovery_toggle
import com.inframap.frontend.generated.resources.create_subnet_gateway_label
import com.inframap.frontend.generated.resources.create_subnet_header_subtitle
import com.inframap.frontend.generated.resources.create_subnet_header_title
import com.inframap.frontend.generated.resources.create_subnet_name_label
import com.inframap.frontend.generated.resources.create_subnet_submit
import com.inframap.frontend.generated.resources.create_subnet_submitting
import com.inframap.frontend.generated.resources.create_subnet_suggestions_toggle
import com.inframap.frontend.generated.resources.create_subnet_vlan_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun CreateSubnetScreen(
    state: CreateSubnetUiState,
    actions: CreateSubnetActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CreateSubnetHeader()

            Spacer(modifier = Modifier.height(20.dp))

            InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    CreateSubnetFormFields(state = state, actions = actions)

                    Spacer(modifier = Modifier.height(24.dp))

                    CreateSubnetFormActions(
                        isSubmitting = state.isSubmitting,
                        onCancelClicked = actions.onCancelClicked,
                        onSubmitClicked = actions.onSubmitClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateSubnetFormFields(
    state: CreateSubnetUiState,
    actions: CreateSubnetActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = state.name,
            onValueChange = actions.onNameChanged,
            label = stringResource(Res.string.create_subnet_name_label),
            error = state.validationErrors["name"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.cidr,
            onValueChange = actions.onCidrChanged,
            label = stringResource(Res.string.create_subnet_cidr_label),
            error = state.validationErrors["cidr"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.detectedInterfaces.isNotEmpty()) {
            InterfaceSuggestionsPanel(
                interfaces = state.detectedInterfaces,
                isExpanded = state.showInterfaceSuggestions,
                onToggle = actions.onToggleSuggestions,
                onInterfaceSelected = actions.onInterfaceSelected,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        CreateSubnetVlanGatewayInputs(state = state, actions = actions)

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.description,
            onValueChange = actions.onDescriptionChanged,
            label = stringResource(Res.string.create_subnet_description_label),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapCheckboxRow(
            checked = state.discoveryEnabled,
            onCheckedChange = actions.onDiscoveryEnabledChanged,
            label = stringResource(Res.string.create_subnet_discovery_toggle),
        )
    }
}

@Composable
private fun InterfaceSuggestionsPanel(
    interfaces: List<NetworkInterface>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onInterfaceSelected: (NetworkInterface) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(Res.string.create_subnet_suggestions_toggle),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector =
                    if (isExpanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            ) {
                interfaces.forEach { iface ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    InterfaceSuggestionRow(
                        iface = iface,
                        onSelected = { onInterfaceSelected(iface) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InterfaceSuggestionRow(
    iface: NetworkInterface,
    onSelected: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelected)
                .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${iface.name} — ${iface.cidr}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "IP: ${iface.ip}  |  MAC: ${iface.mac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun CreateSubnetVlanGatewayInputs(
    state: CreateSubnetUiState,
    actions: CreateSubnetActions,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = state.vlanId,
            onValueChange = actions.onVlanIdChanged,
            label = stringResource(Res.string.create_subnet_vlan_label),
            error = state.validationErrors["vlan_id"]?.asString(),
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        InfraMapTextField(
            value = state.gatewayIp,
            onValueChange = actions.onGatewayIpChanged,
            label = stringResource(Res.string.create_subnet_gateway_label),
            error = state.validationErrors["gateway_ip"]?.asString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CreateSubnetFormActions(
    isSubmitting: Boolean,
    onCancelClicked: () -> Unit,
    onSubmitClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_cancel_button),
            onClick = onCancelClicked,
            enabled = !isSubmitting,
        )
        Spacer(modifier = Modifier.width(12.dp))
        InfraMapButton(
            text =
                if (isSubmitting) {
                    stringResource(Res.string.create_subnet_submitting)
                } else {
                    stringResource(Res.string.create_subnet_submit)
                },
            onClick = onSubmitClicked,
            enabled = !isSubmitting,
        )
    }
}

@Composable
private fun CreateSubnetHeader() {
    Column {
        Text(
            text = stringResource(Res.string.create_subnet_header_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.create_subnet_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}
