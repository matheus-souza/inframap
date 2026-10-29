package com.inframap.frontend.ui.discovery

import com.inframap.frontend.data.dto.DiscoveryTargetRequestDto
import com.inframap.frontend.domain.model.DiscoveryTarget
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.error_target_invalid
import com.inframap.frontend.generated.resources.error_target_subnet_not_found
import com.inframap.frontend.generated.resources.error_targets_overlap
import com.inframap.frontend.generated.resources.error_targets_required
import com.inframap.frontend.generated.resources.error_targets_too_large
import com.inframap.frontend.generated.resources.error_targets_too_many
import com.inframap.frontend.ui.util.UiText
import org.jetbrains.compose.resources.StringResource

/** Validation key of the target list as a whole (required, overlap, limits). */
internal const val TARGETS_ERROR_KEY = "targets"

/** Validation key of the free-form CIDR input of the targets editor. */
internal const val TARGET_INPUT_ERROR_KEY = "target_input"

/**
 * The target rules of RFC-026 §2, each with the error code the backend answers with. The
 * editor applies the same rules locally so the operator sees the problem before submitting.
 */
enum class TargetRuleViolation(
    val code: String,
    private val resource: StringResource,
) {
    Required("error_targets_required", Res.string.error_targets_required),
    Invalid("error_target_invalid", Res.string.error_target_invalid),
    Overlap("error_targets_overlap", Res.string.error_targets_overlap),
    TooLarge("error_targets_too_large", Res.string.error_targets_too_large),
    TooMany("error_targets_too_many", Res.string.error_targets_too_many),
    SubnetNotFound("error_target_subnet_not_found", Res.string.error_target_subnet_not_found),
    ;

    val message: UiText get() = UiText.Resource(resource)

    companion object {
        /** The violation behind a backend 422 `error.code`, or null for any other code. */
        fun fromCode(code: String): TargetRuleViolation? = entries.firstOrNull { it.code == code }
    }
}

/** An IPv4 block reduced to its network address, so overlap is a range comparison. */
private data class Ipv4Block(
    val first: Long,
    val bits: Int,
) {
    val size: Long get() = 1L shl (IPV4_BITS - bits)
    val last: Long get() = first + size - 1

    fun overlaps(other: Ipv4Block): Boolean = first <= other.last && other.first <= last

    /** The block in CIDR notation with its host bits cleared, e.g. `10.99.0.0/24`. */
    override fun toString(): String {
        val octets = (OCTET_COUNT - 1 downTo 0).map { octet -> (first shr (octet * OCTET_BITS)) and OCTET_MAX.toLong() }
        return octets.joinToString(".") + "/$bits"
    }
}

private const val IPV4_BITS = 32
private const val OCTET_MAX = 255
private const val OCTET_BITS = 8
private const val OCTET_COUNT = 4

object DiscoveryTargetRules {
    /** Most targets a source may hold (`dto.MaxSourceTargets`). */
    const val MAX_TARGETS = 32

    /** Summed address count across all targets: the size of one /16 (`dto.MaxSourceTargetAddresses`). */
    const val MAX_ADDRESSES = 65_536L

    /** Shortest prefix a single target may have (`collectors.ParseTargetPrefix`). */
    const val MIN_PREFIX_BITS = 16

    /** Whether [cidr] is an IPv4 CIDR no larger than /16. Host bits may be set, as on the backend. */
    fun isValid(cidr: String): Boolean = parse(cidr) != null

    /**
     * [cidr] reduced to the network it names, `10.99.0.5/24` becoming `10.99.0.0/24`, or null
     * when it is not a valid target. A typed target is stored in this form, so the list, the
     * overlap checks and the request all carry the block the backend scans.
     */
    fun canonical(cidr: String): String? = parse(cidr)?.toString()

    /** Summed address count of the targets, ignoring any that do not parse. */
    fun addressCount(targets: List<DiscoveryTarget>): Long = targets.mapNotNull { parse(it.cidr) }.sumOf { it.size }

    /**
     * Checks [candidate] against the targets already in the list: the first rule it breaks,
     * or null when it can be appended.
     */
    fun checkAddition(
        existing: List<DiscoveryTarget>,
        candidate: DiscoveryTarget,
    ): TargetRuleViolation? {
        val block = parse(candidate.cidr) ?: return TargetRuleViolation.Invalid
        return when {
            existing.size >= MAX_TARGETS -> TargetRuleViolation.TooMany
            existing.any { other -> parse(other.cidr)?.overlaps(block) == true } -> TargetRuleViolation.Overlap
            addressCount(existing) + block.size > MAX_ADDRESSES -> TargetRuleViolation.TooLarge
            else -> null
        }
    }

