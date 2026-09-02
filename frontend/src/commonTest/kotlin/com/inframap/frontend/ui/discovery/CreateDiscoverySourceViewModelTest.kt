package com.inframap.frontend.ui.discovery

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.integrations.TestProviderHealthUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import com.inframap.frontend.fakes.FakeIntegrationsRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDiscoverySourceViewModelTest {
    private val createdSource =
        DiscoverySource(
            id = "src-1",
            name = "ICMP Scanner",
            sourceType = "icmp_sweep",
            enabled = true,
            lastStatus = "idle",
        )

    private val testSubnet =
        Subnet(
            id = "sub-10",
            name = "Office LAN",
            cidr = "10.10.0.0/24",
            discoveryEnabled = true,
        )

    private fun makeVm(
        discoveryRepo: FakeDiscoveryRepository =
            FakeDiscoveryRepository(
                createSourceResult = ApiResult.Success(createdSource, requestId = ""),
            ),
        subnetRepo: FakeSubnetRepository =
            FakeSubnetRepository(
                getSubnetsResult =
                    ApiResult.Success(
                        PaginatedList(items = listOf(testSubnet), total = 1L, page = 1, perPage = 50),
                        requestId = "",
                    ),
            ),
        integrationsRepo: FakeIntegrationsRepository = FakeIntegrationsRepository(),
        scope: CoroutineScope? = null,
    ) = CreateDiscoverySourceViewModel(
        createSourceUseCase = CreateDiscoverySourceUseCase(discoveryRepo),
        listSubnetsUseCase = ListSubnetsUseCase(subnetRepo),
        testProviderHealthUseCase = TestProviderHealthUseCase(integrationsRepo),
        scope = scope,
    )

    @Test
    fun subnetsLoadedSuccessfullyOnInit() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoadingSubnets)
            assertEquals(1, state.subnets.size)
            assertEquals("Office LAN", state.subnets[0].name)
            assertEquals("10.10.0.0/24", state.subnets[0].cidr)
            vm.clear()
        }

    @Test
    fun subnetsLoadFailureHandledGracefully() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.NetworkError(RuntimeException("Failed to load")),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoadingSubnets)
            assertTrue(state.subnets.isEmpty())
            vm.clear()
        }

    @Test
    fun onSubnetSelectedPrefillsCidrAndDefaultNameWhenNameIsBlank() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val summary = SubnetSummary(id = "sub-1", name = "Production", cidr = "192.168.10.0/24")
            vm.onSubnetSelected(summary)

            val state = vm.state.value
            assertEquals("Varredura Production", state.name)
            assertEquals("192.168.10.0/24", state.configCidr)
            assertFalse(state.validationErrors.containsKey("cidr"))
            assertFalse(state.validationErrors.containsKey("name"))
            vm.clear()
        }

    @Test
    fun onSubnetSelectedPreservesExistingCustomName() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Custom Dedicated Scanner")
            val summary = SubnetSummary(id = "sub-1", name = "Production", cidr = "192.168.10.0/24")
            vm.onSubnetSelected(summary)

            val state = vm.state.value
            assertEquals("Custom Dedicated Scanner", state.name)
            assertEquals("192.168.10.0/24", state.configCidr)
            vm.clear()
        }

    @Test
    fun defaultSelectedCollectorsContainsExpectedInitialSet() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val expected = setOf("icmp_sweep", "arp_sweep", "mdns", "reverse_dns")
            assertEquals(expected, vm.state.value.selectedCollectors)
            vm.clear()
        }

    @Test
    fun toggleCollectorAddsAndRemovesCollector() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            // Remove icmp_sweep
            vm.toggleCollector("icmp_sweep")
            assertFalse(
                vm.state.value.selectedCollectors
                    .contains("icmp_sweep"),
            )

            // Add snmp
            vm.toggleCollector("snmp")
            assertTrue(
                vm.state.value.selectedCollectors
                    .contains("snmp"),
            )

            // Remove snmp
            vm.toggleCollector("snmp")
            assertFalse(
                vm.state.value.selectedCollectors
                    .contains("snmp"),
            )

            // onCollectorsChanged
            vm.onCollectorsChanged(setOf("arp_sweep", "snmp"))
            assertEquals(setOf("arp_sweep", "snmp"), vm.state.value.selectedCollectors)

            vm.clear()
        }

    @Test
    fun validationFailsOnEmptyNameAndEmptyCollectors() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onCollectorsChanged(emptySet())

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("name"))
            assertTrue(errors.containsKey("collectors"))
            assertTrue(errors.containsKey("cidr"))
            vm.clear()
        }

    @Test
    fun validationPassesWithValidFields() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onCollectorsChanged(setOf("icmp_sweep"))
            vm.onConfigCidrChanged("192.168.1.0/24")

            assertTrue(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .isEmpty(),
            )
            vm.clear()
        }

    @Test
    fun validationFailsOnEmptyCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onConfigCidrChanged("")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("cidr"),
            )
            vm.clear()
        }

    @Test
    fun validationFailsOnInvalidCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onConfigCidrChanged("not-a-cidr")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("cidr"),
            )
            vm.clear()
        }

    @Test
    fun validationAcceptsValidCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onConfigCidrChanged("192.168.1.0/24")

            assertTrue(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .isEmpty(),
            )
            vm.clear()
        }

    @Test
    fun fieldChangesUpdateState() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("Test Source")
            assertEquals("Test Source", vm.state.value.name)

            vm.toggleCollector("snmp")
            assertTrue(
                vm.state.value.selectedCollectors
                    .contains("snmp"),
            )

            vm.onScheduleCronChanged("0 */6 * * *")
            assertEquals("0 */6 * * *", vm.state.value.scheduleCron)

            vm.onConfigCidrChanged("10.0.0.0/8")
            assertEquals("10.0.0.0/8", vm.state.value.configCidr)

            vm.onEnabledChanged(false)
            assertFalse(vm.state.value.enabled)

            vm.clear()
        }

    @Test
    fun fieldChangeClearsRelatedValidationError() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onCollectorsChanged(emptySet())
            vm.validate()
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("collectors"),
            )

            vm.onNameChanged("Fixed")
            assertFalse(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )

            vm.toggleCollector("icmp_sweep")
            assertFalse(
                vm.state.value.validationErrors
                    .containsKey("collectors"),
            )

            vm.clear()
        }

    @Test
    fun createSourceWorkflowCompletesSuccessfully() =
        runTest {
            var onSuccessCalled = false
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onCollectorsChanged(setOf("icmp_sweep", "arp_sweep"))
            vm.onConfigCidrChanged("192.168.1.0/24")
            vm.onScheduleCronChanged("0 */6 * * *")

            vm.state.test {
                skipItems(1)
                vm.createSource { onSuccessCalled = true }
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                assertNull(state.errorMessage)
                assertTrue(onSuccessCalled)
                assertEquals("0 */6 * * *", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
                assertEquals("192.168.1.0/24", discoveryRepo.lastCreateSourceRequest?.config?.get("cidr"))
                val collectors =
                    discoveryRepo.lastCreateSourceRequest
                        ?.collectors
                        ?.map { it.type }
                        ?.toSet()
                assertEquals(setOf("icmp_sweep", "arp_sweep"), collectors)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceWith15MinPresetScheduleSubmitsCorrectCron() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("Fast Scanner")
            vm.onCollectorsChanged(setOf("arp_sweep"))
            vm.onConfigCidrChanged("192.168.1.0/24")
            vm.onScheduleCronChanged("*/15 * * * *")

            vm.createSource()
            advanceUntilIdle()

            assertEquals("*/15 * * * *", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
            assertEquals(listOf("arp_sweep"), discoveryRepo.lastCreateSourceRequest?.collectors?.map { it.type })
            vm.clear()
        }

    @Test
    fun createSourceWithCustomCronScheduleSubmitsCorrectCron() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("Custom Cron Scanner")
            vm.onCollectorsChanged(setOf("docker"))
            // Docker is a configurable provider now, so the plan needs an endpoint to submit.
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.onConfigCidrChanged("192.168.1.0/24")
            vm.onScheduleCronChanged("30 3 * * 1-5")

            vm.createSource()
            advanceUntilIdle()

            assertEquals("30 3 * * 1-5", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
            vm.clear()
        }

    @Test
    fun createSourceIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onConfigCidrChanged("192.168.1.0/24")

            vm.createSource()
            assertTrue(vm.state.value.isSubmitting)

            vm.createSource()
            assertTrue(vm.state.value.isSubmitting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun createSourceHandlesApiError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Error(
                            code = "DUPLICATE_NAME",
                            message = "Source name already exists",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(discoveryRepo = repo, scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onConfigCidrChanged("192.168.1.0/24")

            vm.state.test {
                skipItems(1)
                vm.createSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.isSubmitting)
                assertFalse(state.isSuccess)
                assertEquals("Source name already exists", state.errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.NetworkError(RuntimeException("timeout")),
                )
            val vm = makeVm(discoveryRepo = repo, scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onConfigCidrChanged("192.168.1.0/24")

            vm.state.test {
                skipItems(1)
                vm.createSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.isSubmitting)
                assertFalse(state.isSuccess)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceSkipsWhenValidationFails() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onCollectorsChanged(emptySet())

            vm.createSource()
            assertFalse(vm.state.value.isSubmitting)
            assertTrue(
                vm.state.value.validationErrors
                    .isNotEmpty(),
            )

            vm.clear()
        }

    @Test
    fun createSourceWithBlankNameFailsAtUseCaseLevel() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("  ")
            vm.onConfigCidrChanged("192.168.1.0/24")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )
            vm.clear()
        }

    @Test
    fun cidrIsNotRequiredForAProviderOnlyPlan() =
        runTest {
            // A plan made purely of providers has no range to sweep. Demanding a CIDR would
            // make a legitimate Docker-only plan impossible to save.
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Docker homelab")
            vm.onCollectorsChanged(setOf("docker"))
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")

            assertTrue(vm.validate())
            assertNull(vm.state.value.validationErrors["cidr"])
        }

    @Test
    fun cidrIsStillRequiredWhenASweepIsSelected() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Mixed plan")
            vm.onCollectorsChanged(setOf("icmp_sweep", "docker"))
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("cidr"),
            )
        }

    @Test
    fun aProviderMissingRequiredFieldsBlocksSubmission() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Proxmox cluster")
            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("provider:proxmox"),
            )
        }

    @Test
    fun dockerWithNeitherSocketNorTcpBlocksSubmission() =
        runTest {
            // Leaving both blank would let the server fall back to its own daemon socket.
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Docker plan")
            vm.onCollectorsChanged(setOf("docker"))

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("provider:docker"),
            )
        }

    @Test
    fun providerConfigTravelsOnItsOwnCollector() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(createSourceResult = ApiResult.Success(createdSource, requestId = ""))
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Docker homelab")
            vm.onCollectorsChanged(setOf("docker"))
            vm.onProviderFieldChanged("docker", "tcp_url", "tcp://192.168.1.50:2376")
            vm.onProviderFieldChanged("docker", "tls_ca", "   ")
            vm.createSource()
            advanceUntilIdle()

            val sent = discoveryRepo.lastCreateSourceRequest!!
            val docker = sent.collectors.single { it.type == "docker" }
            assertEquals(mapOf("tcp_url" to "tcp://192.168.1.50:2376"), docker.config)
        }

    @Test
    fun testConnectionReportsHealthy() =
        runTest {
            val integrations = FakeIntegrationsRepository()
            val vm = makeVm(integrationsRepo = integrations, scope = this)
            advanceUntilIdle()

            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.testConnection("docker")
            advanceUntilIdle()

            assertEquals(ConnectionTest.Healthy, vm.state.value.connectionTests["docker"])
            assertEquals(
                "docker" to mapOf("socket_path" to "unix:///var/run/docker.sock"),
                integrations.calls.single(),
            )
        }

    @Test
    fun anUnreachableProviderIsReportedAsFailedNotHealthy() =
        runTest {
            // The endpoint answers 200 for both outcomes and reports the verdict in the body,
            // so a transport success must not be mistaken for a healthy provider.
            val integrations =
                FakeIntegrationsRepository(
                    healthResult =
                        ApiResult.Success(
                            com.inframap.frontend.domain.model.ProviderHealth(
                                providerId = "docker",
                                isHealthy = false,
                                message = "Health check failed",
                            ),
                            requestId = "",
                        ),
                )
            val vm = makeVm(integrationsRepo = integrations, scope = this)
            advanceUntilIdle()

            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.testConnection("docker")
            advanceUntilIdle()

            assertTrue(vm.state.value.connectionTests["docker"] is ConnectionTest.Failed)
        }

    @Test
    fun testingWithAnEmptyFormNeverReachesTheBackend() =
        runTest {
            val integrations = FakeIntegrationsRepository()
            val vm = makeVm(integrationsRepo = integrations, scope = this)
            advanceUntilIdle()

            vm.testConnection("docker")
            advanceUntilIdle()

            assertTrue(vm.state.value.connectionTests["docker"] is ConnectionTest.Failed)
            assertTrue(integrations.calls.isEmpty())
        }

    @Test
    fun editingTheConfigurationDiscardsAStaleResult() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.testConnection("docker")
            advanceUntilIdle()
            assertEquals(ConnectionTest.Healthy, vm.state.value.connectionTests["docker"])

            vm.onProviderFieldChanged("docker", "socket_path", "unix:///other.sock")

            assertNull(
                vm.state.value.connectionTests["docker"],
                "a result from the previous endpoint must not stay on screen",
            )
        }

    @Test
    fun selectedProvidersFollowChipOrder() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("docker", "icmp_sweep", "proxmox"))

            assertEquals(listOf("proxmox", "docker"), vm.state.value.selectedProviders)
        }
}
