package com.inframap.frontend.data.storage

interface LocalStorage {
    fun get(key: String): String?

    fun set(
        key: String,
        value: String,
    )

    fun remove(key: String)
}
