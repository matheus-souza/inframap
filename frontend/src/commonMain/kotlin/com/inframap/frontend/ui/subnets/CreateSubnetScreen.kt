package com.inframap.frontend.ui.subnets

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.CollapsibleSection
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapCheckboxRow
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.designsystem.SuggestionCard
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
            Spacer(modifier = Modifier.height(16.dp))

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterfaceSuggestionsPanel(
    interfaces: List<NetworkInterface>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onInterfaceSelected: (NetworkInterface) -> Unit,
) {
    // Same shape as the subnet suggestion block on the discovery screen. The two sat one
    // screen apart offering the same thing — pick a value to fill the field — and looked
    // like they came from different products.
    CollapsibleSection(
        title = stringResource(Res.string.create_subnet_suggestions_toggle),
        icon = Icons.Filled.Sensors,
        expanded = isExpanded,
        onToggle = onToggle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            interfaces.forEach { iface ->
                SuggestionCard(
                    title = "${iface.name} — ${iface.cidr}",
                    detail = "${iface.ip} · ${iface.mac}",
                    onClick = { onInterfaceSelected(iface) },
                )
            }
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
