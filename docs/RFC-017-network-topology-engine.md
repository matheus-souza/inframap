# RFC-017 — Network Topology & Mapping Engine Specification

| Status | Accepted |
|---|---|
| Owner | InfraMap Team |
| Created | 2026 |
| Module | `modules/topology` |

---

## 1. Overview
This document specifies the architecture, data structures, auto-inference rules, and API endpoints for **Phase 5: Network Topology & Mapping Engine** in InfraMap.

The Topology Engine calculates, stores, and serves graph representations of physical, routed, virtual, and container connections across network assets.

---

## 2. Link Types & Precedence Matrix

All network relationships live in a unified `topology_links` table governed by a `link_type` and `confidence_score`:

| Link Type | Precedence / Source | Confidence Score | Overwrite Immunity |
|---|---|---|---|
| `manual` | Operator configured via REST API | `1.00` | 🛡️ Protected from automated scanner sweeps |
| `layer2_physical` | LLDP / CDP / MAC switch port tables | `1.00` | Overwrites automated lower-confidence links |
| `virtual_hypervisor` | Proxmox VE discovery metadata (`proxmox.vm_id`) | `0.95` | Reconciled automatically |
| `container_veth` | Docker Engine metadata (`docker.container_id`) | `0.90` | Reconciled automatically |
| `layer3_routed` | Subnet & Gateway routing inference | `0.85` | Reconciled automatically |

---

## 3. Virtual Link Auto-Inference Protocol

When domain events (`device.created`, `device.updated`) arrive on the `EventBus`:
1. **Proxmox VE Matching**: If an ingested device contains `proxmox.vm_id` in metadata and matches a known Proxmox host device, a `virtual_hypervisor` link is created between the host and VM.
2. **Docker Engine Matching**: If an ingested device contains `docker.container_id` in metadata and matches a known Docker host device, a `container_veth` link is created between the host and container.
3. **Topology Updated Event**: Upon creating, modifying, or deleting links, the Topology Engine emits a `topology.updated` domain event on the `EventBus`.

---

## 4. REST API Specification

### `GET /api/v1/topology/graph`
Returns complete network graph containing `nodes` (devices), `edges` (links), `subnets` (groupings), and `metadata`.

### `GET /api/v1/topology/links`
Returns list of topology links with optional filtering by `link_type`, `source_device_id`, or `target_device_id`.

### `POST /api/v1/topology/links`
Creates a manual connection link between two devices/interfaces.

### `DELETE /api/v1/topology/links/{id}`
Deletes a topology link by UUID.
