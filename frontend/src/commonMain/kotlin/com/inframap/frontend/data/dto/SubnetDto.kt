package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubnetDto(
    val id: String,
    val name: String,
    val cidr: String,
    val vlan: Int? = null,
    val gateway: String? = null,
    val description: String? = null,
    @SerialName("auto_discover") val autoDiscover: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreateSubnetRequest(
    val name: String,
    val cidr: String,
    val vlan: Int? = null,
    val gateway: String? = null,
    val description: String? = null,
    @SerialName("auto_discover") val autoDiscover: Boolean = false,
)
