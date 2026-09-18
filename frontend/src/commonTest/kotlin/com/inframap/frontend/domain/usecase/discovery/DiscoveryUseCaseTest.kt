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
            assertEquals(1, repo.createSourceCallCount)
        }

    @Test
    fun triggerRunDelegatesToRepository() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = TriggerDiscoveryRunUseCase(repo)

            val result = useCase("src-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, repo.triggerRunCallCount)
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

    @Test
    fun getSourceByIdRejectsBlankId() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = GetDiscoverySourceByIdUseCase(repo)

            val result = useCase("   ")

            assertIs<ApiResult.Error>(result)
            assertEquals("INVALID_ID", result.code)
        }

    @Test
    fun getSourceByIdDelegatesToRepositoryOnValidId() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = GetDiscoverySourceByIdUseCase(repo)

            val result = useCase("src-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, repo.getSourceByIdCallCount)
        }

    @Test
    fun updateSourceRejectsBlankIdOrName() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = UpdateDiscoverySourceUseCase(repo)

            val res1 =
                useCase(
                    UpdateDiscoverySourceParams(
                        id = "   ",
                        request =
                            com.inframap.frontend.data.dto
                                .UpdateDiscoverySourceRequest(name = "Valid"),
                    ),
                )
            assertIs<ApiResult.Error>(res1)
            assertEquals("INVALID_ID", res1.code)

            val res2 =
                useCase(
                    UpdateDiscoverySourceParams(
                        id = "src-1",
                        request =
                            com.inframap.frontend.data.dto
                                .UpdateDiscoverySourceRequest(name = "   "),
                    ),
                )
            assertIs<ApiResult.Error>(res2)
            assertEquals("INVALID_NAME", res2.code)
        }

    @Test
    fun updateSourceDelegatesToRepositoryOnValidInput() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = UpdateDiscoverySourceUseCase(repo)

            val result =
                useCase(
                    UpdateDiscoverySourceParams(
                        id = "src-1",
                        request =
                            com.inframap.frontend.data.dto
                                .UpdateDiscoverySourceRequest(name = "Updated"),
                    ),
                )

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, repo.updateSourceCallCount)
        }

    @Test
    fun getDiscoverySourceDeletionImpactDelegatesToRepository() =
        runTest {
            val repo = FakeDiscoveryRepository()
            val useCase = GetDiscoverySourceDeletionImpactUseCase(repo)

            val result = useCase("src-1")

            assertIs<ApiResult.Success<*>>(result)
            assertEquals(1, repo.getDeletionImpactCallCount)
            assertEquals("src-1", repo.lastGetDeletionImpactId)
        }
}