    /**
     * Checks a whole list in the backend's order. [required] is `requiresCidr` of the plan:
     * a plan made only of providers is valid with no target.
     */
    fun checkList(
        targets: List<DiscoveryTarget>,
        required: Boolean,
    ): TargetRuleViolation? {
        val blocks = targets.map { parse(it.cidr) }
        val parsed = blocks.filterNotNull()
        return when {
            targets.isEmpty() -> if (required) TargetRuleViolation.Required else null
            targets.size > MAX_TARGETS -> TargetRuleViolation.TooMany
            parsed.size < blocks.size -> TargetRuleViolation.Invalid
            parsed.withIndex().any { (i, block) -> parsed.take(i).any { it.overlaps(block) } } ->
                TargetRuleViolation.Overlap
            parsed.sumOf { it.size } > MAX_ADDRESSES -> TargetRuleViolation.TooLarge
            targets.any { it.subnetDeleted } -> TargetRuleViolation.SubnetNotFound
            else -> null
        }
    }

    private fun parse(cidr: String): Ipv4Block? {
        val numbers = CIDR_PATTERN.matchEntire(cidr.trim())?.groupValues?.drop(1) ?: return null
        val octets = numbers.take(OCTET_COUNT).mapNotNull { it.toCanonicalInt()?.takeIf { v -> v in 0..OCTET_MAX } }
        val bits = numbers[OCTET_COUNT].toCanonicalInt()?.takeIf { it in MIN_PREFIX_BITS..IPV4_BITS }
        return if (bits != null && octets.size == OCTET_COUNT) {
            val address = octets.fold(0L) { acc, octet -> (acc shl OCTET_BITS) or octet.toLong() }
            val size = 1L shl (IPV4_BITS - bits)
            Ipv4Block(first = address - address % size, bits = bits)
        } else {
            null
        }
    }

    // netip.ParsePrefix rejects leading zeros ("010"), so the editor does too.
    private fun String.toCanonicalInt(): Int? = takeIf { length == 1 || first() != '0' }?.toIntOrNull()
}

private val CIDR_PATTERN = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})/(\d{1,2})""")

/** A registered Subnet as a linked target; its CIDR is the display snapshot of RFC-026 §1. */
internal fun SubnetSummary.toTarget(): DiscoveryTarget = DiscoveryTarget(cidr = cidr, subnetId = id, subnetName = name)

/** The request form of a target list, preserving order. */
internal fun List<DiscoveryTarget>.toRequestTargets(): List<DiscoveryTargetRequestDto> =
    map { DiscoveryTargetRequestDto(cidr = it.cidr.trim(), subnetId = it.subnetId) }

/** Result of a reducer step of the targets editor: the new list, or why it was refused. */
internal data class TargetsEdit(
    val targets: List<DiscoveryTarget>,
    val violation: TargetRuleViolation? = null,
)

/**
 * Adds a free-form CIDR at the end of the list in its canonical form (host bits cleared),
 * unless it breaks a rule.
 */
internal fun List<DiscoveryTarget>.withCidr(cidr: String): TargetsEdit {
    val candidate = DiscoveryTarget(cidr = DiscoveryTargetRules.canonical(cidr) ?: cidr.trim())
    val violation = DiscoveryTargetRules.checkAddition(this, candidate)
    return if (violation == null) TargetsEdit(this + candidate) else TargetsEdit(this, violation)
}

/**
 * Selecting a Subnet chip links it as a target; selecting it again unlinks it. A Subnet that
 * would overlap an existing target, or break a limit, is refused.
 */
internal fun List<DiscoveryTarget>.toggledSubnet(subnet: SubnetSummary): TargetsEdit {
    if (any { it.subnetId == subnet.id }) return TargetsEdit(filterNot { it.subnetId == subnet.id })
    val candidate = subnet.toTarget()
    val violation = DiscoveryTargetRules.checkAddition(this, candidate)
    return if (violation == null) TargetsEdit(this + candidate) else TargetsEdit(this, violation)
}

/** Removes the target at [index]; the order of the others is kept. */
internal fun List<DiscoveryTarget>.withoutTarget(index: Int): List<DiscoveryTarget> =
    if (index in indices) filterIndexed { i, _ -> i != index } else this
