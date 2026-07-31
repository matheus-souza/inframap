package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.repository.SubnetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeSubnetRepository : SubnetRepository {
    var createdRequest: CreateSubnetRequest? = null

    override suspend fun getSubnets(): ApiResult<PaginatedList<Subnet>> =
        ApiResult.Success(
            data = PaginatedList(emptyList(), 0),
            requestId = "req-1",
        )

    override suspend fun createSubnet(request: CreateSubnetRequest): ApiResult<Subnet> {
        createdRequest = request
        return ApiResult.Success(
            data =
                Subnet(
                    id = "sub-1",
                    name = request.name,
                    cidr = request.cidr,
                    vlanId = request.vlanId,
                    gatewayIp = request.gatewayIp,
                ),
            requestId = "req-1",
        )
    }
}

class CreateSubnetUseCaseTest {
    @Test
    fun validRequestCallsRepositoryAndReturnsSuccess() =
        runTest {
            val repo = FakeSubnetRepository()
            val useCase = CreateSubnetUseCase(repo)
            val request = CreateSubnetRequest(name = "LAN", cidr = "192.168.1.0/24", vlanId = 10)

            val result = useCase(request)

            assertIs<ApiResult.Success<*>>(result)
            assertEquals("LAN", (result as ApiResult.Success).data.name)
            assertEquals("192.168.1.0/24", result.data.cidr)
        }

    @Test
    fun emptyNameReturnsError() =
        runTest {
            val repo = FakeSubnetRepository()
            val useCase = CreateSubnetUseCase(repo)
            val request = CreateSubnetRequest(name = "   ", cidr = "192.168.1.0/24")

            val result = useCase(request)

            assertIs<ApiResult.Error>(result)
            assertEquals("INVALID_NAME", (result as ApiResult.Error).code)
        }

    @Test
    fun invalidCidrFormatReturnsError() =
        runTest {
            val repo = FakeSubnetRepository()
            val useCase = CreateSubnetUseCase(repo)
            val request = CreateSubnetRequest(name = "LAN", cidr = "invalid-cidr")

            val result = useCase(request)

            assertIs<ApiResult.Error>(result)
            assertEquals("INVALID_CIDR", (result as ApiResult.Error).code)
        }

    @Test
    fun vlanIdOutOfRangeReturnsError() =
        runTest {
            val repo = FakeSubnetRepository()
            val useCase = CreateSubnetUseCase(repo)

            val lowVlanResult = useCase(CreateSubnetRequest(name = "LAN", cidr = "10.0.0.0/8", vlanId = 0))
            assertIs<ApiResult.Error>(lowVlanResult)
            assertEquals("INVALID_VLAN", (lowVlanResult as ApiResult.Error).code)

            val highVlanResult = useCase(CreateSubnetRequest(name = "LAN", cidr = "10.0.0.0/8", vlanId = 4095))
            assertIs<ApiResult.Error>(highVlanResult)
            assertEquals("INVALID_VLAN", (highVlanResult as ApiResult.Error).code)
        }
}
