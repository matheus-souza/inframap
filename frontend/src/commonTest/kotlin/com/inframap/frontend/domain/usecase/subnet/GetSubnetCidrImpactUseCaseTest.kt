package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetCidrImpactResponse
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetSubnetCidrImpactUseCaseTest {
    @Test
    fun getSubnetCidrImpactReturnsImpactFromRepository() =
        runTest {
            val expected =
                SubnetCidrImpactResponse(
                    currentCidr = "192.168.1.0/24",
                    newCidr = "192.168.1.0/25",
                    affectedDevicesCount = 4,
                )
            val repo =
                FakeSubnetRepository(
                    getSubnetCidrImpactResult = ApiResult.Success(expected, requestId = "req-1"),
                )
            val useCase = GetSubnetCidrImpactUseCase(repo)

            val result = useCase("sub-123", "192.168.1.0/25")

            assertIs<ApiResult.Success<SubnetCidrImpactResponse>>(result)
            assertEquals("192.168.1.0/24", result.data.currentCidr)
            assertEquals("192.168.1.0/25", result.data.newCidr)
            assertEquals(4, result.data.affectedDevicesCount)
        }
}
