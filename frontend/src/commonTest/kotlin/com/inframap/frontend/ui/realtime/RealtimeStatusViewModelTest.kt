package com.inframap.frontend.ui.realtime

import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.fakes.FakeSSEClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeStatusViewModelTest {
    @Test
    fun statusIsUnknownUntilTheStreamOpens() =
        runTest {
            val sse = FakeSSEClient()
            val vm = RealtimeStatusViewModel(sse, scope = this)
            runCurrent()
            assertNull(vm.state.value.isConnected)
            assertEquals(1, sse.connectCount)

            sse.events.emit(SSEEvent.Connected())
            runCurrent()
            assertEquals(true, vm.state.value.isConnected)
            vm.clear()
        }

    @Test
    fun aDroppedStreamReadsDisconnectedUntilItReconnects() =
        runTest {
            val sse = FakeSSEClient()
            val vm = RealtimeStatusViewModel(sse, scope = this)
            runCurrent()
            sse.events.emit(SSEEvent.Connected())
            runCurrent()

            sse.events.emit(SSEEvent.Disconnected())
            runCurrent()
            assertEquals(false, vm.state.value.isConnected)
            assertEquals(1, sse.disconnectCount)

            advanceTimeBy(RealtimeStatusViewModel.RECONNECT_DELAY_MS + 1)
            assertEquals(2, sse.connectCount)
            sse.events.emit(SSEEvent.Connected())
            runCurrent()
            assertEquals(true, vm.state.value.isConnected)
            vm.clear()
        }

    @Test
    fun clearingClosesTheStream() =
        runTest {
            val sse = FakeSSEClient()
            val vm = RealtimeStatusViewModel(sse, scope = this)
            runCurrent()

            vm.clear()
            runCurrent()
            assertEquals(1, sse.disconnectCount)
        }

    @Test
    fun withoutAClientTheStatusReadsDisconnected() =
        runTest {
            val vm = RealtimeStatusViewModel(sseClient = null, scope = this)
            runCurrent()
            assertEquals(false, vm.state.value.isConnected)
            vm.clear()
        }
}
