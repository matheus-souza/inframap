package com.inframap.frontend.ui.subnets

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.storage.draft.DRAFT_TTL_MS
import com.inframap.frontend.data.storage.draft.DraftForm
import com.inframap.frontend.data.storage.draft.FormDraftStore
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeLocalStorage
import com.inframap.frontend.fakes.FakeNetworkRepository
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
class CreateSubnetViewModelTest {
    private val fakeStorage = FakeLocalStorage()
    private val fakeClock = FakeEpochClock(now = 1_000_000L)
    private val sessionOwner = SessionOwnerStore(fakeStorage).apply { setOwner("u1") }
    private val formDraftStore = FormDraftStore(fakeStorage, fakeClock, sessionOwner)

    private val sampleCreatedSubnet =
        Subnet(
            id = "sub1",
            name = "Management",
            cidr = "192.168.1.0/24",
            vlanId = 10,
            gatewayIp = "192.168.1.1",
            discoveryEnabled = true,
        )

    private val sampleInterface =
        NetworkInterface(
            name = "eth0",
            ip = "192.168.18.5",
            cidr = "192.168.18.0/24",
            mac = "aa:bb:cc:dd:ee:ff",
            gateway = "192.168.18.1",
        )

    private val otherInterface =
        NetworkInterface(
            name = "wlan0",
            ip = "10.0.0.15",
            cidr = "10.0.0.0/16",
            mac = "11:22:33:44:55:66",
            gateway = "10.0.0.1",
        )

    private fun makeVm(
        subnetRepo: FakeSubnetRepository =
            FakeSubnetRepository(
                createSubnetResult = ApiResult.Success(sampleCreatedSubnet, requestId = ""),
            ),
        networkRepo: FakeNetworkRepository =
            FakeNetworkRepository(
                getInterfacesResult = ApiResult.Success(listOf(sampleInterface), requestId = ""),
            ),
        formDrafts: FormDraftStore = formDraftStore,
        prefilledCidr: String? = null,
        prefilledName: String? = null,
        scope: CoroutineScope? = null,
    ) = CreateSubnetViewModel(
        CreateSubnetUseCase(subnetRepo),
        GetNetworkInterfacesUseCase(networkRepo),
        formDrafts,
        prefilledCidr,
        prefilledName,
        scope,
    )

