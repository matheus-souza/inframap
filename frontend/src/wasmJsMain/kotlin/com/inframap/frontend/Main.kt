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

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = getOrigin()
    val body = document.body ?: return
    ComposeViewport(body) {
        val platformModule =
            module {
                single<LocalStorage> { BrowserLocalStorage() }
            }
        KoinApplication(application = { modules(appModules(baseUrl) + platformModule) }) {
            InfraMapApp()
        }
    }
    notifyReady()
}
