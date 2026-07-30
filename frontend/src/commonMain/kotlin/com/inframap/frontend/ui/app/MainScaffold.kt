@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.DisposableEffect
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
import com.inframap.frontend.ui.devices.CreateDeviceActions
import com.inframap.frontend.ui.devices.CreateDeviceScreen
import com.inframap.frontend.ui.devices.CreateDeviceViewModel
import com.inframap.frontend.ui.devices.DeviceDetailActions
import com.inframap.frontend.ui.devices.DeviceDetailScreen
import com.inframap.frontend.ui.devices.DeviceDetailViewModel
import com.inframap.frontend.ui.devices.DeviceListActions
import com.inframap.frontend.ui.devices.DeviceListScreen
import com.inframap.frontend.ui.devices.DeviceListViewModel
import com.inframap.frontend.ui.devices.EditDeviceActions
import com.inframap.frontend.ui.devices.EditDeviceScreen
import com.inframap.frontend.ui.devices.EditDeviceViewModel

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
                    navigator = navigator,
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
        androidx.compose.foundation.layout
            .Spacer(modifier = Modifier.padding(top = 8.dp))
        navItems.forEach { item ->
            val isSelected =
                when (currentRoute) {
                    Route.Devices,
                    is Route.DeviceDetail,
                    Route.CreateDevice,
                    is Route.EditDevice,
                    -> item.route == Route.Devices

                    else -> currentRoute == item.route
                }
            NavigationRailItem(
                selected = isSelected,
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
    navigator: Navigator,
    apiClient: ApiClient,
    sseClient: SSEClient? = null,
) {
    when (currentRoute) {
        Route.Dashboard -> DashboardRoute(apiClient = apiClient, sseClient = sseClient)
        Route.Devices -> DeviceListRoute(apiClient = apiClient, navigator = navigator)
        is Route.DeviceDetail ->
            DeviceDetailRoute(
                deviceId = currentRoute.id,
                apiClient = apiClient,
                navigator = navigator,
            )

        Route.CreateDevice -> CreateDeviceRoute(apiClient = apiClient, navigator = navigator)
        is Route.EditDevice ->
            EditDeviceRoute(
                deviceId = currentRoute.id,
                apiClient = apiClient,
                navigator = navigator,
            )

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
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    DashboardScreen(
        state = state,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun DeviceListRoute(
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(apiClient) { DeviceListViewModel(apiClient, scope) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        DeviceListActions(
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onPageChanged = viewModel::loadDevices,
            onCreateDeviceClicked = { navigator.navigateTo(Route.CreateDevice) },
            onDeviceClicked = { id -> navigator.navigateTo(Route.DeviceDetail(id)) },
            onEditDeviceClicked = { id -> navigator.navigateTo(Route.EditDevice(id)) },
            onDeleteDeviceClicked = viewModel::confirmDeleteDevice,
            onConfirmDelete = viewModel::deleteDevice,
            onCancelDelete = viewModel::cancelDeleteDevice,
            onRetryClicked = { viewModel.loadDevices() },
        )
    DeviceListScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun DeviceDetailRoute(
    deviceId: String,
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(deviceId, apiClient) { DeviceDetailViewModel(deviceId, apiClient, scope) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        DeviceDetailActions(
            onBackClicked = { navigator.navigateTo(Route.Devices) },
            onEditClicked = { id -> navigator.navigateTo(Route.EditDevice(id)) },
            onDeleteClicked = viewModel::openDeleteDialog,
            onConfirmDelete = {
                viewModel.deleteDevice { navigator.navigateTo(Route.Devices) }
            },
            onCancelDelete = viewModel::closeDeleteDialog,
            onRetryClicked = viewModel::loadDevice,
        )
    DeviceDetailScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun CreateDeviceRoute(
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(apiClient) { CreateDeviceViewModel(apiClient, scope) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()

    DisposableEffect(state.createdDeviceId) {
        if (state.createdDeviceId != null) {
            navigator.navigateTo(Route.DeviceDetail(state.createdDeviceId!!))
        }
        onDispose {}
    }

    val actions =
        CreateDeviceActions(
            onHostnameChanged = viewModel::onHostnameChanged,
            onIpAddressChanged = viewModel::onIpAddressChanged,
            onMacAddressChanged = viewModel::onMacAddressChanged,
            onDeviceTypeChanged = viewModel::onDeviceTypeChanged,
            onSubmitClicked = viewModel::createDevice,
            onCancelClicked = { navigator.navigateTo(Route.Devices) },
        )

    CreateDeviceScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun EditDeviceRoute(
    deviceId: String,
    apiClient: ApiClient,
    navigator: Navigator,
) {
    val scope = rememberCoroutineScope()
    val viewModel = remember(deviceId, apiClient) { EditDeviceViewModel(deviceId, apiClient, scope) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()

    DisposableEffect(state.isSuccess) {
        if (state.isSuccess) {
            navigator.navigateTo(Route.DeviceDetail(deviceId))
        }
        onDispose {}
    }

    val actions =
        EditDeviceActions(
            onHostnameChanged = viewModel::onHostnameChanged,
            onIpAddressChanged = viewModel::onIpAddressChanged,
            onMacAddressChanged = viewModel::onMacAddressChanged,
            onDeviceTypeChanged = viewModel::onDeviceTypeChanged,
            onStatusChanged = viewModel::onStatusChanged,
            onSubmitClicked = viewModel::updateDevice,
            onCancelClicked = { navigator.navigateTo(Route.DeviceDetail(deviceId)) },
            onRetryClicked = viewModel::loadDevice,
        )

    EditDeviceScreen(
        state = state,
        actions = actions,
    )
}
