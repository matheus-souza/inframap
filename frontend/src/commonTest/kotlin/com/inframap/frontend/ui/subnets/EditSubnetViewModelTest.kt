package com.inframap.frontend.ui.subnets

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetCidrImpactResponse
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.subnet.DeleteSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetByIdUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetCidrImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.UpdateSubnetUseCase
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
class EditSubnetViewModelTest {
    private val sampleSubnet =
        Subnet(
            id = "sub-1",
            name = "Office-LAN",
            cidr = "192.168.10.0/24",
            vlanId = 10,
            gatewayIp = "192.168.10.1",
            description = "Main office subnet",
            discoveryEnabled = true,
        )

    private fun makeVm(
        repo: FakeSubnetRepository =
            FakeSubnetRepository(
                getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = EditSubnetViewModel(
        subnetId = "sub-1",
        getSubnetByIdUseCase = GetSubnetByIdUseCase(repo),
        updateSubnetUseCase = UpdateSubnetUseCase(repo),
        getSubnetCidrImpactUseCase = GetSubnetCidrImpactUseCase(repo),
        deleteSubnetUseCase = DeleteSubnetUseCase(repo),
        getSubnetDeletionImpactUseCase = GetSubnetDeletionImpactUseCase(repo),
        scope = scope,
    )

    @Test
    fun loadSubnetPrepopulatesStateSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertEquals("Office-LAN", state.name)
                assertEquals("192.168.10.0/24", state.cidr)
                assertEquals("192.168.10.0/24", state.initialCidr)
                assertEquals("10", state.vlanId)
                assertEquals("192.168.10.1", state.gatewayIp)
                assertEquals("Main office subnet", state.description)
                assertTrue(state.discoveryEnabled)
                assertNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadSubnetHandlesApiError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult =
                        ApiResult.Error(
                            code = "NOT_FOUND",
                            message = "Subnet not found",
                            requestId = "",
                            httpStatus = 404,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

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
    fun loadSubnetHandlesNetworkError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.NetworkError(RuntimeException("Network failure")),
                )
            val vm = makeVm(repo = repo, scope = this)

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
    fun updateSubnetIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.updateSubnet()
                assertTrue(vm.state.value.isSubmitting)

                vm.updateSubnet()
                assertTrue(vm.state.value.isSubmitting)

                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetValidatesAndSucceeds() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onNameChanged("Office-LAN-Renamed")
                vm.onCidrChanged("192.168.20.0/24")
                vm.onGatewayIpChanged("192.168.20.1")
                vm.onVlanIdChanged("20")
                vm.onDescriptionChanged("Updated description")
                vm.onDiscoveryEnabledChanged(false)

                var successCallbackCalled = false
                vm.updateSubnet { successCallbackCalled = true }
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                assertNull(state.errorMessage)
                assertTrue(successCallbackCalled)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetFailsValidationWhenNameOrCidrInvalid() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onNameChanged("")
                vm.onCidrChanged("invalid-cidr")
                vm.onVlanIdChanged("9999")
                vm.onGatewayIpChanged("not-an-ip")

                vm.updateSubnet()

                val errors = vm.state.value.validationErrors
                assertTrue(errors.containsKey("name"))
                assertTrue(errors.containsKey("cidr"))
                assertTrue(errors.containsKey("vlan_id"))
                assertTrue(errors.containsKey("gateway_ip"))
                assertFalse(vm.state.value.isSuccess)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetHandlesConflictError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    updateSubnetResult =
                        ApiResult.Error(
                            code = "error_subnet_conflict",
                            message = "Subnet conflict",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.updateSubnet()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.errorMessage)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetHandlesGatewayNotContainedError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    updateSubnetResult =
                        ApiResult.Error(
                            code = "error_gateway_not_contained",
                            message = "Gateway not contained",
                            requestId = "",
                            httpStatus = 422,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.updateSubnet()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.errorMessage)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetHandlesNetworkError() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    updateSubnetResult = ApiResult.NetworkError(RuntimeException("Connection lost")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.updateSubnet()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.errorMessage)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetShowsImpactDialogWhenCidrChangesAndDevicesAffected() =
        runTest {
            val impactResp =
                SubnetCidrImpactResponse(
                    currentCidr = "192.168.10.0/24",
                    newCidr = "192.168.10.0/25",
                    affectedDevicesCount = 3,
                )
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    getSubnetCidrImpactResult = ApiResult.Success(impactResp, requestId = ""),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onCidrChanged("192.168.10.0/25")
                vm.updateSubnet()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.showImpactDialog)
                assertEquals(3, state.impactAffectedCount)
                assertFalse(state.isSubmitting)
                assertFalse(state.isSuccess)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun confirmImpactDismissesDialogAndPerformsUpdate() =
        runTest {
            val impactResp =
                SubnetCidrImpactResponse(
                    currentCidr = "192.168.10.0/24",
                    newCidr = "192.168.10.0/25",
                    affectedDevicesCount = 2,
                )
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    getSubnetCidrImpactResult = ApiResult.Success(impactResp, requestId = ""),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onCidrChanged("192.168.10.0/25")
                vm.updateSubnet()
                advanceUntilIdle()

                assertTrue(expectMostRecentItem().showImpactDialog)

                var successCallbackCalled = false
                vm.confirmImpact { successCallbackCalled = true }
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.showImpactDialog)
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                assertTrue(successCallbackCalled)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun dismissImpactClosesDialogWithoutUpdating() =
        runTest {
            val impactResp =
                SubnetCidrImpactResponse(
                    currentCidr = "192.168.10.0/24",
                    newCidr = "192.168.10.0/25",
                    affectedDevicesCount = 2,
                )
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    getSubnetCidrImpactResult = ApiResult.Success(impactResp, requestId = ""),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onCidrChanged("192.168.10.0/25")
                vm.updateSubnet()
                advanceUntilIdle()

                assertTrue(expectMostRecentItem().showImpactDialog)

                vm.dismissImpact()

                val state = expectMostRecentItem()
                assertFalse(state.showImpactDialog)
                assertFalse(state.isSuccess)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun updateSubnetSkipsImpactDialogWhenCidrUnchanged() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.onNameChanged("Renamed Office LAN")
                vm.updateSubnet()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.showImpactDialog)
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun requestDeleteOpensDialogAndLoadsImpact() =
        runTest {
            val impact = SubnetDeletionImpact(affectedDevices = 2, unlinkedTopologyEdges = 1)
            val repo =
                FakeSubnetRepository(
                    getSubnetByIdResult = ApiResult.Success(sampleSubnet, requestId = ""),
                    getSubnetDeletionImpactResult =
                        ApiResult.Success(
                            SubnetDeletionImpactResponse(subnetId = "sub-1", impact = impact),
                            requestId = "",
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)
            advanceUntilIdle()

            vm.requestDelete()
            advanceUntilIdle()

            val state = vm.state.value
            assertTrue(state.showDeleteDialog)
            assertFalse(state.isLoadingDeleteImpact)
            assertEquals(impact, state.deleteImpact)
            vm.clear()
        }

    @Test
    fun confirmDeleteExecutesSuccessfully() =
        runTest {
            var callbackCalled = false
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.requestDelete()
            advanceUntilIdle()

            vm.confirmDelete { callbackCalled = true }
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.showDeleteDialog)
            assertFalse(state.isDeleting)
            assertTrue(state.isSuccess)
            assertTrue(callbackCalled)
            vm.clear()
        }

    @Test
    fun dismissDeleteResetsState() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.requestDelete()
            advanceUntilIdle()
            assertTrue(vm.state.value.showDeleteDialog)

            vm.dismissDelete()

            val state = vm.state.value
            assertFalse(state.showDeleteDialog)
            assertNull(state.deleteImpact)
            assertFalse(state.isDeleting)
            vm.clear()
        }
}
