package com.inframap.frontend.domain.model

data class Subnet(
    val id: String,
    val name: String,
    val cidr: String,
    val vlanId: Int? = null,
    val gatewayIp: String? = null,
    val description: String? = null,
    val discoveryEnabled: Boolean = false,
    val createdAt: String? = null,
)
