package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.data.dto.SubnetDeletionImpactResponse
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetSubnetDeletionImpactUseCaseTest {
    @Test
    fun getSubnetDeletionImpactReturnsImpactFromRepository() =
        runTest {
            val expected =
                SubnetDeletionImpactResponse(
                    subnetId = "sub-123",
                    impact = SubnetDeletionImpact(affectedDevices = 5, unlinkedTopologyEdges = 0),
                )
            val repo =
                FakeSubnetRepository(
                    getSubnetDeletionImpactResult = ApiResult.Success(expected, requestId = "req-1"),
                )
            val useCase = GetSubnetDeletionImpactUseCase(repo)

            val result = useCase("sub-123")

            assertIs<ApiResult.Success<SubnetDeletionImpactResponse>>(result)
            assertEquals("sub-123", result.data.subnetId)
            assertEquals(5, result.data.impact.affectedDevices)
            assertEquals(0, result.data.impact.unlinkedTopologyEdges)
        }
}
