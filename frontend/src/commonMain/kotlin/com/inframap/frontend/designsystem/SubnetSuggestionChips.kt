package com.inframap.frontend.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.motion.m3InteractiveScale
import com.inframap.frontend.domain.model.SubnetSummary

private val ChipCornerRadius = 8.dp
private val SkeletonChipWidth = 140.dp
private val SkeletonChipHeight = 36.dp

@Composable
fun SubnetSuggestionChips(
    subnets: List<SubnetSummary>,
    selectedCidr: String? = null,
    isLoading: Boolean = false,
    onSubnetSelected: (SubnetSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    InfraMapCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SubnetSuggestionHeader()
            Spacer(modifier = Modifier.height(12.dp))
            SubnetSuggestionContent(
                subnets = subnets,
                selectedCidr = selectedCidr,
                isLoading = isLoading,
                onSubnetSelected = onSubnetSelected,
            )
        }
    }
}

internal const val SUBNET_CHIPS_TITLE = "Sub-redes cadastradas"
internal const val SUBNET_CHIPS_EMPTY_MESSAGE = "Nenhuma sub-rede cadastrada para sugestão."

@Composable
private fun SubnetSuggestionHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = InfraMapIcons.Lan,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = SUBNET_CHIPS_TITLE,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubnetSuggestionContent(
    subnets: List<SubnetSummary>,
    selectedCidr: String?,
    isLoading: Boolean,
    onSubnetSelected: (SubnetSummary) -> Unit,
) {
    when {
        isLoading -> SubnetSuggestionSkeleton()
        subnets.isEmpty() -> {
            Text(
                text = SUBNET_CHIPS_EMPTY_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                subnets.forEach { subnet ->
                    SubnetSuggestionChip(
                        subnet = subnet,
                        isSelected = subnet.cidr == selectedCidr,
                        onClick = { onSubnetSelected(subnet) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubnetSuggestionSkeleton() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .width(SkeletonChipWidth)
                        .height(SkeletonChipHeight)
                        .m3Shimmer(shape = RoundedCornerShape(ChipCornerRadius)),
            )
        }
    }
}

private data class ChipColors(
    val container: Color,
    val border: Color,
    val name: Color,
    val cidr: Color,
)

@Composable
private fun resolveChipColors(isSelected: Boolean): ChipColors =
    if (isSelected) {
        ChipColors(
            container = MaterialTheme.colorScheme.primaryContainer,
            border = MaterialTheme.colorScheme.primary,
            name = MaterialTheme.colorScheme.onPrimaryContainer,
            cidr = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        ChipColors(
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            name = MaterialTheme.colorScheme.onSurface,
            cidr = MaterialTheme.colorScheme.primary,
        )
    }

@Composable
private fun SubnetSuggestionChip(
    subnet: SubnetSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = resolveChipColors(isSelected)

    Surface(
        modifier =
            modifier
                .m3InteractiveScale(interactionSource)
                .semantics {
                    role = Role.Button
                    selected = isSelected
                },
        shape = RoundedCornerShape(ChipCornerRadius),
        color = colors.container,
        border = BorderStroke(1.dp, colors.border),
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = subnet.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.name,
            )
            Text(
                text = subnet.cidr,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                color = colors.cidr,
            )
        }
    }
}
