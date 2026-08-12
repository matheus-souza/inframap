package com.inframap.frontend.ui.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

actual fun getCurrentTimeString(): String = LocalTime.now().format(timeFormatter)
