# RFC-026 — Multi-Target Discovery Sources Specification

| Status | Accepted |
|---|---|
| Created | 2026-10-27 |
| Related | ADR-015, ADR-011, RFC-025 |

---

## Problem Statement
A Discovery Source is today a plan of collectors (ADR-011) aimed at **a single** CIDR block. A network with several subnets that should be scanned the same way needs one source per subnet:

1. **Duplicated configuration**: every source repeats name, schedule, collectors and credentials. Changing the schedule or rotating an SNMP credential means editing N sources and keeping them in sync by hand.
2. **Auto-setup multiplies sources**: `AutoSetupCoordinator` creates one `ICMP Sweep — <cidr>` source per detected interface; a host with three interfaces starts life with three near-identical sources.
3. **Single-target contract across the stack**: `config.cidr` (a string) in the encrypted JSON, `DiscoverySourceResponse.ConfigCIDR`, `collectors.DiscoveryTarget{CIDR}` and the single CIDR field in `CreateDiscoverySourceScreen` all assume one target per source.
4. **No link to registered Subnets**: a source's CIDR is text copied at creation time. Editing a Subnet's CIDR (RFC-025) is not reflected in the source, and deleting a Subnet does not report which sources still scan it.
5. **Run history without a target**: `discovery_collector_runs` records one row per collector and does not know which block was scanned; with multiple targets it would be impossible to tell which subnet failed.

## Solution
A Discovery Source gains an **ordered list of targets** (`targets`). Each target is a CIDR, optionally linked to a registered Subnet. One run scans every target with the same collector plan, schedule and credentials.

- The list is **optional** when the plan contains only providers (`collectors.RequiresCIDR`, introduced by the providers-only source fix) and **required with at least one target** when the plan contains any network sweep.
- Collectors are classified by **target scope**: per-block sweeps run once per target; link-local collectors run once, filtering by the union of the targets; providers run once, with no target.
- Existing sources keep working with no data migration: `config.cidr` is read as a one-target list.
- Separate sources remain the right model when each block needs a different schedule, credential or collector set.

## User Stories
1. As an operator, I want one source to scan `10.0.10.0/24`, `10.0.20.0/24` and `10.0.30.0/24` with the same plan, so I do not maintain three identical sources.
2. As an operator, I want to pick targets from my registered Subnets, so a CIDR change on the Subnet is followed by the source.
3. As an operator, I also want to enter a free-form CIDR, to scan a block I have not registered as a Subnet.
4. As an operator, I want the run history to show which target failed, to diagnose an unreachable subnet without losing the results of the others.
5. As an operator, when deleting a Subnet, I want to see which sources scan it, to understand the impact before confirming.
6. As an operator, I want auto-setup to create **one** source for all detected interfaces.
7. As the operator of a source with a credential (SNMP), I want to be required to re-enter the credential when **adding** a target, so it is never sent to hosts I did not authorize.

## Implementation Decisions

### 1. Configuration model
`targets` lives in the source's encrypted configuration JSON, where `cidr` lives today:

```json
{
  "targets": [
    { "cidr": "10.0.10.0/24", "subnet_id": "6f1c…" },
    { "cidr": "192.168.50.0/24" }
  ],
  "credential_id": "…"
}
```

- **Compatible read**: if `targets` is absent and `cidr` is present, the source is read as `[{ "cidr": <cidr> }]`. No data migration is needed.
- **Write**: every `POST`/`PUT` stores `targets` and drops `cidr` from the payload (lazy migration on the next save).
- **Linked target** (`subnet_id` present): the effective CIDR is resolved from the Subnet **at run time**; the stored `cidr` is only a snapshot. A soft-deleted Subnet makes the target be skipped (section 4).
- **Free-form target** (`subnet_id` absent): the stored `cidr` is the target.

