package com.inframap.frontend.ui.base

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.designsystem.resources.Res
import com.inframap.frontend.ui.util.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private data class TestState(
    val count: Int = 0,
    val text: String = "",
)

private class TestViewModel(
    scope: CoroutineScope,
) : BaseViewModel<TestState>(TestState(), scope) {
    fun increment() {
        updateState { copy(count = count + 1) }
    }

    fun startJob(
        key: String,
        delayMs: Long = 100L,
    ) {
        launchJob(key) {
            delay(delayMs)
            updateState { copy(text = "Job completed") }
        }
    }

    fun testMapError(result: ApiResult<Unit>): UiText = mapError(result)
}

class BaseViewModelTest {
    @Test
    fun initialStateIsSetCorrectly() =
        runTest {
            val vm = TestViewModel(this)
            assertEquals(TestState(0, ""), vm.state.value)
            vm.clear()
        }

    @Test
    fun updateStateMutatesStateCorrectly() =
        runTest {
            val vm = TestViewModel(this)
            vm.increment()
            assertEquals(1, vm.state.value.count)
            vm.increment()
            assertEquals(2, vm.state.value.count)
            vm.clear()
        }

    @Test
    fun launchJobExecutesAndUpdatesState() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val scope = TestScope(testDispatcher)
            val vm = TestViewModel(scope)

            vm.startJob("testKey", 50L)
            assertEquals("", vm.state.value.text)

            testDispatcher.scheduler.advanceTimeBy(100L)
            testDispatcher.scheduler.runCurrent()

            assertEquals("Job completed", vm.state.value.text)
            vm.clear()
        }

    @Test
    fun mapErrorReturnsNetworkErrorOnNetworkErrorResult() =
        runTest {
            val vm = TestViewModel(this)
            val result = ApiResult.NetworkError(RuntimeException("Network error"))
            val uiText = vm.testMapError(result)

            assertIs<UiText.Resource>(uiText)
            assertEquals(Res.string.error_network, uiText.resId)
            vm.clear()
        }

    @Test
    fun mapErrorReturnsDynamicStringOnApiErrorWithMessage() =
        runTest {
            val vm = TestViewModel(this)
            val result = ApiResult.Error(code = "BAD_REQUEST", message = "Invalid input", requestId = "123", httpStatus = 400)
            val uiText = vm.testMapError(result)

            assertIs<UiText.DynamicString>(uiText)
            assertEquals("Invalid input", uiText.value)
            vm.clear()
        }

    @Test
    fun mapErrorReturnsFallbackOnApiErrorWithoutMessage() =
        runTest {
            val vm = TestViewModel(this)
            val result = ApiResult.Error(code = "INTERNAL", message = "", requestId = "123", httpStatus = 500)
            val uiText = vm.testMapError(result)

            assertIs<UiText.Resource>(uiText)
            assertEquals(Res.string.error_generic, uiText.resId)
            vm.clear()
        }
}
