package com.inframap.frontend.domain.usecase.subnet

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.fakes.FakeSubnetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdateSubnetUseCaseTest {
    @Test
    fun updateSubnetReturnsSuccessFromRepository() =
        runTest {
            val expected = Subnet(id = "sub-123", name = "DMZ-New", cidr = "10.0.1.0/24")
            val repo = FakeSubnetRepository(updateSubnetResult = ApiResult.Success(expected, requestId = "req-1"))
            val useCase = UpdateSubnetUseCase(repo)

            val result =
                useCase(
                    id = "sub-123",
                    name = "DMZ-New",
                    cidr = "10.0.1.0/24",
                )

            assertIs<ApiResult.Success<Subnet>>(result)
            assertEquals("sub-123", result.data.id)
            assertEquals("DMZ-New", result.data.name)
        }
}
