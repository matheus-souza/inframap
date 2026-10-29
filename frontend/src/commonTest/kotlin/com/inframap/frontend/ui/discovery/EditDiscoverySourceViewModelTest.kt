package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CollectorConfigDto
import com.inframap.frontend.data.dto.DiscoverySourceConfigDto
import com.inframap.frontend.data.dto.DiscoveryTargetRequestDto
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.model.SourceCollector
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceByIdUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.TestDiscoverySourceHealthUseCase
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditDiscoverySourceViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var fakeRepo: FakeDiscoveryRepository
    private lateinit var getSourceByIdUseCase: GetDiscoverySourceByIdUseCase
    private lateinit var updateDiscoverySourceUseCase: UpdateDiscoverySourceUseCase
    private lateinit var deleteDiscoverySourceUseCase: DeleteDiscoverySourceUseCase
    private lateinit var getDeletionImpactUseCase: GetDiscoverySourceDeletionImpactUseCase
    private lateinit var testDiscoverySourceHealthUseCase: TestDiscoverySourceHealthUseCase
    private val fakeSubnetRepo =
        FakeSubnetRepository(
            getSubnetsResult =
                ApiResult.Success(
                    PaginatedList(
                        items = listOf(Subnet(id = "sub-20", name = "Lab", cidr = "198.51.100.0/24")),
                        total = 1L,
                        page = 1,
                        perPage = 50,
                    ),
                    requestId = "",
                ),
        )

    private val sweepSource =
        DiscoverySource(
            id = "src-lan",
            name = "LAN sweep",
            sourceType = "icmp_sweep",
            collectors = listOf(SourceCollector(id = "col-icmp", collectorType = "icmp_sweep")),
            targets =
                listOf(
                    DiscoveryTarget("10.0.10.0/24", subnetId = "sub-old", subnetName = "Old", subnetDeleted = true),
                    DiscoveryTarget("192.0.2.0/24"),
                ),
        )

    private val testSource =
        DiscoverySource(
            id = "src-pve",
            name = "Proxmox Cluster",
            sourceType = "proxmox",
            enabled = true,
            scheduleCron = "0 */4 * * *",
            collectors =
                listOf(
                    SourceCollector(
                        id = "col-1",
                        collectorType = "proxmox",
                        enabled = true,
                        config =
                            mapOf(
                                "api_url" to "https://pve1.lab:8006",
                                "token_id" to "root@pam!token",
                            ),
                        configuredSecrets = listOf("token_secret"),
                    ),
                ),
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeDiscoveryRepository()
        fakeRepo.getSourceByIdResult = ApiResult.Success(testSource, requestId = "")
        fakeRepo.updateSourceResult = ApiResult.Success(testSource, requestId = "")
        getSourceByIdUseCase = GetDiscoverySourceByIdUseCase(fakeRepo)
        updateDiscoverySourceUseCase = UpdateDiscoverySourceUseCase(fakeRepo)
        deleteDiscoverySourceUseCase = DeleteDiscoverySourceUseCase(fakeRepo)
        getDeletionImpactUseCase = GetDiscoverySourceDeletionImpactUseCase(fakeRepo)
        testDiscoverySourceHealthUseCase = TestDiscoverySourceHealthUseCase(fakeRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(sourceId: String = "src-pve"): EditDiscoverySourceViewModel =
        EditDiscoverySourceViewModel(
            sourceId = sourceId,
            getSourceByIdUseCase = getSourceByIdUseCase,
            updateDiscoverySourceUseCase = updateDiscoverySourceUseCase,
            deleteDiscoverySourceUseCase = deleteDiscoverySourceUseCase,
            getDiscoverySourceDeletionImpactUseCase = getDeletionImpactUseCase,
            testDiscoverySourceHealthUseCase = testDiscoverySourceHealthUseCase,
            listSubnetsUseCase = ListSubnetsUseCase(fakeSubnetRepo),
            scope = testScope,
        )

    @Test
    fun loadsSourceSuccessfullyAndPopulatesState() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoading)
            assertNull(state.loadError)
            assertEquals("Proxmox Cluster", state.name)
            assertEquals("proxmox", state.sourceType)
            assertEquals("0 */4 * * *", state.scheduleCron)
            assertTrue(state.enabled)
            assertEquals(setOf("proxmox"), state.selectedCollectors)
            assertEquals("https://pve1.lab:8006", state.providerConfigs["proxmox"]?.get("api_url"))
            assertEquals(setOf("token_secret"), state.configuredSecrets["proxmox"])
        }

    @Test
    fun targetChangedWithoutSecretSetsWarning() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            // Change api_url
            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve2-attacker.lab:8006")
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.targetChangedWarnings["proxmox"] == true)
        }

    @Test
    fun targetChangedThenSecretFilledClearsWarning() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve2.lab:8006")
            advanceUntilIdle()
            assertTrue(vm.state.value.targetChangedWarnings["proxmox"] == true)

            vm.onProviderFieldChanged("proxmox", "token_secret", "new-secret-123")
            advanceUntilIdle()
            assertFalse(vm.state.value.targetChangedWarnings["proxmox"] == true)
        }

    @Test
    fun validationFailsOnBlankName() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onNameChanged("   ")
            assertFalse(vm.validate())
            assertNotNull(vm.state.value.validationErrors["name"])
        }

    @Test
    fun validationFailsWhenTargetChangedWithoutSecret() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onProviderFieldChanged("proxmox", "api_url", "https://different-host:8006")
            advanceUntilIdle()

            assertFalse(vm.validate())
            assertNotNull(vm.state.value.validationErrors["secret_proxmox"])
        }

    @Test
    fun submitSuccessUpdatesSource() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onNameChanged("Renamed Proxmox")
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertTrue(vm.state.value.isSuccess)
            assertEquals(1, fakeRepo.updateSourceCallCount)
            assertEquals("Renamed Proxmox", fakeRepo.lastUpdateSourceRequest?.name)
        }

    @Test
    fun submitHandlesSSRFErrorResponse() =
        testScope.runTest {
            fakeRepo.updateSourceResult =
                ApiResult.Error(
                    code = "secret_required_on_target_change",
                    message = "secret required",
                    requestId = "",
                    httpStatus = 422,
                )
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onSubmitClicked()
            advanceUntilIdle()

            assertFalse(vm.state.value.isSuccess)
            assertNotNull(vm.state.value.submitError)
            assertNull(vm.state.value.loadError)
            assertTrue(vm.state.value.targetChangedWarnings["proxmox"] == true)
        }

    @Test
    fun deleteSourceSuccessSetsIsDeleted() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onDeleteClicked()
            assertTrue(vm.state.value.showDeleteDialog)

            vm.onConfirmDeleteClicked()
            advanceUntilIdle()

            assertFalse(vm.state.value.showDeleteDialog)
            assertTrue(vm.state.value.isDeleted)
            assertEquals(1, fakeRepo.deleteSourceCallCount)
        }

    @Test
    fun testConnectionSucceedsUsingStoredServerSecret() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.testConnection("proxmox")
            advanceUntilIdle()

            assertEquals(1, fakeRepo.testSourceHealthCallCount)
            assertEquals("src-pve", fakeRepo.lastTestSourceHealthId)
            assertEquals("proxmox", fakeRepo.lastTestSourceHealthProviderId)
            assertNull(fakeRepo.lastUpdateSourceRequest)
            assertEquals(ConnectionTest.Healthy, vm.state.value.connectionTests["proxmox"])
        }

    @Test
    fun testConnectionBlockedWhenTargetHasPendingChanges() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onProviderFieldChanged("proxmox", "api_url", "https://new-host.lab:8006")
            advanceUntilIdle()

            assertTrue(vm.state.value.hasPendingTargetOrSecretChanges("proxmox"))

            vm.testConnection("proxmox")
            advanceUntilIdle()

            assertEquals(0, fakeRepo.testSourceHealthCallCount)
            assertNull(vm.state.value.connectionTests["proxmox"])
        }

    @Test
    fun testConnectionBlockedWhenSecretHasPendingChanges() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onProviderFieldChanged("proxmox", "token_secret", "some-new-password")
            advanceUntilIdle()

            assertTrue(vm.state.value.hasPendingTargetOrSecretChanges("proxmox"))

            vm.testConnection("proxmox")
            advanceUntilIdle()

            assertEquals(0, fakeRepo.testSourceHealthCallCount)
            assertNull(vm.state.value.connectionTests["proxmox"])
        }

    @Test
    fun testConnectionFailureUpdatesStateWithErrorMessage() =
        testScope.runTest {
            fakeRepo.testSourceHealthResult =
                ApiResult.Success(
                    ProviderHealth("proxmox", isHealthy = false, message = "Connection refused"),
                    requestId = "",
                )
            val vm = createViewModel()
            advanceUntilIdle()

            vm.testConnection("proxmox")
            advanceUntilIdle()

            val testResult = vm.state.value.connectionTests["proxmox"]
            assertTrue(testResult is ConnectionTest.Failed)
        }

    @Test
    fun onDeleteClickedFetchesDeletionImpactAndOpensDialog() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onDeleteClicked()
            assertTrue(vm.state.value.showDeleteDialog)
            assertTrue(vm.state.value.isLoadingDeleteImpact)

            advanceUntilIdle()

            assertFalse(vm.state.value.isLoadingDeleteImpact)
            assertEquals(1, fakeRepo.getDeletionImpactCallCount)
            assertEquals("src-pve", fakeRepo.lastGetDeletionImpactId)
            assertNotNull(vm.state.value.deleteImpact)
            assertEquals(
                2,
                vm.state.value.deleteImpact
                    ?.collectorsHalted,
            )
            assertEquals(
                5,
                vm.state.value.deleteImpact
                    ?.devicesUnlinked,
            )
        }

    @Test
    fun onConfirmDeleteClickedExecutesDeletionAndSetsIsDeleted() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onDeleteClicked()
            advanceUntilIdle()

            vm.onConfirmDeleteClicked()
            advanceUntilIdle()

            assertEquals(1, fakeRepo.deleteSourceCallCount)
            assertFalse(vm.state.value.isDeleting)
            assertFalse(vm.state.value.showDeleteDialog)
            assertNull(vm.state.value.deleteImpact)
            assertTrue(vm.state.value.isDeleted)
            assertNull(vm.state.value.deleteError)
        }

    @Test
    fun onConfirmDeleteClickedHandlesError() =
        testScope.runTest {
            fakeRepo.deleteSourceResult =
                ApiResult.Error(
                    code = "DB_ERROR",
                    message = "Failed to soft-delete",
                    requestId = "",
                    httpStatus = 500,
                )
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onDeleteClicked()
            advanceUntilIdle()

            vm.onConfirmDeleteClicked()
            advanceUntilIdle()

            assertEquals(1, fakeRepo.deleteSourceCallCount)
            assertFalse(vm.state.value.isDeleting)
            assertFalse(vm.state.value.isDeleted)
            assertNotNull(vm.state.value.deleteError)
        }

    @Test
    fun onDismissDeleteDialogClearsState() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.onDeleteClicked()
            advanceUntilIdle()
            assertTrue(vm.state.value.showDeleteDialog)
            assertNotNull(vm.state.value.deleteImpact)

            vm.onDismissDeleteDialog()
            assertFalse(vm.state.value.showDeleteDialog)
            assertNull(vm.state.value.deleteImpact)
            assertFalse(vm.state.value.isLoadingDeleteImpact)
        }

    private fun loadSweepSource(): EditDiscoverySourceViewModel {
        fakeRepo.getSourceByIdResult = ApiResult.Success(sweepSource, requestId = "")
        return createViewModel(sourceId = "src-lan")
    }

    @Test
    fun targetsArePrefilledAndADeletedSubnetIsFlagged() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.requiresCidr)
            assertEquals(sweepSource.targets, state.targets)
            assertTrue(state.targets.first().subnetDeleted)
            assertEquals(listOf("sub-20"), state.subnets.map { it.id })
            assertFalse(state.targetsChanged)
            vm.clear()
        }

    @Test
    fun unchangedTargetsAreNotSentOnUpdate() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.onNameChanged("Renamed")
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertTrue(vm.state.value.isSuccess)
            assertEquals("Renamed", fakeRepo.lastUpdateSourceRequest?.name)
            assertNull(fakeRepo.lastUpdateSourceRequest?.config)
            vm.clear()
        }

    @Test
    fun removingTheDeletedSubnetTargetSendsTheRemainingList() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.onRemoveTarget(0)
            vm.onSubnetToggled(
                vm.state.value.subnets
                    .single(),
            )
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(
                DiscoverySourceConfigDto(
                    targets =
                        listOf(
                            DiscoveryTargetRequestDto("192.0.2.0/24"),
                            DiscoveryTargetRequestDto("198.51.100.0/24", subnetId = "sub-20"),
                        ),
                ),
                fakeRepo.lastUpdateSourceRequest?.config,
            )
            vm.clear()
        }

    @Test
    fun changedListStillHoldingADeletedSubnetIsBlocked() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()
            fakeRepo.lastUpdateSourceRequest = null

            vm.onTargetInputChanged("203.0.113.0/24")
            vm.onAddTargetCidr()
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(
                TargetRuleViolation.SubnetNotFound.message,
                vm.state.value.validationErrors[TARGETS_ERROR_KEY],
            )
            assertNull(fakeRepo.lastUpdateSourceRequest)
            vm.clear()
        }

    @Test
    fun editorRejectsOverlapAndInvalidInput() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.onTargetInputChanged("192.0.2.128/25")
            vm.onAddTargetCidr()
            assertEquals(TargetRuleViolation.Overlap.message, vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY])

            vm.onTargetInputChanged("999.0.0.0/24")
            assertNull(vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY])
            vm.onAddTargetCidr()
            assertEquals(TargetRuleViolation.Invalid.message, vm.state.value.validationErrors[TARGET_INPUT_ERROR_KEY])
            assertEquals(2, vm.state.value.targets.size)
            vm.clear()
        }

    @Test
    fun togglingALinkedSubnetUnlinksIt() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()
            val subnet =
                vm.state.value.subnets
                    .single()

            vm.onSubnetToggled(subnet)
            assertTrue(
                vm.state.value.targets
                    .any { it.subnetId == "sub-20" },
            )
            vm.onSubnetToggled(subnet)
            assertFalse(
                vm.state.value.targets
                    .any { it.subnetId == "sub-20" },
            )
            assertFalse(vm.state.value.targetsChanged)
            vm.clear()
        }

    @Test
    fun providerOnlySourceSendsNoTargets() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            assertFalse(vm.state.value.requiresCidr)
            vm.onNameChanged("Proxmox renamed")
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertNull(fakeRepo.lastUpdateSourceRequest?.config)
            vm.clear()
        }

    @Test
    fun backendTargetErrorCodeIsLocalized() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()
            fakeRepo.updateSourceResult =
                ApiResult.Error(
                    code = "error_targets_overlap",
                    message = "raw",
                    requestId = "",
                    httpStatus = 422,
                )

            vm.onRemoveTarget(0)
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(TargetRuleViolation.Overlap.message, vm.state.value.validationErrors[TARGETS_ERROR_KEY])
            assertNull(vm.state.value.submitError)
            assertNull(vm.state.value.loadError)
            vm.clear()
        }

    @Test
    fun saveRejectedForADeletedSubnetStaysOnTheTargetsField() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()
            fakeRepo.updateSourceResult =
                ApiResult.Error(
                    code = "error_target_subnet_not_found",
                    message = "raw",
                    requestId = "",
                    httpStatus = 422,
                )

            vm.onSubnetToggled(SubnetSummary(id = "sub-20", name = "Lab", cidr = "198.51.100.0/24"))
            vm.onRemoveTarget(0)
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(1, fakeRepo.updateSourceCallCount)
            assertEquals(
                TargetRuleViolation.SubnetNotFound.message,
                vm.state.value.validationErrors[TARGETS_ERROR_KEY],
            )
            assertNull(vm.state.value.loadError)
            assertNull(vm.state.value.submitError)
            assertFalse(vm.state.value.isSubmitting)
            vm.clear()
        }

    @Test
    fun saveFailureIsASubmitErrorNotALoadError() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()
            fakeRepo.updateSourceResult =
                ApiResult.Error(code = "INTERNAL", message = "", requestId = "", httpStatus = 500)

            vm.onSubmitClicked()
            advanceUntilIdle()

            assertNotNull(vm.state.value.submitError)
            assertNull(vm.state.value.loadError)

            fakeRepo.updateSourceResult = ApiResult.NetworkError(RuntimeException("down"))
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertNotNull(vm.state.value.submitError)
            assertNull(vm.state.value.loadError)
            vm.clear()
        }

    @Test
    fun loadFailureIsALoadErrorThatRetryClears() =
        testScope.runTest {
            fakeRepo.getSourceByIdResult = ApiResult.NetworkError(RuntimeException("down"))
            val vm = createViewModel()
            advanceUntilIdle()

            assertNotNull(vm.state.value.loadError)
            assertNull(vm.state.value.submitError)

            fakeRepo.getSourceByIdResult = ApiResult.Success(testSource, requestId = "")
            vm.loadSource()
            advanceUntilIdle()

            assertNull(vm.state.value.loadError)
            assertEquals("Proxmox Cluster", vm.state.value.name)
            vm.clear()
        }

    @Test
    fun subnetLoadFailureLeavesChipsEmpty() =
        testScope.runTest {
            fakeSubnetRepo.getSubnetsResult = ApiResult.NetworkError(RuntimeException("down"))
            val vm = loadSweepSource()
            advanceUntilIdle()

            assertTrue(
                vm.state.value.subnets
                    .isEmpty(),
            )
            assertFalse(vm.state.value.isLoadingSubnets)
            vm.clear()
        }

    /** A plan mixing a sweep with a provider whose token is stored on the server. */
    private val mixedSource =
        sweepSource.copy(
            id = "src-mixed",
            name = "LAN and cluster",
            targets = listOf(DiscoveryTarget("192.0.2.0/24")),
            collectors =
                listOf(
                    SourceCollector(id = "col-icmp", collectorType = "icmp_sweep"),
                    SourceCollector(id = "col-arp", collectorType = "arp_sweep"),
                    SourceCollector(
                        id = "col-pve",
                        collectorType = "proxmox",
                        config =
                            mapOf(
                                "api_url" to "https://pve1.lab:8006",
                                "token_id" to "root@pam!token",
                                "tls_verify" to "true",
                            ),
                        configuredSecrets = listOf("token_secret"),
                    ),
                ),
        )

    private fun loadMixedSource(): EditDiscoverySourceViewModel {
        fakeRepo.getSourceByIdResult = ApiResult.Success(mixedSource, requestId = "")
        return createViewModel(sourceId = "src-mixed")
    }

    private fun sentCollectors(): List<CollectorConfigDto> = fakeRepo.lastUpdateSourceRequest?.collectors.orEmpty()

    @Test
    fun loadSelectsTheCurrentCollectors() =
        testScope.runTest {
            val vm = loadMixedSource()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(setOf("icmp_sweep", "arp_sweep", "proxmox"), state.selectedCollectors)
            assertEquals(state.selectedCollectors, state.initialCollectors)
            assertEquals(listOf("proxmox"), state.selectedProviders)
            assertTrue(state.addedProviders.isEmpty())
            assertTrue(state.requiresCidr)
            vm.clear()
        }

    @Test
    fun theRequestCarriesTheWholeCollectorList() =
        testScope.runTest {
            val vm = loadMixedSource()
            advanceUntilIdle()

            vm.onSubmitClicked()
            advanceUntilIdle()

            assertTrue(vm.state.value.isSuccess)
            assertEquals(setOf("icmp_sweep", "arp_sweep", "proxmox"), sentCollectors().map { it.type }.toSet())
            // The stored token is not resent: a blank secret of an unchanged provider is kept.
            val proxmox = sentCollectors().single { it.type == "proxmox" }
            assertEquals("https://pve1.lab:8006", proxmox.config?.get("api_url"))
            assertNull(proxmox.config?.get("token_secret"))
            assertNull(fakeRepo.lastUpdateSourceRequest?.config)
            vm.clear()
        }

    @Test
    fun switchingTheSweepsOffHidesTargetsAndSavesWithoutThem() =
        testScope.runTest {
            val vm = loadMixedSource()
            advanceUntilIdle()

            vm.toggleCollector("icmp_sweep")
            vm.toggleCollector("arp_sweep")

            assertFalse(vm.state.value.requiresCidr)
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertTrue(vm.state.value.isSuccess)
            assertEquals(listOf("proxmox"), sentCollectors().map { it.type })
            // The stored targets are cleared rather than kept hidden behind a plan that scans nothing.
            assertEquals(DiscoverySourceConfigDto(targets = emptyList()), fakeRepo.lastUpdateSourceRequest?.config)
            vm.clear()
        }

    @Test
    fun removingACollectorLeavesItOutOfThePlan() =
        testScope.runTest {
            val vm = loadMixedSource()
            advanceUntilIdle()

            vm.toggleCollector("arp_sweep")
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(listOf("icmp_sweep", "proxmox"), sentCollectors().map { it.type }.sorted())
            // The sweep that remains still scans the unchanged targets, which are kept as stored.
            assertNull(fakeRepo.lastUpdateSourceRequest?.config)
            vm.clear()
        }

    @Test
    fun theLastCollectorCannotBeRemoved() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.toggleCollector("icmp_sweep")
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertNotNull(vm.state.value.validationErrors[COLLECTORS_ERROR_KEY])
            assertEquals(0, fakeRepo.updateSourceCallCount)

            vm.toggleCollector("mdns")
            assertNull(vm.state.value.validationErrors[COLLECTORS_ERROR_KEY])
            vm.clear()
        }

    @Test
    fun anAddedProviderRequiresItsFieldsIncludingTheSecret() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.toggleCollector("proxmox")
            val added = vm.state.value
            assertEquals(listOf("proxmox"), added.addedProviders)
            assertEquals("proxmox", added.currentProviderTab)
            assertEquals("true", added.providerConfigs["proxmox"]?.get("tls_verify"))

            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve2.lab:8006")
            vm.onProviderFieldChanged("proxmox", "token_id", "root@pam!inframap")
            vm.onSubmitClicked()
            advanceUntilIdle()

            // Nothing is stored for it, so a blank secret is a missing field, not a target change.
            assertNotNull(vm.state.value.validationErrors[ProviderForms.labelKey("proxmox")])
            assertNull(vm.state.value.validationErrors["secret_proxmox"])
            assertFalse(vm.state.value.targetChangedWarnings["proxmox"] == true)
            assertEquals(0, fakeRepo.updateSourceCallCount)

            vm.onProviderFieldChanged("proxmox", "token_secret", "synthetic-token")
            assertNull(vm.state.value.validationErrors[ProviderForms.labelKey("proxmox")])
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertTrue(vm.state.value.isSuccess)
            assertEquals(
                CollectorConfigDto(
                    type = "proxmox",
                    config =
                        mapOf(
                            "tls_verify" to "true",
                            "api_url" to "https://pve2.lab:8006",
                            "token_id" to "root@pam!inframap",
                            "token_secret" to "synthetic-token",
                        ),
                ),
                sentCollectors().single { it.type == "proxmox" },
            )
            vm.clear()
        }

    @Test
    fun anAddedDockerNeedsAnEndpoint() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.onCollectorsChanged(setOf("icmp_sweep", "docker"))
            assertFalse(vm.validate())
            assertNotNull(vm.state.value.validationErrors[ProviderForms.labelKey("docker")])

            vm.onProviderFieldChanged("docker", "tcp_url", "tcp://192.0.2.10:2376")
            assertTrue(vm.validate())
            vm.clear()
        }

    @Test
    fun anAddedProviderCannotBeTestedBeforeSaving() =
        testScope.runTest {
            val vm = loadSweepSource()
            advanceUntilIdle()

            vm.toggleCollector("docker")
            assertTrue(vm.state.value.hasPendingTargetOrSecretChanges("docker"))
            vm.testConnection("docker")
            advanceUntilIdle()

            assertEquals(0, fakeRepo.testSourceHealthCallCount)
            vm.clear()
        }

    @Test
    fun removingAProviderDropsItsErrors() =
        testScope.runTest {
            val vm = loadMixedSource()
            advanceUntilIdle()

            vm.onProviderFieldChanged("proxmox", "api_url", "https://pve9.lab:8006")
            vm.toggleCollector("docker")
            assertFalse(vm.validate())
            assertNotNull(vm.state.value.validationErrors["secret_proxmox"])
            assertNotNull(vm.state.value.validationErrors[ProviderForms.labelKey("docker")])
            assertEquals("proxmox", vm.state.value.currentProviderTab)

            vm.toggleCollector("proxmox")
            vm.toggleCollector("docker")

            val state = vm.state.value
            assertNull(state.validationErrors["secret_proxmox"])
            assertNull(state.validationErrors[ProviderForms.labelKey("docker")])
            assertFalse(state.targetChangedWarnings["proxmox"] == true)
            assertTrue(vm.validate())
            vm.clear()
        }

    @Test
    fun reAddingALoadedProviderKeepsItsStoredSecret() =
        testScope.runTest {
            val vm = loadMixedSource()
            advanceUntilIdle()

            vm.toggleCollector("proxmox")
            vm.toggleCollector("proxmox")

            val state = vm.state.value
            assertTrue(state.addedProviders.isEmpty())
            assertEquals("https://pve1.lab:8006", state.providerConfigs["proxmox"]?.get("api_url"))
            assertTrue(vm.validate())
            vm.clear()
        }

    @Test
    fun aFirstSweepNeedsATarget() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.toggleCollector("icmp_sweep")
            assertTrue(vm.state.value.requiresCidr)
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(TargetRuleViolation.Required.message, vm.state.value.validationErrors[TARGETS_ERROR_KEY])
            assertEquals(0, fakeRepo.updateSourceCallCount)

            vm.onTargetInputChanged("198.51.100.0/24")
            vm.onAddTargetCidr()
            vm.onSubmitClicked()
            advanceUntilIdle()

            assertEquals(
                DiscoverySourceConfigDto(targets = listOf(DiscoveryTargetRequestDto("198.51.100.0/24"))),
                fakeRepo.lastUpdateSourceRequest?.config,
            )
            assertEquals(setOf("proxmox", "icmp_sweep"), sentCollectors().map { it.type }.toSet())
            vm.clear()
        }

    @Test
    fun aRefusedTargetChangeFlagsOnlyProvidersWithAStoredSecret() =
        testScope.runTest {
            fakeRepo.updateSourceResult =
                ApiResult.Error(
                    code = "secret_required_on_target_change",
                    message = "secret required",
                    requestId = "",
                    httpStatus = 422,
                )
            val vm = loadMixedSource()
            advanceUntilIdle()

            vm.toggleCollector("docker")
            vm.onProviderFieldChanged("docker", "socket_path", "unix:///var/run/docker.sock")
            vm.onSubmitClicked()
            advanceUntilIdle()

            val state = vm.state.value
            assertNotNull(state.submitError)
            assertTrue(state.targetChangedWarnings["proxmox"] == true)
            assertFalse(state.targetChangedWarnings["docker"] == true)
            vm.clear()
        }
}
