package com.inframap.frontend.data.storage

import com.inframap.frontend.fakes.FakeLocalStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionOwnerStoreTest {
    @Test
    fun currentOwnerIsNullWhenNothingStored() {
        val storage = FakeLocalStorage()
        val store = SessionOwnerStore(storage)

        assertNull(store.currentOwnerId())
    }

    @Test
    fun setOwnerPersistsUnderSessionOwnerKey() {
        val storage = FakeLocalStorage()
        val store = SessionOwnerStore(storage)

        store.setOwner("user-123")

        assertEquals("user-123", store.currentOwnerId())
        assertEquals("user-123", storage.get(SessionOwnerStore.KEY_SESSION_OWNER))
    }

    @Test
    fun blankStoredOwnerReadsAsNull() {
        val storage = FakeLocalStorage()
        val store = SessionOwnerStore(storage)

        storage.set(SessionOwnerStore.KEY_SESSION_OWNER, "   ")

        assertNull(store.currentOwnerId())
    }

    @Test
    fun clearOwnerRemovesKeyFromStorage() {
        val storage = FakeLocalStorage()
        val store = SessionOwnerStore(storage)

        store.setOwner("user-123")
        store.clearOwner()

        assertNull(store.currentOwnerId())
        assertNull(storage.get(SessionOwnerStore.KEY_SESSION_OWNER))
    }
}
