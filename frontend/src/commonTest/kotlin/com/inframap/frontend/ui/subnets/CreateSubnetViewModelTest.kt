package com.inframap.frontend.ui.subnets

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
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

    private fun makeVm(
        repo: FakeSubnetRepository =
            FakeSubnetRepository(
                createSubnetResult = ApiResult.Success(sampleCreatedSubnet, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = CreateSubnetViewModel(CreateSubnetUseCase(repo), scope = scope)

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
            val vm = makeVm(repo = repo, scope = this)
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
}