### 2. Validation (backend, `CreateSource` and `UpdateSource`)
| Rule | Error |
|---|---|
| Plan with a sweep and empty `targets` | `422 error_targets_required` |
| Invalid CIDR or larger than `/16` (`ParseTargetPrefix`) | `422 error_target_invalid` |
| Two equal or overlapping targets in the same source | `422 error_targets_overlap` |
| Sum of hosts across all targets above 65 536 (the size of one `/16`, today's per-target limit) | `422 error_targets_too_large` |
| `subnet_id` missing or soft-deleted | `422 error_target_subnet_not_found` |
| More than 32 targets | `422 error_targets_too_many` |

Overlapping targets are rejected rather than deduplicated, so host counts and per-target history stay unambiguous. Overlap **across different sources** remains allowed, as today.

### 3. Collector target scope and orchestration
Each collector type declares a scope in the `collectors` registry, next to `RequiresCIDR`:

| Scope | Collectors | Execution |
|---|---|---|
| `per_target` | `icmp_sweep`, `reverse_dns`, `snmp` | One `Collect` call per target |
| `union` | `mdns`, `arp_sweep` | One call; a single query (multicast / local ARP table) filtering responses by the union of the targets |
| `none` | `proxmox`, `docker`, `unifi` | One call, with no target |

- `DiscoveryTarget` gains `Prefixes []netip.Prefix` for the `union` scope; `CIDR` remains the target of the `per_target` scope.
- Targets are processed **sequentially**; within a target, collectors keep running in parallel under the current worker pool. A multi-target source therefore puts no more load on the network at any moment than a single-target source.
- Context cancellation interrupts the current target and skips the remaining ones (recorded as `skipped`).
- The scheduler's per-source lock (AD-036) does not change: one run covers every target, and a schedule that fires during a long run is still skipped.

### 4. Run history and aggregated status
- Migration: `ALTER TABLE discovery_collector_runs ADD COLUMN target_cidr CIDR NULL`.
- `per_target` scope: one row per (collector, target) with `target_cidr` set. `union` and `none` scopes: one row per collector with a null `target_cidr`.
- Target linked to a soft-deleted Subnet: `skipped` row with `error_message = "target subnet deleted"`, and no scan.
- Collector the environment does not let scan at all (an error matching `collectors.ErrCollectorUnavailable`, e.g. an ICMP sweep with no ICMP socket and no `ping` binary): `skipped` row with the collector's reason as `error_message`, one per target for a `per_target` collector, so the run never reads as a successful sweep that found nothing.
- Source status aggregated over all rows with the current rule: all `success` → `idle`; a mix → `partial`; no `success` → `error`.

### 5. Credential guard (extends RFC-025 §4)
For sources with a stored secret, **adding** a target or **changing** an existing target's CIDR is treated as a destination change (`dto.IsTargetKey`): without the secret re-sent, the `PUT` is rejected with `422 secret_required_on_target_change`. **Removing** targets does not require the secret, as it exposes the credential to no new host.

### 6. Linked Subnets
- `GET /api/v1/subnets/{id}/deletion-impact` adds `discovery_sources: [{ "id", "name" }]` with the active sources holding a target linked to the Subnet.
- Deleting the Subnet does **not** change the sources (ADR-014 *preserve & unlink*): the target stays in the list, is skipped on runs (section 4) and is flagged on the source edit screen for manual removal.

### 7. API contract (updates RFC-008)
- **Request** (`POST`/`PUT /api/v1/discovery/sources`): `config.targets` as in section 1. `config.cidr` is still accepted and converted into one target.
- **Response** (`DiscoverySourceResponse`): new `targets: [{ "cidr", "subnet_id", "subnet_name", "subnet_deleted" }]`. `config_cidr` stays filled with the first target's CIDR, marked **deprecated**, and is removed in the next minor version.
- **Runs** (`GET /api/v1/discovery/sources/{id}/runs`): every item gains `target_cidr`.
- `POST /api/v1/discovery/scan` (ad-hoc scan with a `cidr` body) is unchanged.

### 8. Frontend
- **Create/Edit Source**: the single CIDR field becomes a targets editor:
  - multi-select of registered Subnets (chips);
  - free-form CIDR input with local validation (same rules as section 2);
  - summed host counter.

  The editor is hidden when the plan only has providers (`requiresCidr`), and the persisted draft (`CreateDiscoverySourceDraft`) stores the list.
- **Sources list**: the CIDR column shows the first target and `+N`, with the full list in a tooltip.
- **Run diagnostics**: history rows display the target (`target_cidr`).
- **Auto-setup**: creates a single "Local network sweep" source with one target per detected interface. Installations that already went through auto-setup are not changed.

### 9. Localization Keys (PT/EN parity)
`discovery_targets_label`, `discovery_targets_add_subnet`, `discovery_targets_add_cidr`, `discovery_targets_host_count`, `discovery_targets_more` (`+%1$d`), `discovery_target_subnet_deleted`, `error_targets_required`, `error_target_invalid`, `error_targets_overlap`, `error_targets_too_large`, `error_targets_too_many`, `error_target_subnet_not_found`, `delete_subnet_impact_sources`.

## Testing Decisions
- **Backend Unit Tests**:
  - Compatible read: a legacy `cidr` payload becomes one target; a `targets` payload is read as is; writing drops `cidr`.
  - The validation matrix of section 2, including partial overlap (`10.0.0.0/23` × `10.0.1.0/24`) and the summed host limit.
  - Orchestration: a `per_target` collector called N times with each target's CIDR; `union` called once with every prefix; `none` called once with no target.
  - Aggregated status: one failing target with the others succeeding yields `partial`, with the correct `target_cidr` on each row.
  - A target linked to a soft-deleted Subnet is recorded as `skipped` and calls no collector.
  - Credential guard: adding a target without the secret → `422`; removing a target without the secret → `200`.
- **Backend Integration Tests**: the `target_cidr` migration applied and rolled back; Subnet `deletion-impact` lists the linked sources.
- **Frontend ViewModel Tests**: targets editor (add/remove Subnet and free-form CIDR, overlap and limit errors), a draft with a target list surviving F5, editor hidden for a providers-only plan.
- **Coverage Gate**: coverage $\ge 85\%$, zero `golangci-lint`, `ktlintCheck` and `detekt` violations.

## Out of Scope
- Schedule, credential or collector set **per target** (use separate sources).
- Running targets in parallel.
- IPv6 targets.
- Automatic migration merging existing single-target sources into a multi-target source.

## Further Notes
The structural decisions are recorded in [ADR-015](./adr/ADR-015-multi-target-discovery-sources.md). This RFC builds on the fix that made providers-only sources runnable (`collectors.RequiresCIDR`, `CONTEXT.md` guideline **#218**), on which the target requirement of section 2 is based.

### Implementation notes
Where the implementation refines this specification:

- **Engine entry point**: `Orchestrator.RunTargets(ctx, run, targets, …)` takes a separate `run` target carrying the fields shared by the whole run (`SourceID`, `CredentialSetID`), so a providers-only run with no targets still reaches its provider configuration. `RunScan` stays for the ad-hoc scan (`POST /api/v1/discovery/scan`).
- **Every linked Subnet deleted**: per-block and link-local collectors are recorded as `skipped` (`target subnet deleted`); providers in the same plan still run, so the source ends `partial`. A sweep-only plan in that state does not reach the engine and ends `error` (`CONTEXT.md` guideline **#220**).
- **Credential guard identity**: a linked target is identified by `subnet_id` and a free-form target by its masked prefix, so reordering, host bits, a Subnet edit, or linking a free-form target to a Subnet with the same CIDR are not destination changes. Collectors with target scope `none` are never guarded by source targets.
- **Run diagnostics**: the frontend shows each collector row's `target_cidr` from `last_run.collectors[]` in the source status tooltip; `target_cidr` is also returned by `GET /api/v1/discovery/sources/{id}/runs`.
- **Whole-run summary**: `last_run` holds every row of the latest run, the rows sharing its `started_at` (`TriggerRun` stamps all rows of a run with the same start time), so a run with a row per (collector, target) is never truncated. `GET /api/v1/discovery/sources` fills it for every source with one query, which the status tooltip of the sources list reads.
- **Recorded run failures**: `POST /api/v1/discovery/sources/{id}/run` answers `200` with `last_status: "error"` for a run that failed but was recorded, such as a sweep-only plan whose every linked Subnet was deleted; `500` is kept for a run whose outcome could not be persisted.
- **Detached manual runs**: a manual run runs on the scheduler's context, not the request's, so a page reload in the middle of a multi-target sweep no longer cancels the targets still pending. A second run of a source already running answers `409` (`CONTEXT.md` guideline **#222**).
- **Credential references on edit**: a provider whose updated config points at a stored credential (`credential_id`) takes its secrets from that credential. Its previous inline secrets are not kept (they would override the credential's when the reference is resolved), and a change of its endpoint does not require a typed secret, as on create. The edit screen offers the same credential picker as the create screen; a provider that stops using a credential must have its secrets typed.
- **Canonical targets**: every target CIDR is stored as its masked prefix (`10.0.10.5/24` becomes `10.0.10.0/24`), so the stored, returned and scanned values match the credential guard's identity.
- **`config_cidr` deprecation**: documented in the field's comment and in RFC-008 rather than with a `// Deprecated:` marker, which would fail lint on the remaining readers until the field is removed.
- **Auto-setup limits**: an interface that would break the §2 rules (wider than `/16`, overlapping an earlier one, or beyond the target or address caps) is skipped and the rest are kept.
- **Setup wizard**: the first-run wizard follows the same rule as the Dashboard auto-setup. It creates one "Local network sweep" source whose targets are the networks selected in its first step, each linked to the Subnet created there, and whose collectors come from the chosen scan preset.
- **Linked target display**: source responses show an active linked Subnet's current CIDR, not the stored snapshot, so the screens match what the next run scans right after a Subnet edit. The snapshot is shown only once the Subnet is deleted.
- **Auto-setup source name**: "Local network sweep" is a constant in the coordinator rather than a string resource. It is stored server-side as the source name, and resolving a Compose string resource outside the UI fails on headless JVMs.
