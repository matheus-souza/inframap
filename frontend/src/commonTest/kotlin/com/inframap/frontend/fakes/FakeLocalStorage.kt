package com.inframap.frontend.fakes

import com.inframap.frontend.data.storage.LocalStorage

class FakeLocalStorage : LocalStorage {
    private val store = mutableMapOf<String, String>()

    override fun get(key: String): String? = store[key]

    override fun set(
        key: String,
        value: String,
    ) {
        store[key] = value
    }

    override fun remove(key: String) {
        store.remove(key)
    }
}
