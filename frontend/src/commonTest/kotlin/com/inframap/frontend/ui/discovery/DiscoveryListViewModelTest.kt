package com.inframap.frontend.ui.discovery

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import com.inframap.frontend.fakes.FakeSSEClient
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.discovery_error_run_in_progress
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
        sse: FakeSSEClient? = null,
    ) = DiscoveryListViewModel(
        GetDiscoverySourcesUseCase(repo),
        TriggerDiscoveryRunUseCase(repo),
        DeleteDiscoverySourceUseCase(repo),
        GetDiscoverySourceDeletionImpactUseCase(repo),
        sseClient = sse,
        scope = scope,
    )

    private fun sourcesResult(vararg sources: DiscoverySource): ApiResult<PaginatedList<DiscoverySource>> =
        ApiResult.Success(
            PaginatedList(items = sources.toList(), total = sources.size.toLong(), page = 1, perPage = 50),
            requestId = "",
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
                // Only the trigger: advancing further would let the running poll fetch the list.
                runCurrent()

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
                advanceUntilIdle()

                val confirmedState = expectMostRecentItem()
                assertEquals("src-1", confirmedState.sourceToDelete?.id)
                assertEquals(2, confirmedState.deleteImpact?.collectorsHalted)
                assertEquals(5, confirmedState.deleteImpact?.devicesUnlinked)

                vm.deleteSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertNull(state.sourceToDelete)
                assertNull(state.deleteImpact)
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

    @Test
    fun aBurstOfDiscoverySourceEventsRefreshesTheListOnce() =
        runTest {
            val repo = FakeDiscoveryRepository(getSourcesResult = sourcesResult(sampleSource))
            val sse = FakeSSEClient()
            val vm = makeVm(repo = repo, scope = this, sse = sse)
            runCurrent()
            assertEquals(1, repo.getSourcesCallCount)

            repo.getSourcesResult = sourcesResult(sampleSource.copy(lastStatus = "success"))
            repeat(5) {
                sse.events.emit(SSEEvent.DiscoverySourceChanged("evt-$it", "discovery_source.run_finished", "{}"))
            }
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(2, repo.getSourcesCallCount)
            assertEquals(
                "success",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )
            assertFalse(vm.state.value.isLoading)
            vm.clear()
        }

    @Test
    fun eventsOfOtherModulesDoNotRefreshTheList() =
        runTest {
            val repo = FakeDiscoveryRepository(getSourcesResult = sourcesResult(sampleSource))
            val sse = FakeSSEClient()
            val vm = makeVm(repo = repo, scope = this, sse = sse)
            runCurrent()

            sse.events.emit(SSEEvent.DeviceCreated("evt-1", "{}"))
            sse.events.emit(SSEEvent.TopologyUpdated("evt-2", "{}"))
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(1, repo.getSourcesCallCount)
            vm.clear()
        }

    @Test
    fun aRefreshThatFailsKeepsTheRowsShown() =
        runTest {
            val repo = FakeDiscoveryRepository(getSourcesResult = sourcesResult(sampleSource))
            val sse = FakeSSEClient()
            val vm = makeVm(repo = repo, scope = this, sse = sse)
            runCurrent()

            repo.getSourcesResult = ApiResult.NetworkError(RuntimeException("down"))
            sse.events.emit(SSEEvent.DiscoverySourceChanged("evt-1", "discovery_source.updated", "{}"))
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(2, repo.getSourcesCallCount)
            assertEquals(
                listOf(sampleSource.id),
                vm.state.value.sources
                    .map { it.id },
            )
            assertNull(vm.state.value.errorMessage)
            vm.clear()
        }

    @Test
    fun aRunningRowIsPolledUntilItSettles() =
        runTest {
            val repo = FakeDiscoveryRepository(getSourcesResult = sourcesResult(sampleSource.copy(lastStatus = "running")))
            val vm = makeVm(repo = repo, scope = this)
            runCurrent()
            assertEquals(
                "running",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )

            advanceTimeBy(5_001)
            assertEquals(2, repo.getSourcesCallCount)
            assertEquals(
                "running",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )

            repo.getSourcesResult = sourcesResult(sampleSource.copy(lastStatus = "success"))
            advanceTimeBy(5_000)
            assertEquals(3, repo.getSourcesCallCount)
            assertEquals(
                "success",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )

            advanceUntilIdle()
            assertEquals(3, repo.getSourcesCallCount)
            vm.clear()
        }

    @Test
    fun triggeringARunPollsUntilItFinishes() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = sourcesResult(sampleSource),
                    triggerRunResult = ApiResult.Success(sampleSource.copy(lastStatus = "running"), requestId = ""),
                )
            val vm = makeVm(repo = repo, scope = this)
            runCurrent()

            vm.triggerRun("src-1")
            runCurrent()
            assertEquals(
                "running",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )

            repo.getSourcesResult = sourcesResult(sampleSource.copy(lastStatus = "success"))
            advanceUntilIdle()

            assertEquals(
                "success",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )
            assertEquals(2, repo.getSourcesCallCount)
            vm.clear()
        }

    @Test
    fun aRunAlreadyInProgressIsFollowedInsteadOfReportedAsAFailure() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    getSourcesResult = sourcesResult(sampleSource),
                    triggerRunResult =
                        ApiResult.Error(
                            code = "RUN_IN_PROGRESS",
                            message = "A run of this discovery source is already in progress",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)
            runCurrent()

            vm.triggerRun("src-1")
            runCurrent()
            val state = vm.state.value
            assertEquals("running", state.sources.single().lastStatus)
            assertEquals(UiText.Resource(Res.string.discovery_error_run_in_progress), state.triggerRunError)

            repo.getSourcesResult = sourcesResult(sampleSource.copy(lastStatus = "success"))
            advanceUntilIdle()
            assertEquals(
                "success",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )
            vm.clear()
        }

    @Test
    fun pollingARunThatNeverEndsIsBounded() =
        runTest {
            val repo = FakeDiscoveryRepository(getSourcesResult = sourcesResult(sampleSource.copy(lastStatus = "running")))
            val vm = makeVm(repo = repo, scope = this)

            advanceUntilIdle()

            // The initial load plus thirty minutes of polls every five seconds.
            assertEquals(1 + 360, repo.getSourcesCallCount)
            assertEquals(
                "running",
                vm.state.value.sources
                    .single()
                    .lastStatus,
            )
            vm.clear()
        }
}
