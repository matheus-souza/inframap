package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.ProviderHealth
import com.inframap.frontend.domain.model.SourceCollector
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceByIdUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.TestDiscoverySourceHealthUseCase
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
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
            scope = testScope,
        )

    @Test
    fun loadsSourceSuccessfullyAndPopulatesState() =
        testScope.runTest {
            val vm = createViewModel()
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
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
            assertNotNull(vm.state.value.errorMessage)
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
}
