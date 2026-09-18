package com.inframap.frontend.domain.usecase.credentials

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.fakes.FakeCredentialsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteCredentialUseCaseTest {
    @Test
    fun invokeSuccessDelegatesToRepository() =
        runTest {
            val fakeRepo = FakeCredentialsRepository()
            val useCase = DeleteCredentialUseCase(fakeRepo)

            val result = useCase("cred-test-123")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(listOf("cred-test-123"), fakeRepo.deletedIds)
        }

    @Test
    fun invokeConflictPropagatesError() =
        runTest {
            val fakeRepo =
                FakeCredentialsRepository(
                    deleteResult =
                        ApiResult.Error(
                            code = "CREDENTIAL_IN_USE",
                            message = "credential in use",
                            requestId = "req-1",
                            httpStatus = 409,
                        ),
                )
            val useCase = DeleteCredentialUseCase(fakeRepo)

            val result = useCase("cred-test-456")

            assertIs<ApiResult.Error>(result)
            assertEquals("CREDENTIAL_IN_USE", result.code)
            assertEquals(409, result.httpStatus)
            assertEquals(listOf("cred-test-456"), fakeRepo.deletedIds)
        }
}
