package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.delete_discovery_source_error
import com.inframap.frontend.generated.resources.delete_discovery_source_success
import com.inframap.frontend.generated.resources.discovery_error_load
import com.inframap.frontend.generated.resources.discovery_error_run_in_progress
import com.inframap.frontend.generated.resources.discovery_error_trigger
import com.inframap.frontend.generated.resources.discovery_run_triggered
import com.inframap.frontend.ui.base.BaseListViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive

/**
 * The Discovery Sources list. A run started elsewhere (scheduler, API, another tab) changes a
 * row's status without this screen asking, so the list follows it two ways: it refreshes when
 * a `discovery_source.*` event arrives, and while a row shows `running` it polls until no row
 * does, so the status settles even when no event reaches the browser.
 */
@Suppress("TooManyFunctions")
class DiscoveryListViewModel(
    private val getSourcesUseCase: GetDiscoverySourcesUseCase,
    private val triggerRunUseCase: TriggerDiscoveryRunUseCase,
    private val deleteSourceUseCase: DeleteDiscoverySourceUseCase,
    private val getDeletionImpactUseCase: GetDiscoverySourceDeletionImpactUseCase,
    private val sseClient: SSEClient? = null,
    scope: CoroutineScope? = null,
) : BaseListViewModel<DiscoveryListUiState>(DiscoveryListUiState(), scope = scope) {
    /** Conflated, so a burst of events while a refresh is pending collapses into that refresh. */
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var pollingRunningSources = false

    init {
        loadPage(1)
        startRefreshOnRequest()
        startSseListening()
    }

    override fun loadPage(
        page: Int,
        perPage: Int,
    ) {
        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("fetch") {
            when (val result = getSourcesUseCase()) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            sources = result.data.items,
                            totalItems = result.data.total,
                            currentPage = result.data.page,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                    pollWhileRunning()
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.discovery_error_load)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.discovery_error_load)),
                        )
                    }
                }
            }
        }
    }

    fun triggerRun(sourceId: String) {
        launchJob("trigger-$sourceId") {
            when (val result = triggerRunUseCase(sourceId)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            sources =
                                it.sources.map { src ->
                                    if (src.id == sourceId) {
                                        src.copy(
                                            lastStatus = result.data.lastStatus,
                                            lastRunAt = result.data.lastRunAt,
                                        )
                                    } else {
                                        src
                                    }
                                },
                            toastMessage = UiText.Resource(Res.string.discovery_run_triggered),
                        )
                    }
                    pollWhileRunning()
                }
                is ApiResult.Error -> {
                    if (result.code == RUN_IN_PROGRESS_CODE) {
                        // The source is already running (another tab, or its schedule):
                        // follow that run instead of reporting a failure.
                        updateState {
                            it.copy(
                                sources =
                                    it.sources.map { src ->
                                        if (src.id == sourceId) src.copy(lastStatus = RUNNING_STATUS) else src
                                    },
                                triggerRunError = UiText.Resource(Res.string.discovery_error_run_in_progress),
                            )
                        }
                        pollWhileRunning()
                    } else {
                        updateState {
                            it.copy(
                                triggerRunError = mapError(result, UiText.Resource(Res.string.discovery_error_trigger)),
                            )
                        }
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            triggerRunError = mapError(result, UiText.Resource(Res.string.discovery_error_trigger)),
                        )
                    }
                }
            }
        }
    }

    fun confirmDeleteSource(source: DiscoverySource) {
        updateState {
            it.copy(
                sourceToDelete = source,
                isLoadingDeleteImpact = true,
                deleteImpact = null,
                deleteError = null,
            )
        }
        launchJob("fetch_deletion_impact") {
            when (val result = getDeletionImpactUseCase(source.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                            deleteImpact = result.data,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                        )
                    }
                }
            }
        }
    }

    fun deleteSource() {
        val source = currentState.sourceToDelete ?: return
        updateState { it.copy(sourceToDelete = null, deleteImpact = null) }

        launchJob("delete") {
            when (val result = deleteSourceUseCase(source.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            sources = it.sources.filter { src -> src.id != source.id },
                            totalItems = it.totalItems - 1,
                            toastMessage =
                                UiText.Resource(
                                    Res.string.delete_discovery_source_success,
                                    listOf(source.name),
                                ),
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            deleteError = mapError(result, UiText.Resource(Res.string.delete_discovery_source_error)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            deleteError = mapError(result, UiText.Resource(Res.string.delete_discovery_source_error)),
                        )
                    }
                }
            }
        }
    }

    fun cancelDeleteSource() {
        updateState { it.copy(sourceToDelete = null, deleteImpact = null, isLoadingDeleteImpact = false) }
    }

    fun dismissDeleteError() {
        updateState { it.copy(deleteError = null) }
    }

    fun dismissTriggerRunError() {
        updateState { it.copy(triggerRunError = null) }
    }

    fun dismissToast() {
        updateState { it.copy(toastMessage = null) }
    }

    /** Asks for a quiet refresh of the list; a burst of requests costs one fetch per window. */
    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    private fun startRefreshOnRequest() {
        launchJob("refresh_requests") {
            while (isActive) {
                refreshRequests.receive()
                delay(REFRESH_DEBOUNCE_MS)
                // Requests made while waiting are answered by this fetch; one made while it
                // runs may have seen newer data, so it is kept for the next round.
                refreshRequests.tryReceive()
                refreshSources()
            }
        }
    }

    private fun startSseListening() {
        val client = sseClient ?: return
        launchJob("sse_listening") {
            // clear() cancels this job; the finally closes the browser's connection with it.
            try {
                while (isActive) {
                    client
                        .connect("/api/v1/events/stream")
                        .takeWhile { it !is SSEEvent.Disconnected }
                        .collect { event ->
                            if (event is SSEEvent.DiscoverySourceChanged) requestRefresh()
                        }
                    client.disconnect()
                    delay(SSE_RECONNECT_DELAY_MS)
                }
            } finally {
                client.disconnect()
            }
        }
    }

    /**
     * Polls while a row shows `running`, ending when none does or after
     * [MAX_RUNNING_POLL_ATTEMPTS], so a run that never reports back cannot poll forever.
     */
    private fun pollWhileRunning() {
        if (pollingRunningSources || currentState.sources.none { it.isRunning }) return
        pollingRunningSources = true
        launchJob("poll_running") {
            try {
                var attempts = 0
                while (attempts < MAX_RUNNING_POLL_ATTEMPTS && currentState.sources.any { it.isRunning }) {
                    delay(RUNNING_POLL_INTERVAL_MS)
                    attempts++
                    refreshSources()
                }
            } finally {
                pollingRunningSources = false
            }
        }
    }

    /**
     * Fetches the list without the loading state, so the rows stay on screen. A failure keeps
     * the rows already shown; the next event or poll tries again.
     */
    private suspend fun refreshSources() {
        val result = getSourcesUseCase()
        if (result is ApiResult.Success) {
            updateState {
                it.copy(
                    sources = result.data.items,
                    totalItems = result.data.total,
                    currentPage = result.data.page,
                )
            }
            pollWhileRunning()
        }
    }

    private val DiscoverySource.isRunning: Boolean get() = lastStatus == RUNNING_STATUS

    private companion object {
        const val RUNNING_STATUS = "running"
        const val RUN_IN_PROGRESS_CODE = "RUN_IN_PROGRESS"
        const val REFRESH_DEBOUNCE_MS = 500L
        const val SSE_RECONNECT_DELAY_MS = 5_000L
        const val RUNNING_POLL_INTERVAL_MS = 5_000L

        /** Thirty minutes of polling at [RUNNING_POLL_INTERVAL_MS]. */
        const val MAX_RUNNING_POLL_ATTEMPTS = 360
    }
}
