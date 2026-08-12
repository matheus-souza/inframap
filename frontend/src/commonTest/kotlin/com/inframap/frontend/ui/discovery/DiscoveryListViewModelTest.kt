package com.inframap.frontend.ui.discovery

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
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
class DiscoveryListViewModelTest {
    private val sampleSource =
        DiscoverySource(
            id = "src-1",
            name = "Docker Network",
            sourceType = "docker",
            enabled = true,
            lastStatus = "idle",
        )

    private val pagedSources =
        PaginatedList(items = listOf(sampleSource), total = 1, page = 1, perPage = 50)

    private fun makeVm(
        repo: FakeDiscoveryRepository =
            FakeDiscoveryRepository(
                getSourcesResult = ApiResult.Success(pagedSources, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = DiscoveryListViewModel(
        GetDiscoverySourcesUseCase(repo),
        TriggerDiscoveryRunUseCase(repo),
        DeleteDiscoverySourceUseCase(repo),
        scope = scope,
    )

    @Test
    fun loadSourcesPopulatesListSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                val state = awaitItem()

                assertFalse(state.isLoading)
                assertNull(state.errorMessage)
                assertEquals(1, state.sources.size)
                assertEquals("Docker Network", state.sources.first().name)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun loadSourcesHandlesApiError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult =
                        ApiResult.Error(
                            code = "DB_ERROR",
                            message = "DB Error",
                            requestId = "",
                            httpStatus = 500,
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
    fun loadSourcesHandlesNetworkError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = ApiResult.NetworkError(RuntimeException("Network failure")),
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
    fun triggerRunUpdatesSourceStatusOnSuccess() =
        runTest {
            val updatedSource = sampleSource.copy(lastStatus = "running", lastRunAt = "2026-08-12T10:00:00Z")
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = ApiResult.Success(pagedSources, requestId = ""),
                    triggerRunResult = ApiResult.Success(updatedSource, requestId = ""),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.triggerRun("src-1")
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertEquals("running", state.sources.first().lastStatus)
                assertNotNull(state.toastMessage)
                assertNull(state.triggerRunError)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun triggerRunHandlesApiError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = ApiResult.Success(pagedSources, requestId = ""),
                    triggerRunResult =
                        ApiResult.Error(
                            code = "LOCKED",
                            message = "Source already running",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.triggerRun("src-1")
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.triggerRunError)
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissTriggerRunError()
            assertNull(vm.state.value.triggerRunError)
            vm.clear()
        }

    @Test
    fun deleteSourceWorkflowCompletesSuccessfully() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteSource(sampleSource)
                assertEquals("src-1", vm.state.value.sourceToDelete?.id)

                vm.deleteSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNull(state.sourceToDelete)
                assertTrue(state.sources.isEmpty())
                assertEquals(0, state.totalItems)
                assertNotNull(state.toastMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun deleteSourceHandlesApiError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = ApiResult.Success(pagedSources, requestId = ""),
                    deleteSourceResult =
                        ApiResult.Error(
                            code = "FORBIDDEN",
                            message = "Cannot delete active source",
                            requestId = "",
                            httpStatus = 403,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteSource(sampleSource)
                vm.deleteSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.deleteError)
                assertNull(state.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }

            vm.dismissDeleteError()
            assertNull(vm.state.value.deleteError)
            vm.clear()
        }

    @Test
    fun deleteSourceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = ApiResult.Success(pagedSources, requestId = ""),
                    deleteSourceResult = ApiResult.NetworkError(RuntimeException("timeout")),
                )
            val vm = makeVm(repo = repo, scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteSource(sampleSource)
                vm.deleteSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNotNull(state.deleteError)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun cancelDeleteSourceClearsSelection() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.confirmDeleteSource(sampleSource)
                assertNotNull(vm.state.value.sourceToDelete)

                vm.cancelDeleteSource()
                assertNull(vm.state.value.sourceToDelete)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun dismissToastClearsToast() =
        runTest {
            val vm = makeVm(scope = this)

            vm.state.test {
                skipItems(1)
                awaitItem()

                vm.dismissToast()
                assertNull(vm.state.value.toastMessage)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }
}
