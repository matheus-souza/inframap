# RFC-007 — Discovery & Reconciliation Engine Specification

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official design, algorithms, state machines, and reconciliation rules for the **InfraMap Discovery & Reconciliation Engine**.

The Discovery Engine is the core intelligence of InfraMap. Its primary purpose is to consolidate infrastructure information collected across heterogeneous sources (e.g., mDNS, ARP, Proxmox, Docker, UniFi, SNMP, Home Assistant) into a unified, accurate, and authoritative inventory without duplicating assets or overwriting user-curated data.

This specification builds directly upon the architectural boundaries defined in [RFC-005](./RFC-005-architecture.md) and the relational data model in [RFC-006](./RFC-006-data-model.md).

---

# Guiding Principles

1. **Zero Data Loss & Non-Destructive Reconciliation**
   - Discovered information never blindly overwrites user-curated attributes.
   - Raw scan payloads are preserved immutably in `device_discovery_records`.

2. **Source-Based Confidence Precedence**
   - Data updates are governed by a deterministic confidence matrix rather than a naive "Last Write Wins" strategy.

3. **Hybrid Device Onboarding**
   - Trusted integration providers (Proxmox, Docker, UniFi) automatically register devices into active inventory.
   - Unverified network scans (ARP sweeps, Ping scans) place newly discovered devices in a **Staging Queue** for user review.

4. **Network Friendliness & Low Overhead**
   - Active network scanning must enforce strict rate limits, packet pacing, and worker concurrency limits to prevent network congestion in homelab and enterprise environments.

---

# Pipeline Architecture

The Discovery Engine processes incoming data in a multi-stage sequential pipeline:

```text
  [ Raw Scanner / Integration Payload ]
                    │
                    ▼
          [ Normalizer Phase ]
   (Converts raw payload to Normalized Device DTO)
                    │
                    ▼
          [ Matcher Engine ]
   (Resolves identity using MAC / Provider UUID / Hostname)
                    │
                    ▼
       [ Reconciliation Engine ]
   (Applies Source Precedence Matrix & User Locks)
                    │
                    ▼
       [ Asset State Machine ]
   (Updates device status & interface mappings)
                    │
                    ▼
         [ Inventory Database ]
   (Persists change & emits DeviceUpdated event)
```

---

# Device Matching & Identity Resolution

To prevent duplicate records when the same device is reported by multiple sources (e.g., mDNS and a UniFi controller), the Matcher Engine evaluates identity using a strict precedence hierarchy.

### Identity Precedence Rules

| Priority | Identifier | Condition | Action |
| :---: | :--- | :--- | :--- |
| **1 (Highest)** | **Primary MAC Address** | Match on `mac_address` of any active interface | **Match Found:** Associate payload to existing `device_id`. |
| **2** | **Provider UUID** | Match on provider namespace in `metadata` (e.g., `proxmox.vm_id` or `docker.container_id`) | **Match Found:** Associate payload to existing `device_id`. |
| **3** | **Hardware Serial Number** | Match on `serial_number` reported via SNMP/DMI | **Match Found:** Associate payload to existing `device_id`. |
| **4** | **Subnet + Hostname + IP** | Exact match on `hostname` and `ip_address` within the same `subnet_id` | **Match Found:** Associate payload to existing `device_id`. |
| **5 (Lowest)** | **No Match** | None of the above matchers yield a hit | **New Device:** Trigger onboarding flow (Staging or Auto-Approve). |

---

# Dynamic IP Handling (DHCP Churn)

In homelabs and dynamic environments, devices frequently receive new IP addresses via DHCP.

### Resolution Rules:
1. When a scanner reports a known MAC Address (`AA:BB:CC:11:22:33`) associated with a new IP address (`192.168.1.150` instead of `192.168.1.100`):
   - The Engine **does not** create a new device.
   - The primary IP address on the existing `devices` record is updated to `192.168.1.150`.
   - The interface history in `ip_addresses` records the update.
2. If another device previously held `192.168.1.150`, its `ip_address` field is marked as unassigned until verified by the next scan cycle.

---

# Source Precedence & Field-Level Reconciliation

