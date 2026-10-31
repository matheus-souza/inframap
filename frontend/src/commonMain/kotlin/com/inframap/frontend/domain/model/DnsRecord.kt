package com.inframap.frontend.domain.model

import kotlinx.serialization.SerialName

data class DnsRecord(
    val id: String,
    val name: String,
    @SerialName("record_type") val recordType: String,
    val deviceId: String?,
    val address: String?,
    val target: String?,
    val notes: String?,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("updated_at") val updatedAt: String?,
)
