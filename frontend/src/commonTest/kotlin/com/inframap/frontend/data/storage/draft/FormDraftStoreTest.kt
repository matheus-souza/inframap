package com.inframap.frontend.data.storage.draft

import com.inframap.frontend.data.storage.SessionOwnerStore
import com.inframap.frontend.fakes.FakeEpochClock
import com.inframap.frontend.fakes.FakeLocalStorage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Serializable
private data class SamplePayload(
    val text: String,
    val count: Int = 42,
)

@Serializable
private data class OtherPayload(
    @SerialName("other_id") val otherId: String,
)

class FormDraftStoreTest {
    private val storage = FakeLocalStorage()
    private val clock = FakeEpochClock(now = 1_000_000L)
    private val sessionOwner = SessionOwnerStore(storage)
    private val store = FormDraftStore(storage, clock, sessionOwner)

    @Test
    fun saveThenLoadReturnsSamePayload() {
        sessionOwner.setOwner("user-1")
        val sample = SamplePayload(text = "hello world", count = 10)

        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())
        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertEquals(sample, loaded)
    }

    @Test
    fun saveWritesEnvelopeWithOwnerVersionAndTimestamp() {
        sessionOwner.setOwner("user-1")
        clock.now = 5_000_000L
        val sample = SamplePayload(text = "draft test")

        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())
        val raw = storage.get(DraftForm.CreateSubnet.storageKey)

        assertNotNull(raw)
        val envelope = DraftJson.decodeFromString(DraftEnvelope.serializer(), raw)
        assertEquals(DRAFT_SCHEMA_VERSION, envelope.version)
        assertEquals("user-1", envelope.ownerUserId)
        assertEquals(5_000_000L, envelope.savedAtEpochMs)
    }

    @Test
    fun saveIsNoOpWhenOwnerUnknown() {
        // no owner set
        val sample = SamplePayload(text = "no owner")

        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())

        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
        assertNull(store.load(DraftForm.CreateSubnet, SamplePayload.serializer()))
    }

    @Test
    fun loadReturnsNullWhenNothingStored() {
        sessionOwner.setOwner("user-1")

        assertNull(store.load(DraftForm.CreateSubnet, SamplePayload.serializer()))
    }

    @Test
    fun loadJustBeforeTtlReturnsPayload() {
        sessionOwner.setOwner("user-1")
        clock.now = 10_000_000L
        val sample = SamplePayload(text = "active")
        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())

        // Advance to 29m 59.999s
        clock.advanceBy(DRAFT_TTL_MS - 1)
        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertEquals(sample, loaded)
    }

    @Test
    fun loadAtTtlBoundaryDiscardsAndReturnsNull() {
        sessionOwner.setOwner("user-1")
        clock.now = 10_000_000L
        val sample = SamplePayload(text = "expired")
        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())

        // Advance to exactly 30 minutes
        clock.advanceBy(DRAFT_TTL_MS)
        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadWithFutureTimestampDiscards() {
        sessionOwner.setOwner("user-1")
        clock.now = 10_000_000L
        val sample = SamplePayload(text = "clock skew")
        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())

        // Clock moved backwards (age < 0)
        clock.now = 9_000_000L
        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadFromDifferentOwnerDiscards() {
        sessionOwner.setOwner("user-1")
        val sample = SamplePayload(text = "user1-data")
        store.save(DraftForm.CreateSubnet, sample, SamplePayload.serializer())

        // Switch to user-2
        sessionOwner.setOwner("user-2")
        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadCorruptJsonDiscardsWithoutThrowing() {
        sessionOwner.setOwner("user-1")
        storage.set(DraftForm.CreateSubnet.storageKey, "{ not valid json")

        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadWithUnknownVersionDiscards() {
        sessionOwner.setOwner("user-1")
        val raw = """{"version":999,"owner_user_id":"user-1","saved_at_ms":${clock.now},"payload":{"text":"old"}}"""
        storage.set(DraftForm.CreateSubnet.storageKey, raw)

        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadWithMissingVersionDiscards() {
        sessionOwner.setOwner("user-1")
        val raw = """{"owner_user_id":"user-1","saved_at_ms":${clock.now},"payload":{"text":"no version"}}"""
        storage.set(DraftForm.CreateSubnet.storageKey, raw)

        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadWithIncompatiblePayloadDiscards() {
        sessionOwner.setOwner("user-1")
        val raw = """{"version":1,"owner_user_id":"user-1","saved_at_ms":${clock.now},"payload":"just a string"}"""
        storage.set(DraftForm.CreateSubnet.storageKey, raw)

        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertNull(loaded)
        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
    }

    @Test
    fun loadIgnoresUnknownPayloadKeys() {
        sessionOwner.setOwner("user-1")
        val raw =
            """{"version":1,"owner_user_id":"user-1","saved_at_ms":${clock.now},"payload":{"text":"ok","count":5,"unknown_field":"ignored"}}"""
        storage.set(DraftForm.CreateSubnet.storageKey, raw)

        val loaded = store.load(DraftForm.CreateSubnet, SamplePayload.serializer())

        assertEquals(SamplePayload(text = "ok", count = 5), loaded)
    }

    @Test
    fun discardRemovesOnlyThatForm() {
        sessionOwner.setOwner("user-1")
        store.save(DraftForm.CreateSubnet, SamplePayload("subnet"), SamplePayload.serializer())
        store.save(DraftForm.CreateDiscoverySource, OtherPayload("discovery"), OtherPayload.serializer())

        store.discard(DraftForm.CreateSubnet)

        assertNull(store.load(DraftForm.CreateSubnet, SamplePayload.serializer()))
        assertNotNull(store.load(DraftForm.CreateDiscoverySource, OtherPayload.serializer()))
    }

    @Test
    fun discardAllRemovesEveryDraftButKeepsOwner() {
        sessionOwner.setOwner("user-1")
        store.save(DraftForm.CreateSubnet, SamplePayload("subnet"), SamplePayload.serializer())
        store.save(DraftForm.CreateDiscoverySource, OtherPayload("discovery"), OtherPayload.serializer())

        store.discardAll()

        assertNull(store.load(DraftForm.CreateSubnet, SamplePayload.serializer()))
        assertNull(store.load(DraftForm.CreateDiscoverySource, OtherPayload.serializer()))
        assertEquals("user-1", sessionOwner.currentOwnerId())
    }

    @Test
    fun discardExpiredPurgesExpiredDraftsLeavingValidOnes() {
        sessionOwner.setOwner("user-1")
        clock.now = 1_000_000L
        store.save(DraftForm.CreateSubnet, SamplePayload("valid"), SamplePayload.serializer())

        // Move time past 30 min (1_800_000 ms)
        clock.now = 1_000_000L + 1_900_000L
        store.save(DraftForm.CreateDiscoverySource, OtherPayload("fresh"), OtherPayload.serializer())

        store.discardExpired()

        assertNull(storage.get(DraftForm.CreateSubnet.storageKey))
        assertNotNull(storage.get(DraftForm.CreateDiscoverySource.storageKey))
    }
}
