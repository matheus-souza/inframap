package com.inframap.frontend.fakes

import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeSSEClient : SSEClient {
    val events = MutableSharedFlow<SSEEvent>()
    var connectCount = 0

    override fun connect(url: String): SharedFlow<SSEEvent> {
        connectCount++
        return events.asSharedFlow()
    }

    override fun disconnect() {}
}
