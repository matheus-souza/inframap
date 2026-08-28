@file:Suppress("MatchingDeclarationName")

package com.inframap.frontend.ui.app

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.designsystem.InfraMapIcons
import com.inframap.frontend.designsystem.InfraMapToastHost
import com.inframap.frontend.designsystem.motion.MotionTransitions
import com.inframap.frontend.domain.model.CommandPaletteAction
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.create_device_title
import com.inframap.frontend.generated.resources.create_discovery_source_title
import com.inframap.frontend.generated.resources.create_subnet_title
import com.inframap.frontend.generated.resources.dashboard_title
import com.inframap.frontend.generated.resources.device_detail_title
import com.inframap.frontend.generated.resources.devices_title
import com.inframap.frontend.generated.resources.discovery_title
import com.inframap.frontend.generated.resources.edit_device_title
import com.inframap.frontend.generated.resources.staging_header
import com.inframap.frontend.generated.resources.subnets_title
import com.inframap.frontend.generated.resources.topology_title
import com.inframap.frontend.navigation.Navigator
import com.inframap.frontend.navigation.Route
import com.inframap.frontend.platform.updateBrowserTitle
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
import com.inframap.frontend.ui.onboarding.OnboardingCoordinator
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope
import org.koin.core.parameter.parametersOf

data class NavItem(
    val titleRes: StringResource,
    val icon: ImageVector,
    val route: Route,
    val routeKey: String,
)

private const val KEY_NAVRAIL_COLLAPSED = "inframap_navrail_collapsed"

internal val defaultNavItems =
    listOf(
        NavItem(Res.string.dashboard_title, InfraMapIcons.Dashboard, Route.Dashboard, "dashboard"),
        NavItem(Res.string.devices_title, InfraMapIcons.Dns, Route.Devices, "devices"),
        NavItem(Res.string.staging_header, InfraMapIcons.MoveToInbox, Route.Staging, "staging"),
        NavItem(Res.string.subnets_title, InfraMapIcons.Lan, Route.Subnets, "subnets"),
        NavItem(Res.string.discovery_title, InfraMapIcons.Radar, Route.DiscoverySources, "discovery"),
        NavItem(Res.string.topology_title, InfraMapIcons.AccountTree, Route.Topology, "topology"),
    )

fun resolveScreenTitleResource(route: Route): StringResource? =
    when (route) {
        Route.Dashboard -> Res.string.dashboard_title
        Route.Devices -> Res.string.devices_title
        is Route.DeviceDetail -> Res.string.device_detail_title
        Route.CreateDevice -> Res.string.create_device_title
        is Route.EditDevice -> Res.string.edit_device_title
        Route.Staging -> Res.string.staging_header
        Route.Subnets -> Res.string.subnets_title
        is Route.CreateSubnet -> Res.string.create_subnet_title
        Route.DiscoverySources -> Res.string.discovery_title
        Route.CreateDiscoverySource -> Res.string.create_discovery_source_title
        Route.Topology -> Res.string.topology_title
        else -> null
    }

