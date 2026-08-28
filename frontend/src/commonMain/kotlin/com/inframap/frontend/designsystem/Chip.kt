package com.inframap.frontend.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3InteractiveScale

private val ChipIconSize = 18.dp
private val ChipSpacing = 8.dp
private val CustomInputTopPadding = 12.dp
private val HelperTextTopPadding = 4.dp
private val HelperTextStartPadding = 16.dp

data class ChipOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val description: String? = null,
)

@Suppress("LongParameterList")
data class ChipCustomOption<T>(
    val chipLabel: String = "Personalizado",
    val chipIcon: ImageVector? = null,
    val inputLabel: String,
    val inputPlaceholder: String = "",
    val currentValue: String,
    val onValueChanged: (String) -> Unit,
    val parseValue: (String) -> T?,
    val formatValue: (T) -> String,
    val helperText: String? = null,
    val isCustom: (T) -> Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> InfraMapChoiceChipGroup(
    options: List<ChipOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    customOption: ChipCustomOption<T>? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = ChipSpacing),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            verticalArrangement = Arrangement.spacedBy(ChipSpacing),
        ) {
            options.forEach { option ->
                InfraMapChoiceChipItem(
                    label = option.label,
                    description = option.description,
                    icon = option.icon,
                    isSelected = option.value == selected,
                    enabled = enabled,
                    onClick = { onSelected(option.value) },
                )
            }
            if (customOption != null) {
                InfraMapChoiceChipItem(
                    label = customOption.chipLabel,
                    description = null,
                    icon = customOption.chipIcon,
                    isSelected = customOption.isCustom(selected),
                    enabled = enabled,
                    onClick = {
                        val parsed = customOption.parseValue(customOption.currentValue)
                        if (parsed != null) {
                            onSelected(parsed)
                        }
                    },
                )
            }
        }
        if (customOption != null) {
            AnimatedVisibility(
                visible = customOption.isCustom(selected),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                CustomInputSection(
                    customOption = customOption,
                    onSelected = onSelected,
                    enabled = enabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> InfraMapFilterChipGroup(
    options: List<ChipOption<T>>,
    selected: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = ChipSpacing),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
            verticalArrangement = Arrangement.spacedBy(ChipSpacing),
        ) {
            options.forEach { option ->
                val isSelected = selected.contains(option.value)
                InfraMapChoiceChipItem(
                    label = option.label,
                    description = option.description,
                    icon = option.icon,
                    isSelected = isSelected,
                    enabled = enabled,
                    onClick = {
                        val newSelection =
                            if (isSelected) {
                                selected - option.value
                            } else {
                                selected + option.value
                            }
                        onSelectionChanged(newSelection)
                    },
                )
            }
        }
    }
}

@Composable
private fun InfraMapChoiceChipItem(
    label: String,
    description: String?,
    icon: ImageVector?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            if (description != null) {
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            } else {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        },
        modifier = Modifier.m3InteractiveScale(interactionSource),
        enabled = enabled,
        leadingIcon =
            icon?.let { imageVector ->
                {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        modifier = Modifier.size(ChipIconSize),
                    )
                }
            },
        interactionSource = interactionSource,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                labelColor = MaterialTheme.colorScheme.onSurface,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = isSelected,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                selectedBorderColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

@Composable
private fun <T> CustomInputSection(
    customOption: ChipCustomOption<T>,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = CustomInputTopPadding),
    ) {
        OutlinedTextField(
            value = customOption.currentValue,
            onValueChange = { newValue ->
                customOption.onValueChanged(newValue)
                val parsed = customOption.parseValue(newValue)
                if (parsed != null) {
                    onSelected(parsed)
                }
            },
            label = { Text(customOption.inputLabel) },
            placeholder =
                if (customOption.inputPlaceholder.isNotEmpty()) {
                    { Text(customOption.inputPlaceholder) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
        )
        if (customOption.helperText != null) {
            Text(
                text = customOption.helperText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier.padding(
                        start = HelperTextStartPadding,
                        top = HelperTextTopPadding,
                    ),
            )
        }
    }
}
