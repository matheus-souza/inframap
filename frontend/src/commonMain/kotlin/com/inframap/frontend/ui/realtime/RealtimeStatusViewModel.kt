package com.inframap.frontend.ui.realtime

import com.inframap.frontend.data.sse.SSEClient
import com.inframap.frontend.data.sse.SSEEvent
import com.inframap.frontend.ui.base.BaseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive

/**
 * State of the app's realtime event stream: `null` while the first connection is being
 * opened, then whether the stream is connected.
 */
data class RealtimeStatus(
    val isConnected: Boolean? = null,
)

/**
 * Holds the event stream behind the top bar's realtime badge. It keeps a connection of its
 * own for the lifetime of the main screens, so the badge shows the stream's real state on
 * every screen, including those that do not listen to events. Without an [SSEClient] the app
 * has no realtime stream and the badge reads disconnected.
 */
class RealtimeStatusViewModel(
    private val sseClient: SSEClient? = null,
    scope: CoroutineScope? = null,
) : BaseViewModel<RealtimeStatus>(RealtimeStatus(isConnected = if (sseClient == null) false else null), scope) {
    init {
        startListening()
    }

    private fun startListening() {
        val client = sseClient ?: return
        launchJob("sse_status") {
            // clear() cancels this job; the finally closes the browser's connection with it.
            try {
                while (isActive) {
                    client
                        .connect(STREAM_URL)
                        .takeWhile { it !is SSEEvent.Disconnected }
                        .collect { event ->
                            if (event is SSEEvent.Connected) updateState { it.copy(isConnected = true) }
                        }
                    updateState { it.copy(isConnected = false) }
                    client.disconnect()
                    delay(RECONNECT_DELAY_MS)
                }
            } finally {
                client.disconnect()
            }
        }
    }

    companion object {
        const val STREAM_URL = "/api/v1/events/stream"
        const val RECONNECT_DELAY_MS = 5_000L
    }
}
