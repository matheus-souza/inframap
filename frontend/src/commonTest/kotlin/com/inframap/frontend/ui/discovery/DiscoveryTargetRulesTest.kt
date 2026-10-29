package com.inframap.frontend.ui.discovery

import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SubnetSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryTargetRulesTest {
    private fun targets(vararg cidrs: String) = cidrs.map { DiscoveryTarget(it) }

    @Test
    fun acceptsIpv4BlocksUpToSlash16IncludingHostBits() {
        assertTrue(DiscoveryTargetRules.isValid("10.0.0.0/16"))
        assertTrue(DiscoveryTargetRules.isValid("192.0.2.10/24"))
        assertTrue(DiscoveryTargetRules.isValid("192.0.2.1/32"))
    }

    @Test
    fun rejectsMalformedOrOversizedBlocks() {
        listOf(
            "10.0.0.0/15",
            "10.0.0.0/8",
            "10.0.0.0",
            "10.0.0/24",
            "256.0.0.0/24",
            "010.0.0.0/24",
            "10.0.0.0/033",
            "10.0.0.0/33",
            "2001:db8::/120",
            "",
            "a.b.c.d/24",
        ).forEach { assertFalse(DiscoveryTargetRules.isValid(it), it) }
    }

    @Test
    fun countsAddressesOfEveryTarget() {
        assertEquals(256L + 128L + 1L, DiscoveryTargetRules.addressCount(targets("10.0.0.0/24", "10.1.0.0/25", "10.2.0.1/32")))
    }

    @Test
    fun checkListFollowsTheBackendRules() {
        assertEquals(TargetRuleViolation.Required, DiscoveryTargetRules.checkList(emptyList(), required = true))
        assertNull(DiscoveryTargetRules.checkList(emptyList(), required = false))
        assertEquals(TargetRuleViolation.Invalid, DiscoveryTargetRules.checkList(targets("nope"), required = true))
        assertEquals(
            TargetRuleViolation.Overlap,
            DiscoveryTargetRules.checkList(targets("10.0.0.0/23", "10.0.1.0/24"), required = true),
        )
        assertEquals(
            TargetRuleViolation.Overlap,
            DiscoveryTargetRules.checkList(targets("192.0.2.0/24", "192.0.2.7/24"), required = true),
        )
        assertEquals(
            TargetRuleViolation.TooLarge,
            DiscoveryTargetRules.checkList(targets("10.1.0.0/16", "10.2.0.0/32"), required = true),
        )
        assertEquals(
            TargetRuleViolation.TooMany,
            DiscoveryTargetRules.checkList((0..32).map { DiscoveryTarget("10.0.$it.0/28") }, required = true),
        )
        assertEquals(
            TargetRuleViolation.SubnetNotFound,
            DiscoveryTargetRules.checkList(
                listOf(DiscoveryTarget("10.0.0.0/24", subnetId = "s", subnetDeleted = true)),
                required = true,
            ),
        )
        assertNull(DiscoveryTargetRules.checkList(targets("10.0.0.0/24", "10.0.1.0/24"), required = true))
    }

    @Test
    fun adjacentBlocksDoNotOverlap() {
        assertNull(DiscoveryTargetRules.checkAddition(targets("10.0.0.0/24"), DiscoveryTarget("10.0.1.0/24")))
    }

    @Test
    fun exactlyTheAddressLimitIsAllowed() {
        assertNull(DiscoveryTargetRules.checkAddition(targets("10.1.0.0/17"), DiscoveryTarget("10.2.0.0/17")))
    }

    @Test
    fun everyBackendCodeMapsToAViolation() {
        listOf(
            "error_targets_required",
            "error_target_invalid",
            "error_targets_overlap",
            "error_targets_too_large",
            "error_targets_too_many",
            "error_target_subnet_not_found",
        ).forEach { code -> assertEquals(code, TargetRuleViolation.fromCode(code)?.code) }
        assertNull(TargetRuleViolation.fromCode("secret_required_on_target_change"))
    }

    @Test
    fun reducersEditTheListInPlace() {
        val subnet = SubnetSummary(id = "sub-1", name = "Servers", cidr = "198.51.100.0/24")
        val linked = targets("192.0.2.0/24").toggledSubnet(subnet)
        assertNull(linked.violation)
        assertEquals(listOf("192.0.2.0/24", "198.51.100.0/24"), linked.targets.map { it.cidr })
        assertEquals(targets("192.0.2.0/24"), linked.targets.toggledSubnet(subnet).targets)
        assertEquals(targets("192.0.2.0/24"), linked.targets.withoutTarget(1))
        assertEquals(linked.targets, linked.targets.withoutTarget(5))
        assertEquals(TargetRuleViolation.Invalid, emptyList<DiscoveryTarget>().withCidr("x").violation)
    }

    @Test
    fun canonicalClearsHostBits() {
        assertEquals("10.99.0.0/24", DiscoveryTargetRules.canonical("10.99.0.5/24"))
        assertEquals("10.99.0.0/16", DiscoveryTargetRules.canonical(" 10.99.200.7/16 "))
        assertEquals("192.0.2.1/32", DiscoveryTargetRules.canonical("192.0.2.1/32"))
        assertEquals("198.51.100.128/25", DiscoveryTargetRules.canonical("198.51.100.200/25"))
        assertEquals("255.255.255.0/24", DiscoveryTargetRules.canonical("255.255.255.255/24"))
        assertNull(DiscoveryTargetRules.canonical("10.0.0.0/8"))
        assertNull(DiscoveryTargetRules.canonical("nope"))
    }

    @Test
    fun typedTargetIsStoredAsItsNetwork() {
        val added = emptyList<DiscoveryTarget>().withCidr(" 10.99.0.5/24 ")
        assertNull(added.violation)
        assertEquals(listOf(DiscoveryTarget("10.99.0.0/24")), added.targets)
        assertEquals(
            "10.99.0.0/24",
            added.targets
                .toRequestTargets()
                .single()
                .cidr,
        )
    }

    @Test
    fun typedTargetWithHostBitsStillOverlapsItsNetwork() {
        val existing = emptyList<DiscoveryTarget>().withCidr("10.99.0.5/24").targets
        assertEquals(TargetRuleViolation.Overlap, existing.withCidr("10.99.0.0/24").violation)
        assertEquals(existing, existing.withCidr("10.99.0.0/24").targets)
    }
}
