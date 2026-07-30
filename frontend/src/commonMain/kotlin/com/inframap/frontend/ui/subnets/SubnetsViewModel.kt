package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.SubnetListResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubnetsViewModel(
    private val apiClient: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SubnetsUiState())
    val state: StateFlow<SubnetsUiState> = _state.asStateFlow()

    private var fetchJob: Job? = null

    init {
        loadSubnets()
    }

    fun loadSubnets() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }

        fetchJob?.cancel()
        fetchJob =
            scope.launch {
                when (val result = apiClient.get<SubnetListResponse>("/api/v1/subnets")) {
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                subnets = result.data.subnets,
                                total = result.data.total,
                                isLoading = false,
                                errorMessage = null,
                            )
                        }
                    }
                    is ApiResult.Error -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.message.ifEmpty { "Falha ao carregar subredes" },
                            )
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Erro de rede. Não foi possível conectar ao servidor.",
                            )
                        }
                    }
                }
            }
    }

    fun dismissToast() {
        _state.update { it.copy(toastMessage = null) }
    }

    fun clear() {
        fetchJob?.cancel()
        fetchJob = null
    }
}
