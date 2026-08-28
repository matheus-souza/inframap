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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.ChipCustomOption
import com.inframap.frontend.designsystem.ChipOption
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapChoiceChipGroup
import com.inframap.frontend.designsystem.InfraMapFormSkeleton
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_back_button
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.create_device_custom_type
import com.inframap.frontend.generated.resources.create_device_custom_type_label
import com.inframap.frontend.generated.resources.create_device_custom_type_placeholder
import com.inframap.frontend.generated.resources.create_device_hostname_label
import com.inframap.frontend.generated.resources.create_device_ip_label
import com.inframap.frontend.generated.resources.create_device_mac_label
import com.inframap.frontend.generated.resources.create_device_type_label
import com.inframap.frontend.generated.resources.devices_retry
import com.inframap.frontend.generated.resources.edit_device_form_title
import com.inframap.frontend.generated.resources.edit_device_header_subtitle
import com.inframap.frontend.generated.resources.edit_device_header_title
import com.inframap.frontend.generated.resources.edit_device_status_active
import com.inframap.frontend.generated.resources.edit_device_status_inactive
import com.inframap.frontend.generated.resources.edit_device_status_label
import com.inframap.frontend.generated.resources.edit_device_status_maintenance
import com.inframap.frontend.generated.resources.edit_device_submit_button
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditDeviceScreen(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        if (state.isLoading) {
            InfraMapFormSkeleton(fields = 5)
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                EditDeviceHeader(
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
private fun EditDeviceHeader(onCancelClicked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_back_button),
            onClick = onCancelClicked,
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.edit_device_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.edit_device_header_subtitle),
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
                text = stringResource(Res.string.devices_retry),
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
                text = stringResource(Res.string.edit_device_form_title),
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
        EditDeviceNetworkFields(state = state, actions = actions)
        Spacer(modifier = Modifier.height(16.dp))
        EditDeviceTypeField(state = state, actions = actions)
        Spacer(modifier = Modifier.height(16.dp))
        EditDeviceStatusField(state = state, actions = actions)
    }
}

@Composable
private fun EditDeviceNetworkFields(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
) {
    InfraMapTextField(
        value = state.hostname,
        onValueChange = actions.onHostnameChanged,
        label = stringResource(Res.string.create_device_hostname_label),
        error = state.validationErrors["hostname"]?.asString(),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(16.dp))

    InfraMapTextField(
        value = state.ipAddress,
        onValueChange = actions.onIpAddressChanged,
        label = stringResource(Res.string.create_device_ip_label),
        error = state.validationErrors["ip_address"]?.asString(),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(16.dp))

    InfraMapTextField(
        value = state.macAddress,
        onValueChange = actions.onMacAddressChanged,
        label = stringResource(Res.string.create_device_mac_label),
        error = state.validationErrors["mac_address"]?.asString(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditDeviceTypeField(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
) {
    val deviceTypeOptions =
        listOf(
            ChipOption("router", "Router", Icons.Default.Router),
            ChipOption("switch", "Switch", InfraMapIcons.Lan),
            ChipOption("server", "Server", InfraMapIcons.Dns),
            ChipOption("firewall", "Firewall", Icons.Default.Security),
        )

    InfraMapChoiceChipGroup(
        options = deviceTypeOptions,
        selected = state.deviceType,
        onSelected = actions.onDeviceTypeChanged,
        label = stringResource(Res.string.create_device_type_label),
        customOption =
            ChipCustomOption(
                chipLabel = stringResource(Res.string.create_device_custom_type),
                chipIcon = Icons.Default.MoreHoriz,
                inputLabel = stringResource(Res.string.create_device_custom_type_label),
                inputPlaceholder = stringResource(Res.string.create_device_custom_type_placeholder),
                currentValue = state.deviceType,
                onValueChanged = actions.onDeviceTypeChanged,
                parseValue = { it },
                formatValue = { it },
                isCustom = { it !in listOf("router", "switch", "server", "firewall", "") },
            ),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.validationErrors["device_type"] != null) {
        Text(
            text = state.validationErrors["device_type"]!!.asString(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
}

@Composable
private fun EditDeviceStatusField(
    state: EditDeviceUiState,
    actions: EditDeviceActions,
) {
    val statusOptions =
        listOf(
            ChipOption("active", stringResource(Res.string.edit_device_status_active), Icons.Default.CheckCircle),
            ChipOption("inactive", stringResource(Res.string.edit_device_status_inactive), Icons.Default.Error),
            ChipOption("maintenance", stringResource(Res.string.edit_device_status_maintenance), Icons.Default.Build),
        )

    InfraMapChoiceChipGroup(
        options = statusOptions,
        selected = state.status,
        onSelected = actions.onStatusChanged,
        label = stringResource(Res.string.edit_device_status_label),
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.validationErrors["status"] != null) {
        Text(
            text = state.validationErrors["status"]!!.asString(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
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
            text = stringResource(Res.string.create_device_cancel_button),
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
                text = stringResource(Res.string.edit_device_submit_button),
                onClick = actions.onSubmitClicked,
            )
        }
    }
}
