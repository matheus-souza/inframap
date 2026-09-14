package com.inframap.frontend.ui.session

import com.inframap.frontend.data.storage.InterruptedRouteStore
import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.storage.draft.FormDraftStore

class SessionResume(
    private val sessionOwner: SessionOwnerStore,
    private val formDrafts: FormDraftStore,
    private val interruptedRouteStore: InterruptedRouteStore? = null,
) {
    /**
     * Determines whether the newly authenticated [userId] matches the previous session owner.
     *
     * - If [userId] is blank: purges all drafts and stored routes and returns false (R10).
     * - If previous owner matches [userId]: preserves drafts and returns true (R10, R20).
     * - If previous owner was absent or different: purges all drafts and stored routes, registers [userId] as new owner,
     *   and returns false (R4, R10).
     */
    fun onAuthenticated(userId: String): Boolean {
        val trimmed = userId.trim()
        if (trimmed.isNotEmpty() && sessionOwner.currentOwnerId() == trimmed) {
            formDrafts.discardExpired()
            return true
        }

        formDrafts.discardAll()
        interruptedRouteStore?.clear()
        if (trimmed.isNotEmpty()) {
            sessionOwner.setOwner(trimmed)
        } else {
            sessionOwner.clearOwner()
        }
        return false
    }
}
