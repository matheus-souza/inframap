package com.inframap.frontend.ui.subnets

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.DeleteSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.fakes.FakeNetworkRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
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
                getSubnetsResult = ApiResult.Success(pagedSubnets, requestId = ""),
            ),
        networkRepo: FakeNetworkRepository =
            FakeNetworkRepository(
                getInterfacesResult = ApiResult.Success(listOf(sampleInterface), requestId = ""),
            ),
        scope: kotlinx.coroutines.CoroutineScope,
    ) = SubnetsViewModel(
        GetSubnetsUseCase(subnetRepo),
        GetNetworkInterfacesUseCase(networkRepo),
        DeleteSubnetUseCase(subnetRepo),
        GetSubnetDeletionImpactUseCase(subnetRepo),
        scope = scope,
    )

    @Test
    fun loadSubnetsPopulatesListSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

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
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB Error",
                            requestId = "",
                            httpStatus = 500,
                        ),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)

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
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)

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
    fun loadNetworkInterfacesPopulatesDetectedInterfaces() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(1, state.detectedInterfaces.size)
            assertEquals("eth0", state.detectedInterfaces.first().name)
            assertEquals("192.168.18.0/24", state.detectedInterfaces.first().cidr)
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
    fun requestDeleteSetsSubnetAndLoadsImpact() =
        runTest {
            val impact = SubnetDeletionImpact(affectedDevices = 4, unlinkedTopologyEdges = 2)
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.Success(pagedSubnets, requestId = ""),
                    getSubnetDeletionImpactResult =
                        ApiResult.Success(
                            SubnetDeletionImpactResponse(subnetId = "sub1", impact = impact),
                            requestId = "",
                        ),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)
            advanceUntilIdle()

            vm.requestDelete(sampleSubnet)
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(sampleSubnet, state.subnetToDelete)
            assertFalse(state.isLoadingDeleteImpact)
            assertEquals(impact, state.deleteImpact)
            vm.clear()
        }

    @Test
    fun confirmDeleteDeletesSubnetAndReloads() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.Success(pagedSubnets, requestId = ""),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)
            advanceUntilIdle()

            vm.requestDelete(sampleSubnet)
            advanceUntilIdle()

            vm.confirmDelete()
            advanceUntilIdle()

            val state = vm.state.value
            assertNull(state.subnetToDelete)
            assertNull(state.deleteImpact)
            assertFalse(state.isDeleting)
            assertNotNull(state.toastMessage)
            vm.clear()
        }

    @Test
    fun dismissDeleteResetsDeletionState() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.requestDelete(sampleSubnet)
            advanceUntilIdle()
            assertEquals(sampleSubnet, vm.state.value.subnetToDelete)

            vm.dismissDelete()

            val state = vm.state.value
            assertNull(state.subnetToDelete)
            assertNull(state.deleteImpact)
            assertFalse(state.isLoadingDeleteImpact)
            assertFalse(state.isDeleting)
            vm.clear()
        }
}
