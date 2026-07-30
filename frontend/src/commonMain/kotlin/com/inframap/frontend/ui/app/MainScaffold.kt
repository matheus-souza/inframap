@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.designsystem.InfraMapGreen
import com.inframap.frontend.designsystem.InfraMapRed
import com.inframap.frontend.navigation.Navigator
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.ui.dashboard.DashboardScreen
import com.inframap.frontend.ui.dashboard.DashboardViewModel

data class NavItem(
    val label: String,
    val route: Route,
)

private val navItems =
    listOf(
        NavItem("Dashboard", Route.Dashboard),
        NavItem("Devices", Route.Devices),
        NavItem("Staging", Route.Staging),
        NavItem("Subnets", Route.Subnets),
        NavItem("Topology", Route.Topology),
    )

@Composable
fun MainScaffold(
    currentRoute: Route,
    navigator: Navigator,
    isHealthy: Boolean?,
    apiClient: ApiClient,
    sseClient: SSEClient? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(isHealthy = isHealthy)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
        Row(modifier = Modifier.weight(1f)) {
            AppNavRail(currentRoute = currentRoute, navigator = navigator)
            VerticalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp),
            ) {
                RouteContent(
                    currentRoute = currentRoute,
                    apiClient = apiClient,
                    sseClient = sseClient,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(isHealthy: Boolean?) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "InfraMap",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (isHealthy != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val dotColor =
                        if (isHealthy) InfraMapGreen else InfraMapRed
                    val description =
                        if (isHealthy) "System healthy" else "System unhealthy"
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .semantics { contentDescription = description },
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun AppNavRail(
    currentRoute: Route,
    navigator: Navigator,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        navItems.forEach { item ->
            NavigationRailItem(
                selected = currentRoute == item.route,
                onClick = { navigator.navigateTo(item.route) },
                icon = {},
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

@Composable
private fun RouteContent(
    currentRoute: Route,
    apiClient: ApiClient,
    sseClient: SSEClient? = null,
) {
    when (currentRoute) {
        Route.Dashboard -> DashboardRoute(apiClient = apiClient, sseClient = sseClient)
        Route.Devices -> PlaceholderScreen("Devices")
        Route.Staging -> PlaceholderScreen("Staging")
        Route.Subnets -> PlaceholderScreen("Subnets")
        Route.Topology -> PlaceholderScreen("Topology")
        else -> PlaceholderScreen("")
    }
}

@Composable
private fun DashboardRoute(
    apiClient: ApiClient,
    sseClient: SSEClient? = null,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(apiClient, sseClient) { DashboardViewModel(apiClient, sseClient, scope) }
    val state by viewModel.state.collectAsState()
    DashboardScreen(
        state = state,
        onRefresh = viewModel::refresh,
    )
}
