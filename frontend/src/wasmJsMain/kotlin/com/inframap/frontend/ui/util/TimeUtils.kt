package com.inframap.frontend.ui.util

@JsFun("function() { return new Date().toTimeString().split(' ')[0]; }")
private external fun getCurrentTimeStringJs(): String

actual fun getCurrentTimeString(): String = getCurrentTimeStringJs()
