package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.subnet_chips_empty
import com.inframap.frontend.generated.resources.subnet_chips_title
import org.jetbrains.compose.resources.stringResource

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
    // The card, the outline and the fold all come from CollapsibleSection: this block and the
    // detected-interface block on the subnet screen answer the same need, so they have to be
    // the same shape.
    CollapsibleSection(
        title = stringResource(Res.string.subnet_chips_title),
        icon = InfraMapIcons.Lan,
        modifier = modifier,
    ) {
        SubnetSuggestionContent(
            subnets = subnets,
            selectedCidr = selectedCidr,
            isLoading = isLoading,
            onSubnetSelected = onSubnetSelected,
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
                text = stringResource(Res.string.subnet_chips_empty),
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
                    SuggestionCard(
                        title = subnet.name,
                        detail = subnet.cidr,
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
