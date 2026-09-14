package com.inframap.frontend.data.storage

import com.inframap.frontend.data.storage.draft.DRAFT_SCHEMA_VERSION
import com.inframap.frontend.data.storage.draft.DRAFT_TTL_MS
import com.inframap.frontend.data.time.EpochClock
import com.inframap.frontend.navigation.Route
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class InterruptedRouteEnvelope(
    val version: Int,
    @SerialName("owner_user_id") val ownerUserId: String,
    @SerialName("saved_at_ms") val savedAtEpochMs: Long,
    val route: Route,
)

private val RouteJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

const val ROUTE_TTL_MS: Long = DRAFT_TTL_MS

class InterruptedRouteStore(
    private val storage: LocalStorage,
    private val clock: EpochClock,
    private val sessionOwner: SessionOwnerStore,
) {
    fun save(route: Route) {
        val ownerId = sessionOwner.currentOwnerId() ?: return
        try {
            val envelope =
                InterruptedRouteEnvelope(
                    version = DRAFT_SCHEMA_VERSION,
                    ownerUserId = ownerId,
                    savedAtEpochMs = clock.nowMillis(),
                    route = route,
                )
            val json = RouteJson.encodeToString(InterruptedRouteEnvelope.serializer(), envelope)
            storage.set(KEY_INTERRUPTED_ROUTE, json)
        } catch (_: Throwable) {
            // fail-soft: never throw from storage operations
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun restore(): Route? {
        val raw = storage.get(KEY_INTERRUPTED_ROUTE)
        val currentOwner = sessionOwner.currentOwnerId()
        if (raw == null || currentOwner.isNullOrBlank()) {
            if (raw != null) storage.remove(KEY_INTERRUPTED_ROUTE)
            return null
        }

        return try {
            val envelope = RouteJson.decodeFromString(InterruptedRouteEnvelope.serializer(), raw)
            if (!isValidEnvelope(envelope, currentOwner)) {
                storage.remove(KEY_INTERRUPTED_ROUTE)
                null
            } else {
                storage.remove(KEY_INTERRUPTED_ROUTE)
                envelope.route
            }
        } catch (_: Throwable) {
            storage.remove(KEY_INTERRUPTED_ROUTE)
            null
        }
    }

    private fun isValidEnvelope(
        envelope: InterruptedRouteEnvelope,
        currentOwner: String,
    ): Boolean {
        if (envelope.version != DRAFT_SCHEMA_VERSION || envelope.ownerUserId != currentOwner) {
            return false
        }
        val age = clock.nowMillis() - envelope.savedAtEpochMs
        return age in 0 until DRAFT_TTL_MS
    }

    fun clear() {
        storage.remove(KEY_INTERRUPTED_ROUTE)
    }

    companion object {
        const val KEY_INTERRUPTED_ROUTE = "inframap_interrupted_route"
    }
}
