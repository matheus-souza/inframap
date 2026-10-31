package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DnsRecordDto(
    val id: String,
    val name: String,
    @SerialName("record_type") val recordType: String,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("address") val address: String?,
    @SerialName("target") val target: String?,
    @SerialName("notes") val notes: String?,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("updated_at") val updatedAt: String?,
)

@Serializable
data class CreateDnsRecordRequest(
    val name: String,
    @SerialName("record_type") val recordType: String,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("address") val address: String?,
    @SerialName("target") val target: String?,
    @SerialName("notes") val notes: String?,
)

@Serializable
data class UpdateDnsRecordRequest(
    val name: String?,
    @SerialName("record_type") val recordType: String?,
    @SerialName("device_id") val deviceId: String?,
    @SerialName("address") val address: String?,
    @SerialName("target") val target: String?,
    @SerialName("notes") val notes: String?,
)

@Serializable
data class DnsRecordListResponse(
    @SerialName("items") val items: List<DnsRecordDto>?,
    @SerialName("total") val total: Int,
)

@Serializable
data class DeleteDnsRecordResponse(
    @SerialName("deleted_id") val deletedId: String?,
)
