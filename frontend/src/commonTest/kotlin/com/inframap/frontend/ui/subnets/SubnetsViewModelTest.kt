package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SubnetsViewModelTest {
    private val sampleSubnet =
        Subnet(
            id = "sub1",
            name = "Management",
            cidr = "192.168.1.0/24",
            vlanId = 10,
            gatewayIp = "192.168.1.1",
            discoveryEnabled = true,
        )

    private val pagedSubnets =
        PaginatedList(items = listOf(sampleSubnet), total = 1, page = 1, perPage = 50)

    @Test
    fun loadSubnetsPopulatesListSuccessfully() =
        runTest {
            val repo =
                object : SubnetRepository {
                    override suspend fun getSubnets() = ApiResult.Success(pagedSubnets, requestId = "")

                    override suspend fun createSubnet(request: CreateSubnetRequest) = ApiResult.Success(sampleSubnet, requestId = "")
                }
            val vm = SubnetsViewModel(GetSubnetsUseCase(repo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNull(state.errorMessage)
            assertEquals(1, state.subnets.size)
            assertEquals("Management", state.subnets.first().name)
            assertEquals("192.168.1.0/24", state.subnets.first().cidr)

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
            vm.clear()
        }

    @Test
    fun loadSubnetsHandlesApiError() =
        runTest {
            val repo =
                object : SubnetRepository {
                    override suspend fun getSubnets() =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB Error",
                            requestId = "",
                            httpStatus = 500,
                        )

                    override suspend fun createSubnet(request: CreateSubnetRequest) = ApiResult.Success(sampleSubnet, requestId = "")
                }
            val vm = SubnetsViewModel(GetSubnetsUseCase(repo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }

    @Test
    fun loadSubnetsHandlesNetworkError() =
        runTest {
            val repo =
                object : SubnetRepository {
                    override suspend fun getSubnets() = ApiResult.NetworkError(RuntimeException("Network failure"))

                    override suspend fun createSubnet(request: CreateSubnetRequest) = ApiResult.Success(sampleSubnet, requestId = "")
                }
            val vm = SubnetsViewModel(GetSubnetsUseCase(repo), scope = this)

            val stateDeferred = async { vm.state.first { !it.isLoading } }
            advanceUntilIdle()
            val state = stateDeferred.await()

            assertFalse(state.isLoading)
            assertNotNull(state.errorMessage)
            vm.clear()
        }
}
