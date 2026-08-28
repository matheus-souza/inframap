package com.inframap.frontend.platform

import kotlinx.browser.document

actual fun updateBrowserTitle(screenTitle: String?) {
    document.title = if (screenTitle != null) "$screenTitle · InfraMap" else "InfraMap"
}
