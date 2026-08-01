package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.ui.base.BaseListViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class SubnetsViewModel(
    private val getSubnetsUseCase: GetSubnetsUseCase,
    scope: CoroutineScope? = null,
) : BaseListViewModel<SubnetsUiState>(SubnetsUiState(), scope = scope) {
    init {
        loadPage(1)
    }

    override fun loadPage(
        page: Int,
        perPage: Int,
    ) {
        updateState { it.copy(isLoading = true, errorMessage = null) }

        launchJob("fetch") {
            when (val result = getSubnetsUseCase()) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            subnets = result.data.items,
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
                            errorMessage = mapError(result, UiText.Resource(Res.string.subnets_error_load)),
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapError(result, UiText.Resource(Res.string.subnets_error_load)),
                        )
                    }
                }
            }
        }
    }

    fun loadSubnets() {
        loadPage(1)
    }

    fun dismissToast() {
        updateState { it.copy(toastMessage = null) }
    }
}
