package com.inframap.frontend.ui.subnets

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField

@Composable
fun CreateSubnetScreen(
    state: CreateSubnetUiState,
    actions: CreateSubnetActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CreateSubnetHeader()

            Spacer(modifier = Modifier.height(20.dp))

            InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage,
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
            label = "Nome da Subrede *",
            error = state.validationErrors["name"],
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.cidr,
            onValueChange = actions.onCidrChanged,
            label = "CIDR (ex: 192.168.1.0/24) *",
            error = state.validationErrors["cidr"],
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        CreateSubnetVlanGatewayInputs(state = state, actions = actions)

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.description,
            onValueChange = actions.onDescriptionChanged,
            label = "Descrição (Opcional)",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        CreateSubnetDiscoveryToggle(
            discoveryEnabled = state.discoveryEnabled,
            onDiscoveryEnabledChanged = actions.onDiscoveryEnabledChanged,
        )
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
            label = "VLAN ID (Opcional)",
            error = state.validationErrors["vlan_id"],
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        InfraMapTextField(
            value = state.gatewayIp,
            onValueChange = actions.onGatewayIpChanged,
            label = "Gateway IP (Opcional)",
            error = state.validationErrors["gateway_ip"],
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CreateSubnetDiscoveryToggle(
    discoveryEnabled: Boolean,
    onDiscoveryEnabledChanged: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Checkbox(
            checked = discoveryEnabled,
            onCheckedChange = onDiscoveryEnabledChanged,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Habilitar Varredura Automática de Descoberta",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
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
            text = "Cancelar",
            onClick = onCancelClicked,
            enabled = !isSubmitting,
        )
        Spacer(modifier = Modifier.width(12.dp))
        InfraMapButton(
            text = if (isSubmitting) "Cadastrando..." else "Cadastrar Subrede",
            onClick = onSubmitClicked,
            enabled = !isSubmitting,
        )
    }
}

@Composable
private fun CreateSubnetHeader() {
    Column {
        Text(
            text = "Nova Subrede",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Cadastre uma nova faixa de subrede para segmentação e varredura de ativos",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}
