@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inframap.frontend.designsystem.InfraMapIcons
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
import com.inframap.frontend.ui.discovery.CreateDiscoverySourceActions
import com.inframap.frontend.ui.discovery.CreateDiscoverySourceScreen
import com.inframap.frontend.ui.discovery.CreateDiscoverySourceViewModel
import com.inframap.frontend.ui.discovery.DiscoveryListActions
import com.inframap.frontend.ui.discovery.DiscoveryListScreen
import com.inframap.frontend.ui.discovery.DiscoveryListViewModel
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
import com.inframap.frontend.ui.tour.ProductTourActions
import com.inframap.frontend.ui.tour.ProductTourOverlay
import com.inframap.frontend.ui.tour.ProductTourUiState
import com.inframap.frontend.ui.tour.ProductTourViewModel
import com.inframap.frontend.ui.wizard.SetupWizardActions
import com.inframap.frontend.ui.wizard.SetupWizardScreen
import com.inframap.frontend.ui.wizard.SetupWizardViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: Route,
)

private val navItems =
    listOf(
        NavItem("Dashboard", InfraMapIcons.Dashboard, Route.Dashboard),
        NavItem("Devices", InfraMapIcons.Dns, Route.Devices),
        NavItem("Staging", InfraMapIcons.MoveToInbox, Route.Staging),
        NavItem("Subnets", InfraMapIcons.Lan, Route.Subnets),
        NavItem("Discovery", InfraMapIcons.Radar, Route.DiscoverySources),
        NavItem("Topology", InfraMapIcons.AccountTree, Route.Topology),
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
    val tourViewModel: ProductTourViewModel = koinInject()
    val tourState by tourViewModel.state.collectAsState()

    DisposableEffect(commandPaletteViewModel) {
        onDispose { commandPaletteViewModel.clear() }
    }
    DisposableEffect(tourViewModel) {
        onDispose { tourViewModel.clear() }
    }

    LaunchedEffect(Unit) {
        tourViewModel.checkShouldShow()
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
            onRestartTourClicked = tourViewModel::startTour,
        )
        MainScaffoldOverlays(
            commandPaletteState = commandPaletteState,
            commandPaletteViewModel = commandPaletteViewModel,
            tourState = tourState,
            tourViewModel = tourViewModel,
        )
    }
}

@Composable
private fun MainScaffoldOverlays(
    commandPaletteState: com.inframap.frontend.ui.command.CommandPaletteUiState,
    commandPaletteViewModel: CommandPaletteViewModel,
    tourState: ProductTourUiState,
    tourViewModel: ProductTourViewModel,
) {
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

    val tourActions =
        ProductTourActions(
            onDismiss = tourViewModel::dismissTour,
            onNext = tourViewModel::nextStep,
            onBack = tourViewModel::previousStep,
            onRestart = tourViewModel::startTour,
        )
    ProductTourOverlay(
        state = tourState,
        actions = tourActions,
    )
}

@Composable
private fun MainScaffoldContent(
    currentRoute: Route,
    navigator: Navigator,
    isHealthy: Boolean?,
    onHealthChanged: (Boolean?) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onRestartTourClicked: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                isHealthy = isHealthy,
                onOpenCommandPalette = onOpenCommandPalette,
                onRestartTourClicked = onRestartTourClicked,
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
}

@Composable
private fun AppTopBar(
    isHealthy: Boolean?,
    onOpenCommandPalette: () -> Unit = {},
    onRestartTourClicked: () -> Unit = {},
) {
    com.inframap.frontend.designsystem.InfraMapTopBar(
        title = "InfraMap",
        isHealthy = isHealthy,
        isSseConnected = isHealthy ?: true,
        onSearchClicked = onOpenCommandPalette,
        onRestartTourClicked = onRestartTourClicked,
    )
}

@Composable
private fun AppNavRail(
    currentRoute: Route,
    navigator: Navigator,
) {
    val items =
        navItems.map { navItem ->
            com.inframap.frontend.designsystem.NavRailItem(
                label = navItem.label,
                icon = navItem.icon,
                route = navItem.label,
            )
        }

    val selectedRouteKey =
        when (currentRoute) {
            Route.Dashboard -> "Dashboard"
            Route.Devices,
            is Route.DeviceDetail,
            Route.CreateDevice,
            is Route.EditDevice,
            -> "Devices"

            Route.DiscoverySources,
            Route.CreateDiscoverySource,
            -> "Discovery"

            Route.Staging -> "Staging"
            Route.Subnets,
            is Route.CreateSubnet,
            -> "Subnets"

            Route.Topology -> "Topology"
            else -> "Dashboard"
        }

    com.inframap.frontend.designsystem.InfraMapNavRail(
        items = items,
        selectedRoute = selectedRouteKey,
        onItemSelected = { selectedKey ->
            val targetNavItem = navItems.find { it.label == selectedKey }
            if (targetNavItem != null) {
                navigator.navigateTo(targetNavItem.route)
            }
        },
    )
}

