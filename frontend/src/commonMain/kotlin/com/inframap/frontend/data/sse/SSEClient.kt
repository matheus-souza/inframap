package com.inframap.frontend.data.sse

import kotlinx.coroutines.flow.SharedFlow

interface SSEClient {
    fun connect(url: String): SharedFlow<SSEEvent>

    fun disconnect()
}
