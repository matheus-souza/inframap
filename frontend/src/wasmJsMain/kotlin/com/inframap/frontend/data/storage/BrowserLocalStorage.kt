package com.inframap.frontend.data.storage

@JsFun("function(key) { return window.localStorage.getItem(key); }")
private external fun jsGetItem(key: String): String?

@JsFun("function(key, value) { window.localStorage.setItem(key, value); }")
private external fun jsSetItem(
    key: String,
    value: String,
)

@JsFun("function(key) { window.localStorage.removeItem(key); }")
private external fun jsRemoveItem(key: String)

class BrowserLocalStorage : LocalStorage {
    override fun get(key: String): String? = jsGetItem(key)

    override fun set(
        key: String,
        value: String,
    ) = jsSetItem(key, value)

    override fun remove(key: String) = jsRemoveItem(key)
}
