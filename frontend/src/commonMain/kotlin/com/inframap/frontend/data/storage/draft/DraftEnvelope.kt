package com.inframap.frontend.data.storage.draft

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal const val DRAFT_SCHEMA_VERSION = 1
const val DRAFT_TTL_MS: Long = 30L * 60L * 1000L

@Serializable
internal data class DraftEnvelope(
    val version: Int,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("saved_at_ms") val savedAtEpochMs: Long,
    val payload: JsonElement,
)

internal val DraftJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
