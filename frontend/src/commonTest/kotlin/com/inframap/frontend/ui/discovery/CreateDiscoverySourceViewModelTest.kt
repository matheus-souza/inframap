package com.inframap.frontend.ui.discovery

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.DiscoverySourceConfigDto
import com.inframap.frontend.data.dto.DiscoveryTargetRequestDto
import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.storage.draft.DraftForm
import com.inframap.frontend.data.storage.draft.FormDraftStore
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.usecase.credentials.ListCredentialsUseCase
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.integrations.TestProviderHealthUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.fakes.FakeCredentialsRepository
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeIntegrationsRepository
import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDiscoverySourceViewModelTest {
    private val fakeStorage = FakeLocalStorage()
    private val fakeClock = FakeEpochClock(now = 1_000_000L)
    private val sessionOwner = SessionOwnerStore(fakeStorage).apply { setOwner("u1") }
    private val formDraftStore = FormDraftStore(fakeStorage, fakeClock, sessionOwner)

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
        credentialsRepo: FakeCredentialsRepository = FakeCredentialsRepository(),
        formDrafts: FormDraftStore = formDraftStore,
        scope: CoroutineScope? = null,
    ) = CreateDiscoverySourceViewModel(
        createSourceUseCase = CreateDiscoverySourceUseCase(discoveryRepo),
        listSubnetsUseCase = ListSubnetsUseCase(subnetRepo),
        testProviderHealthUseCase = TestProviderHealthUseCase(integrationsRepo),
        listCredentialsUseCase = ListCredentialsUseCase(credentialsRepo),
        formDrafts = formDrafts,
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
    fun onSubnetToggledAddsLinkedTargetAndDefaultNameWhenNameIsBlank() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val summary = SubnetSummary(id = "sub-1", name = "Production", cidr = "192.168.10.0/24")
            vm.onSubnetToggled(summary)

            val state = vm.state.value
            assertEquals("Varredura Production", state.name)
            assertEquals(
                listOf(DiscoveryTarget("192.168.10.0/24", subnetId = "sub-1", subnetName = "Production")),
                state.targets,
            )
            assertFalse(state.validationErrors.containsKey(TARGETS_ERROR_KEY))
            assertFalse(state.validationErrors.containsKey("name"))
            vm.clear()
        }

    @Test
    fun onSubnetToggledPreservesExistingCustomName() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Custom Dedicated Scanner")
            val summary = SubnetSummary(id = "sub-1", name = "Production", cidr = "192.168.10.0/24")
            vm.onSubnetToggled(summary)

            val state = vm.state.value
            assertEquals("Custom Dedicated Scanner", state.name)
            assertEquals(listOf("192.168.10.0/24"), state.targets.map { it.cidr })
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
            assertTrue(errors.containsKey(TARGETS_ERROR_KEY))
            vm.clear()
        }

    @Test
    fun validationPassesWithValidFields() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onCollectorsChanged(setOf("icmp_sweep"))
            vm.addCidr("192.168.1.0/24")

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

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey(TARGETS_ERROR_KEY),
            )
            vm.clear()
        }

    @Test
    fun validationFailsOnInvalidCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.addCidr("not-a-cidr")

            assertTrue(
                vm.state.value.targets
                    .isEmpty(),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey(TARGET_INPUT_ERROR_KEY),
            )
            assertFalse(vm.validate())
            vm.clear()
        }

    @Test
    fun validationAcceptsValidCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.addCidr("192.168.1.0/24")

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

            vm.addCidr("10.0.0.0/16")
            assertEquals(listOf(DiscoveryTarget("10.0.0.0/16")), vm.state.value.targets)

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
            vm.addCidr("192.168.1.0/24")
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
                assertEquals(
                    DiscoverySourceConfigDto(targets = listOf(DiscoveryTargetRequestDto("192.168.1.0/24"))),
                    discoveryRepo.lastCreateSourceRequest?.config,
                )
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
            vm.addCidr("192.168.1.0/24")
            vm.onScheduleCronChanged("*/15 * * * *")

            vm.createSource()
            advanceUntilIdle()

            assertEquals("*/15 * * * *", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
            assertEquals(listOf("arp_sweep"), discoveryRepo.lastCreateSourceRequest?.collectors?.map { it.type })
            vm.clear()
        }

    @Test
    fun typedTargetWithHostBitsIsSentAsItsNetwork() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("Lab")
            vm.onCollectorsChanged(setOf("icmp_sweep"))
            vm.addCidr("10.99.0.5/24")

            assertEquals(listOf(DiscoveryTarget("10.99.0.0/24")), vm.state.value.targets)
            vm.createSource()
            advanceUntilIdle()

            assertEquals(
                listOf("10.99.0.0/24"),
                discoveryRepo.lastCreateSourceRequest
                    ?.config
                    ?.targets
                    ?.map { it.cidr },
            )
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
            vm.addCidr("192.168.1.0/24")
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
            vm.addCidr("192.168.1.0/24")

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
            vm.addCidr("192.168.1.0/24")

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
            vm.addCidr("192.168.1.0/24")

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
            vm.addCidr("192.168.1.0/24")

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
            assertNull(vm.state.value.validationErrors[TARGETS_ERROR_KEY])
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
                    .containsKey(TARGETS_ERROR_KEY),
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

    @Test
    fun storedCredentialsAreOfferedOnceLoaded() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            assertEquals(
                listOf("cred-1"),
                vm.state.value.credentials
                    .map { it.id },
            )
        }

    @Test
    fun referencingACredentialWaivesTheInlineRequiredFields() =
        runTest {
            // The credential holds the secrets, so demanding them again would defeat the
            // point of storing one.
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Proxmox via credencial")
            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onProviderFieldChanged("proxmox", "credential_id", "cred-1")
            // The endpoint is still required: a credential says who, never where.
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")
            vm.onProviderFieldChanged("proxmox", "token_id", "root@pam!inframap")

            assertTrue(vm.validate())
            assertNull(vm.state.value.validationErrors["provider:proxmox"])
        }

    @Test
    fun aCredentialAlsoSatisfiesTheDockerEndpointRule() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Docker via credencial")
            vm.onCollectorsChanged(setOf("docker"))
            vm.onProviderFieldChanged("docker", "credential_id", "cred-1")
            vm.onProviderFieldChanged("docker", "tcp_url", "tcp://192.168.1.50:2376")

            assertTrue(vm.validate())
        }

    @Test
    fun theCredentialReferenceTravelsOnTheCollectorConfig() =
        runTest {
            val discoveryRepo =
                FakeDiscoveryRepository(createSourceResult = ApiResult.Success(createdSource, requestId = ""))
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Proxmox via credencial")
            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")
            vm.onProviderFieldChanged("proxmox", "token_id", "root@pam!inframap")
            vm.onProviderFieldChanged("proxmox", "credential_id", "cred-1")
            vm.createSource()
            advanceUntilIdle()

            val proxmox = discoveryRepo.lastCreateSourceRequest!!.collectors.single { it.type == "proxmox" }
            assertEquals("cred-1", proxmox.config?.get("credential_id"))
        }

    @Test
    fun aFailedCredentialListStillAllowsInlineConfiguration() =
        runTest {
            // The list is a convenience; losing it must not block creating a plan.
            val credentialsRepo =
                FakeCredentialsRepository(
                    listResult = ApiResult.Error(code = "INTERNAL", message = "boom", requestId = "", httpStatus = 500),
                )
            val vm = makeVm(credentialsRepo = credentialsRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Docker inline")
            vm.onCollectorsChanged(setOf("docker"))
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")

            assertTrue(
                vm.state.value.credentials
                    .isEmpty(),
            )
            assertTrue(vm.validate())
        }

    @Test
    fun pickingACredentialDropsSecretsTypedBeforeIt() =
        runTest {
            // The backend gives collector values precedence over the credential's, so a stale
            // token left in the config would win and the run would use the old authentication.
            val discoveryRepo =
                FakeDiscoveryRepository(createSourceResult = ApiResult.Success(createdSource, requestId = ""))
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Proxmox")
            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")
            vm.onProviderFieldChanged("proxmox", "token_id", "root@pam!typed")
            vm.onProviderFieldChanged("proxmox", "token_secret", "typed-secret")

            vm.onProviderFieldChanged("proxmox", "credential_id", "cred-1")

            val config =
                vm.state.value.providerConfigs["proxmox"]
                    .orEmpty()
            assertNull(config["token_secret"], "the typed secret must not survive picking a credential")
            assertEquals("https://pve.local:8006", config["api_url"], "the endpoint is not a secret and must remain")
        }

    @Test
    fun addingASecondProviderMakesItTheActiveTab() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox"))
            assertEquals("proxmox", vm.state.value.activeProviderTab)

            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            assertEquals("docker", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun addingANetworkCollectorKeepsTheActiveTab() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            assertEquals("docker", vm.state.value.activeProviderTab)

            vm.onCollectorsChanged(setOf("proxmox", "docker", "icmp_sweep"))
            assertEquals("docker", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun onProviderTabSelectedSwitchesTheActiveTab() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            assertEquals("docker", vm.state.value.activeProviderTab)

            vm.onProviderTabSelected("proxmox")
            assertEquals("proxmox", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun onProviderTabSelectedIgnoresAnUnselectedProvider() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox"))
            assertEquals("proxmox", vm.state.value.activeProviderTab)

            vm.onProviderTabSelected("docker")
            assertEquals("proxmox", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun deselectingTheActiveProviderFallsBackToTheRemainingOne() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            vm.onProviderTabSelected("docker")

            vm.onCollectorsChanged(setOf("proxmox"))
            assertEquals("proxmox", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun deselectingAllProvidersClearsTheTab() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            vm.onCollectorsChanged(setOf("icmp_sweep"))

            assertNull(vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun switchingTabsKeepsConfigsTestsAndErrors() =
        runTest {
            val integrations = FakeIntegrationsRepository()
            val vm = makeVm(integrationsRepo = integrations, scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.testConnection("docker")
            advanceUntilIdle()

            // Validate generates an error for the incomplete proxmox provider
            vm.validate()

            assertEquals(ConnectionTest.Healthy, vm.state.value.connectionTests["docker"])
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("provider:proxmox"),
            )

            vm.onProviderTabSelected("docker")
            assertEquals(
                "https://pve.local:8006",
                vm.state.value.providerConfigs["proxmox"]
                    ?.get("api_url"),
            )
            assertEquals(
                "unix:///var/run/docker.sock",
                vm.state.value.providerConfigs["docker"]
                    ?.get("socket_path"),
            )
            assertEquals(ConnectionTest.Healthy, vm.state.value.connectionTests["docker"])
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("provider:proxmox"),
            )

            vm.onProviderTabSelected("proxmox")
            assertEquals(
                "https://pve.local:8006",
                vm.state.value.providerConfigs["proxmox"]
                    ?.get("api_url"),
            )
            assertEquals(
                "unix:///var/run/docker.sock",
                vm.state.value.providerConfigs["docker"]
                    ?.get("socket_path"),
            )
            assertEquals(ConnectionTest.Healthy, vm.state.value.connectionTests["docker"])
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("provider:proxmox"),
            )
            vm.clear()
        }

    @Test
    fun failedValidationBringsTheFirstProviderWithAnErrorForward() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Multi-provider plan")
            // Use only providers so CIDR is not required
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            // Docker is the active tab and valid
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.onProviderTabSelected("docker")
            // Proxmox is missing required fields (empty)
            val isValid = vm.validate()

            assertFalse(isValid)
            assertEquals("proxmox", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun failedValidationBringsFirstProviderForwardEvenIfActiveTabHasError() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Multi-provider plan")
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            // Both have errors, but docker is active
            vm.onProviderTabSelected("docker")
            val isValid = vm.validate()

            assertFalse(isValid)
            assertEquals("proxmox", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun failedValidationJumpsToSecondProviderWhenFirstIsValid() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Multi-provider plan")
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            vm.onProviderTabSelected("proxmox")
            // Proxmox valid
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")
            vm.onProviderFieldChanged("proxmox", "token_id", "root@pam!token")
            vm.onProviderFieldChanged("proxmox", "token_secret", "secret")
            // Docker is invalid (missing socket_path / tcp_url)

            val isValid = vm.validate()

            assertFalse(isValid)
            assertEquals("docker", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun successfulValidationDoesNotMoveTheTab() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Multi-provider plan")
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve.local:8006")
            vm.onProviderFieldChanged("proxmox", "token_id", "root@pam!token")
            vm.onProviderFieldChanged("proxmox", "token_secret", "secret")
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.onProviderTabSelected("docker")

            val isValid = vm.validate()

            assertTrue(isValid)
            assertEquals("docker", vm.state.value.activeProviderTab)
            vm.clear()
        }

    @Test
    fun providerFieldEditPersistsWithoutSecrets() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("proxmox"))
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve:8006")
            vm.onProviderFieldChanged("proxmox", "token_secret", "SUPER-SECRET-TOKEN")

            val raw = fakeStorage.get(DraftForm.CreateDiscoverySource.storageKey)
            assertNotNull(raw)
            assertFalse(raw.contains("SUPER-SECRET-TOKEN"), "Storage must not contain secret values")

            val loaded = formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer())
            assertNotNull(loaded)
            assertEquals("https://pve:8006", loaded.providerConfigs["proxmox"]?.get("api_url"))
            assertFalse(loaded.providerConfigs["proxmox"]?.containsKey("token_secret") == true)
            vm.clear()
        }

    @Test
    fun collectorToggleSubnetSelectionAndTabChangePersist() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Discovery Ops")
            vm.onCollectorsChanged(setOf("proxmox", "docker"))
            vm.onProviderTabSelected("docker")
            vm.onScheduleCronChanged("0 * * * *")
            vm.addCidr("192.168.1.0/24")
            vm.onEnabledChanged(false)

            val loaded = formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer())
            assertNotNull(loaded)
            assertEquals("Discovery Ops", loaded.name)
            assertEquals(setOf("proxmox", "docker"), loaded.selectedCollectors)
            assertEquals("docker", loaded.activeProviderTab)
            assertEquals("0 * * * *", loaded.scheduleCron)
            assertEquals(listOf(CreateDiscoverySourceDraftTarget("192.168.1.0/24")), loaded.targets)
            assertFalse(loaded.enabled)
            vm.clear()
        }

    @Test
    fun connectionTestAndDataLoadsDoNotPersist() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.loadSubnets()
            advanceUntilIdle()

            assertNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun untouchedFormCreatesNoDraft() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            assertNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun initRestoresDraftWithSecretsBlank() =
        runTest {
            val draft =
                CreateDiscoverySourceDraft(
                    name = "Restored Source",
                    selectedCollectors = setOf("proxmox"),
                    providerConfigs =
                        mapOf(
                            "proxmox" to
                                mapOf(
                                    "api_url" to "https://proxmox.local:8006",
                                    "token_id" to "root@pam!token",
                                ),
                        ),
                )
            formDraftStore.save(DraftForm.CreateDiscoverySource, draft, CreateDiscoverySourceDraft.serializer())

            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals("Restored Source", state.name)
            assertEquals(setOf("proxmox"), state.selectedCollectors)
            assertTrue(state.restoredFromDraft)
            assertEquals("https://proxmox.local:8006", state.providerConfigs["proxmox"]?.get("api_url"))
            assertEquals("root@pam!token", state.providerConfigs["proxmox"]?.get("token_id"))
            assertFalse(state.providerConfigs["proxmox"]?.containsKey("token_secret") == true)

            // Validation must fail because the secret token is required and blank
            assertFalse(vm.validate())
            assertTrue(
                state.validationErrors.containsKey("provider:proxmox") ||
                    vm.state.value.validationErrors
                        .containsKey("provider:proxmox"),
            )
            vm.clear()
        }

    @Test
    fun restoredActiveTabFallsBackWhenProviderNoLongerSelected() =
        runTest {
            val draft =
                CreateDiscoverySourceDraft(
                    name = "Fallback Tab Source",
                    selectedCollectors = setOf("proxmox"),
                    activeProviderTab = "docker",
                )
            formDraftStore.save(DraftForm.CreateDiscoverySource, draft, CreateDiscoverySourceDraft.serializer())

            val vm = makeVm(scope = this)
            advanceUntilIdle()

            assertEquals("proxmox", vm.state.value.currentProviderTab)
            vm.clear()
        }

    @Test
    fun successfulSubmitDiscardsDraft() =
        runTest {
            var onSuccessCalled = false
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Valid Discovery")
            vm.onCollectorsChanged(setOf("icmp_sweep"))
            vm.addCidr("10.0.0.0/24")

            assertNotNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))

            vm.createSource { onSuccessCalled = true }
            advanceUntilIdle()

            assertTrue(onSuccessCalled)
            assertNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun failedSubmitKeepsDraft() =
        runTest {
            val failingRepo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Error(
                            code = "INTERNAL_ERROR",
                            message = "Database down",
                            requestId = "req-1",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(discoveryRepo = failingRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Failed Discovery")
            vm.onCollectorsChanged(setOf("icmp_sweep"))
            vm.addCidr("10.0.0.0/24")

            vm.createSource()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            assertNotNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun discardDraftRemovesIt() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Draft to discard")
            assertNotNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))

            vm.discardDraft()
            assertNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun revertingToPristineRemovesDraft() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Temporary Name")
            assertNotNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))

            vm.onNameChanged("")
            assertNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun selectingAndDeselectingProviderLeavesNoDraft() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val defaultCollectors = setOf("icmp_sweep", "arp_sweep", "mdns", "reverse_dns")
            vm.onCollectorsChanged(defaultCollectors + "proxmox")
            assertNotNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))

            vm.onCollectorsChanged(defaultCollectors)
            assertNull(formDraftStore.load(DraftForm.CreateDiscoverySource, CreateDiscoverySourceDraft.serializer()))
            vm.clear()
        }

    @Test
    fun freeCidrIsAddedInOrderAndInputCleared() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.addCidr("192.0.2.0/24")
            vm.addCidr(" 198.51.100.0/24 ")

            assertEquals(
                listOf("192.0.2.0/24", "198.51.100.0/24"),
                vm.state.value.targets
                    .map { it.cidr },
            )
            assertEquals("", vm.state.value.targetInput)
            assertTrue(
                vm.state.value.targets
                    .all { it.subnetId == null },
            )
            vm.clear()
        }

    @Test
    fun removingATargetKeepsTheOrderOfTheOthers() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.addCidr("10.0.1.0/24")
            vm.addCidr("10.0.2.0/24")
            vm.addCidr("10.0.3.0/24")
            vm.onRemoveTarget(1)

            assertEquals(
                listOf("10.0.1.0/24", "10.0.3.0/24"),
                vm.state.value.targets
                    .map { it.cidr },
            )
            vm.clear()
        }

    @Test
    fun togglingASubnetTwiceUnlinksIt() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()
            val subnet =
                vm.state.value.subnets
                    .single()

            vm.onSubnetToggled(subnet)
            assertEquals(listOf(DiscoveryTarget("10.10.0.0/24", "sub-10", "Office LAN")), vm.state.value.targets)

            vm.onSubnetToggled(subnet)
            assertTrue(
                vm.state.value.targets
                    .isEmpty(),
            )
            vm.clear()
        }

    @Test
    fun overlappingCidrIsRefusedWithTheOverlapError() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.addCidr("10.0.0.0/23")
            vm.addCidr("10.0.1.0/24")

            assertEquals(
                listOf("10.0.0.0/23"),
                vm.state.value.targets
                    .map { it.cidr },
            )
            assertEquals("10.0.1.0/24", vm.state.value.targetInput)
            assertEquals(
                TargetRuleViolation.Overlap.message,
                vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY],
            )
            vm.clear()
        }

    @Test
    fun subnetOverlappingAFreeTargetIsRefused() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.addCidr("10.10.0.0/16")
            vm.onSubnetToggled(
                vm.state.value.subnets
                    .single(),
            )

            assertEquals(
                listOf("10.10.0.0/16"),
                vm.state.value.targets
                    .map { it.cidr },
            )
            assertEquals(TargetRuleViolation.Overlap.message, vm.state.value.validationErrors[TARGETS_ERROR_KEY])
            vm.clear()
        }

    @Test
    fun blockLargerThanSlash16IsInvalid() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.addCidr("10.0.0.0/8")

            assertTrue(
                vm.state.value.targets
                    .isEmpty(),
            )
            assertEquals(TargetRuleViolation.Invalid.message, vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY])
            vm.clear()
        }

    @Test
    fun summedAddressLimitIsEnforcedBeforeSubmit() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.addCidr("10.1.0.0/16")
            assertEquals(65_536L, vm.state.value.targetsEditor.addressCount)

            vm.addCidr("10.2.0.0/24")

            assertEquals(
                listOf("10.1.0.0/16"),
                vm.state.value.targets
                    .map { it.cidr },
            )
            assertEquals(
                TargetRuleViolation.TooLarge.message,
                vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY],
            )
            vm.clear()
        }

    @Test
    fun aThirtyThirdTargetIsRefused() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            repeat(DiscoveryTargetRules.MAX_TARGETS) { vm.addCidr("10.0.$it.0/28") }
            vm.addCidr("10.0.200.0/28")

            assertEquals(DiscoveryTargetRules.MAX_TARGETS, vm.state.value.targets.size)
            assertEquals(TargetRuleViolation.TooMany.message, vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY])
            vm.clear()
        }

    @Test
    fun submitSendsTargetsInOrderWithSubnetLinks() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Multi target")
            vm.onCollectorsChanged(setOf("icmp_sweep"))
            vm.onSubnetToggled(
                vm.state.value.subnets
                    .single(),
            )
            vm.addCidr("192.0.2.0/24")
            vm.createSource()
            advanceUntilIdle()

            val config = assertNotNull(discoveryRepo.lastCreateSourceRequest?.config)
            assertNull(config.cidr)
            assertEquals(
                listOf(
                    DiscoveryTargetRequestDto("10.10.0.0/24", subnetId = "sub-10"),
                    DiscoveryTargetRequestDto("192.0.2.0/24"),
                ),
                config.targets,
            )
            vm.clear()
        }

    @Test
    fun providerOnlyPlanHidesTheEditorAndSendsNoTargets() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Docker homelab")
            vm.addCidr("192.0.2.0/24")
            vm.onCollectorsChanged(setOf("docker"))
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")

            assertFalse(vm.state.value.requiresCidr)
            vm.createSource()
            advanceUntilIdle()

            assertNotNull(discoveryRepo.lastCreateSourceRequest)
            assertNull(discoveryRepo.lastCreateSourceRequest?.config)
            vm.clear()
        }

    @Test
    fun backendTargetErrorCodesMapToLocalizedMessages() =
        runTest {
            TargetRuleViolation.entries.forEach { violation ->
                val repo =
                    FakeDiscoveryRepository(
                        createSourceResult =
                            ApiResult.Error(
                                code = violation.code,
                                message = "raw backend text",
                                requestId = "",
                                httpStatus = 422,
                            ),
                    )
                val vm = makeVm(discoveryRepo = repo, scope = this)
                advanceUntilIdle()
                vm.onNameChanged("Scanner")
                vm.addCidr("192.0.2.0/24")

                vm.createSource()
                advanceUntilIdle()

                assertEquals(violation.message, vm.state.value.errorMessage)
                assertEquals(violation.message, vm.state.value.validationErrors[TARGETS_ERROR_KEY])
                vm.clear()
            }
        }

    @Test
    fun draftWithTargetListSurvivesReload() =
        runTest {
            val first = makeVm(scope = this)
            advanceUntilIdle()
            first.onSubnetToggled(
                first.state.value.subnets
                    .single(),
            )
            first.addCidr("192.0.2.0/24")
            first.onTargetInputChanged("198.51.100.0/24")
            first.clear()

            val reloaded = makeVm(scope = this)
            advanceUntilIdle()

            assertEquals(
                listOf(DiscoveryTarget("10.10.0.0/24", "sub-10", "Office LAN"), DiscoveryTarget("192.0.2.0/24")),
                reloaded.state.value.targets,
            )
            assertEquals("198.51.100.0/24", reloaded.state.value.targetInput)
            assertTrue(reloaded.state.value.restoredFromDraft)
            reloaded.clear()
        }

    @Test
    fun legacyDraftWithSingleCidrIsRestoredAsOneTarget() =
        runTest {
            formDraftStore.save(
                DraftForm.CreateDiscoverySource,
                CreateDiscoverySourceDraft(name = "Old draft", configCidr = "10.0.0.0/24"),
                CreateDiscoverySourceDraft.serializer(),
            )

            val vm = makeVm(scope = this)
            advanceUntilIdle()

            assertEquals(listOf(DiscoveryTarget("10.0.0.0/24")), vm.state.value.targets)
            vm.clear()
        }
}

/** Types [cidr] into the targets editor and adds it, as the operator would. */
private fun CreateDiscoverySourceViewModel.addCidr(cidr: String) {
    onTargetInputChanged(cidr)
    onAddTargetCidr()
}
