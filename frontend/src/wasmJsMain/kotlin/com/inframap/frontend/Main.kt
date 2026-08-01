package com.inframap.frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.inframap.frontend.di.appModules
import com.inframap.frontend.ui.app.InfraMapApp
import org.koin.compose.KoinApplication

@JsFun("function() { return window.location.origin; }")
private external fun getOrigin(): String

@JsFun("function() { if (typeof window.infraMapReady === 'function') window.infraMapReady(); }")
private external fun notifyReady()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val baseUrl = getOrigin()
    CanvasBasedWindow(canvasElementId = "inframap-canvas", title = "InfraMap") {
        KoinApplication(application = { modules(appModules(baseUrl)) }) {
            InfraMapApp()
        }
    }
    notifyReady()
}
