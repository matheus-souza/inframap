package com.inframap.frontend.domain.model

data class SubnetSummary(
    val id: String,
    val name: String,
    val cidr: String,
    val discoveryEnabled: Boolean = false,
)

fun Subnet.toSummary(): SubnetSummary =
    SubnetSummary(
        id = id,
        name = name,
        cidr = cidr,
        discoveryEnabled = discoveryEnabled,
    )
