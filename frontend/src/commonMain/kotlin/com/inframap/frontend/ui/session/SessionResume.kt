package com.inframap.frontend.ui.session

import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.storage.draft.FormDraftStore

class SessionResume(
    private val sessionOwner: SessionOwnerStore,
    private val formDrafts: FormDraftStore,
) {
    /**
     * Determines whether the newly authenticated [userId] matches the previous session owner.
     *
     * - If [userId] is blank: purges all drafts and returns false (R10).
     * - If previous owner matches [userId]: preserves drafts and returns true (R10, R20).
     * - If previous owner was absent or different: purges all drafts, registers [userId] as new owner,
     *   and returns false (R4, R10).
     */
    fun onAuthenticated(userId: String): Boolean {
        val trimmed = userId.trim()
        if (trimmed.isNotEmpty() && sessionOwner.currentOwnerId() == trimmed) {
            formDrafts.discardExpired()
            return true
        }

        formDrafts.discardAll()
        if (trimmed.isNotEmpty()) {
            sessionOwner.setOwner(trimmed)
        } else {
            sessionOwner.clearOwner()
        }
        return false
    }
}
