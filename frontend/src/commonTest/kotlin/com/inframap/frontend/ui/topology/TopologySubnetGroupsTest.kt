package com.inframap.frontend.ui.topology

import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.model.TopologyNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopologySubnetGroupsTest {
    private val lan = SubnetSummary(id = "s-lan", name = "Home LAN", cidr = "192.0.2.0/24")
    private val lab = SubnetSummary(id = "s-lab", name = "Lab", cidr = "10.0.0.0/16")
    private val labRack = SubnetSummary(id = "s-rack", name = "Rack", cidr = "10.0.5.0/24")

    private fun node(
        id: String,
        ip: String?,
    ) = TopologyNode(id = id, label = id, deviceType = "host", status = "active", ipAddress = ip)

    @Test
    fun nodesAreGroupedByTheRegisteredSubnetThatContainsThem() {
        val groups =
            TopologySubnetGroups.group(
                nodes = listOf(node("router", "192.0.2.1"), node("nas", "192.0.2.20"), node("pve", "10.0.1.4")),
                subnets = listOf(lan, lab),
            )

        assertEquals(
            mapOf("Home LAN" to listOf("router", "nas"), "Lab" to listOf("pve")),
            groups.associate { it.subnet.name to it.nodeIds },
        )
    }

    @Test
    fun aNodeInsideNestedSubnetsBelongsToTheMostSpecificOne() {
        val groups = TopologySubnetGroups.group(listOf(node("switch", "10.0.5.2")), listOf(lab, labRack))

        assertEquals(listOf("Rack"), groups.map { it.subnet.name })
    }

    @Test
    fun nodesOutsideEverySubnetOrWithoutAnAddressGetNoBoundary() {
        val groups =
            TopologySubnetGroups.group(
                nodes = listOf(node("phone", "172.16.0.9"), node("vm", null), node("odd", "not-an-ip")),
                subnets = listOf(lan, SubnetSummary(id = "bad", name = "Bad", cidr = "garbage")),
            )

        assertTrue(groups.isEmpty())
    }
}
