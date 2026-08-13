package com.inframap.frontend.domain.model

data class NetworkInterface(
    val name: String,
    val ip: String,
    val cidr: String,
    val mac: String,
    val gateway: String = "",
)
