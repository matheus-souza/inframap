# 13. Proxmox VE & Docker Engine Provider Collectors Architecture

Date: 2026-08-31

## Context

InfraMap aims to discover and map homelab infrastructure across both network sweeps (ICMP, ARP, mDNS, SNMP) and virtualization/container platforms (Proxmox VE, Docker Engine). 

Prior to this decision:
1. `modules/integrations` held partial implementations of `sdk.Provider` (`proxmox` and `docker`) with health-check endpoints, but they were not wired to inventory persistence or reconciliation.
2. `modules/discovery/collectors` contained network sweep collectors implementing `collectors.Collector`, but lacked provider support.
3. Network sweeps require IP or MAC addresses (`validator.go`), which caused stopped containers and offline VMs without guest agents to be silently dropped.
4. Topology parent-child links (`virtual_hypervisor`, `container_veth`) were inferred solely via asynchronous string matching on metadata events, leading to race conditions on discovery ordering and inability to cleanly track VM migration between cluster nodes.

## Decisions

### 1. Adapter Pattern (`sdk.Provider` + `ProviderCollector`)
- Retain `internal/platform/sdk.Provider` as the public extension contract for provider transport, config schemas, and health-checks.
- Create a unified `ProviderCollector` in `modules/discovery/collectors` implementing `collectors.Collector` that wraps any registered `sdk.Provider`.
- All discovered resources pass through the single, canonical Discovery Engine pipeline (Normalize → Validate → Match → Reconcile → InventorySync → Events), eliminating duplicate reconciliation logic.

### 2. Pull-Lazy Credentials via `ProviderConfigResolver`
- Extend `DiscoveryTarget` with `SourceID uuid.UUID`.
- Inject a `ProviderConfigResolver` port into `ProviderCollector`.
- The repository (`PgDiscoveryRepository`) decrypts and resolves credentials lazily at execution time via `discovery_source_collectors.config_encrypted` or central `credentials` reference. Decrypted secrets are never broadcast to unrelated network collectors or exposed in `ScanResult.Target`.

### 3. Workload Identity (`ProviderRef`) and Matcher Tier 0
- Introduce `ProviderRef` (`provider:scope:kind:native_id`) as a first-class identity attribute on `RawObservation` and `NormalizedDevice`:
  - Proxmox Node: `proxmox:<scope>:node:<nodename>`
  - Proxmox VM: `proxmox:<scope>:qemu:<vmid>`
  - Proxmox LXC: `proxmox:<scope>:lxc:<vmid>`
  - Docker Host: `docker:<scope>:engine:<daemon_id>`
  - Docker Container: `docker:<scope>:container:<container_id>`
  - ParentProviderRef:
    - VM/LXC Parent: `proxmox:<scope>:node:<nodename>`
    - Container Parent: `docker:<scope>:engine:<daemon_id>`
- Update `validator.go`: Observations are valid if they have IP, MAC, OR a valid `ProviderRef` (`ErrMissingIdentity`).
- Matcher: Promote `ProviderRef` to **Tier 0** (highest precedence, above MAC), preventing container recreation churn.
- Cycle deduplication: Keyed by `processedKeys` (`ProviderRef` → MAC → IP → Hostname).
- Deterministic synthetic hostname fallback: `pve-node1/qemu/101` for unnamed workloads.


### 4. Explicit Hierarchy and Topology Re-anchoring (`ParentProviderRef`)
- Add `ParentProviderRef` to `RawObservation` and `NormalizedDevice`.
- In `devices` table, add `parent_provider_ref TEXT` and `parent_device_id UUID REFERENCES devices(id) ON DELETE SET NULL`.
- Reconcile parent-child relationships with two-pass batch resolution and late event resolution.
- Enforce unique constraint `uq_topology_links_source_target_type UNIQUE (source_device_id, target_device_id, link_type)`.
- When a VM migrates between cluster nodes (`ParentProviderRef` changes), atomically update the device record and replace the topology link, publishing `topology.reparented`.

### 5. Workload Lifecycle and Scope Guard-Rails
- Distinguish `power_state` (reported runtime state: `running`, `stopped`, `paused`) from `status` (InfraMap observation state: `active`, `offline`, `archived`).
- Transition policy:
  - Authoritative provider on successful complete run: 1st absence → `status = 'offline'`; 2nd consecutive absence → `status = 'archived'`.
  - Partial or failed run: State is frozen (prevents mass inventory deletion on cluster outage or token expiration).
  - Network sweeps: Retain RFC-016 slow hysteresis (24h → offline, 30d → archived).
- Topology queries filter by `deleted_at IS NULL` on both endpoints.

### 6. Collection Scope v1
- **Proxmox VE**: Cluster Nodes + QEMU VMs + LXC Containers + Allocated Capacity (vCPU, RAM, Disk) + `power_state` + IP addresses (via QEMU guest agent best-effort without failing run).
- **Docker**: Engine Host + Containers in all states (`all=true`) + Port Mappings + Attached Networks + Image metadata (`repo:tag` and digest).
- Volatile time-series telemetry (live CPU%/RAM% utilization) is excluded from device JSONB metadata to prevent write churn.

### 7. Auto-Approval with Bulk Threshold Guard-Rail
- Default `auto_approve = true` on `discovery_source_collectors` when authenticated provider credentials are present.
- Ingest directly into inventory, bypassing staging, up to a configurable batch safety threshold per execution.

### 8. Unified Discovery Plans UX & Integrations Catalog
- Unified management: Proxmox and Docker are configured as selectable collectors within **Discovery Plans** (`discovery_sources` / `discovery_source_collectors`).
- UI includes dynamic credential fields and a "Test Connection" (Health-Check) action using the `modules/integrations` SDK catalogue.
- Full reuse of discovery scheduler, run retention, and chunked history endpoints.

## Consequences

- Clean architectural separation: Integrations module acts as capability catalog and SDK transport; Discovery Engine owns execution, matching, and reconciliation.
- Resilient topology modeling: Complete visibility of hypervisors, containers, VMs, and their live network/virtual containment links.
- Backward compatibility: Existing network sweeps continue running unaffected with single-binary WASM deployment.