    @Test
    fun validationFailsOnEmptyFields() =
        runTest {
            val vm = makeVm(scope = this)

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("name"))
            assertTrue(errors.containsKey("cidr"))
            vm.clear()
        }

    @Test
    fun validationFailsOnInvalidCidrAndVlan() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("Servers")
            vm.onCidrChanged("invalid-cidr")
            vm.onVlanIdChanged("99999")
            vm.onGatewayIpChanged("bad-ip")

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("cidr"))
            assertTrue(errors.containsKey("vlan_id"))
            assertTrue(errors.containsKey("gateway_ip"))
            vm.clear()
        }

    @Test
    fun createSubnetWorkflowCompletesSuccessfully() =
        runTest {
            var onSuccessCalled = false
            val vm = makeVm(scope = this)

            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")
            vm.onVlanIdChanged("10")
            vm.onGatewayIpChanged("192.168.1.1")
            vm.onDescriptionChanged("Core mgmt subnet")
            vm.onDiscoveryEnabledChanged(true)

            vm.state.test {
                skipItems(1)
                vm.createSubnet { onSuccessCalled = true }
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                assertNull(state.errorMessage)
                assertTrue(onSuccessCalled)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSubnetIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")

            vm.createSubnet()
            assertTrue(vm.state.value.isSubmitting)

            vm.createSubnet()
            assertTrue(vm.state.value.isSubmitting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun createSubnetHandlesApiError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    createSubnetResult =
                        ApiResult.Error(
                            code = "DUPLICATE_CIDR",
                            message = "Subnet CIDR already registered",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(subnetRepo = repo, scope = this)
            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")

            vm.state.test {
                skipItems(1)
                vm.createSubnet()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.isSubmitting)
                assertFalse(state.isSuccess)
                assertEquals("Subnet CIDR already registered", state.errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun prefilledDataPopulatesInitialState() =
        runTest {
            val vm =
                makeVm(
                    prefilledCidr = "10.0.0.0/8",
                    prefilledName = "wlan0",
                    scope = this,
                )

            assertEquals("10.0.0.0/8", vm.state.value.cidr)
            assertEquals("wlan0", vm.state.value.name)
            vm.clear()
        }

    @Test
    fun onInterfaceSelectedFillsCidrAndName() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onInterfaceSelected(sampleInterface)

            val state = vm.state.value
            assertEquals("192.168.18.0/24", state.cidr)
            assertEquals("eth0", state.name)
            assertEquals("192.168.18.1", state.gatewayIp)
            assertTrue(state.showInterfaceSuggestions)
            assertEquals(sampleInterface, state.selectedInterface)
            vm.clear()
        }

    @Test
    fun selectingSelectedInterfaceAgainClearsHighlightButKeepsFields() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onInterfaceSelected(sampleInterface)
            val state = vm.state.value
            assertNull(state.selectedInterface)
            assertEquals("192.168.18.0/24", state.cidr)
            assertEquals("eth0", state.name)
            assertEquals("192.168.18.1", state.gatewayIp)
            assertTrue(state.showInterfaceSuggestions)
            vm.clear()
        }

    @Test
    fun selectingAnotherInterfaceMovesHighlightAndRefills() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onInterfaceSelected(otherInterface)
            val state = vm.state.value
            assertEquals(otherInterface, state.selectedInterface)
            assertEquals("10.0.0.0/16", state.cidr)
            assertEquals("wlan0", state.name)
            assertEquals("10.0.0.1", state.gatewayIp)
            assertTrue(state.showInterfaceSuggestions)
            vm.clear()
        }

    @Test
    fun reselectingAfterToggleOffHighlightsAgain() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onInterfaceSelected(sampleInterface)
            assertNull(vm.state.value.selectedInterface)

            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun togglingOffKeepsValidationErrors() =
        runTest {
            val vm = makeVm(scope = this)
            val ifaceWithoutGateway =
                NetworkInterface(
                    name = "lo",
                    ip = "127.0.0.1",
                    cidr = "127.0.0.0/8",
                    mac = "00:00:00:00:00:00",
                    gateway = "",
                )
            vm.onGatewayIpChanged("bad-ip")
            vm.onVlanIdChanged("invalid-vlan")
            vm.validate()
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("vlan_id"),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("gateway_ip"),
            )

            // An interface without gateway does not clear gateway_ip on first selection
            vm.onInterfaceSelected(ifaceWithoutGateway)
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("vlan_id"),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("gateway_ip"),
            )

            // Toggle off preserves errors without re-running any clearance
            vm.onInterfaceSelected(ifaceWithoutGateway)
            assertNull(vm.state.value.selectedInterface)
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("vlan_id"),
            )
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("gateway_ip"),
            )
            vm.clear()
        }

    @Test
    fun toggleSuggestionsFlipsVisibility() =
        runTest {
            val vm = makeVm(scope = this)

            // The panel starts open: a suggestion block that opens closed is a button nobody
            // presses, and the values it offers are the reason the screen has the section.
            assertTrue(vm.state.value.showInterfaceSuggestions)
            vm.toggleSuggestions()
            assertFalse(vm.state.value.showInterfaceSuggestions)
            vm.toggleSuggestions()
            assertTrue(vm.state.value.showInterfaceSuggestions)
            vm.clear()
        }

    @Test
    fun loadNetworkInterfacesPopulatesDetectedInterfaces() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(1, state.detectedInterfaces.size)
            assertEquals("eth0", state.detectedInterfaces.first().name)
            vm.clear()
        }

    @Test
    fun loadNetworkInterfacesSilentlyIgnoresError() =
        runTest {
            val networkRepo =
                FakeNetworkRepository(
                    getInterfacesResult = ApiResult.NetworkError(RuntimeException("no network")),
                )
            val vm = makeVm(networkRepo = networkRepo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.detectedInterfaces.isEmpty())
            assertNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun onInterfaceSelectedClearsGatewayValidationError() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("Servers")
            vm.onCidrChanged("10.0.0.0/24")
            vm.onGatewayIpChanged("bad-ip")
            vm.validate()
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("gateway_ip"),
            )

            vm.onInterfaceSelected(sampleInterface)

            assertFalse(
                vm.state.value.validationErrors
                    .containsKey("gateway_ip"),
            )
            vm.clear()
        }

    @Test
    fun onInterfaceSelectedDoesNotOverwriteGatewayWhenEmpty() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onGatewayIpChanged("10.0.0.1")

            val ifaceWithoutGateway =
                NetworkInterface(
                    name = "lo",
                    ip = "127.0.0.1",
                    cidr = "127.0.0.0/8",
                    mac = "00:00:00:00:00:00",
                    gateway = "",
                )

            vm.onInterfaceSelected(ifaceWithoutGateway)

            assertEquals("10.0.0.1", vm.state.value.gatewayIp)
            vm.clear()
        }

    @Test
    fun editingCidrAwayFromSelectedInterfaceClearsHighlight() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onCidrChanged("192.168.18.0/25")
            assertNull(vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun editingNameAwayFromSelectedInterfaceClearsHighlight() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onNameChanged("eth1")
            assertNull(vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun editingGatewayAwayClearsHighlightWhenInterfaceHasGateway() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onGatewayIpChanged("192.168.18.254")
            assertNull(vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun editingGatewayKeepsHighlightWhenInterfaceHasNoGateway() =
        runTest {
            val vm = makeVm(scope = this)
            val ifaceWithoutGateway =
                NetworkInterface(
                    name = "eth0",
                    ip = "192.168.18.5",
                    cidr = "192.168.18.0/24",
                    mac = "aa:bb:cc:dd:ee:ff",
                    gateway = "",
                )
            vm.onInterfaceSelected(ifaceWithoutGateway)
            assertEquals(ifaceWithoutGateway, vm.state.value.selectedInterface)

            vm.onGatewayIpChanged("192.168.18.1")
            assertEquals(ifaceWithoutGateway, vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun editingVlanDescriptionOrDiscoveryKeepsHighlight() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onVlanIdChanged("20")
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onDescriptionChanged("Custom description")
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onDiscoveryEnabledChanged(false)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun whitespaceOnlyEditKeepsHighlight() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onCidrChanged("  192.168.18.0/24  ")
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onNameChanged(" eth0 ")
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onGatewayIpChanged(" 192.168.18.1 ")
            assertEquals(sampleInterface, vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun typingMatchingValuesBackDoesNotReselect() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onCidrChanged("10.0.0.0/8")
            assertNull(vm.state.value.selectedInterface)

            vm.onCidrChanged(sampleInterface.cidr)
            assertNull(vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun rewritingSameValueKeepsHighlight() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.onNameChanged("eth0")
            assertEquals(sampleInterface, vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun toggleSuggestionsDoesNotAffectSelection() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onInterfaceSelected(sampleInterface)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.toggleSuggestions()
            assertFalse(vm.state.value.showInterfaceSuggestions)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)

            vm.toggleSuggestions()
            assertTrue(vm.state.value.showInterfaceSuggestions)
            assertEquals(sampleInterface, vm.state.value.selectedInterface)
            vm.clear()
        }

    @Test
    fun editingPersistsDraftImmediately() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("My Edited Subnet")

            val loaded = formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer())
            assertNotNull(loaded)
            assertEquals("My Edited Subnet", loaded.name)
            vm.clear()
        }

    @Test
    fun everyFieldMutatorPersists() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("VLAN 20")
            vm.onCidrChanged("10.0.20.0/24")
            vm.onVlanIdChanged("20")
            vm.onGatewayIpChanged("10.0.20.1")
            vm.onDescriptionChanged("Floor 2")
            vm.onDiscoveryEnabledChanged(false)
            vm.onInterfaceSelected(sampleInterface)

            val loaded = formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer())
            assertNotNull(loaded)
            assertEquals("eth0", loaded.name)
            assertEquals("192.168.18.0/24", loaded.cidr)
            assertEquals("20", loaded.vlanId)
            assertEquals("192.168.18.1", loaded.gatewayIp)
            assertEquals("Floor 2", loaded.description)
            assertFalse(loaded.discoveryEnabled)
            vm.clear()
        }

    @Test
    fun toggleSuggestionsDoesNotPersist() =
        runTest {
            val vm = makeVm(scope = this)
            vm.toggleSuggestions()

            assertNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }

    @Test
    fun untouchedFormCreatesNoDraft() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            assertNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }

    @Test
    fun revertingToPristineRemovesDraft() =
        runTest {
            val vm = makeVm(prefilledCidr = "192.168.1.0/24", prefilledName = "Default", scope = this)

            vm.onNameChanged("Modified")
            assertNotNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))

            vm.onNameChanged("Default")
            assertNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }

    @Test
    fun initRestoresDraftAndFlagsRestored() =
        runTest {
            val draft =
                CreateSubnetDraft(
                    name = "Saved Draft",
                    cidr = "10.10.0.0/16",
                    vlanId = "10",
                    gatewayIp = "10.10.0.1",
                    description = "Restored notes",
                    discoveryEnabled = false,
                )
            formDraftStore.save(DraftForm.CreateSubnet, draft, CreateSubnetDraft.serializer())

            val vm = makeVm(scope = this)

            val state = vm.state.value
            assertEquals("Saved Draft", state.name)
            assertEquals("10.10.0.0/16", state.cidr)
            assertEquals("10", state.vlanId)
            assertEquals("10.10.0.1", state.gatewayIp)
            assertEquals("Restored notes", state.description)
            assertFalse(state.discoveryEnabled)
            assertTrue(state.restoredFromDraft)
            vm.clear()
        }

    @Test
    fun restoreDoesNotRefreshTimestamp() =
        runTest {
            fakeClock.now = 100_000L
            val draft = CreateSubnetDraft(name = "Original Draft")
            formDraftStore.save(DraftForm.CreateSubnet, draft, CreateSubnetDraft.serializer())

            fakeClock.now = 200_000L
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val raw = fakeStorage.get(DraftForm.CreateSubnet.storageKey)
            assertNotNull(raw)
            assertTrue(raw.contains("\"saved_at_ms\":100000"))
            vm.clear()
        }

    @Test
    fun expiredDraftIsNotRestored() =
        runTest {
            fakeClock.now = 1_000_000L
            val draft = CreateSubnetDraft(name = "Old Draft")
            formDraftStore.save(DraftForm.CreateSubnet, draft, CreateSubnetDraft.serializer())

            fakeClock.advanceBy(DRAFT_TTL_MS + 1)
            val vm = makeVm(scope = this)

            assertEquals("", vm.state.value.name)
            assertFalse(vm.state.value.restoredFromDraft)
            assertNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }

    @Test
    fun draftFromOtherPrefillIsIgnoredButKept() =
        runTest {
            val draft =
                CreateSubnetDraft(
                    name = "Interface eth0",
                    cidr = "192.168.1.0/24",
                    prefilledCidr = "192.168.1.0/24",
                    prefilledName = "eth0",
                )
            formDraftStore.save(DraftForm.CreateSubnet, draft, CreateSubnetDraft.serializer())

            // Operator navigates with different prefill: eth1
            val vm = makeVm(prefilledCidr = "10.0.0.0/24", prefilledName = "eth1", scope = this)

            assertEquals("eth1", vm.state.value.name)
            assertEquals("10.0.0.0/24", vm.state.value.cidr)
            assertFalse(vm.state.value.restoredFromDraft)
            // Draft kept for eth0
            assertNotNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }

    @Test
    fun draftWithMatchingPrefillWinsOverPrefill() =
        runTest {
            val draft =
                CreateSubnetDraft(
                    name = "Customized eth0",
                    cidr = "192.168.1.0/24",
                    description = "Customized during session",
                    prefilledCidr = "192.168.1.0/24",
                    prefilledName = "eth0",
                )
            formDraftStore.save(DraftForm.CreateSubnet, draft, CreateSubnetDraft.serializer())

            val vm = makeVm(prefilledCidr = "192.168.1.0/24", prefilledName = "eth0", scope = this)

            assertEquals("Customized eth0", vm.state.value.name)
            assertEquals("Customized during session", vm.state.value.description)
            assertTrue(vm.state.value.restoredFromDraft)
            vm.clear()
        }

    @Test
    fun successfulSubmitDiscardsDraftBeforeOnSuccess() =
        runTest {
            var onSuccessCalled = false
            var draftPresentWhenOnSuccessCalled: Boolean? = null

            val vm = makeVm(scope = this)
            vm.onNameChanged("Production")
            vm.onCidrChanged("10.0.0.0/24")

            assertNotNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))

            vm.createSubnet {
                onSuccessCalled = true
                draftPresentWhenOnSuccessCalled =
                    formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()) != null
            }
            advanceUntilIdle()

            assertTrue(onSuccessCalled)
            assertEquals(false, draftPresentWhenOnSuccessCalled)
            assertNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }

    @Test
    fun failedSubmitKeepsDraft() =
        runTest {
            val failingRepo =
                FakeSubnetRepository(
                    createSubnetResult =
                        ApiResult.Error(
                            code = "CONFLICT",
                            message = "CIDR already exists",
                            requestId = "req-1",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(subnetRepo = failingRepo, scope = this)
            vm.onNameChanged("Conflict Net")
            vm.onCidrChanged("10.0.0.0/24")

            vm.createSubnet()
            advanceUntilIdle()

            assertNotNull(vm.state.value.errorMessage)
            val draft = formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer())
            assertNotNull(draft)
            assertEquals("Conflict Net", draft.name)
            vm.clear()
        }

    @Test
    fun discardDraftRemovesIt() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("To be discarded")
            assertNotNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))

            vm.discardDraft()
            assertNull(formDraftStore.load(DraftForm.CreateSubnet, CreateSubnetDraft.serializer()))
            vm.clear()
        }
}
