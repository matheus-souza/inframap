package com.inframap.frontend.ui.discovery

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.DangerZone
import com.inframap.frontend.designsystem.ImpactConfirmationDialog
import com.inframap.frontend.designsystem.InfraMapButton
import com.inframap.frontend.designsystem.InfraMapCard
import com.inframap.frontend.designsystem.InfraMapCheckboxRow
import com.inframap.frontend.designsystem.InfraMapFormSkeleton
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.designsystem.motion.m3ClickableCursor
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_back_button
import com.inframap.frontend.generated.resources.create_device_cancel_button
import com.inframap.frontend.generated.resources.create_discovery_source_enabled_label
import com.inframap.frontend.generated.resources.danger_zone_delete_button
import com.inframap.frontend.generated.resources.danger_zone_discovery_source_description
import com.inframap.frontend.generated.resources.danger_zone_title
import com.inframap.frontend.generated.resources.delete_discovery_source_confirm_message
import com.inframap.frontend.generated.resources.delete_discovery_source_dialog_title
import com.inframap.frontend.generated.resources.delete_discovery_source_impact_collectors
import com.inframap.frontend.generated.resources.delete_discovery_source_impact_devices
import com.inframap.frontend.generated.resources.devices_retry
import com.inframap.frontend.generated.resources.discovery_title
import com.inframap.frontend.generated.resources.edit_discovery_source_form_title
import com.inframap.frontend.generated.resources.edit_discovery_source_header_subtitle
import com.inframap.frontend.generated.resources.edit_discovery_source_header_title
import com.inframap.frontend.generated.resources.edit_discovery_source_name_label
import com.inframap.frontend.generated.resources.edit_discovery_source_schedule_label
import com.inframap.frontend.generated.resources.edit_discovery_source_secret_hint
import com.inframap.frontend.generated.resources.edit_discovery_source_submit_button
import com.inframap.frontend.generated.resources.edit_discovery_source_submitting
import com.inframap.frontend.generated.resources.edit_discovery_source_target_changed_warning
import com.inframap.frontend.generated.resources.edit_discovery_source_test_unsaved_warning
import com.inframap.frontend.generated.resources.legend_required_fields
import com.inframap.frontend.generated.resources.provider_section_config
import com.inframap.frontend.generated.resources.provider_test_connection
import com.inframap.frontend.generated.resources.provider_test_connection_ok
import com.inframap.frontend.generated.resources.provider_test_connection_testing
import org.jetbrains.compose.resources.stringResource

@Composable
fun EditDiscoverySourceScreen(
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp)) {
        if (state.isLoading) {
            InfraMapFormSkeleton(fields = 4)
        } else {
            EditDiscoverySourceContent(state = state, actions = actions)
        }

        EditDiscoverySourceOverlays(state = state, actions = actions)
    }
}

@Composable
private fun EditDiscoverySourceContent(
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        EditDiscoverySourceHeader(onBackClicked = actions.onBackClicked)

        Spacer(modifier = Modifier.height(20.dp))

        if (state.errorMessage != null) {
            EditDiscoverySourceErrorCard(
                errorMessage = state.errorMessage.asString(),
                onDismiss = actions.onDismissError,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        EditDiscoverySourceFormCard(state = state, actions = actions)

        Spacer(modifier = Modifier.height(24.dp))

        DangerZone(
            title = stringResource(Res.string.danger_zone_title),
            description = stringResource(Res.string.danger_zone_discovery_source_description),
            deleteLabel =
                stringResource(
                    Res.string.danger_zone_delete_button,
                    state.name.ifEmpty { stringResource(Res.string.discovery_title) },
                ),
            onDelete = actions.onDeleteClicked,
            enabled = !state.isSubmitting && !state.isDeleting,
        )
    }
}

@Composable
private fun EditDiscoverySourceHeader(onBackClicked: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(Res.string.edit_discovery_source_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.edit_discovery_source_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }

        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_back_button),
            onClick = onBackClicked,
            leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
    }
}

