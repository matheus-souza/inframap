package com.inframap.frontend.domain.model

/**
 * How a device's address is assigned (RFC-027): the operator's record of whether DHCP hands it
 * out, reserves it for the device, or the device is configured statically. Discovery never
 * sets it.
 */
object IpAssignments {
    const val UNKNOWN = "unknown"
    const val DYNAMIC = "dynamic"
    const val RESERVED = "reserved"
    const val STATIC = "static"

    val ALL: List<String> = listOf(UNKNOWN, DYNAMIC, RESERVED, STATIC)

    /** Reserved and static addresses are the ones an operator can rely on. */
    fun isFixed(assignment: String): Boolean = assignment == RESERVED || assignment == STATIC
}
