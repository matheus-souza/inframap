package com.inframap.frontend.data.storage.draft

import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.time.EpochClock
import kotlinx.serialization.KSerializer

class FormDraftStore(
    private val storage: LocalStorage,
    private val clock: EpochClock,
    private val sessionOwner: SessionOwnerStore,
) {
    fun <T> save(
        form: DraftForm,
        payload: T,
        serializer: KSerializer<T>,
    ) {
        val ownerId = sessionOwner.currentOwnerId() ?: return
        try {
            val jsonElement = DraftJson.encodeToJsonElement(serializer, payload)
            val envelope =
                DraftEnvelope(
                    version = DRAFT_SCHEMA_VERSION,
                    ownerUserId = ownerId,
                    savedAtEpochMs = clock.nowMillis(),
                    payload = jsonElement,
                )
            val json = DraftJson.encodeToString(DraftEnvelope.serializer(), envelope)
            storage.set(form.storageKey, json)
        } catch (_: Throwable) {
            // fail-soft: never throw from storage operations
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun <T> load(
        form: DraftForm,
        serializer: KSerializer<T>,
    ): T? {
        val raw = storage.get(form.storageKey)
        val currentOwner = sessionOwner.currentOwnerId()
        if (raw == null || currentOwner.isNullOrBlank()) {
            if (raw != null) storage.remove(form.storageKey)
            return null
        }

        return try {
            val envelope = DraftJson.decodeFromString(DraftEnvelope.serializer(), raw)
            if (!isValidEnvelope(envelope, currentOwner)) {
                storage.remove(form.storageKey)
                null
            } else {
                DraftJson.decodeFromJsonElement(serializer, envelope.payload)
            }
        } catch (_: Throwable) {
            storage.remove(form.storageKey)
            null
        }
    }

    private fun isValidEnvelope(
        envelope: DraftEnvelope,
        currentOwner: String,
    ): Boolean {
        if (envelope.version != DRAFT_SCHEMA_VERSION || envelope.ownerUserId != currentOwner) {
            return false
        }
        val age = clock.nowMillis() - envelope.savedAtEpochMs
        return age in 0 until DRAFT_TTL_MS
    }

    fun discard(form: DraftForm) {
        storage.remove(form.storageKey)
    }

    fun discardAll() {
        DraftForm.entries.forEach { storage.remove(it.storageKey) }
    }

    fun discardExpired() {
        val currentOwner = sessionOwner.currentOwnerId() ?: return
        DraftForm.entries.forEach { form ->
            val raw = storage.get(form.storageKey) ?: return@forEach
            try {
                val envelope = DraftJson.decodeFromString(DraftEnvelope.serializer(), raw)
                if (!isValidEnvelope(envelope, currentOwner)) {
                    storage.remove(form.storageKey)
                }
            } catch (_: Throwable) {
                storage.remove(form.storageKey)
            }
        }
    }
}
