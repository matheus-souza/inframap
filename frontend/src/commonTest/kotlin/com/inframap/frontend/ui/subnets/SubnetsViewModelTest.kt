package com.inframap.frontend.ui.subnets

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.Success(pagedSubnets, requestId = ""),
                )
            val vm = SubnetsViewModel(GetSubnetsUseCase(repo), scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNull(state.errorMessage)
                assertEquals(1, state.subnets.size)
                assertEquals("Management", state.subnets.first().name)
                assertEquals("192.168.1.0/24", state.subnets.first().cidr)
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissToast()
            assertNull(vm.state.value.toastMessage)
            vm.clear()
        }

    @Test
    fun loadSubnetsHandlesApiError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetsResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB Error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = SubnetsViewModel(GetSubnetsUseCase(repo), scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadSubnetsHandlesNetworkError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = SubnetsViewModel(GetSubnetsUseCase(repo), scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNotNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
