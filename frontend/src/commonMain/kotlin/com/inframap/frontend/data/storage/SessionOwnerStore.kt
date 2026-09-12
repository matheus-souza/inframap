package com.inframap.frontend.data.storage

class SessionOwnerStore(
    private val storage: LocalStorage,
) {
    fun currentOwnerId(): String? = storage.get(KEY_SESSION_OWNER)?.takeIf { it.isNotBlank() }

    fun setOwner(userId: String) {
        if (userId.isNotBlank()) {
            storage.set(KEY_SESSION_OWNER, userId.trim())
        }
    }

    fun clearOwner() {
        storage.remove(KEY_SESSION_OWNER)
    }

    companion object {
        const val KEY_SESSION_OWNER = "inframap_session_owner"
    }
}
