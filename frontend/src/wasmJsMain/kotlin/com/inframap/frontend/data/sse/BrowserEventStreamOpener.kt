package com.inframap.frontend.data.sse

@JsFun(
    """
function(url, eventTypes, onOpen, onError, onEvent) {
    var es = new EventSource(url);
    es.onopen = function() { onOpen(); };
    es.onerror = function() { onError(); };
    var types = eventTypes.split(',');
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
    eventTypes: String,
    onOpen: () -> Unit,
    onError: () -> Unit,
    onEvent: (String, String, String) -> Unit,
): JsAny

@JsFun("function(es) { es.close(); }")
private external fun jsCloseEventSource(es: JsAny)

/** Opens the tab's event stream with the browser's EventSource (see [SharedEventStream]). */
class BrowserEventStreamOpener : EventStreamOpener {
    override fun open(
        url: String,
        onOpen: () -> Unit,
        onError: () -> Unit,
        onEvent: (id: String, type: String, data: String) -> Unit,
    ): EventStreamConnection {
        val eventSource =
            jsCreateEventSource(
                url,
                // A named SSE event reaches only the listeners registered for its name.
                SSEEventParser.EVENT_TYPES.joinToString(","),
                onOpen,
                onError,
                onEvent,
            )
        return EventStreamConnection { jsCloseEventSource(eventSource) }
    }
}
