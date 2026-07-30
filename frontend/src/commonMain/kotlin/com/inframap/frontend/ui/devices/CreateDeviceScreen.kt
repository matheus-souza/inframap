package com.inframap.frontend.ui.devices

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
fun CreateDeviceScreen(
    state: CreateDeviceUiState,
    actions: CreateDeviceActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            CreateDeviceHeader(onCancelClicked = actions.onCancelClicked)

            Spacer(modifier = Modifier.height(24.dp))

            if (state.errorMessage != null) {
                InfraMapCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = state.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            CreateDeviceFormCard(state = state, actions = actions)
        }
    }
}

@Composable
private fun CreateDeviceHeader(onCancelClicked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfraMapOutlinedButton(text = "← Voltar", onClick = onCancelClicked)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Novo Dispositivo",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Cadastre um novo ativo no inventário da rede",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CreateDeviceFormCard(
    state: CreateDeviceUiState,
    actions: CreateDeviceActions,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = "Dados do Dispositivo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))

            CreateDeviceFormFields(state = state, actions = actions)

            Spacer(modifier = Modifier.height(28.dp))

            CreateDeviceFormButtons(state = state, actions = actions)
        }
    }
}

@Composable
private fun CreateDeviceFormFields(
    state: CreateDeviceUiState,
    actions: CreateDeviceActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = state.hostname,
            onValueChange = actions.onHostnameChanged,
            label = "Hostname *",
            error = state.validationErrors["hostname"],
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.ipAddress,
            onValueChange = actions.onIpAddressChanged,
            label = "Endereço IP (ex: 192.168.1.1)",
            error = state.validationErrors["ip_address"],
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.macAddress,
            onValueChange = actions.onMacAddressChanged,
            label = "Endereço MAC (ex: 00:11:22:33:44:55)",
            error = state.validationErrors["mac_address"],
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.deviceType,
            onValueChange = actions.onDeviceTypeChanged,
            label = "Tipo de Dispositivo (router, switch, server, firewall...)",
            error = state.validationErrors["device_type"],
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CreateDeviceFormButtons(
    state: CreateDeviceUiState,
    actions: CreateDeviceActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfraMapOutlinedButton(
            text = "Cancelar",
            onClick = actions.onCancelClicked,
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (state.isSubmitting) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(36.dp),
            )
        } else {
            InfraMapButton(
                text = "Salvar Dispositivo",
                onClick = actions.onSubmitClicked,
            )
        }
    }
}
