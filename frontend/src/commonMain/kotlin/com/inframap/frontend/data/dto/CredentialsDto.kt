package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CredentialDto(
    val id: String,
    val name: String = "",
    val type: String = "",
    val description: String? = null,
)

@Suppress("ConstructorParameterNaming")
@Serializable
data class CredentialListResponse(
    @SerialName("items") private val _items: List<CredentialDto>? = null,
    val total: Long = 0,
) {
    val items: List<CredentialDto> get() = _items ?: emptyList()
}
