package com.inframap.frontend.ui.session

import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.data.storage.draft.DraftForm
import com.inframap.frontend.data.storage.draft.FormDraftStore
import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeLocalStorage
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class TestDraft(
    val value: String,
)

class SessionResumeTest {
    private val storage = FakeLocalStorage()
    private val clock = FakeEpochClock(now = 1_000_000L)
    private val sessionOwner = SessionOwnerStore(storage)
    private val formDrafts = FormDraftStore(storage, clock, sessionOwner)
    private val sessionResume = SessionResume(sessionOwner, formDrafts)

    @Test
    fun firstLoginWithNoPreviousOwnerIsNotSameUserAndPurgesDrafts() {
        assertNull(sessionOwner.currentOwnerId())

        val resumed = sessionResume.onAuthenticated("user-alice")

        assertFalse(resumed)
        assertEquals("user-alice", sessionOwner.currentOwnerId())
    }

    @Test
    fun sameUserKeepsDraftsAndReturnsTrue() {
        sessionOwner.setOwner("user-alice")
        formDrafts.save(DraftForm.CreateSubnet, TestDraft("subnet-draft"), TestDraft.serializer())
        assertNotNull(formDrafts.load(DraftForm.CreateSubnet, TestDraft.serializer()))

        val resumed = sessionResume.onAuthenticated("user-alice")

        assertTrue(resumed)
        assertEquals("user-alice", sessionOwner.currentOwnerId())
        assertNotNull(formDrafts.load(DraftForm.CreateSubnet, TestDraft.serializer()))
    }

    @Test
    fun sameUserWithWhitespaceMatchesProperly() {
        sessionOwner.setOwner("user-alice")

        val resumed = sessionResume.onAuthenticated("  user-alice  ")

        assertTrue(resumed)
        assertEquals("user-alice", sessionOwner.currentOwnerId())
    }

    @Test
    fun differentUserPurgesAllDraftsAndBecomesOwner() {
        sessionOwner.setOwner("user-alice")
        formDrafts.save(DraftForm.CreateSubnet, TestDraft("subnet-draft"), TestDraft.serializer())
        formDrafts.save(DraftForm.CreateDiscoverySource, TestDraft("discovery-draft"), TestDraft.serializer())

        val resumed = sessionResume.onAuthenticated("user-bob")

        assertFalse(resumed)
        assertEquals("user-bob", sessionOwner.currentOwnerId())
        assertNull(formDrafts.load(DraftForm.CreateSubnet, TestDraft.serializer()))
        assertNull(formDrafts.load(DraftForm.CreateDiscoverySource, TestDraft.serializer()))
    }

    @Test
    fun blankUserIdPurgesAndDoesNotBecomeOwner() {
        sessionOwner.setOwner("user-alice")
        formDrafts.save(DraftForm.CreateSubnet, TestDraft("subnet-draft"), TestDraft.serializer())

        val resumed = sessionResume.onAuthenticated("   ")

        assertFalse(resumed)
        assertNull(sessionOwner.currentOwnerId())
        assertNull(formDrafts.load(DraftForm.CreateSubnet, TestDraft.serializer()))
    }

    @Test
    fun sameUserPurgesExpiredDraftsOnAuthentication() {
        sessionOwner.setOwner("user-alice")
        clock.now = 1_000_000L
        formDrafts.save(DraftForm.CreateSubnet, TestDraft("subnet-draft"), TestDraft.serializer())

        // Age beyond 30 min
        clock.now = 1_000_000L + 2_000_000L

        val resumed = sessionResume.onAuthenticated("user-alice")

        assertTrue(resumed)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }
}
