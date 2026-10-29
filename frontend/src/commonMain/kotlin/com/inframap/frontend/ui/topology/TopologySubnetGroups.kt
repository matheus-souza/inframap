package com.inframap.frontend.ui.topology

import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.TopologyNode

/** Nodes drawn inside one registered Subnet's boundary on the topology canvas. */
data class SubnetGroup(
    val subnet: SubnetSummary,
    val nodeIds: List<String>,
)

/**
 * Places topology nodes in the registered Subnets that contain their IPv4 address. A node
 * inside several Subnets (a /16 holding a /24) belongs to the most specific one; a node with
 * no address, or outside every Subnet, is drawn without a boundary.
 */
object TopologySubnetGroups {
    fun group(
        nodes: List<TopologyNode>,
        subnets: List<SubnetSummary>,
    ): List<SubnetGroup> {
        val blocks = subnets.mapNotNull { subnet -> Ipv4Block.parse(subnet.cidr)?.let { subnet to it } }
        if (blocks.isEmpty()) return emptyList()

        return nodes
            .mapNotNull { node ->
                val address = node.ipAddress?.let(::parseIpv4) ?: return@mapNotNull null
                blocks
                    .filter { (_, block) -> address in block }
                    .maxByOrNull { (_, block) -> block.prefixLength }
                    ?.let { (subnet, _) -> subnet to node.id }
            }.groupBy({ it.first }, { it.second })
            .map { (subnet, nodeIds) -> SubnetGroup(subnet, nodeIds) }
    }

    private class Ipv4Block(
        private val network: Long,
        val prefixLength: Int,
    ) {
        private val mask: Long =
            if (prefixLength == 0) 0L else (IPV4_ALL_BITS shl (IPV4_BITS - prefixLength)) and IPV4_ALL_BITS

        operator fun contains(address: Long): Boolean = (address and mask) == (network and mask)

        companion object {
            private val CIDR = Regex("""^\s*([0-9.]+)/(\d{1,2})\s*$""")

            fun parse(cidr: String): Ipv4Block? {
                val match = CIDR.matchEntire(cidr) ?: return null
                val address = parseIpv4(match.groupValues[1])
                val prefix = match.groupValues[2].toInt()
                return if (address != null && prefix <= IPV4_BITS) Ipv4Block(address, prefix) else null
            }
        }
    }

    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    private fun parseIpv4(text: String): Long? {
        val octets =
            IPV4
                .matchEntire(text.trim())
                ?.groupValues
                ?.drop(1)
                ?.map { it.toInt() }
        return octets
            ?.takeIf { values -> values.all { it <= MAX_OCTET } }
            ?.fold(0L) { acc, octet -> (acc shl OCTET_BITS) or octet.toLong() }
    }

    private const val IPV4_BITS = 32
    private const val OCTET_BITS = 8
    private const val MAX_OCTET = 255
    private const val IPV4_ALL_BITS = 0xFFFFFFFFL
}
