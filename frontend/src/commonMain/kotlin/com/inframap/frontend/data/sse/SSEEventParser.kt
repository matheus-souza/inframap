package com.inframap.frontend.data.sse

object SSEEventParser {
    /**
     * Event names the Discovery Sources module publishes when a source is created, updated or
     * deleted, or when one of its runs starts or finishes, whoever started it (operator,
     * scheduler, API). The backend gateway forwards the same names.
     */
    val DISCOVERY_SOURCE_EVENT_TYPES: List<String> =
        listOf(
            "discovery_source.created",
            "discovery_source.updated",
            "discovery_source.deleted",
            "discovery_source.scan.started",
            "discovery_source.run_finished",
        )

    /** Every event name the parser understands; the browser client listens to exactly these. */
    val EVENT_TYPES: List<String> =
        listOf(
            "discovery.progress",
            "topology.updated",
            "system.notification",
            "device.created",
            "device.updated",
        ) + DISCOVERY_SOURCE_EVENT_TYPES

    fun parse(
        type: String,
        id: String,
        data: String,
    ): SSEEvent =
        when (type) {
            "discovery.progress" -> SSEEvent.DiscoveryProgress(id, data)
            "topology.updated" -> SSEEvent.TopologyUpdated(id, data)
            "system.notification" -> SSEEvent.SystemNotification(id, data)
            "device.created" -> SSEEvent.DeviceCreated(id, data)
            "device.updated" -> SSEEvent.DeviceUpdated(id, data)
            in DISCOVERY_SOURCE_EVENT_TYPES -> SSEEvent.DiscoverySourceChanged(id, type, data)
            else -> SSEEvent.Unknown(id, type, data)
        }
}
