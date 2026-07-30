package com.inframap.frontend.data.sse

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@JsFun(
    """
function(url, onOpen, onError, onEvent) {
    var es = new EventSource(url);
    es.onopen = function() { onOpen(); };
    es.onerror = function() { onError(); };
    var types = [
        'discovery.progress', 'topology.updated',
        'system.notification', 'device.created', 'device.updated'
    ];
    for (var i = 0; i < types.length; i++) {
        (function(type) {
            es.addEventListener(type, function(e) {
                onEvent(e.lastEventId || '', type, e.data);
            });
        })(types[i]);
    }
    return es;
}
""",
)
private external fun jsCreateEventSource(
    url: String,
    onOpen: () -> Unit,
    onError: () -> Unit,
    onEvent: (String, String, String) -> Unit,
): JsAny

@JsFun("function(es) { es.close(); }")
private external fun jsCloseEventSource(es: JsAny)

private const val EXTRA_BUFFER_CAPACITY = 64

class BrowserSSEClient(
    private val scope: CoroutineScope,
) : SSEClient {
    private val eventFlow = MutableSharedFlow<SSEEvent>(extraBufferCapacity = EXTRA_BUFFER_CAPACITY)
    private var eventSource: JsAny? = null
    private var lastEventId: String? = null

    override fun connect(url: String): SharedFlow<SSEEvent> {
        disconnect()
        val connectUrl =
            lastEventId?.let { "$url?last_event_id=$it" } ?: url
        eventSource =
            jsCreateEventSource(
                connectUrl,
                onOpen = {
                    scope.launch { eventFlow.emit(SSEEvent.Connected()) }
                },
                onError = {
                    disconnect()
                    scope.launch { eventFlow.emit(SSEEvent.Disconnected()) }
                },
                onEvent = { id, type, data ->
                    if (id.isNotEmpty()) lastEventId = id
                    scope.launch { eventFlow.emit(SSEEventParser.parse(type, id, data)) }
                },
            )
        return eventFlow.asSharedFlow()
    }

    override fun disconnect() {
        eventSource?.let { jsCloseEventSource(it) }
        eventSource = null
    }
}
