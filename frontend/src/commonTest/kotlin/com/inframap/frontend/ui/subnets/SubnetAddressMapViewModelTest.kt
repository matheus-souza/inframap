package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetAddressDeviceDto
import com.inframap.frontend.data.dto.SubnetAddressDto
import com.inframap.frontend.data.dto.SubnetAddressMapResponse
import com.inframap.frontend.domain.usecase.subnet.GetSubnetAddressMapUseCase
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SubnetAddressMapViewModelTest {
    private val map =
        SubnetAddressMapResponse(
            subnetId = "sub1",
            cidr = "192.0.2.0/30",
            total = 2,
            free = 1,
            reserved = 1,
            addresses =
                listOf(
                    SubnetAddressDto(
                        address = "192.0.2.1",
                        state = "reserved",
                        isGateway = true,
                        devices = listOf(SubnetAddressDeviceDto(id = "d1", hostname = "router", ipAssignment = "static")),
                    ),
                    SubnetAddressDto(address = "192.0.2.2", state = "free"),
                ),
        )

    @Test
    fun loadsTheMapAndSelectsAnAddress() =
        runTest {
            val repo = FakeSubnetRepository(getSubnetAddressMapResult = ApiResult.Success(map, requestId = ""))
            val vm = SubnetAddressMapViewModel("sub1", GetSubnetAddressMapUseCase(repo), scope = this)
            advanceUntilIdle()

            assertFalse(vm.state.value.isLoading)
            assertEquals(
                "192.0.2.0/30",
                vm.state.value.map
                    ?.cidr,
            )
            assertNull(vm.state.value.selected)

            vm.onAddressSelected("192.0.2.1")
            assertEquals(
                "router",
                vm.state.value.selected
                    ?.devices
                    ?.single()
                    ?.hostname,
            )

            vm.onAddressSelected("192.0.2.1")
            assertNull(vm.state.value.selected, "selecting the same address again clears the selection")
            vm.clear()
        }

    @Test
    fun aFailedLoadShowsTheErrorAndRetriesOnLoad() =
        runTest {
            val repo =
                FakeSubnetRepository(
                    getSubnetAddressMapResult =
                        ApiResult.Error(code = "SUBNET_TOO_LARGE_TO_MAP", message = "too large", requestId = "", httpStatus = 422),
                )
            val vm = SubnetAddressMapViewModel("sub1", GetSubnetAddressMapUseCase(repo), scope = this)
            advanceUntilIdle()
            assertNotNull(vm.state.value.errorMessage)

            repo.getSubnetAddressMapResult = ApiResult.Success(map, requestId = "")
            vm.load()
            advanceUntilIdle()

            assertNull(vm.state.value.errorMessage)
            assertEquals(
                2,
                vm.state.value.map
                    ?.total,
            )
            vm.clear()
        }
}
