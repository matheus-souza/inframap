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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun EditDeviceScreen(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                EditDeviceHeader(
                    deviceId = state.deviceId,
                    onCancelClicked = actions.onCancelClicked,
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (state.errorMessage != null) {
                    EditDeviceErrorCard(
                        errorMessage = state.errorMessage.asString(),
                        onRetryClicked = actions.onRetryClicked,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                EditDeviceFormCard(state = state, actions = actions)
            }
        }
    }
}

@Composable
private fun EditDeviceHeader(
    deviceId: String,
    onCancelClicked: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfraMapOutlinedButton(
            text = "Voltar",
            onClick = onCancelClicked,
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Editar Dispositivo",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Atualize as informações do ativo ID: $deviceId",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun EditDeviceErrorCard(
    errorMessage: String,
    onRetryClicked: () -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            InfraMapOutlinedButton(
                text = "Tentar Novamente",
                onClick = onRetryClicked,
            )
        }
    }
}

@Composable
private fun EditDeviceFormCard(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = "Informações do Ativo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(20.dp))

            EditDeviceFormFields(state = state, actions = actions)

            Spacer(modifier = Modifier.height(28.dp))

            EditDeviceFormButtons(state = state, actions = actions)
        }
    }
}

@Composable
private fun EditDeviceFormFields(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = state.hostname,
            onValueChange = actions.onHostnameChanged,
            label = "Hostname *",
            error = state.validationErrors["hostname"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.ipAddress,
            onValueChange = actions.onIpAddressChanged,
            label = "Endereço IP",
            error = state.validationErrors["ip_address"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.macAddress,
            onValueChange = actions.onMacAddressChanged,
            label = "Endereço MAC",
            error = state.validationErrors["mac_address"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.deviceType,
            onValueChange = actions.onDeviceTypeChanged,
            label = "Tipo de Dispositivo",
            error = state.validationErrors["device_type"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.status,
            onValueChange = actions.onStatusChanged,
            label = "Status (active, inactive, maintenance)",
            error = state.validationErrors["status"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditDeviceFormButtons(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
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
                text = "Salvar Alterações",
                onClick = actions.onSubmitClicked,
            )
        }
    }
}
