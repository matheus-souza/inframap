package com.inframap.frontend.data.sse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/** An open event stream connection; closing it ends the stream. */
fun interface EventStreamConnection {
    fun close()
}

/** Opens the platform's event stream connection (the browser's EventSource). */
fun interface EventStreamOpener {
    fun open(
        url: String,
        onOpen: () -> Unit,
        onError: () -> Unit,
        onEvent: (id: String, type: String, data: String) -> Unit,
    ): EventStreamConnection
}

private const val EXTRA_BUFFER_CAPACITY = 64

/**
 * The one event stream connection of a browser tab, shared by every [SSEClient] in it.
 *
 * A browser allows six HTTP/1.1 connections per host, and an event stream holds its
 * connection for as long as it is open. With a connection per listener (the top bar badge and
 * the current screen), three tabs used all six and every other request to the server queued
 * forever, so pages stayed on their loading placeholders. The connection opens with its first
 * client, closes with its last, and a dropped connection is reported to every client, which
 * reopens it by connecting again.
 */
class SharedEventStream(
    private val scope: CoroutineScope,
    private val opener: EventStreamOpener,
) {
    private val events = MutableSharedFlow<SSEEvent>(extraBufferCapacity = EXTRA_BUFFER_CAPACITY)
    private var connection: EventStreamConnection? = null
    private var isOpen = false
    private var clients = 0
    private var lastEventId: String? = null

    /**
     * Adds a client, opening the connection when there is none. A client that joins an open
     * connection is told it is connected, since the connection's own open event is long gone.
     */
    fun acquire(url: String): SharedFlow<SSEEvent> {
        clients++
        if (connection == null) open(url)
        return events.onSubscription { if (isOpen) emit(SSEEvent.Connected()) }
    }

    /** Removes a client, closing the connection with the last one. */
    fun release() {
        if (clients == 0) return
        clients--
        if (clients == 0) close()
    }

    private fun open(url: String) {
        val connectUrl = lastEventId?.let { "$url?last_event_id=$it" } ?: url
        connection =
            opener.open(
                connectUrl,
                onOpen = {
                    isOpen = true
                    scope.launch { events.emit(SSEEvent.Connected()) }
                },
                onError = {
                    close()
                    scope.launch { events.emit(SSEEvent.Disconnected()) }
                },
                onEvent = { id, type, data ->
                    if (id.isNotEmpty()) lastEventId = id
                    scope.launch { events.emit(SSEEventParser.parse(type, id, data)) }
                },
            )
    }

    private fun close() {
        connection?.close()
        connection = null
        isOpen = false
    }
}

/** A listener's handle on the tab's [SharedEventStream]; each ViewModel holds its own. */
class SharedSSEClient(
    private val stream: SharedEventStream,
) : SSEClient {
    private var holding = false

    override fun connect(url: String): SharedFlow<SSEEvent> {
        if (holding) stream.release()
        holding = true
        return stream.acquire(url)
    }

    override fun disconnect() {
        if (!holding) return
        holding = false
        stream.release()
    }
}
