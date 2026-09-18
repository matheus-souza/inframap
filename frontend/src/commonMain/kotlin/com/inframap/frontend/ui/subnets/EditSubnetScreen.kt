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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.DangerZone
import com.inframap.frontend.designsystem.ImpactConfirmationDialog
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapCheckboxRow
import com.inframap.frontend.designsystem.InfraMapFormSkeleton
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_back_button
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.create_subnet_cidr_label
import com.inframap.frontend.generated.resources.create_subnet_description_label
import com.inframap.frontend.generated.resources.create_subnet_discovery_toggle
import com.inframap.frontend.generated.resources.create_subnet_gateway_label
import com.inframap.frontend.generated.resources.create_subnet_name_label
import com.inframap.frontend.generated.resources.create_subnet_vlan_label
import com.inframap.frontend.generated.resources.danger_zone_delete_button
import com.inframap.frontend.generated.resources.danger_zone_subnet_description
import com.inframap.frontend.generated.resources.danger_zone_title
import com.inframap.frontend.generated.resources.delete_subnet_confirm_message
import com.inframap.frontend.generated.resources.delete_subnet_dialog_title
import com.inframap.frontend.generated.resources.delete_subnet_impact_devices
import com.inframap.frontend.generated.resources.delete_subnet_impact_edges
import com.inframap.frontend.generated.resources.devices_retry
import com.inframap.frontend.generated.resources.edit_subnet_form_title
import com.inframap.frontend.generated.resources.edit_subnet_header_subtitle
import com.inframap.frontend.generated.resources.edit_subnet_header_title
import com.inframap.frontend.generated.resources.edit_subnet_impact_title
import com.inframap.frontend.generated.resources.edit_subnet_impact_warning
import com.inframap.frontend.generated.resources.edit_subnet_submit_button
import com.inframap.frontend.generated.resources.legend_required_fields
import com.inframap.frontend.generated.resources.subnets_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditSubnetScreen(
    state: EditSubnetUiState,
    actions: EditSubnetActions,
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
                EditSubnetHeader(onCancelClicked = actions.onCancelClicked)

                Spacer(modifier = Modifier.height(20.dp))

                if (state.errorMessage != null) {
                    EditSubnetErrorCard(
                        errorMessage = state.errorMessage.asString(),
                        onRetryClicked = actions.onRetryClicked,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                EditSubnetFormCard(state = state, actions = actions)

                Spacer(modifier = Modifier.height(24.dp))

                DangerZone(
                    title = stringResource(Res.string.danger_zone_title),
                    description = stringResource(Res.string.danger_zone_subnet_description),
                    deleteLabel =
                        stringResource(
                            Res.string.danger_zone_delete_button,
                            state.name.ifEmpty { stringResource(Res.string.subnets_title) },
                        ),
                    onDelete = actions.onDeleteClicked,
                    enabled = !state.isSubmitting && !state.isDeleting,
                )
            }
        }

        EditSubnetOverlays(state = state, actions = actions)
    }
}

@Composable
private fun EditSubnetOverlays(
    state: EditSubnetUiState,
    actions: EditSubnetActions,
) {
    if (state.showImpactDialog) {
        ImpactConfirmationDialog(
            title = stringResource(Res.string.edit_subnet_impact_title),
            description = stringResource(Res.string.edit_subnet_impact_warning, state.impactAffectedCount),
            onConfirm = actions.onConfirmImpactClicked,
            onDismiss = actions.onDismissImpactClicked,
        )
    }

    if (state.showDeleteDialog) {
        val impactLines = mutableListOf<String>()
        val impact = state.deleteImpact
        if (impact != null) {
            impactLines.add(stringResource(Res.string.delete_subnet_impact_devices, impact.affectedDevices))
            impactLines.add(stringResource(Res.string.delete_subnet_impact_edges, impact.unlinkedTopologyEdges))
        }
        ImpactConfirmationDialog(
            title = stringResource(Res.string.delete_subnet_dialog_title),
            description = stringResource(Res.string.delete_subnet_confirm_message, state.name),
            impactLines = impactLines,
            isLoadingImpact = state.isLoadingDeleteImpact,
            onConfirm = actions.onConfirmDeleteClicked,
            onDismiss = actions.onDismissDeleteClicked,
        )
    }
}

@Composable
private fun EditSubnetHeader(onCancelClicked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_back_button),
            onClick = onCancelClicked,
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(Res.string.edit_subnet_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.edit_subnet_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun EditSubnetErrorCard(
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
private fun EditSubnetFormCard(
    state: EditSubnetUiState,
    actions: EditSubnetActions,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = stringResource(Res.string.edit_subnet_form_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.legend_required_fields),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp).testTag("legend_required_fields"),
            )
            Spacer(modifier = Modifier.height(8.dp))

            EditSubnetFormFields(state = state, actions = actions)

            Spacer(modifier = Modifier.height(28.dp))

            EditSubnetFormButtons(
                isSubmitting = state.isSubmitting,
                onCancelClicked = actions.onCancelClicked,
                onSubmitClicked = actions.onSubmitClicked,
            )
        }
    }
}

@Composable
private fun EditSubnetFormFields(
    state: EditSubnetUiState,
    actions: EditSubnetActions,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = state.name,
            onValueChange = actions.onNameChanged,
            label = stringResource(Res.string.create_subnet_name_label),
            required = true,
            error = state.validationErrors["name"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        InfraMapTextField(
            value = state.cidr,
            onValueChange = actions.onCidrChanged,
            label = stringResource(Res.string.create_subnet_cidr_label),
            required = true,
            error = state.validationErrors["cidr"]?.asString(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        EditSubnetVlanGatewayInputs(state = state, actions = actions)

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
private fun EditSubnetVlanGatewayInputs(
    state: EditSubnetUiState,
    actions: EditSubnetActions,
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
private fun EditSubnetFormButtons(
    isSubmitting: Boolean,
    onCancelClicked: () -> Unit,
    onSubmitClicked: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_cancel_button),
            onClick = onCancelClicked,
            enabled = !isSubmitting,
        )
        Spacer(modifier = Modifier.width(12.dp))
        if (isSubmitting) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(36.dp),
            )
        } else {
            InfraMapButton(
                text = stringResource(Res.string.edit_subnet_submit_button),
                onClick = onSubmitClicked,
            )
        }
    }
}
