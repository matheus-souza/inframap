package com.inframap.frontend.ui.subnets

import androidx.compose.runtime.Composable
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.delete_subnet_impact_devices
import com.inframap.frontend.generated.resources.delete_subnet_impact_edges
import com.inframap.frontend.generated.resources.delete_subnet_impact_sources
import org.jetbrains.compose.resources.stringResource

/**
 * The impact lines of the Subnet deletion dialog. The discovery sources line only appears
 * when a source scans the Subnet: deleting it keeps those sources and their runs skip the
 * linked target (RFC-026 §6).
 */
@Composable
internal fun subnetDeletionImpactLines(impact: SubnetDeletionImpact?): List<String> {
    if (impact == null) return emptyList()
    val lines =
        mutableListOf(
            stringResource(Res.string.delete_subnet_impact_devices, impact.affectedDevices),
            stringResource(Res.string.delete_subnet_impact_edges, impact.unlinkedTopologyEdges),
        )
    val sources = impact.discoverySources
    if (sources.isNotEmpty()) {
        lines +=
            stringResource(
                Res.string.delete_subnet_impact_sources,
                sources.size,
                sources.joinToString(", ") { it.name },
            )
    }
    return lines
}
