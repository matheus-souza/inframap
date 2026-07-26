# RFC-016: Automated Discovery & Reconciliation Engine Specification

- **Author**: InfraMap Core Engineering
- **Status**: PROPOSED & READY FOR IMPLEMENTATION
- **Created**: 2026-07-25
- **Applies to**: `backend/modules/discovery`

---

## 1. Executive Summary

This RFC specifies the architecture, pipeline stages, identity resolution algorithm, source precedence matrix, state machine transitions, and REST API for the **InfraMap Automated Discovery & Reconciliation Engine** per [RFC-007](./RFC-007-discovery-engine.md).

The Discovery Engine ingests heterogeneous scan data (ICMP Ping, passive ARP, mDNS, Proxmox VE, Docker Engine, UniFi Controller), normalizes raw payloads, resolves asset identity using hierarchical precedence, applies per-field confidence scoring to resolve attribute conflicts, and drives asset state transitions.

---

## 2. Pipeline Architecture

The Discovery Engine operates as an asynchronous sequential pipeline:

```text
  [ Raw Collector Payload ]
              │
              ▼
    [ Payload Normalizer ]  ──────►  (Converts raw JSON payload into NormalizedDevice DTO)
              │
              ▼
    [ Identity Matcher ]    ──────►  (Resolves device identity: MAC -> Provider UUID -> Serial -> Hostname+IP)
              │
              ▼
    [ Field Reconciler ]    ──────►  (Applies Per-Field Confidence Precedence Matrix & User-Lock Immunity)
              │
              ▼
    [ State Machine ]       ──────►  (Evaluates status: active / staged / degraded / offline via background sweep worker)
              │
              ▼
  [ Database & Event Bus ]  ──────►  (Persists record & publishes device.created / device.updated / device.staged)
```

---

## 3. Identity Resolution Hierarchy

To prevent asset duplication across multiple discovery sources, incoming payloads are matched against active inventory using strict precedence:

| Priority | Identifier | Match Condition | Action on Match |
|---|---|---|---|
| **1 (Highest)** | **Primary MAC Address** | Match on `mac_address` of primary device interface (`dev.MacAddress`) | Associate payload to existing `device_id` |
| **2** | **Provider UUID** | Match on provider namespace in `metadata` (`metadata.proxmox.vm_id`, `metadata.docker.container_id`) | Associate payload to existing `device_id` |
| **3** | **Hardware Serial** | Match on `serial_number` | Associate payload to existing `device_id` |
| **4** | **Hostname + IP Address** | Exact match on `hostname` and `ip_address` | Associate payload to existing `device_id` |
| **5 (Lowest)** | **No Match** | None of the above matchers yielded a hit | Route to Staging Queue or Auto-Approve |

---

## 4. Source Precedence & Field Reconciliation Matrix

When multiple sources provide conflicting values for a device attribute, updates are governed by the **Confidence Precedence Matrix** applied per individual field:

| Source Category | Confidence Score | Examples |
|---|:---:|---|
| **User Manual Override** | **100** | Edits made via Web UI or API (`user_locked_fields`) |
| **Direct Provider API** | **80** | Proxmox VE API, Docker Engine API, UniFi Controller API |
| **L2/L3 Protocol Scans** | **50** | mDNS, LLDP, CDP, UPnP, SNMP |
| **Generic Network Sweeps** | **20** | Passive ARP sweep, ICMP ping sweep |

### Reconciliation Rules:
1. **User Lock Immunity (Score = 100)**: Any field listed in `metadata->'user_locked_fields'` **can never be overwritten** by automated discovery scanners.
2. **Per-Field Confidence Score Precedence**: An incoming field update is applied **only if** `IncomingSourceScore >= ExistingFieldScore[field]`. Each attribute's confidence score is tracked independently in `metadata.field_confidence_scores`.
3. **Deep Additive Metadata Merge**: Provider payloads are deep-merged into their respective JSONB namespace (`metadata.proxmox`, `metadata.docker`), preserving existing nested keys during partial scans.

---

## 5. Asset State Machine & Lifecycle Rules

Devices transition between operational states based on scan feedback:

```text
    [ Staged/Discovered ] ──► [ Active ] ──(2 Failed Scans)──► [ Degraded ]
                                 ▲                                 │
                                 │                                 │
                           (Scan Success)                    (24h Unresponsive)
                                 │                                 │
                                 │                                 ▼
                           [ Offline ] ◄───────────────────────────┘
                                 │
                          (30d Unresponsive)
                                 │
                                 ▼
                           [ Archived ]
```

> **Note on Implementation Scope**:
> Direct discovery ingestion transitions incoming devices to `active` (for trusted provider sources) or `staged` (for generic sweeps). Automated state transitions (`degraded` after 2 missed scans, `offline` after 24h, and `archived` after 30d) are evaluated by the background lifecycle worker daemon in Phase 4.2.

---

## 6. Implementation Plan & Tracer-Bullet Tickets

1. **Ticket 01 (`sqlc` Queries & Schema Extensions)**: Add queries for `discovery_sources` and `device_discovery_records` in `backend/queries/discovery.sql`.
2. **Ticket 02 (DTOs & Repository)**: Implement `PgDiscoveryRepository` in `backend/modules/discovery/repository/discovery_repository.go`.
3. **Ticket 03 (Normalizer & Matcher & Reconciler Engine)**: Implement `Normalizer`, `Matcher`, and `Reconciler` in `backend/modules/discovery/engine/`.
4. **Ticket 04 (UseCase & Event Bus Integration)**: Implement `DefaultDiscoveryUseCase` in `backend/modules/discovery/usecase/discovery_usecase.go`.
5. **Ticket 05 (HTTP Controller & Endpoints & E2E Verification)**: Wire HTTP handlers in `backend/modules/discovery/controller/` and E2E test in `backend/tests/e2e/discovery_e2e_test.go`.
