package com.inframap.frontend.ui.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapOutlinedButton
import com.inframap.frontend.designsystem.InfraMapTextField
import com.inframap.frontend.designsystem.SuggestionCard
import com.inframap.frontend.designsystem.motion.m3ClickableCursor
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_discovery_source_cidr_label
import com.inframap.frontend.generated.resources.discovery_target_remove
import com.inframap.frontend.generated.resources.discovery_target_subnet_deleted
import com.inframap.frontend.generated.resources.discovery_targets_add_cidr
import com.inframap.frontend.generated.resources.discovery_targets_add_subnet
import com.inframap.frontend.generated.resources.discovery_targets_host_count
import com.inframap.frontend.generated.resources.discovery_targets_label
import com.inframap.frontend.generated.resources.subnet_chips_empty
import org.jetbrains.compose.resources.stringResource

private val TargetSpacing = 8.dp
private val TargetCornerRadius = 8.dp

/**
 * The ordered target list of a discovery source (RFC-026 §8): registered Subnets picked as
 * chips, free-form CIDRs typed and added, and the summed address count against the limit.
 * Shared by the create and edit screens, which hide it for a providers-only plan.
 */
@Composable
fun DiscoveryTargetsEditor(
    state: DiscoveryTargetsEditorState,
    actions: DiscoveryTargetsEditorActions,
    required: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("discovery_targets_editor")) {
        TargetsLabel(required = required)

        if (state.targets.isNotEmpty()) {
            TargetList(targets = state.targets, onRemove = actions.onRemoveTarget)
            Spacer(modifier = Modifier.height(TargetSpacing))
            Text(
                text =
                    stringResource(
                        Res.string.discovery_targets_host_count,
                        state.addressCount.toInt(),
                        DiscoveryTargetRules.MAX_ADDRESSES.toInt(),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (state.exceedsAddressLimit) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.testTag("discovery_targets_host_count"),
            )
        }

        state.listError?.let { error ->
            Text(
                text = error.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp).testTag("discovery_targets_error"),
            )
        }

        Spacer(modifier = Modifier.height(TargetSpacing))

        SubnetPicker(
            subnets = state.subnets,
            isLoading = state.isLoadingSubnets,
            linkedSubnetIds = state.linkedSubnetIds,
            onSubnetToggled = actions.onSubnetToggled,
        )

        Spacer(modifier = Modifier.height(TargetSpacing))

        CidrInputRow(
            input = state.input,
            error = state.inputError?.asString(),
            onInputChanged = actions.onInputChanged,
            onAddClicked = actions.onAddCidrClicked,
        )
    }
}

@Composable
private fun TargetsLabel(required: Boolean) {
    val label = stringResource(Res.string.discovery_targets_label)
    Text(
        text =
            buildAnnotatedString {
                append(label)
                if (required) {
                    append(" ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") }
                }
            },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = TargetSpacing),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetList(
    targets: List<DiscoveryTarget>,
    onRemove: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TargetSpacing),
        verticalArrangement = Arrangement.spacedBy(TargetSpacing),
    ) {
        targets.forEachIndexed { index, target ->
            TargetChip(index = index, target = target, onRemove = { onRemove(index) })
        }
    }
}

/** One target, removable; a target whose Subnet was deleted is flagged for removal (RFC-026 §6). */
@Composable
private fun TargetChip(
    index: Int,
    target: DiscoveryTarget,
    onRemove: () -> Unit,
) {
    val flagged = target.subnetDeleted
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border =
            BorderStroke(
                1.dp,
                if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
            ),
        shape = RoundedCornerShape(TargetCornerRadius),
        modifier = Modifier.testTag("discovery_target_$index"),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = target.cidr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                target.subnetName?.takeIf { it.isNotBlank() }?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (flagged) {
                    Text(
                        text = stringResource(Res.string.discovery_target_subnet_deleted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("discovery_target_subnet_deleted_$index"),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp).m3ClickableCursor().testTag("discovery_target_remove_$index"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.discovery_target_remove, target.cidr),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Registered Subnets as multi-select chips: a selected chip is a linked target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubnetPicker(
    subnets: List<SubnetSummary>,
    isLoading: Boolean,
    linkedSubnetIds: Set<String>,
    onSubnetToggled: (SubnetSummary) -> Unit,
) {
    Text(
        text = stringResource(Res.string.discovery_targets_add_subnet),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = TargetSpacing),
    )
    when {
        isLoading -> Unit
        subnets.isEmpty() ->
            Text(
                text = stringResource(Res.string.subnet_chips_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TargetSpacing),
                verticalArrangement = Arrangement.spacedBy(TargetSpacing),
            ) {
                subnets.forEach { subnet ->
                    SuggestionCard(
                        title = subnet.name,
                        detail = subnet.cidr,
                        isSelected = subnet.id in linkedSubnetIds,
                        onClick = { onSubnetToggled(subnet) },
                        modifier = Modifier.testTag("discovery_target_subnet_${subnet.id}"),
                    )
                }
            }
    }
}

@Composable
private fun CidrInputRow(
    input: String,
    error: String?,
    onInputChanged: (String) -> Unit,
    onAddClicked: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        InfraMapTextField(
            value = input,
            onValueChange = onInputChanged,
            label = stringResource(Res.string.create_discovery_source_cidr_label),
            error = error,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onAddClicked() }),
            modifier = Modifier.weight(1f).testTag("discovery_target_cidr_input"),
        )
        Spacer(modifier = Modifier.width(TargetSpacing))
        InfraMapOutlinedButton(
            text = stringResource(Res.string.discovery_targets_add_cidr),
            onClick = onAddClicked,
            enabled = input.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp).testTag("discovery_targets_add_cidr_button"),
        )
    }
}
