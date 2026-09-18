package com.inframap.frontend.ui.subnets

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.subnet.DeleteSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.delete_subnet_error
import com.inframap.frontend.generated.resources.delete_subnet_success
import com.inframap.frontend.generated.resources.subnets_error_load
import com.inframap.frontend.ui.base.BaseListViewModel
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope

class SubnetsViewModel(
    private val getSubnetsUseCase: GetSubnetsUseCase,
    private val getNetworkInterfacesUseCase: GetNetworkInterfacesUseCase,
    private val deleteSubnetUseCase: DeleteSubnetUseCase,
    private val getSubnetDeletionImpactUseCase: GetSubnetDeletionImpactUseCase,
    scope: CoroutineScope? = null,
) : BaseListViewModel<SubnetsUiState>(SubnetsUiState(), scope = scope) {
    init {
        loadPage(1)
        loadNetworkInterfaces()
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

    private fun loadNetworkInterfaces() {
        launchJob("fetch_interfaces") {
            when (val result = getNetworkInterfacesUseCase()) {
                is ApiResult.Success -> {
                    updateState { it.copy(detectedInterfaces = result.data) }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    // Silently ignore — interfaces are a best-effort suggestion
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

    fun requestDelete(subnet: Subnet) {
        updateState {
            it.copy(
                subnetToDelete = subnet,
                isLoadingDeleteImpact = true,
                deleteImpact = null,
                deleteErrorMessage = null,
            )
        }
        launchJob("fetch_deletion_impact") {
            when (val result = getSubnetDeletionImpactUseCase(subnet.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                            deleteImpact = result.data.impact,
                        )
                    }
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isLoadingDeleteImpact = false,
                            deleteErrorMessage = mapError(result, UiText.Resource(Res.string.delete_subnet_error)),
                        )
                    }
                }
            }
        }
    }

    fun confirmDelete() {
        if (state.value.isDeleting) return
        val subnet = state.value.subnetToDelete ?: return

        updateState { it.copy(isDeleting = true, deleteErrorMessage = null) }

        launchJob("delete_subnet") {
            when (val result = deleteSubnetUseCase(subnet.id)) {
                is ApiResult.Success -> {
                    updateState {
                        it.copy(
                            subnetToDelete = null,
                            deleteImpact = null,
                            isDeleting = false,
                            deleteErrorMessage = null,
                            toastMessage =
                                UiText.Resource(
                                    Res.string.delete_subnet_success,
                                    listOf(subnet.name),
                                ),
                        )
                    }
                    loadSubnets()
                }
                is ApiResult.Error,
                is ApiResult.NetworkError,
                -> {
                    updateState {
                        it.copy(
                            isDeleting = false,
                            deleteErrorMessage = mapError(result, UiText.Resource(Res.string.delete_subnet_error)),
                        )
                    }
                }
            }
        }
    }

    fun dismissDelete() {
        updateState {
            it.copy(
                subnetToDelete = null,
                deleteImpact = null,
                isLoadingDeleteImpact = false,
                isDeleting = false,
                deleteErrorMessage = null,
            )
        }
    }
}
