package com.inframap.frontend.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3Clickable
import kotlin.jvm.JvmName

private val ChipIconSize = 18.dp
private val TooltipCornerRadius = 8.dp
private val ChipSpacing = 8.dp
private val CustomInputTopPadding = 12.dp
private val HelperTextTopPadding = 4.dp
private val HelperTextStartPadding = 16.dp
private const val DISABLED_ALPHA = 0.38f
private const val DISABLED_BORDER_ALPHA = 0.12f

data class ChipOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val description: String? = null,
    val enabled: Boolean = true,
    val disabledHint: String? = null,
    val tooltip: String? = null,
)

data class ChipSection<T>(
    val title: String? = null,
    val options: List<ChipOption<T>>,
    val enabled: Boolean = true,
)

@Suppress("LongParameterList")
data class ChipCustomOption<T>(
    val chipLabel: String,
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

@JvmName("InfraMapChoiceChipGroupSections")
@Suppress("LongParameterList")
@Composable
fun <T> InfraMapChoiceChipGroup(
    sections: List<ChipSection<T>>,
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
        sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = ChipSpacing),
                )
            }
            if (section.title != null) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ChipSpacing),
                )
            }
            ChoiceSectionFlowRow(
                section = section,
                selected = selected,
                onSelected = onSelected,
                enabled = enabled,
                isLastSection = sectionIndex == sections.lastIndex,
                customOption = customOption,
            )
        }
        if (sections.isEmpty() && customOption != null) {
            CustomOptionFlowRow(
                customOption = customOption,
                selected = selected,
                onSelected = onSelected,
                enabled = enabled,
            )
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

@Suppress("LongParameterList")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceSectionFlowRow(
    section: ChipSection<T>,
    selected: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
    isLastSection: Boolean,
    customOption: ChipCustomOption<T>?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        section.options.forEach { option ->
            val isOptionEnabled = enabled && section.enabled && option.enabled
            InfraMapChoiceChipItem(
                label = option.label,
                description = option.description,
                icon = option.icon,
                isSelected = option.value == selected,
                enabled = isOptionEnabled,
                disabledHint = option.disabledHint,
                tooltip = option.tooltip,
                onClick = { onSelected(option.value) },
            )
        }
        if (customOption != null && isLastSection) {
            CustomChipItem(
                customOption = customOption,
                selected = selected,
                onSelected = onSelected,
                enabled = enabled,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> CustomOptionFlowRow(
    customOption: ChipCustomOption<T>,
    selected: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
        verticalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        CustomChipItem(
            customOption = customOption,
            selected = selected,
            onSelected = onSelected,
            enabled = enabled,
        )
    }
}

@Composable
private fun <T> CustomChipItem(
    customOption: ChipCustomOption<T>,
    selected: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    InfraMapChoiceChipItem(
        label = customOption.chipLabel,
        description = null,
        icon = customOption.chipIcon,
        isSelected = customOption.isCustom(selected),
        enabled = enabled,
        disabledHint = null,
        onClick = {
            val parsed = customOption.parseValue(customOption.currentValue)
            if (parsed != null) {
                onSelected(parsed)
            }
        },
    )
}

@Composable
fun <T> InfraMapChoiceChipGroup(
    options: List<ChipOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    customOption: ChipCustomOption<T>? = null,
    enabled: Boolean = true,
) = InfraMapChoiceChipGroup(
    sections = listOf(ChipSection(options = options)),
    selected = selected,
    onSelected = onSelected,
    modifier = modifier,
    label = label,
    customOption = customOption,
    enabled = enabled,
)

@JvmName("InfraMapFilterChipGroupSections")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> InfraMapFilterChipGroup(
    sections: List<ChipSection<T>>,
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
        sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = ChipSpacing),
                )
            }
            if (section.title != null) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = ChipSpacing),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
                verticalArrangement = Arrangement.spacedBy(ChipSpacing),
            ) {
                section.options.forEach { option ->
                    val isSelected = selected.contains(option.value)
                    val isOptionEnabled = enabled && section.enabled && option.enabled
                    InfraMapChoiceChipItem(
                        label = option.label,
                        description = option.description,
                        icon = option.icon,
                        isSelected = isSelected,
                        enabled = isOptionEnabled,
                        disabledHint = option.disabledHint,
                        tooltip = option.tooltip,
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
}

@Composable
fun <T> InfraMapFilterChipGroup(
    options: List<ChipOption<T>>,
    selected: Set<T>,
    onSelectionChanged: (Set<T>) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) = InfraMapFilterChipGroup(
    sections = listOf(ChipSection(options = options)),
    selected = selected,
    onSelectionChanged = onSelectionChanged,
    modifier = modifier,
    label = label,
    enabled = enabled,
)

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfraMapChoiceChipItem(
    label: String,
    description: String?,
    icon: ImageVector?,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    disabledHint: String? = null,
    tooltip: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
    val chipContent: @Composable () -> Unit = {
        FilterChip(
            selected = isSelected,
            onClick = onClick,
            label = {
                ChipItemContent(
                    label = label,
                    description = description,
                    enabled = enabled,
                    isSelected = isSelected,
                    disabledContentColor = disabledContentColor,
                )
            },
            modifier =
                Modifier
                    .m3Clickable(interactionSource)
                    .semantics {
                        if (disabledHint != null) {
                            stateDescription = disabledHint
                        }
                    },
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
            colors = chipItemColors(disabledContentColor),
            border = chipItemBorder(enabled = enabled, isSelected = isSelected),
        )
    }

    if (tooltip != null) {
        ChipTooltip(text = tooltip, anchor = chipContent)
    } else {
        chipContent()
    }
}

/**
 * The explanation that hangs off a chip on hover.
 *
 * Three Material defaults had to be overridden, each visible on screen. `PlainTooltip`'s
 * default colours are `inverseSurface`/`inverseOnSurface`, which in a dark theme is a light
 * box belonging to no other surface in the app. `caretSize` gives it the pointer that ties
 * it to the chip it describes. And `rememberTooltipState` defaults to `isPersistent = false`,
 * which hides the tooltip after roughly a second and a half even while the pointer is still
 * resting on the chip — the reader was being timed out mid-sentence.
 *
 * Persistent removes only that timeout. `TooltipBox` still dismisses on the pointer's Exit
 * event, and the fade out is its own `animateTooltip`.
 *
 * `focusable = false` is the fourth override and the one that is not cosmetic. `TooltipBox`
 * defaults it to `true`, which renders the tooltip in a Popup that takes focus and
 * intercepts pointer input. Combined with the persistence above — which is what stops the
 * popup from timing itself out — clicking a chip left that popup standing and the whole
 * screen stopped accepting clicks. A tooltip explains; it must never take focus.
 *
 * The JVM test harness does not reproduce that freeze: `performClick` on the anchor dismisses
 * the tooltip there, so the blocking state never forms and any assertion written around it
 * passes with or without this argument. It is verified by clicking chips in a running build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipTooltip(
    text: String,
    anchor: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                caretSize = TooltipDefaults.caretSize,
                shape = RoundedCornerShape(TooltipCornerRadius),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(text = text, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = rememberTooltipState(isPersistent = true),
        focusable = false,
        content = anchor,
    )
}

@Composable
private fun ChipItemContent(
    label: String,
    description: String?,
    enabled: Boolean,
    isSelected: Boolean,
    disabledContentColor: Color,
) {
    if (description != null) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (!enabled) disabledContentColor else Color.Unspecified,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (!enabled) {
                        disabledContentColor
                    } else if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    } else {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (!enabled) disabledContentColor else Color.Unspecified,
        )
    }
}

@Composable
private fun chipItemColors(disabledContentColor: Color): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        labelColor = MaterialTheme.colorScheme.onSurface,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        disabledLabelColor = disabledContentColor,
        disabledLeadingIconColor = disabledContentColor,
        disabledTrailingIconColor = disabledContentColor,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledSelectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )

@Composable
private fun chipItemBorder(
    enabled: Boolean,
    isSelected: Boolean,
): BorderStroke? =
    FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = isSelected,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        selectedBorderColor = MaterialTheme.colorScheme.primary,
        disabledBorderColor =
            MaterialTheme.colorScheme.outlineVariant
                .copy(alpha = DISABLED_BORDER_ALPHA),
        disabledSelectedBorderColor =
            MaterialTheme.colorScheme.outlineVariant
                .copy(alpha = DISABLED_BORDER_ALPHA),
    )

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
