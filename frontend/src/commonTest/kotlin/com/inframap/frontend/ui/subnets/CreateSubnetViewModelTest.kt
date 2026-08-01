package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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

    private val emptyPagedSubnets =
        PaginatedList(items = emptyList<Subnet>(), total = 0, page = 1, perPage = 50)

    private fun successRepo(result: Subnet = sampleCreatedSubnet): SubnetRepository =
        object : SubnetRepository {
            override suspend fun getSubnets() = ApiResult.Success(emptyPagedSubnets, requestId = "")

            override suspend fun createSubnet(request: CreateSubnetRequest) = ApiResult.Success(result, requestId = "")
        }

    @Test
    fun validationFailsOnEmptyFields() =
        runTest {
            val vm = CreateSubnetViewModel(CreateSubnetUseCase(successRepo()), scope = this)

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("name"))
            assertTrue(errors.containsKey("cidr"))
            vm.clear()
        }

    @Test
    fun validationFailsOnInvalidCidrAndVlan() =
        runTest {
            val vm = CreateSubnetViewModel(CreateSubnetUseCase(successRepo()), scope = this)

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
            val vm = CreateSubnetViewModel(CreateSubnetUseCase(successRepo()), scope = this)

            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")
            vm.onVlanIdChanged("10")
            vm.onGatewayIpChanged("192.168.1.1")
            vm.onDescriptionChanged("Core mgmt subnet")
            vm.onDiscoveryEnabledChanged(true)

            val stateDeferred = async { vm.state.first { !it.isSubmitting && it.isSuccess } }
            vm.createSubnet { onSuccessCalled = true }
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertTrue(state.isSuccess)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)
            assertTrue(onSuccessCalled)
            vm.clear()
        }

    @Test
    fun createSubnetIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = CreateSubnetViewModel(CreateSubnetUseCase(successRepo()), scope = this)
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
            val errorRepo =
                object : SubnetRepository {
                    override suspend fun getSubnets() = ApiResult.Success(emptyPagedSubnets, requestId = "")

                    override suspend fun createSubnet(request: CreateSubnetRequest) =
                        ApiResult.Error(
                            code = "DUPLICATE_CIDR",
                            message = "Subnet CIDR already registered",
                            requestId = "",
                            httpStatus = 409,
                        )
                }
            val vm = CreateSubnetViewModel(CreateSubnetUseCase(errorRepo), scope = this)
            vm.onNameChanged("Management")
            vm.onCidrChanged("192.168.1.0/24")

            val stateDeferred = async { vm.state.first { !it.isSubmitting && it.errorMessage != null } }
            vm.createSubnet()
            advanceUntilIdle()

            val state = stateDeferred.await()
            assertFalse(state.isSubmitting)
            assertFalse(state.isSuccess)
            assertEquals("Subnet CIDR already registered", state.errorMessage?.asStringAsync())
            vm.clear()
        }
}
