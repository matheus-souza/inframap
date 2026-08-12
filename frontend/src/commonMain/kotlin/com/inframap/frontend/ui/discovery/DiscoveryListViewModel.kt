package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.ui.base.BaseListViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class DiscoveryListViewModel(
    private val getSourcesUseCase: GetDiscoverySourcesUseCase,
    private val triggerRunUseCase: TriggerDiscoveryRunUseCase,
    private val deleteSourceUseCase: DeleteDiscoverySourceUseCase,
    scope: CoroutineScope? = null,
) : BaseListViewModel<DiscoveryListUiState>(DiscoveryListUiState(), scope = scope) {
    init {
        loadPage(1)
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
                            sources = it.sources.map { src ->
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
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            triggerRunError = mapError(result, UiText.Resource(Res.string.discovery_error_trigger)),
                        )
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
        updateState { it.copy(sourceToDelete = source) }
    }

    fun deleteSource() {
        val source = currentState.sourceToDelete ?: return
        updateState { it.copy(sourceToDelete = null) }

        launchJob("delete") {
            when (val result = deleteSourceUseCase(source.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            sources = it.sources.filter { src -> src.id != source.id },
                            totalItems = it.totalItems - 1,
                            toastMessage = UiText.DynamicString("Fonte '${source.name}' excluida com sucesso."),
                        )
                    }
                }
                is ApiResult.Error -> {
                    updateState {
                        it.copy(
                            deleteError = mapError(result, UiText.Resource(Res.string.discovery_error_delete)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            deleteError = mapError(result, UiText.Resource(Res.string.discovery_error_delete)),
                        )
                    }
                }
            }
        }
    }

    fun cancelDeleteSource() {
        updateState { it.copy(sourceToDelete = null) }
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
}
