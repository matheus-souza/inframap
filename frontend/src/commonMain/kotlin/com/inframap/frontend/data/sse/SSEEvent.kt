package com.inframap.frontend.data.sse

sealed class SSEEvent {
    abstract val id: String

    data class DiscoveryProgress(
        override val id: String,
        val data: String,
    ) : SSEEvent()

    data class TopologyUpdated(
        override val id: String,
        val data: String,
    ) : SSEEvent()

    data class SystemNotification(
        override val id: String,
        val data: String,
    ) : SSEEvent()

    data class DeviceCreated(
        override val id: String,
        val data: String,
    ) : SSEEvent()

    data class DeviceUpdated(
        override val id: String,
        val data: String,
    ) : SSEEvent()

    data class Connected(
        override val id: String = "",
    ) : SSEEvent()

    data class Disconnected(
        override val id: String = "",
    ) : SSEEvent()

    data class Unknown(
        override val id: String,
        val type: String,
        val data: String,
    ) : SSEEvent()
}
