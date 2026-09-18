package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.DeleteSubnetResponse
import com.inframap.frontend.data.dto.SubnetDeletionImpact
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteSubnetUseCaseTest {
    @Test
    fun deleteSubnetReturnsSuccessFromRepository() =
        runTest {
            val expected =
                DeleteSubnetResponse(
                    deletedId = "sub-123",
                    impact = SubnetDeletionImpact(affectedDevices = 3, unlinkedTopologyEdges = 0),
                )
            val repo = FakeSubnetRepository(deleteSubnetResult = ApiResult.Success(expected, requestId = "req-1"))
            val useCase = DeleteSubnetUseCase(repo)

            val result = useCase("sub-123")

            assertIs<ApiResult.Success<DeleteSubnetResponse>>(result)
            assertEquals("sub-123", result.data.deletedId)
            assertEquals(3, result.data.impact.affectedDevices)
            assertEquals(0, result.data.impact.unlinkedTopologyEdges)
        }
}
