package com.inframap.frontend.data.sse

object SSEEventParser {
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
            else -> SSEEvent.Unknown(id, type, data)
        }
}
