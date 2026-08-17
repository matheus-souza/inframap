package com.inframap.frontend.ui.base

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.error_generic
import com.inframap.frontend.generated.resources.error_network
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<S>(
    initialState: S,
    scope: CoroutineScope? = null,
) {
    private val ownsScope = scope == null
    protected val scope: CoroutineScope = scope ?: CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    protected val currentState: S get() = _state.value

    private val jobs = mutableMapOf<String, Job?>()

    protected fun updateState(reducer: (S) -> S) {
        _state.update(reducer)
    }

    protected fun cancelJob(key: String) {
        jobs[key]?.cancel()
        jobs.remove(key)
    }

    protected fun launchJob(
        key: String,
        cancelPrevious: Boolean = true,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (cancelPrevious) {
            jobs[key]?.cancel()
        }
        val job = scope.launch(block = block)
        jobs[key] = job
        job.invokeOnCompletion {
            if (jobs[key] == job) {
                jobs.remove(key)
            }
        }
    }

    protected fun <T> mapError(
        result: ApiResult<T>,
        fallbackMessage: UiText = UiText.Resource(Res.string.error_generic),
    ): UiText =
        when (result) {
            is ApiResult.NetworkError -> UiText.Resource(Res.string.error_network)
            is ApiResult.Error ->
                if (result.message.isNotEmpty()) {
                    UiText.DynamicString(result.message)
                } else {
                    fallbackMessage
                }

            is ApiResult.Success -> fallbackMessage
        }

    open fun clear() {
        jobs.values.forEach { it?.cancel() }
        jobs.clear()
        if (ownsScope) {
            scope.cancel()
        }
    }
}
