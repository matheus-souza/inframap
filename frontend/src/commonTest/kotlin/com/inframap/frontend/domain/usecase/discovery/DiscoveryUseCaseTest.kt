package com.inframap.frontend.domain.usecase.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDiscoverySourceRequest
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiscoveryUseCaseTest {
    @Test
    fun getSourcesDelegatesToRepository() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = GetDiscoverySourcesUseCase(repo)

            val result = useCase()

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, repo.getSourcesCallCount)
        }

    @Test
    fun createSourceRejectsBlankName() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = CreateDiscoverySourceUseCase(repo)

            val result =
                useCase(
                    CreateDiscoverySourceRequest(
                        name = "   ",
                        type = "docker",
                    ),
                )

            assertIs<ApiResult.Error>(result)
            assertEquals("INVALID_NAME", result.code)
        }

    @Test
    fun createSourceDelegatesToRepositoryOnValidInput() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = CreateDiscoverySourceUseCase(repo)

            val result =
                useCase(
                    CreateDiscoverySourceRequest(
                        name = "Docker Scanner",
                        type = "docker",
                    ),
                )

            assertIs<ApiResult.Success<*>>(result)
        }

    @Test
    fun triggerRunDelegatesToRepository() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = TriggerDiscoveryRunUseCase(repo)

            val result = useCase("src-1")

            assertIs<ApiResult.Success<*>>(result)
        }

    @Test
    fun deleteSourceDelegatesToRepository() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = DeleteDiscoverySourceUseCase(repo)

            val result = useCase("src-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, repo.deleteSourceCallCount)
        }
}
