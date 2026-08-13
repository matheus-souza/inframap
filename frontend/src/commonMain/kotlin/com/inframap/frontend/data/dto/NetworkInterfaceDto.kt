package com.inframap.frontend.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class NetworkInterfaceDto(
    val name: String,
    val ip: String,
    val cidr: String,
    val mac: String,
    val gateway: String = "",
)
