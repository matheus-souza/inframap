package com.inframap.frontend.ui.subnets

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.fakes.FakeNetworkRepository
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
class CreateSubnetViewModelTest {
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

    private fun makeVm(
        subnetRepo: FakeSubnetRepository =
            FakeSubnetRepository(
                createSubnetResult = ApiResult.Success(sampleCreatedSubnet, requestId = ""),
            ),
        networkRepo: FakeNetworkRepository =
            FakeNetworkRepository(
                getInterfacesResult = ApiResult.Success(listOf(sampleInterface), requestId = ""),
            ),
        prefilledCidr: String? = null,
        prefilledName: String? = null,
        scope: CoroutineScope? = null,
    ) = CreateSubnetViewModel(
        CreateSubnetUseCase(subnetRepo),
        GetNetworkInterfacesUseCase(networkRepo),
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
            assertFalse(state.showInterfaceSuggestions)
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
}
