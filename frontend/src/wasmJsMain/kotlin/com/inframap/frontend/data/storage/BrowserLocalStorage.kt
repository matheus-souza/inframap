package com.inframap.frontend.data.storage

@JsFun("function(key) { try { return window.localStorage.getItem(key); } catch(e) { return null; } }")
private external fun jsGetItem(key: String): String?

@JsFun("function(key, value) { try { window.localStorage.setItem(key, value); } catch(e) {} }")
private external fun jsSetItem(
    key: String,
    value: String,
)

@JsFun("function(key) { try { window.localStorage.removeItem(key); } catch(e) {} }")
private external fun jsRemoveItem(key: String)

class BrowserLocalStorage : LocalStorage {
    private val fallback = mutableMapOf<String, String>()

    override fun get(key: String): String? =
        try {
            jsGetItem(key) ?: fallback[key]
        } catch (_: Throwable) {
            fallback[key]
        }

    override fun set(
        key: String,
        value: String,
    ) {
        try {
            jsSetItem(key, value)
        } catch (_: Throwable) {
            // browser storage unavailable
        }
        fallback[key] = value
    }

    override fun remove(key: String) {
        try {
            jsRemoveItem(key)
        } catch (_: Throwable) {
            // browser storage unavailable
        }
        fallback.remove(key)
    }
}
