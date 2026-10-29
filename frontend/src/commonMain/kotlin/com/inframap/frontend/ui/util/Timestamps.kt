package com.inframap.frontend.ui.util

/**
 * Formats an API timestamp (RFC 3339, e.g. `2026-10-29T19:25:46.868245Z`) for display as
 * `2026-10-29 19:25 UTC`, keeping a non-UTC offset as given. A value that is not such a
 * timestamp is returned unchanged.
 */
object Timestamps {
    private val RFC3339 = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:\d{2})$""")

    fun format(timestamp: String): String {
        val match = RFC3339.matchEntire(timestamp.trim()) ?: return timestamp
        val (date, time, zone) = match.destructured
        return "$date $time ${if (zone == "Z") "UTC" else zone}"
    }
}
