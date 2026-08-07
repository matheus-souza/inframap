# RFC-018 — Discovery Engine Specification

| Status | Accepted |
|---|---|
| Owner | InfraMap Team |
| Created | 2026-08-07 |
| Last Updated | 2026-08-07 |
| Replaces/Extends | RFC-007, RFC-016 |

---

# 1. Overview & Architectural Vision

The **InfraMap Discovery Engine** is the core intelligence subsystem of InfraMap. It orchestrates automated discovery of physical, virtual, and cloud infrastructure assets across homelab and enterprise networks.

Rather than implementing monolithic scanning routines, the Discovery Engine uses a **Plug-and-Play Collector Architecture**. Independent collectors gather raw facts (observations) from target subnets, APIs, and network protocols. Observations flow through a unified, multi-stage pipeline (Normalization → Validation → Identity Matching → Conflict Reconciliation → Inventory Sync) before emitting domain events to downstream consumers (Topology Engine, Audit Logger, Observability, Notifications).

```text
Discovery Source (CIDR / Subnet Range / Provider Config)
                       │
                       ▼
               [ Discovery Engine ]
                       │
  ┌────────────────────┼────────────────────┬────────────────────┐
  │                    │                    │                    │
  ▼                    ▼                    ▼                    ▼
[ ICMP Collector ]   [ ARP Collector ]   [ Reverse DNS ]     [ SNMP Collector ]
(Native/Ping/Degr)  (OS ARP Table)      (PTR Lookup)        (v2c/v3 Cred Set)
  │                    │                    │                    │
  └────────────────────┴──────────┬─────────┴────────────────────┘
                                  │ (Raw Observations)
                                  ▼
                            [ Normalizer ]
                                  │
                                  ▼
                            [ Validator ]
                                  │
                                  ▼
                            [ Matcher ]
                                  │
                                  ▼
                       [ Conflict Resolver ]
                                  │
                                  ▼
                        [ Inventory Sync ]
                                  │
                                  ▼
                         [ Domain Events ]
                                  │
             ┌────────────────────┼────────────────────┐
             ▼                    ▼                    ▼
     [ Topology Engine ]   [ Audit Logger ]    [ Observability ]
```

---

# 2. Pipeline Architecture & Stages

### Stage 1: Discovery Collectors (Primary Collection)
Collectors are lightweight, single-responsibility workers that execute targeted fact-gathering protocols:

1. **ICMP Collector**: Tests IPv4/IPv6 reachability and measures RTT latency.
   - *Resilience Strategy*: Attempts native ICMP socket (`golang.org/x/net/icmp`). If OS permissions are restricted, falls back to system `ping` command (`exec.Command("ping", ...)`). If neither is available (e.g. Distroless non-root container), logs `slog.Warn` and gracefully bypasses ICMP without halting discovery.
2. **ARP Collector**: Reads OS ARP table (`/proc/net/arp` on Linux, `sysctl`/`arp -an` on BSD/macOS). Operates with zero privileges (unprivileged OS read) to map local L2 `IP → MAC`.
3. **Reverse DNS Collector**: Performs asynchronous PTR lookups against configured DNS resolvers to obtain canonical hostnames (FQDN).
4. **SNMP Collector**: Queries standard MIB OIDs (`sysName`, `sysDescr`, `sysObjectID`, `ifTable`, `ipAddrTable`) using credentials resolved via Credential Sets. Returns hardware vendor, OS version, serial numbers, and interface mappings.
5. *Extensible Collectors (Future)*: mDNS, SSDP, LLDP, Mikrotik RouterOS API, UniFi Controller API.

### Stage 2: Normalizer
Converts protocol-specific raw responses into standardized `RawObservation` DTOs with timestamped metadata and source confidence scores.

### Stage 3: Validator
Enforces strict sanity constraints before identity resolution:
- Validates IPv4/IPv6 address syntax (`netip.ParseAddr`).
- Validates MAC address format (`net.ParseMAC`). Rejects multicast, broadcast (`FF:FF:FF:FF:FF:FF`), and zero MACs (`00:00:00:00:00:00`).
- Validates and sanitizes hostname strings (strips unprintable control characters, verifies length).
- Discards invalid or corrupted observation records prior to matching.

### Stage 4: Matcher Engine
Resolves target observation identity against existing inventory using a 5-tier precedence hierarchy:

