package com.inframap.frontend.ui.topology.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inframap.frontend.ui.topology.TopologyActions
import com.inframap.frontend.ui.topology.TopologyCanvas as CoreTopologyCanvas
import com.inframap.frontend.ui.topology.TopologyState

@Composable
fun TopologyCanvas(
    state: TopologyState,
    actions: TopologyActions,
    modifier: Modifier = Modifier,
) {
    CoreTopologyCanvas(
        state = state,
        actions = actions,
        modifier = modifier,
    )
}
