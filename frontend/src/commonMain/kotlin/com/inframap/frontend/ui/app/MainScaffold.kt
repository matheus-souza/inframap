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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapGreen
import com.inframap.frontend.designsystem.InfraMapRed
import com.inframap.frontend.navigation.Navigator
import com.inframap.frontend.navigation.Route

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
                RouteContent(currentRoute = currentRoute)
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
private fun RouteContent(currentRoute: Route) {
    when (currentRoute) {
        Route.Dashboard -> PlaceholderScreen("Dashboard")
        Route.Devices -> PlaceholderScreen("Devices")
        Route.Staging -> PlaceholderScreen("Staging")
        Route.Subnets -> PlaceholderScreen("Subnets")
        Route.Topology -> PlaceholderScreen("Topology")
        else -> PlaceholderScreen("")
    }
}
