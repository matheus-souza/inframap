# ADR-015 — Multi-Target Discovery Sources

| Status | Accepted |
|---|---|
| Created | 2026-10-27 |

---

## Context

A Discovery Source is a plan of collectors (ADR-011) aimed at exactly one CIDR, stored as `config.cidr` in the encrypted source configuration and passed to every collector as `collectors.DiscoveryTarget{CIDR}`. Operators with several subnets that should be scanned the same way have to create one source per subnet, duplicating schedule, collectors and credentials, and the dashboard auto-setup creates one source per detected interface.

Three properties of the current system make a list of targets per source cheap to introduce:

1. **Network sweeps never drive absence.** Only authoritative providers report closed scopes (`ProviderScopeObservation`); a sweep "observes packets, not a closed set of entities", so lifecycle hysteresis (guideline #171) is unaffected by how many ranges a source sweeps.
2. **Device-to-subnet association is containment-based.** No foreign key links devices or IP addresses to a subnet or to a source's CIDR; membership is computed in the inventory use cases with `netip.Prefix.Contains`.
3. **The target lives in a JSON document.** No relational column holds a source's CIDR, so a list can be introduced without a data migration.

## Decisions

### 1. A source owns an ordered list of targets
`config.targets` is a list of `{cidr, subnet_id?}`. It replaces `config.cidr`, which is still read as a one-element list and is dropped on the next save. The list is optional for a providers-only plan (`collectors.RequiresCIDR`) and must hold at least one target otherwise.

### 2. Targets may reference registered subnets
A target with `subnet_id` resolves its effective CIDR from the subnet **at run time**, so editing a subnet's CIDR is followed by every source scanning it. A soft-deleted subnet is not cascaded into its sources (ADR-014 "preserve & unlink"): the target stays in the list, is recorded as `skipped` on every run, and is flagged for manual removal. A target without `subnet_id` is a free-form CIDR.

### 3. Collectors declare a target scope
| Scope | Collectors | Invocation |
|---|---|---|
| `per_target` | ICMP sweep, reverse DNS, SNMP | Once per target |
| `union` | mDNS, ARP | Once, filtering by the union of all targets |
| `none` | Proxmox, Docker, UniFi | Once, with no target |

Running link-local collectors once per target would repeat the same multicast query or ARP-table read N times, so their one query is filtered by the union of the targets instead.

### 4. Targets run sequentially
Within a run, targets are processed one after another, while collectors inside a target keep running in parallel under the existing worker pool. A multi-target source therefore never puts more load on the network at once than a single-target source does today. The scheduler's per-source lock (AD-036) is unchanged.

### 5. Overlap is rejected and the total size is capped
Overlapping or duplicate targets within one source are rejected (`422 error_targets_overlap`) rather than deduplicated, keeping host counts and per-target run history unambiguous. The sum of hosts across all targets is capped at 65 536 — the size of one `/16`, today's per-target limit — and the list at 32 targets.

### 6. Run history records the target
`discovery_collector_runs` gains a nullable `target_cidr` column: one row per (collector, target) for `per_target` collectors, one row per collector with a null target for `union` and `none`. The source status keeps the existing aggregation over all rows (`idle` / `partial` / `error`).

### 7. Adding a target is a destination change
For sources with a stored secret, adding a target or changing a target's CIDR counts as a destination change under the RFC-025 credential guard and requires the secret to be re-entered (`422 secret_required_on_target_change`). Removing targets does not, since it exposes the credential to no new host.

## Consequences

- **Good**: one source covers "the same plan in several places"; schedule and credential changes are made once.
- **Good**: subnet edits propagate to linked targets, and subnet deletion impact reports the sources that scan it.
- **Good**: existing sources keep working with no data migration, and `config_cidr` stays in API responses for one minor version.
- **Bad**: a long multi-target run holds the per-source lock longer, so a schedule that fires mid-run is skipped. Operators who need independent cadences keep separate sources.
- **Bad**: the orchestrator gains a scope dimension (target × collector), and run history grows by one row per target for `per_target` collectors.
- **Neutral**: per-target schedules, credentials or collector sets are explicitly not supported; separate sources remain the model for them.
