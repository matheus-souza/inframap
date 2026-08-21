package com.inframap.frontend.domain.usecase.command

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.CommandPaletteCategory
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.fakes.FakeDashboardRepository
import com.inframap.frontend.fakes.FakeDeviceRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchIndexUseCaseTest {
    @Test
    fun emptyQueryReturnsAllQuickActionsAndAvailableItems() =
        runTest {
            val deviceRepo = FakeDeviceRepository()
            val subnetRepo = FakeSubnetRepository()
            val dashRepo = FakeDashboardRepository()

            val useCase = SearchIndexUseCase(deviceRepo, subnetRepo, dashRepo)
            val results = useCase("")

            assertTrue(results.any { it.category == CommandPaletteCategory.DISPOSITIVOS })
            assertTrue(results.any { it.category == CommandPaletteCategory.SUBREDES })
            assertTrue(results.any { it.category == CommandPaletteCategory.NAVEGACAO })
            assertTrue(results.any { it.category == CommandPaletteCategory.ACOES })
        }

    @Test
    fun queryFiltersByHostnameSubnetNameAndAction() =
        runTest {
            val deviceRepo =
                FakeDeviceRepository(
                    getDevicesResult =
                        ApiResult.Success(
                            PaginatedList(
                                items =
                                    listOf(
                                        Device(
                                            id = "d1",
                                            hostname = "core-router-01",
                                            ipAddress = "10.0.0.1",
                                            deviceType = "router",
                                            status = "online",
                                        ),
                                        Device(
                                            id = "d2",
                                            hostname = "bare-device",
                                            ipAddress = null,
                                            macAddress = null,
                                            deviceType = "",
                                            status = "offline",
                                        ),
                                    ),
                                total = 2,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult =
                        ApiResult.Success(
                            PaginatedList(
                                items =
                                    listOf(
                                        Subnet(
                                            id = "s1",
                                            name = "DMZ Network",
                                            cidr = "10.0.1.0/24",
                                            vlanId = 100,
                                            description = "DMZ zone",
                                        ),
                                        Subnet(id = "s2", name = "Internal", cidr = "192.168.1.0/24", vlanId = null, description = null),
                                    ),
                                total = 2,
                                page = 1,
                                perPage = 50,
                            ),
                            requestId = "",
                        ),
                )
            val dashRepo =
                FakeDashboardRepository(
                    getDiscoverySourcesResult =
                        ApiResult.Success(
                            listOf(
                                DiscoverySource(id = "src1", name = "NetFlow Collector", sourceType = "netflow", enabled = true),
                                DiscoverySource(id = "src2", name = "SNMP Poller", sourceType = "snmp", enabled = false),
                            ),
                            requestId = "",
                        ),
                )

            val useCase = SearchIndexUseCase(deviceRepo, subnetRepo, dashRepo)

            val routerResults = useCase("router")
            assertTrue(routerResults.any { it.title.contains("core-router-01") })

            val bareDeviceResults = useCase("bare-device")
            val bareItem = bareDeviceResults.first { it.title == "bare-device" }
            assertNull(bareItem.subtitle)

            val subnetResults = useCase("DMZ")
            assertTrue(subnetResults.any { it.title.contains("DMZ Network") })

            val cidrResults = useCase("192.168")
            assertTrue(cidrResults.any { it.title.contains("Internal") })

            val vlanResults = useCase("100")
            assertTrue(vlanResults.any { it.title.contains("DMZ Network") })

            val descResults = useCase("zone")
            assertTrue(descResults.any { it.title.contains("DMZ Network") })

            val sourceResults = useCase("NetFlow")
            assertTrue(sourceResults.any { it.title.contains("NetFlow Collector") })

            val inactiveSourceResults = useCase("snmp")
            assertTrue(inactiveSourceResults.any { it.subtitle?.contains("(Inativo)") == true })

            val subtitleActionResults = useCase("inventário")
            assertTrue(subtitleActionResults.any { it.title == "Criar Dispositivo" })
        }

    @Test
    fun repositoryErrorHandledGracefully() =
        runTest {
            val deviceRepo = FakeDeviceRepository(getDevicesResult = ApiResult.Error("ERR", "Failed", "", 500))
            val subnetRepo = FakeSubnetRepository(getSubnetsResult = ApiResult.Error("ERR", "Failed", "", 500))
            val dashRepo = FakeDashboardRepository(getDiscoverySourcesResult = ApiResult.Error("ERR", "Failed", "", 500))

            val useCase = SearchIndexUseCase(deviceRepo, subnetRepo, dashRepo)
            val results = useCase("Dashboard")

            assertTrue(results.isNotEmpty())
            assertTrue(results.all { it.category == CommandPaletteCategory.NAVEGACAO })
        }
}
