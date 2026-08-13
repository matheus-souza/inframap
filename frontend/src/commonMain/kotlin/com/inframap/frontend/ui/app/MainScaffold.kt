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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapGreen
import com.inframap.frontend.designsystem.InfraMapRed
import com.inframap.frontend.domain.model.CommandPaletteAction
import com.inframap.frontend.navigation.Navigator
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.ui.command.CommandPaletteActions
import com.inframap.frontend.ui.command.CommandPaletteEffect
import com.inframap.frontend.ui.command.CommandPaletteListener
import com.inframap.frontend.ui.command.CommandPaletteModal
import com.inframap.frontend.ui.command.CommandPaletteViewModel
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
import com.inframap.frontend.ui.staging.StagingActions
import com.inframap.frontend.ui.staging.StagingScreen
import com.inframap.frontend.ui.staging.StagingViewModel
import com.inframap.frontend.ui.subnets.CreateSubnetActions
import com.inframap.frontend.ui.subnets.CreateSubnetScreen
import com.inframap.frontend.ui.subnets.CreateSubnetViewModel
import com.inframap.frontend.ui.subnets.SubnetsActions
import com.inframap.frontend.ui.subnets.SubnetsScreen
import com.inframap.frontend.ui.subnets.SubnetsViewModel
import com.inframap.frontend.ui.topology.TopologyActions
import com.inframap.frontend.ui.topology.TopologyScreen
import com.inframap.frontend.ui.topology.TopologyViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: Route,
)

private val navItems =
    listOf(
        NavItem("Dashboard", Icons.Filled.SpaceDashboard, Route.Dashboard),
        NavItem("Devices", Icons.Filled.Dns, Route.Devices),
        NavItem("Staging", Icons.Filled.MoveToInbox, Route.Staging),
        NavItem("Subnets", Icons.Filled.Lan, Route.Subnets),
        NavItem("Topology", Icons.Filled.AccountTree, Route.Topology),
    )

@Composable
fun MainScaffold(
    currentRoute: Route,
    navigator: Navigator,
    isHealthy: Boolean?,
    onHealthChanged: (Boolean?) -> Unit = {},
) {
    val commandPaletteViewModel: CommandPaletteViewModel = koinInject()
    val commandPaletteState by commandPaletteViewModel.state.collectAsState()

    DisposableEffect(commandPaletteViewModel) {
        onDispose { commandPaletteViewModel.clear() }
    }

    LaunchedEffect(Unit) {
        commandPaletteViewModel.effects.collect { effect ->
            when (effect) {
                is CommandPaletteEffect.ExecuteItem -> {
                    when (val action = effect.item.action) {
                        is CommandPaletteAction.Navigate -> navigator.navigateTo(action.route)
                        CommandPaletteAction.RefreshData -> {}
                    }
                }
                CommandPaletteEffect.ClosePalette -> {}
            }
        }
    }

    CommandPaletteListener(onTogglePalette = commandPaletteViewModel::toggle) {
        MainScaffoldContent(
            currentRoute = currentRoute,
            navigator = navigator,
            isHealthy = isHealthy,
            onHealthChanged = onHealthChanged,
            onOpenCommandPalette = commandPaletteViewModel::open,
        )
        val paletteActions =
            CommandPaletteActions(
                onQueryChanged = commandPaletteViewModel::onQueryChanged,
                onNextItem = commandPaletteViewModel::onNextItem,
                onPreviousItem = commandPaletteViewModel::onPreviousItem,
                onSelectItem = commandPaletteViewModel::selectCurrentItem,
                onItemClicked = commandPaletteViewModel::onItemClicked,
                onDismiss = commandPaletteViewModel::close,
            )
        CommandPaletteModal(
            state = commandPaletteState,
            actions = paletteActions,
        )
    }
}