@Composable
private fun RouteContent(
    currentRoute: Route,
    navigator: Navigator,
    onHealthChanged: (Boolean?) -> Unit = {},
) {
    when (currentRoute) {
        Route.Dashboard -> DashboardRoute(onHealthChanged, navigator)
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
        Route.DiscoverySources -> DiscoveryListRoute(navigator = navigator)
        Route.CreateDiscoverySource -> CreateDiscoverySourceRoute(navigator = navigator)
        Route.Topology -> TopologyRoute(navigator = navigator)
        else -> PlaceholderScreen("")
    }
}

@Composable
private fun DashboardRoute(
    onHealthChanged: (Boolean?) -> Unit = {},
    navigator: Navigator,
) {
    val viewModel: DashboardViewModel = koinInject()
    val wizardViewModel: SetupWizardViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    DisposableEffect(wizardViewModel) {
        onDispose { wizardViewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val wizardState by wizardViewModel.state.collectAsState()

    LaunchedEffect(state.isSystemHealthy) {
        onHealthChanged(state.isSystemHealthy)
    }
    LaunchedEffect(state.isLoading, state.errorMessage, state.totalSubnets, state.totalActiveDevices) {
        if (!state.isLoading && state.errorMessage == null) {
            wizardViewModel.checkShouldShow(state.totalSubnets, state.totalActiveDevices)
        }
    }

    Box {
        DashboardScreen(
            state = state,
            onRefresh = viewModel::refresh,
            onDismissError = viewModel::dismissError,
        )
        SetupWizardScreen(
            state = wizardState,
            actions =
                SetupWizardActions(
                    onDismiss = wizardViewModel::dismiss,
                    onNext = wizardViewModel::nextStep,
                    onBack = wizardViewModel::previousStep,
                    onToggleInterface = wizardViewModel::toggleInterface,
                    onDismissError = wizardViewModel::dismissError,
                    onSelectScanType = wizardViewModel::selectScanType,
                    onSelectFrequency = wizardViewModel::selectFrequency,
                    onStartScan = wizardViewModel::startScan,
                    onComplete = {
                        wizardViewModel.complete()
                        navigator.navigateTo(Route.Staging)
                    },
                ),
        )
    }
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
private fun DiscoveryListRoute(navigator: Navigator) {
    val viewModel: DiscoveryListViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()
    val actions =
        DiscoveryListActions(
            onCreateSourceClicked = { navigator.navigateTo(Route.CreateDiscoverySource) },
            onTriggerRunClicked = viewModel::triggerRun,
            onDeleteSourceClicked = viewModel::confirmDeleteSource,
            onConfirmDelete = viewModel::deleteSource,
            onCancelDelete = viewModel::cancelDeleteSource,
            onDismissDeleteError = viewModel::dismissDeleteError,
            onDismissToast = viewModel::dismissToast,
            onDismissTriggerRunError = viewModel::dismissTriggerRunError,
            onRetryClicked = { viewModel.loadPage(1) },
        )
    DiscoveryListScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun CreateDiscoverySourceRoute(navigator: Navigator) {
    val viewModel: CreateDiscoverySourceViewModel = koinInject()
    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }
    val state by viewModel.state.collectAsState()

    DisposableEffect(state.isSuccess) {
        if (state.isSuccess) {
            navigator.navigateTo(Route.DiscoverySources)
        }
        onDispose {}
    }

    val actions =
        CreateDiscoverySourceActions(
            onNameChanged = viewModel::onNameChanged,
            onSourceTypeChanged = viewModel::onSourceTypeChanged,
            onScheduleCronChanged = viewModel::onScheduleCronChanged,
            onConfigCidrChanged = viewModel::onConfigCidrChanged,
            onEnabledChanged = viewModel::onEnabledChanged,
            onSubmitClicked = {
                viewModel.createSource {
                    navigator.navigateTo(Route.DiscoverySources)
                }
            },
            onCancelClicked = { navigator.navigateTo(Route.DiscoverySources) },
        )
    CreateDiscoverySourceScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun TopologyRoute(navigator: Navigator) {
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
            onConfigureDiscovery = { navigator.navigateTo(Route.DiscoverySources) },
        )
    TopologyScreen(
        state = state,
        actions = actions,
    )
}