When multiple sources provide conflicting information for the same device (e.g., mDNS reports hostname `pve-node.local` while Proxmox reports `pve-node`), the Reconciliation Engine evaluates field updates using a **Confidence Score Matrix**.

### Confidence Score Levels

| Source Category | Confidence Score | Examples |
| :--- | :---: | :--- |
| **User Manual Override** | **100** | User edits via Web UI or API |
| **Direct Provider API** | **80** | Proxmox VE, Docker Engine, UniFi Controller, Mikrotik RouterOS |
| **L2/L3 Protocol Scans** | **50** | mDNS, LLDP, CDP, SNMP, UPnP |
| **Generic Network Sweeps** | **20** | ARP Table, ICMP Ping, Nmap Port Scan |

### Field-Level Merging Rules:
1. **Higher Score Wins:** A field update is applied **only if** the incoming source's confidence score is greater than or equal to the confidence score of the current field provider.
2. **User Lock Immunity:** Any field manually edited by a user receives a score of **100** and a `user_locked: true` flag in metadata. Automated scans can **never** overwrite a user-locked field.
3. **Additive Metadata:** Integration-specific details are merged into their respective JSONB namespaces (`metadata.proxmox`, `metadata.docker`) without clearing other namespaces.

---

# Hybrid Device Onboarding Flow

Newly discovered entities follow a hybrid onboarding policy:

```text
                       [ New Device Discovered ]
                                   │
                     Is source a Direct Provider API?
                     (Proxmox, Docker, UniFi, etc.)
                                  / \
                                 /   \
                             YES/     \NO (ARP / Ping Sweep)
                               /       \
                              ▼         ▼
                      [ Auto-Approve ]  [ Staging Queue ]
                      Status: active    Status: discovered
                                        (Requires User Approval)
```

1. **Auto-Approve Route:** Devices discovered via explicit integrations (Proxmox VMs, Docker Containers, UniFi APs) are automatically registered in `devices` with `status = 'active'`.
2. **Staging Queue Route:** Unverified network IPs discovered via passive ARP or Ping sweeps enter `devices` with `status = 'discovered'`. Users can approve them into active inventory or dismiss them.

---

# Asset State Machine & Lifecycle Timers

Every device in InfraMap transitions through a deterministic state machine based on scan feedback:

```text
    [ Discovered ] ──(User Approve)──► [ Active ] ──(Scan Timeout x2)──► [ Degraded ]
                                          ▲                                   │
                                          │                                   │
                                    (Scan Responded)                    (Scan Timeout 24h)
                                          │                                   │
                                          │                                   ▼
                                    [ Offline ] ◄─────────────────────────────┘
                                          │
                                   (Incapable 30d)
                                          │
                                          ▼
                                    [ Archived ]
```

### State Definitions & Transition Criteria

| State | Description | Transition Trigger |
| :--- | :--- | :--- |
| **`discovered`** | Pending review in Staging Queue | Created via generic scan sweep |
| **`active`** | Fully operational asset | Approved by user or created by trusted provider; responding to scans |
| **`degraded`** | Potential connectivity issue | Fails **2 consecutive** scheduled scans (or unresponsive for >15 minutes) |
| **`offline`** | Confirmed unreachable | Remains unresponsive for **>24 hours** |
| **`archived`** | Soft-deleted / Decommissioned | Unresponsive for **>30 days** or manually archived by user |

---

# Execution & Network Pacing Policy

To ensure InfraMap remains homelab-friendly and does not cause network degradation or firewall alerts during discovery sweeps:

1. **Worker Concurrency Limit:** Active ICMP/ARP sweeps use a configurable worker pool (default: **50 concurrent workers**).
2. **Packet Pacing:** Active scanning caps outbound traffic at a maximum of **100 packets/second** per subnet.
3. **Polling Intervals:**
   - Provider APIs (Proxmox/Docker/UniFi): Every 60 seconds.
   - Local L2 Protocol (mDNS/LLDP): Every 5 minutes.
   - Network ICMP/ARP Sweeps: Every 15 minutes.

---

# Raw Payload Retention Policy

Raw payloads stored in `device_discovery_records` enable complete historical auditability. To manage database storage:

1. Records retain up to the **last 50 scan payloads** per discovery source per device.
2. Historical payloads older than **30 days** are automatically pruned via a scheduled background job.