@Composable
private fun MainScaffoldContent(
    currentRoute: Route,
    navigator: Navigator,
    isHealthy: Boolean?,
    onHealthChanged: (Boolean?) -> Unit,
    onOpenCommandPalette: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTopBar(
            isHealthy = isHealthy,
            onOpenCommandPalette = onOpenCommandPalette,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Row(modifier = Modifier.weight(1f)) {
            AppNavRail(currentRoute = currentRoute, navigator = navigator)
            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                    onHealthChanged = onHealthChanged,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    isHealthy: Boolean?,
    onOpenCommandPalette: () -> Unit = {},
) {
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
                    val dotColor = if (isHealthy) InfraMapGreen else InfraMapRed
                    val description = if (isHealthy) "System healthy" else "System unhealthy"
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
        actions = {
            CommandPaletteTopBarButton(onClick = onOpenCommandPalette)
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun CommandPaletteTopBarButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Command Palette",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Buscar...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.background,
            ) {
                Text(
                    text = "⌘K",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
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

                    Route.Subnets,
                    is Route.CreateSubnet,
                    -> item.route == Route.Subnets

                    else -> currentRoute == item.route
                }
            NavigationRailItem(
                selected = isSelected,
                onClick = { navigator.navigateTo(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
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
    onHealthChanged: (Boolean?) -> Unit = {},
) {
    when (currentRoute) {
        Route.Dashboard -> DashboardRoute(onHealthChanged)
        Route.Devices -> DeviceListRoute(navigator = navigator)
        is Route.DeviceDetail ->
            DeviceDetailRoute(
                deviceId = currentRoute.id,
                navigator = navigator,
            )

        Route.CreateDevice -> CreateDeviceRoute(navigator = navigator)
        is Route.EditDevice ->
            EditDeviceRoute(
                deviceId = currentRoute.id,
                navigator = navigator,
            )

        Route.Staging -> StagingRoute()
        Route.Subnets -> SubnetsRoute(navigator = navigator)
        is Route.CreateSubnet ->
            CreateSubnetRoute(
                prefilledCidr = currentRoute.prefilledCidr,
                prefilledName = currentRoute.prefilledName,
                navigator = navigator,
            )
        Route.Topology -> TopologyRoute()
        else -> PlaceholderScreen("")
    }
}

@Composable
private fun DashboardRoute(onHealthChanged: (Boolean?) -> Unit = {}) {
    val viewModel: DashboardViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.isSystemHealthy) {
        onHealthChanged(state.isSystemHealthy)
    }
    DashboardScreen(
        state = state,
        onRefresh = viewModel::refresh,
    )
}

@Composable
private fun DeviceListRoute(navigator: Navigator) {
    val viewModel: DeviceListViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        DeviceListActions(
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onPageChanged = { page -> viewModel.loadPage(page) },
            onCreateDeviceClicked = { navigator.navigateTo(Route.CreateDevice) },
            onDeviceClicked = { id -> navigator.navigateTo(Route.DeviceDetail(id)) },
            onEditDeviceClicked = { id -> navigator.navigateTo(Route.EditDevice(id)) },
            onDeleteDeviceClicked = viewModel::confirmDeleteDevice,
            onConfirmDelete = viewModel::deleteDevice,
            onCancelDelete = viewModel::cancelDeleteDevice,
            onDismissDeleteError = viewModel::dismissDeleteError,
            onDismissToast = viewModel::dismissToast,
            onRetryClicked = { viewModel.loadPage(1) },
        )
    DeviceListScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun DeviceDetailRoute(
    deviceId: String,
    navigator: Navigator,
) {
    val viewModel: DeviceDetailViewModel = koinInject { parametersOf(deviceId) }
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
private fun CreateDeviceRoute(navigator: Navigator) {
    val viewModel: CreateDeviceViewModel = koinInject()
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
    navigator: Navigator,
) {
    val viewModel: EditDeviceViewModel = koinInject { parametersOf(deviceId) }
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

@Composable
private fun StagingRoute() {
    val viewModel: StagingViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        StagingActions(
            onPageChanged = { page -> viewModel.loadPage(page) },
            onApproveClicked = viewModel::approveDevice,
            onDismissClicked = viewModel::confirmDismissDevice,
            onConfirmDismiss = viewModel::dismissDevice,
            onCancelDismiss = viewModel::cancelDismissDevice,
            onDismissActionError = viewModel::dismissActionError,
            onDismissToast = viewModel::dismissToast,
            onRetryClicked = { viewModel.loadPage(1) },
        )
    StagingScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun SubnetsRoute(navigator: Navigator) {
    val viewModel: SubnetsViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        SubnetsActions(
            onCreateSubnetClicked = { navigator.navigateTo(Route.CreateSubnet()) },
            onAddInterfaceClicked = { iface ->
                navigator.navigateTo(
                    Route.CreateSubnet(
                        prefilledCidr = iface.cidr,
                        prefilledName = iface.name,
                    ),
                )
            },
            onDismissToast = viewModel::dismissToast,
            onRetryClicked = viewModel::loadSubnets,
        )
    SubnetsScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun CreateSubnetRoute(
    prefilledCidr: String? = null,
    prefilledName: String? = null,
    navigator: Navigator,
) {
    val viewModel: CreateSubnetViewModel =
        koinInject {
            parametersOf(prefilledCidr, prefilledName)
        }
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        CreateSubnetActions(
            onNameChanged = viewModel::onNameChanged,
            onCidrChanged = viewModel::onCidrChanged,
            onVlanIdChanged = viewModel::onVlanIdChanged,
            onGatewayIpChanged = viewModel::onGatewayIpChanged,
            onDescriptionChanged = viewModel::onDescriptionChanged,
            onDiscoveryEnabledChanged = viewModel::onDiscoveryEnabledChanged,
            onInterfaceSelected = viewModel::onInterfaceSelected,
            onToggleSuggestions = viewModel::toggleSuggestions,
            onSubmitClicked = {
                viewModel.createSubnet {
                    navigator.navigateTo(Route.Subnets)
                }
            },
            onCancelClicked = { navigator.navigateTo(Route.Subnets) },
        )
    CreateSubnetScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun TopologyRoute() {
    val viewModel: TopologyViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        TopologyActions(
            onRefresh = viewModel::refresh,
            onNodeSelected = viewModel::selectNode,
            onDismissNodeDetails = viewModel::dismissNodeDetails,
            onPan = viewModel::onPan,
            onZoom = viewModel::onZoom,
            onResetViewport = viewModel::resetViewport,
        )
    TopologyScreen(
        state = state,
        actions = actions,
    )
}