@Composable
fun MainScaffold(
    currentRoute: Route,
    navigator: Navigator,
    isHealthy: Boolean?,
    onHealthChanged: (Boolean?) -> Unit = {},
) {
    val koinScope = currentKoinScope()
    val commandPaletteViewModel: CommandPaletteViewModel = remember { koinScope.get() }
    val commandPaletteState by commandPaletteViewModel.state.collectAsState()
    val tourViewModel: ProductTourViewModel = remember { koinScope.get() }
    val tourState by tourViewModel.state.collectAsState()
    val onboardingCoordinator: OnboardingCoordinator = remember { koinScope.get() }

    DisposableEffect(commandPaletteViewModel) {
        onDispose { commandPaletteViewModel.clear() }
    }
    DisposableEffect(tourViewModel) {
        onDispose { tourViewModel.clear() }
    }

    val titleResource = resolveScreenTitleResource(currentRoute)
    val screenTitle = titleResource?.let { stringResource(it) }

    LaunchedEffect(screenTitle) {
        updateBrowserTitle(screenTitle)
    }

    LaunchedEffect(Unit) {
        if (onboardingCoordinator.shouldShowTour()) {
            tourViewModel.checkShouldShow()
        }
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

    val onTogglePalette = remember(commandPaletteViewModel) { { commandPaletteViewModel.toggle() } }
    val onOpenCommandPalette = remember(commandPaletteViewModel) { { commandPaletteViewModel.open() } }
    val onRestartTourClicked = remember(tourViewModel) { { tourViewModel.startTour() } }

    CommandPaletteListener(
        onTogglePalette = onTogglePalette,
    ) {
        MainScaffoldContent(
            currentRoute = currentRoute,
            navigator = navigator,
            isHealthy = isHealthy,
            onHealthChanged = onHealthChanged,
            onOpenCommandPalette = onOpenCommandPalette,
            onRestartTourClicked = onRestartTourClicked,
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

    InfraMapToastHost()
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
    val koinScope = currentKoinScope()
    val localStorage: LocalStorage = remember { koinScope.get() }
    var isNavRailExpanded by remember {
        mutableStateOf(localStorage.get(KEY_NAVRAIL_COLLAPSED) == null)
    }

    val titleResource = resolveScreenTitleResource(currentRoute)
    val screenTitle = titleResource?.let { stringResource(it) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                screenTitle = screenTitle,
                isHealthy = isHealthy,
                onOpenCommandPalette = onOpenCommandPalette,
                onRestartTourClicked = onRestartTourClicked,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Row(modifier = Modifier.weight(1f)) {
                AppNavRail(
                    currentRoute = currentRoute,
                    navigator = navigator,
                    isExpanded = isNavRailExpanded,
                    onToggleExpanded = {
                        isNavRailExpanded = !isNavRailExpanded
                        if (isNavRailExpanded) {
                            localStorage.remove(KEY_NAVRAIL_COLLAPSED)
                        } else {
                            localStorage.set(KEY_NAVRAIL_COLLAPSED, "true")
                        }
                    },
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ScaffoldMainContent(
                    currentRoute = currentRoute,
                    navigator = navigator,
                    onHealthChanged = onHealthChanged,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ScaffoldMainContent(
    currentRoute: Route,
    navigator: Navigator,
    onHealthChanged: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = currentRoute,
            transitionSpec = {
                MotionTransitions.scaffoldTransition(
                    initialRoute = initialState,
                    targetRoute = targetState,
                )
            },
            label = "MainScaffoldRouteTransition",
        ) { targetRoute ->
            RouteContent(
                currentRoute = targetRoute,
                navigator = navigator,
                onHealthChanged = onHealthChanged,
            )
        }
    }
}

@Composable
private fun AppTopBar(
    screenTitle: String? = null,
    isHealthy: Boolean?,
    onOpenCommandPalette: () -> Unit = {},
    onRestartTourClicked: () -> Unit = {},
) {
    com.inframap.frontend.designsystem.InfraMapTopBar(
        screenTitle = screenTitle,
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
    isExpanded: Boolean = true,
    onToggleExpanded: () -> Unit = {},
) {
    val items =
        defaultNavItems.map { navItem ->
            com.inframap.frontend.designsystem.NavRailItem(
                label = stringResource(navItem.titleRes),
                icon = navItem.icon,
                route = navItem.routeKey,
            )
        }

    val selectedRouteKey =
        when (currentRoute) {
            Route.Dashboard -> "dashboard"
            Route.Devices,
            is Route.DeviceDetail,
            Route.CreateDevice,
            is Route.EditDevice,
            -> "devices"

            Route.DiscoverySources,
            Route.CreateDiscoverySource,
            -> "discovery"

            Route.Staging -> "staging"
            Route.Subnets,
            is Route.CreateSubnet,
            -> "subnets"

            Route.Topology -> "topology"
            else -> "dashboard"
        }

    com.inframap.frontend.designsystem.InfraMapNavRail(
        items = items,
        selectedRoute = selectedRouteKey,
        onItemSelected = { selectedKey ->
            val targetNavItem = defaultNavItems.find { it.routeKey == selectedKey }
            if (targetNavItem != null) {
                navigator.navigateTo(targetNavItem.route)
            }
        },
        isExpanded = isExpanded,
        onToggleExpanded = onToggleExpanded,
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

        Route.Staging -> StagingRoute(navigator = navigator)
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
    val koinScope = currentKoinScope()
    val viewModel: DashboardViewModel = remember { koinScope.get() }
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
        onDismissError = viewModel::dismissError,
        onNavigateToSubnets = { navigator.navigateTo(Route.Subnets) },
        onNavigateToDiscovery = { navigator.navigateTo(Route.DiscoverySources) },
        onStartAutoSetup = viewModel::startAutoSetup,
        onDismissAutoSetup = viewModel::dismissAutoSetup,
        onNavigateToStaging = { navigator.navigateTo(Route.Staging) },
    )
}

@Composable
private fun DeviceListRoute(navigator: Navigator) {
    val koinScope = currentKoinScope()
    val viewModel: DeviceListViewModel = remember { koinScope.get() }
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
    val koinScope = currentKoinScope()
    val viewModel: DeviceDetailViewModel = remember(deviceId) { koinScope.get { parametersOf(deviceId) } }
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
    val koinScope = currentKoinScope()
    val viewModel: CreateDeviceViewModel = remember { koinScope.get() }
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
    val koinScope = currentKoinScope()
    val viewModel: EditDeviceViewModel = remember(deviceId) { koinScope.get { parametersOf(deviceId) } }
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
private fun StagingRoute(navigator: Navigator) {
    val koinScope = currentKoinScope()
    val viewModel: StagingViewModel = remember { koinScope.get() }
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
            onConfigureDiscovery = { navigator.navigateTo(Route.DiscoverySources) },
        )
    StagingScreen(
        state = state,
        actions = actions,
    )
}

@Composable
private fun SubnetsRoute(navigator: Navigator) {
    val koinScope = currentKoinScope()
    val viewModel: SubnetsViewModel = remember { koinScope.get() }
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
    val koinScope = currentKoinScope()
    val viewModel: CreateSubnetViewModel =
        remember(prefilledCidr, prefilledName) {
            koinScope.get { parametersOf(prefilledCidr, prefilledName) }
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
    val koinScope = currentKoinScope()
    val viewModel: DiscoveryListViewModel = remember { koinScope.get() }
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
    val koinScope = currentKoinScope()
    val viewModel: CreateDiscoverySourceViewModel = remember { koinScope.get() }
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
            onSubnetSelected = viewModel::onSubnetSelected,
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
    val koinScope = currentKoinScope()
    val viewModel: TopologyViewModel = remember { koinScope.get() }
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