@Composable
private fun EditDiscoverySourceErrorCard(
    errorMessage: String,
    onDismiss: () -> Unit,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfraMapOutlinedButton(
                text = stringResource(Res.string.devices_retry),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun EditDiscoverySourceFormCard(
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
) {
    InfraMapCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                text = stringResource(Res.string.edit_discovery_source_form_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.legend_required_fields),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            InfraMapTextField(
                value = state.name,
                onValueChange = actions.onNameChanged,
                label = stringResource(Res.string.edit_discovery_source_name_label),
                required = true,
                error = state.validationErrors["name"]?.asString(),
                modifier = Modifier.fillMaxWidth().testTag("edit_source_name_input"),
            )
            Spacer(modifier = Modifier.height(16.dp))

            InfraMapTextField(
                value = state.scheduleCron,
                onValueChange = actions.onScheduleCronChanged,
                label = stringResource(Res.string.edit_discovery_source_schedule_label),
                modifier = Modifier.fillMaxWidth().testTag("edit_source_schedule_input"),
            )
            Spacer(modifier = Modifier.height(16.dp))

            InfraMapCheckboxRow(
                checked = state.enabled,
                onCheckedChange = actions.onEnabledChanged,
                label = stringResource(Res.string.create_discovery_source_enabled_label),
                modifier = Modifier.fillMaxWidth().testTag("edit_source_enabled_checkbox"),
            )
            Spacer(modifier = Modifier.height(16.dp))

            EditProvidersSection(state = state, actions = actions)

            Spacer(modifier = Modifier.height(24.dp))

            EditDiscoverySourceFormButtons(state = state, actions = actions)
        }
    }
}

