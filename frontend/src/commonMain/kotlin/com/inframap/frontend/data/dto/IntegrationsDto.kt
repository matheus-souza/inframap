package com.inframap.frontend.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Payload for POST /api/v1/integrations/providers/{id}/health.
 *
 * The backend wraps the provider settings in a `config` object rather than taking them at
 * the top level, so the credentials never collide with envelope fields.
 */
@Serializable
data class ProviderHealthRequestDto(
    val config: Map<String, String>,
)

@Serializable
data class ProviderHealthResponseDto(
    @SerialName("provider_id") val providerId: String = "",
    val status: String = "",
    val message: String? = null,
)
