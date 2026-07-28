package com.inframap.frontend

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.ui.app.InfraMapApp

@JsFun("function() { return window.location.origin; }")
private external fun getOrigin(): String

@JsFun("function() { if (typeof window.infraMapReady === 'function') window.infraMapReady(); }")
private external fun notifyReady()

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val apiClient = ApiClient(baseUrl = getOrigin())
    CanvasBasedWindow(canvasElementId = "inframap-canvas", title = "InfraMap") {
        InfraMapApp(apiClient = apiClient)
    }
    notifyReady()
}
