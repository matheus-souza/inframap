# RFC-016: Automated Discovery & Reconciliation Engine Specification

- **Author**: InfraMap Core Engineering
- **Status**: PROPOSED & READY FOR IMPLEMENTATION
- **Created**: 2026-07-25
- **Applies to**: `backend/modules/discovery`

---

## 1. Executive Summary

This RFC specifies the architecture, pipeline stages, identity resolution algorithm, source precedence matrix, state machine transitions, and REST API for the **InfraMap Automated Discovery & Reconciliation Engine** per [RFC-007](./RFC-007-discovery-engine.md).

The Discovery Engine ingests heterogeneous scan data (ICMP Ping, passive ARP, mDNS, Proxmox VE, Docker Engine, UniFi Controller), normalizes raw payloads, resolves asset identity using hierarchical precedence, applies confidence scoring to resolve field conflicts, and drives asset state transitions (`active`, `degraded`, `offline`, `archived`).

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
    [ Field Reconciler ]    ──────►  (Applies Confidence Precedence Matrix & User-Lock Immunity)
              │
              ▼
    [ State Machine ]       ──────►  (Evaluates status: active, degraded, offline, archived)
              │
              ▼
  [ Database & Event Bus ]  ──────►  (Persists record & publishes device.discovered / device.updated)
```

---

## 3. Identity Resolution Hierarchy

To prevent asset duplication across multiple discovery sources, incoming payloads are matched against active inventory using strict precedence:

| Priority | Identifier | Match Condition | Action on Match |
|---|---|---|---|
| **1 (Highest)** | **Primary MAC Address** | Match on `mac_address` of any active device interface | Associate payload to existing `device_id` |
| **2** | **Provider UUID** | Match on provider namespace in `metadata` (`metadata.proxmox.vm_id`, `metadata.docker.container_id`) | Associate payload to existing `device_id` |
| **3** | **Hardware Serial** | Match on `serial_number` | Associate payload to existing `device_id` |
| **4** | **Subnet + Hostname + IP** | Exact match on `hostname` and `ip_address` | Associate payload to existing `device_id` |
| **5 (Lowest)** | **No Match** | None of the above matchers yielded a hit | Route to Staging Queue or Auto-Approve |

---

## 4. Source Precedence & Field Reconciliation Matrix

When multiple sources provide conflicting values for a device attribute, updates are governed by the **Confidence Precedence Matrix**:

| Source Category | Confidence Score | Examples |
|---|:---:|---|
| **User Manual Override** | **100** | Edits made via Web UI or API (`user_locked_fields`) |
| **Direct Provider API** | **80** | Proxmox VE API, Docker Engine API, UniFi Controller API |
| **L2/L3 Protocol Scans** | **50** | mDNS, LLDP, CDP, UPnP, SNMP |
| **Generic Network Sweeps** | **20** | Passive ARP sweep, ICMP ping sweep |

### Reconciliation Rules:
1. **User Lock Immunity (Score = 100)**: Any field listed in `metadata->'user_locked_fields'` **can never be overwritten** by automated discovery scanners.
2. **Confidence Score Precedence**: An incoming field update is applied **only if** `IncomingSourceScore >= ExistingFieldScore`.
3. **Additive Metadata Merge**: Integration details are merged into their respective JSONB namespace (`metadata.proxmox`, `metadata.docker`) without clearing existing namespaces.

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

---

## 6. Implementation Plan & Tracer-Bullet Tickets

1. **Ticket 01 (`sqlc` Queries & Schema Extensions)**: Add queries for `discovery_sources` and `device_discovery_records` in `backend/queries/discovery.sql`.
2. **Ticket 02 (DTOs & Repository)**: Implement `PgDiscoveryRepository` in `backend/modules/discovery/repository/discovery_repository.go`.
3. **Ticket 03 (Normalizer & Matcher & Reconciler Engine)**: Implement `Normalizer`, `Matcher`, and `Reconciler` in `backend/modules/discovery/engine/`.
4. **Ticket 04 (Discovery UseCase & Poller)**: Implement `DefaultDiscoveryUseCase` in `backend/modules/discovery/usecase/discovery_usecase.go` with background worker polling.
5. **Ticket 05 (HTTP Controller, Routes & E2E Tests)**: Implement REST endpoints (`/api/v1/discovery/sources`, `/api/v1/discovery/records`) and write E2E integration test suite.