| Priority | Identifier | Match Condition | Action |
|---|---|---|---|
| **1 (Highest)** | **Primary MAC Address** | Match on `mac_address` of any active interface | Associate observation with existing `device_id`. |
| **2** | **Provider UUID** | Match on provider namespace in metadata (`proxmox.vm_id`, `docker.container_id`) | Associate observation with existing `device_id`. |
| **3** | **Hardware Serial Number** | Match on `serial_number` reported via SNMP/DMI | Associate observation with existing `device_id`. |
| **4** | **Subnet + Hostname + IP** | Compound match on `hostname` + `ip_address` within same `subnet_id` | Associate observation with existing `device_id`. |
| **5 (Lowest)** | **No Match** | None of the above matchers yield a hit | Flag as new asset for Inventory Sync. |

### Stage 5: Conflict Resolver (Reconciler Engine)
Evaluates field-level updates when multiple collectors or integrations supply data for the same asset:
- Applies attribute-level **Confidence Scores** (`metadata->'field_confidence_scores'`).
- Respects **User-Locked Fields** (`metadata->'user_locked_fields'`) to prevent automated overwrites of operator-curated names, roles, or notes.
- Performs deep additive JSON metadata merging (`reflect.DeepEqual` check prevents redundant updates).

### Stage 6: Inventory Sync
Persists verified device changes to the `devices`, `interfaces`, and `ip_addresses` database tables within transactional boundaries (`pgx.Tx`).

### Stage 7: Reactive Domain Events
Upon successful Inventory Sync, the Engine emits strongly-typed domain events over the `EventBus`:
- `device.discovered` (new device added)
- `device.updated` (existing device attributes reconciled)
- `device.offline` (device unreachable after threshold)

Subscribers automatically react:
- **Topology Engine**: Updates graph nodes, links, and adjacency matrices.
- **Audit Logger**: Appends structured operational log to `audit_logs`.
- **Observability**: Increments Prometheus metrics counters.
- **Notifications**: Triggers webhook / alert dispatches if configured.

---

# 3. Worker Pool & Concurrency Strategy

To prevent network congestion or CPU exhaustion in homelab environments:

- **Dynamic Concurrency Ceiling**:
  - Default Worker Pool size: `min(CPU_CORES, 4)`.
  - Override via environment variable: `INFRAMAP_SCAN_CONCURRENCY`.
- **Protocol-Specific Rate Limiting**:
  Each collector enforces its own independent rate limiter (token bucket):
  - **ICMP Collector**: Max 100 packets/sec.
  - **SNMP Collector**: Max 20 queries/sec (prevents CPU spikes on embedded switches).
  - **Reverse DNS Collector**: Max 50 queries/sec.
  - **ARP Collector**: Unrestricted (local OS table read).
- **CIDR Chunking**:
  Discovery targets larger than `/24` (e.g. `/16`) are automatically partitioned into `/24` block tasks executed sequentially by the worker pool.

---

# 4. Credential Sets Architecture

Security credentials for active collectors (such as SNMP v2c/v3) are managed via **Credential Sets** linked to Discovery Sources:

```text
Discovery Source ──► Credential Set ("Network Switches")
                            │
                            ├── Priority 1: SNMP v3 ("prod-net-v3")
                            ├── Priority 2: SNMP v2c ("homelab-community")
                            └── Priority 3: Fallback ("public")
```

During the collection phase:
1. Collector fetches the target's assigned `CredentialSet`.
2. Attempts authentication using credentials in strict priority order.
3. On successful response, caches the working credential ID for the target host to accelerate subsequent scan cycles.
4. If all set credentials fail, logs warning and proceeds without SNMP data.

---

# 5. Database & Entity Schema Integration

The Discovery Engine integrates directly with PostgreSQL tables defined in RFC-006:
- `discovery_sources`: Stores CIDR ranges, schedules, and CredentialSet links.
- `device_discovery_records`: Preserves raw un-normalized payloads immutably.
- `credentials`: Encrypted vault table managed by `modules/credentials`.
- `devices`, `interfaces`, `ip_addresses`: Core inventory tables updated by Inventory Sync.

---

# 6. Quality Gates & Test Requirements

- **Unit Test Coverage**: `discovery/engine`, `discovery/collectors`, `discovery/usecase` MUST maintain >= 85% patch coverage.
- **Resilience Verification**: ICMP Collector unit tests MUST verify fallback behavior (Native ICMP → OS Ping → Graceful bypass) using mocks.
- **Deterministic Concurrency Tests**: Tests exercising worker pools MUST use bounded channels and deterministic time controls.
- **Zero Raw Error Leakage**: All HTTP endpoints serving discovery state MUST return opaque client errors while logging detailed root cause via `slog.Error`.