@Composable
private fun EditProvidersSection(
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
) {
    if (state.selectedProviders.isEmpty()) return

    if (state.showsProviderTabs) {
        EditProviderTabs(
            selectedProviders = state.selectedProviders,
            active = state.currentProviderTab,
            warnings = state.targetChangedWarnings,
            onSelected = actions.onProviderTabSelected,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    val currentTab = state.currentProviderTab
    if (currentTab != null) {
        EditProviderConfigSection(
            providerId = currentTab,
            state = state,
            actions = actions,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EditProviderTabs(
    selectedProviders: List<String>,
    active: String?,
    warnings: Map<String, Boolean>,
    onSelected: (String) -> Unit,
) {
    val forms = selectedProviders.mapNotNull { ProviderForms.formFor(it) }
    val activeIndex = forms.indexOfFirst { it.id == active }.coerceAtLeast(0)

    SecondaryTabRow(selectedTabIndex = activeIndex) {
        forms.forEach { form ->
            val hasWarning = warnings[form.id] == true
            LeadingIconTab(
                selected = form.id == active,
                onClick = { onSelected(form.id) },
                text = { Text(stringResource(form.label)) },
                icon = {
                    if (hasWarning) {
                        BadgedBox(
                            badge = {
                                Badge(modifier = Modifier.testTag("edit_provider_tab_badge_${form.id}"))
                            },
                        ) {
                            Icon(form.icon, contentDescription = null)
                        }
                    } else {
                        Icon(form.icon, contentDescription = null)
                    }
                },
                modifier = Modifier.m3ClickableCursor().testTag("edit_provider_tab_${form.id}"),
            )
        }
    }
}

@Composable
private fun EditProviderConfigSection(
    providerId: String,
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
) {
    val form = ProviderForms.formFor(providerId) ?: return
    val config = state.providerConfigs[providerId].orEmpty()
    val configuredSecrets = state.configuredSecrets[providerId].orEmpty()
    val targetChangedWarning = state.targetChangedWarnings[providerId] == true
    val hasPendingChanges = state.hasPendingTargetOrSecretChanges(providerId)
    val connectionTest = state.connectionTests[providerId]
    val isSubmitting = state.isSubmitting || state.isDeleting

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.provider_section_config),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        form.fields.forEach { field ->
            val isConfigured = field.secret && field.key in configuredSecrets
            EditProviderFieldInput(
                providerId = providerId,
                field = field,
                value = config[field.key],
                isSecretConfigured = isConfigured,
                targetChangedWarning = targetChangedWarning,
                onValueChange = { actions.onProviderFieldChanged(providerId, field.key, it) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        InfraMapOutlinedButton(
            text = stringResource(Res.string.provider_test_connection),
            onClick = { actions.onTestConnectionClicked(providerId) },
            enabled = !hasPendingChanges && connectionTest !is ConnectionTest.Testing && !isSubmitting,
            modifier = Modifier.testTag("test_connection_$providerId"),
        )

        if (hasPendingChanges) {
            Text(
                text = stringResource(Res.string.edit_discovery_source_test_unsaved_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).testTag("test_connection_unsaved_hint_$providerId"),
            )
        }

        ConnectionTestFeedback(connectionTest)
    }
}

@Composable
private fun ConnectionTestFeedback(connectionTest: ConnectionTest?) {
    if (connectionTest == null) return

    val (text, color) =
        when (connectionTest) {
            is ConnectionTest.Testing ->
                stringResource(Res.string.provider_test_connection_testing) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            is ConnectionTest.Healthy ->
                stringResource(Res.string.provider_test_connection_ok) to
                    MaterialTheme.colorScheme.primary
            is ConnectionTest.Failed ->
                connectionTest.message.asString() to MaterialTheme.colorScheme.error
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(top = 8.dp).testTag("connection_test_feedback"),
    )
}

@Composable
private fun EditProviderFieldInput(
    providerId: String,
    field: ProviderField,
    value: String?,
    isSecretConfigured: Boolean,
    targetChangedWarning: Boolean,
    onValueChange: (String) -> Unit,
) {
    val tag = "edit_provider_field_${providerId}_${field.key}"
    if (field.boolean) {
        InfraMapCheckboxRow(
            checked = (value ?: field.default).toBoolean(),
            onCheckedChange = { onValueChange(it.toString()) },
            label = stringResource(field.label),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        return
    }

    val helperText =
        if (field.secret && isSecretConfigured && value.isNullOrBlank()) {
            stringResource(Res.string.edit_discovery_source_secret_hint)
        } else {
            null
        }

    val fieldError =
        if (field.secret && targetChangedWarning && value.isNullOrBlank()) {
            stringResource(Res.string.edit_discovery_source_target_changed_warning)
        } else {
            null
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        InfraMapTextField(
            value = value.orEmpty(),
            onValueChange = onValueChange,
            label = stringResource(field.label),
            required = field.required && (!field.secret || !isSecretConfigured),
            error = fieldError,
            visualTransformation =
                if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
        if (helperText != null && fieldError == null) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun EditDiscoverySourceFormButtons(
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfraMapOutlinedButton(
            text = stringResource(Res.string.create_device_cancel_button),
            onClick = actions.onBackClicked,
            enabled = !state.isSubmitting && !state.isDeleting,
        )
        Spacer(modifier = Modifier.width(12.dp))
        InfraMapButton(
            text =
                if (state.isSubmitting) {
                    stringResource(Res.string.edit_discovery_source_submitting)
                } else {
                    stringResource(Res.string.edit_discovery_source_submit_button)
                },
            onClick = actions.onSubmitClicked,
            enabled = !state.isSubmitting && !state.isDeleting,
            modifier = Modifier.testTag("edit_source_submit_button"),
        )
    }
}

@Composable
private fun EditDiscoverySourceOverlays(
    state: EditDiscoverySourceUiState,
    actions: EditDiscoverySourceActions,
) {
    if (state.showDeleteDialog) {
        val impactLines = mutableListOf<String>()
        val impact = state.deleteImpact
        if (impact != null) {
            impactLines.add(
                stringResource(Res.string.delete_discovery_source_impact_collectors, impact.collectorsHalted),
            )
            impactLines.add(
                stringResource(Res.string.delete_discovery_source_impact_devices, impact.devicesUnlinked),
            )
        }
        ImpactConfirmationDialog(
            title = stringResource(Res.string.delete_discovery_source_dialog_title),
            description = stringResource(Res.string.delete_discovery_source_confirm_message, state.name),
            impactLines = impactLines,
            isLoadingImpact = state.isLoadingDeleteImpact,
            onConfirm = actions.onConfirmDeleteClicked,
            onDismiss = actions.onDismissDeleteDialog,
        )
    }
}
