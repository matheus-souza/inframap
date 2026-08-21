package com.inframap.frontend.domain.usecase.command

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.CommandPaletteAction
import com.inframap.frontend.domain.model.CommandPaletteCategory
import com.inframap.frontend.domain.model.CommandPaletteItem
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.navigation.Route

class SearchIndexUseCase(
    private val deviceRepository: DeviceRepository,
    private val subnetRepository: SubnetRepository,
    private val dashboardRepository: DashboardRepository,
) {
    private val defaultQuickActions =
        listOf(
            CommandPaletteItem(
                id = "action-dashboard",
                title = "Ir para Dashboard",
                subtitle = "Visualizar estatísticas e métricas do sistema",
                category = CommandPaletteCategory.NAVEGACAO,
                action = CommandPaletteAction.Navigate(Route.Dashboard),
            ),
            CommandPaletteItem(
                id = "action-devices",
                title = "Ir para Dispositivos",
                subtitle = "Listar e gerenciar dispositivos de rede",
                category = CommandPaletteCategory.NAVEGACAO,
                action = CommandPaletteAction.Navigate(Route.Devices),
            ),
            CommandPaletteItem(
                id = "action-create-device",
                title = "Criar Dispositivo",
                subtitle = "Adicionar um novo dispositivo ao inventário",
                category = CommandPaletteCategory.ACOES,
                action = CommandPaletteAction.Navigate(Route.CreateDevice),
            ),
            CommandPaletteItem(
                id = "action-staging",
                title = "Ir para Staging",
                subtitle = "Analisar dispositivos descobertos pendentes",
                category = CommandPaletteCategory.NAVEGACAO,
                action = CommandPaletteAction.Navigate(Route.Staging),
            ),
            CommandPaletteItem(
                id = "action-subnets",
                title = "Ir para Subredes",
                subtitle = "Gerenciar faixas CIDR e VLANs",
                category = CommandPaletteCategory.NAVEGACAO,
                action = CommandPaletteAction.Navigate(Route.Subnets),
            ),
            CommandPaletteItem(
                id = "action-create-subnet",
                title = "Cadastrar Sub-rede",
                subtitle = "Cadastrar uma nova faixa de subrede",
                category = CommandPaletteCategory.ACOES,
                action = CommandPaletteAction.Navigate(Route.CreateSubnet()),
            ),
            CommandPaletteItem(
                id = "action-topology",
                title = "Ir para Topologia",
                subtitle = "Visualizar o mapa interativo da rede",
                category = CommandPaletteCategory.NAVEGACAO,
                action = CommandPaletteAction.Navigate(Route.Topology),
            ),
            CommandPaletteItem(
                id = "action-refresh",
                title = "Nova Varredura",
                subtitle = "Disparar varredura da rede",
                category = CommandPaletteCategory.ACOES,
                action = CommandPaletteAction.RefreshData,
            ),
            CommandPaletteItem(
                id = "action-export-inventory",
                title = "Exportar Inventário",
                subtitle = "Baixar inventário em CSV",
                category = CommandPaletteCategory.ACOES,
                action = CommandPaletteAction.RefreshData,
            ),
            CommandPaletteItem(
                id = "action-toggle-sidebar",
                title = "Alternar Barra Lateral",
                subtitle = "Expandir ou recolher o menu lateral",
                category = CommandPaletteCategory.ACOES,
                action = CommandPaletteAction.RefreshData,
            ),
        )

    suspend operator fun invoke(query: String): List<CommandPaletteItem> {
        val trimmedQuery = query.trim().lowercase()

        val devicesResult =
            runCatching {
                deviceRepository.getDevices(page = 1, perPage = 50, search = query)
            }.getOrNull()
        val subnetsResult = runCatching { subnetRepository.getSubnets() }.getOrNull()
        val sourcesResult = runCatching { dashboardRepository.getDiscoverySources() }.getOrNull()

        val deviceItems = mapDevices(devicesResult)
        val subnetItems = mapSubnets(subnetsResult, trimmedQuery)
        val sourceItems = mapDiscoverySources(sourcesResult, trimmedQuery)
        val filteredActions = filterQuickActions(trimmedQuery)

        return deviceItems + subnetItems + sourceItems + filteredActions
    }

    private fun mapDevices(result: ApiResult<PaginatedList<Device>>?): List<CommandPaletteItem> {
        if (result !is ApiResult.Success) return emptyList()
        return result.data.items.map { dev ->
            val subtitleParts = listOfNotNull(dev.ipAddress, dev.macAddress, dev.deviceType).filter { it.isNotBlank() }
            CommandPaletteItem(
                id = "device-${dev.id}",
                title = dev.hostname,
                subtitle = subtitleParts.joinToString(" • ").ifEmpty { null },
                category = CommandPaletteCategory.DISPOSITIVOS,
                action = CommandPaletteAction.Navigate(Route.DeviceDetail(dev.id)),
                status = dev.status,
            )
        }
    }

    private fun mapSubnets(
        result: ApiResult<PaginatedList<Subnet>>?,
        query: String,
    ): List<CommandPaletteItem> {
        if (result !is ApiResult.Success) return emptyList()
        return result.data.items
            .filter { subnet -> matchesSubnet(subnet, query) }
            .map { subnet ->
                val vlanPart = subnet.vlanId?.let { " • VLAN $it" } ?: ""
                CommandPaletteItem(
                    id = "subnet-${subnet.id}",
                    title = subnet.name,
                    subtitle = "CIDR: ${subnet.cidr}$vlanPart",
                    category = CommandPaletteCategory.SUBREDES,
                    action = CommandPaletteAction.Navigate(Route.Subnets),
                    badge = "0", // Placeholder for host count
                )
            }
    }

    private fun matchesSubnet(
        subnet: Subnet,
        query: String,
    ): Boolean {
        if (query.isEmpty()) return true
        return subnet.name.lowercase().contains(query) ||
            subnet.cidr.lowercase().contains(query) ||
            (subnet.vlanId != null && subnet.vlanId.toString().contains(query)) ||
            (subnet.description != null && subnet.description.lowercase().contains(query))
    }

    private fun mapDiscoverySources(
        result: ApiResult<List<DiscoverySource>>?,
        query: String,
    ): List<CommandPaletteItem> {
        if (result !is ApiResult.Success) return emptyList()
        return result.data
            .filter { source ->
                query.isEmpty() ||
                    source.name.lowercase().contains(query) ||
                    source.sourceType.lowercase().contains(query)
            }.map { source ->
                val statusPart = if (source.enabled) " (Ativo)" else " (Inativo)"
                CommandPaletteItem(
                    id = "source-${source.id}",
                    title = source.name,
                    subtitle = "Tipo: ${source.sourceType}$statusPart",
                    category = CommandPaletteCategory.NAVEGACAO,
                    action = CommandPaletteAction.Navigate(Route.Dashboard),
                )
            }
    }

    private fun filterQuickActions(query: String): List<CommandPaletteItem> {
        if (query.isEmpty()) return defaultQuickActions
        return defaultQuickActions.filter { action ->
            action.title.lowercase().contains(query) ||
                (action.subtitle != null && action.subtitle.lowercase().contains(query))
        }
    }
}
