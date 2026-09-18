package com.inframap.frontend.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    @SerialName("splash")
    data object Splash : Route

    @Serializable
    @SerialName("login")
    data object Login : Route

    @Serializable
    @SerialName("onboarding")
    data object Onboarding : Route

    @Serializable
    @SerialName("dashboard")
    data object Dashboard : Route

    @Serializable
    @SerialName("devices")
    data object Devices : Route

    @Serializable
    @SerialName("device_detail")
    data class DeviceDetail(
        val id: String,
    ) : Route

    @Serializable
    @SerialName("create_device")
    data object CreateDevice : Route

    @Serializable
    @SerialName("edit_device")
    data class EditDevice(
        val id: String,
    ) : Route

    @Serializable
    @SerialName("staging")
    data object Staging : Route

    @Serializable
    @SerialName("subnets")
    data object Subnets : Route

    @Serializable
    @SerialName("create_subnet")
    data class CreateSubnet(
        val prefilledCidr: String? = null,
        val prefilledName: String? = null,
    ) : Route

    @Serializable
    @SerialName("edit_subnet")
    data class EditSubnet(
        val id: String,
    ) : Route

    @Serializable
    @SerialName("discovery_sources")
    data object DiscoverySources : Route

    @Serializable
    @SerialName("create_discovery_source")
    data object CreateDiscoverySource : Route

    @Serializable
    @SerialName("edit_discovery_source")
    data class EditDiscoverySource(
        val id: String,
    ) : Route

    @Serializable
    @SerialName("topology")
    data object Topology : Route
}
