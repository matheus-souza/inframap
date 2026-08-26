package com.inframap.frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.inframap.frontend.data.storage.BrowserLocalStorage
import com.inframap.frontend.data.storage.LocalStorage
import com.inframap.frontend.di.appModules
import com.inframap.frontend.ui.app.InfraMapApp
import kotlinx.browser.document
import org.koin.compose.KoinApplication
import org.koin.dsl.module

@JsFun("function() { return window.location.origin; }")
private external fun getOrigin(): String

@JsFun("function() { if (typeof window.infraMapReady === 'function') window.infraMapReady(); }")
private external fun notifyReady()

@JsFun(
    """
function() {
    var originalFetch = window.fetch;
    window.fetch = function(resource, init) {
        if (typeof resource === 'string' && resource.indexOf('composeResources/') !== -1) {
            return originalFetch.call(window, resource, init);
        }
        if (!init) { init = {}; }
        if (!init.credentials) { init.credentials = 'same-origin'; }
        return originalFetch.call(window, resource, init);
    };
}
""",
)
private external fun overrideFetchCredentials()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    overrideFetchCredentials()
    val baseUrl = getOrigin()
    println("[InfraMap-Startup] Initializing application at base URL: $baseUrl")

    val container = document.getElementById("inframap-app") ?: document.body ?: return
    println("[InfraMap-Startup] Attaching ComposeViewport to container: ${container.nodeName}")

    ComposeViewport(container) {
        val platformModule =
            module {
                single<LocalStorage> { BrowserLocalStorage() }
            }
        KoinApplication(application = { modules(appModules(baseUrl) + platformModule) }) {
            InfraMapApp()
        }
    }
    println("[InfraMap-Startup] Compose tree mounted successfully. Notifying ready.")
    notifyReady()
}
