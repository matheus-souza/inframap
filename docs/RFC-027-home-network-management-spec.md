# RFC-027 — Home Network Management: IP Assignment, DNS Records and Device Groups

| Status | Accepted |
|---|---|
| Created | 2026-10-30 |
| Related | ADR-016, RFC-014, RFC-025, RFC-026 |

---

## Problem Statement
InfraMap discovers what is on a network (sweeps, Proxmox and Docker providers) and lets the operator name and type each device, but it cannot yet be the place where a home or small-lab network is **managed**. Three questions an operator asks every time they add a device have no answer in it:

1. **Is this address reserved?** Nothing records whether a device's IP is handed out dynamically by DHCP, reserved for it on the DHCP server, or configured statically on the device. The schema has an `ip_addresses.assignment_type` column, but no code reads or writes the table. An operator choosing an address for a new VM cannot see which addresses of a subnet are free, in use or reserved.
2. **Which names point where?** The home's DNS records (`nas.home.arpa → 192.0.2.20`, `jellyfin → nas`) live outside InfraMap, so nothing tells the operator that a record points at an address no device holds any more, or that a device moved and its record did not.
3. **How are devices organised?** Devices have a name and a type but no grouping: the operator cannot keep "media", "lab" or "IoT" devices together, filter the inventory by them, or see them as a set.

## Solution
Three operator-owned layers on top of the discovered inventory. Discovery never writes them: they describe the operator's intent, not what was observed, and they are compared against what discovery observes.

- **IP assignment**: each device's address carries an assignment (`unknown`, `dynamic`, `reserved`, `static`), set by the operator now and imported from the DHCP server later. Each registered Subnet gets an **address map**: every host address with its state (free, in use, reserved, conflict), the devices that hold it and the DNS names that point at it.
- **DNS records**: `A` and `CNAME` records kept in InfraMap, the source of truth for the home's local names. An `A` record links to a device, explicitly or by its address, and is flagged when its address no longer matches.
- **Device groups**: named, coloured sets of devices. A device may belong to several groups; the inventory filters by group.

## User Stories
1. As an operator, I want to mark a device's address as reserved, static or dynamic, so I know which addresses I can rely on.
2. As an operator, I want to open a Subnet and see which addresses are free, in use and reserved, so I can pick an address for a new VM without colliding.
3. As an operator, I want to see addresses held by two devices, to find a conflict before it breaks something.
4. As an operator, I want to keep my home's DNS records in InfraMap, linked to the devices they name.
5. As an operator, I want to see a device's DNS names on its page, and be warned when a record points at an address the device no longer has.
6. As an operator, I want to put devices in groups (media, lab, IoT) and filter the device list by group.
7. As an operator, I want the DHCP reservations of my router imported (later phase), so I do not mark each one by hand.

## Implementation Decisions

### 1. IP assignment (phase 1)
- New column `devices.ip_assignment VARCHAR(16) NOT NULL DEFAULT 'unknown'`, with a check constraint on `unknown`, `dynamic`, `reserved`, `static`. Additive migration: existing rows read `unknown`.
- `UpdateDeviceRequest.ip_assignment` sets it (case-insensitive); any other value is `400 BAD_REQUEST`, like an invalid address or MAC. Setting it does not add to `user_locked_fields`, which protects discovered fields only. `DeviceResponse.ip_assignment` returns it.
- Discovery never writes the column: the `UpdateDevice` query keeps the stored value when the parameter is empty (`COALESCE(NULLIF(…, ''), ip_assignment)`, the pattern `provider_scope` already uses), and the reconciliation path always passes it empty. Clearing a device's address does not reset its assignment.
- The device edit screen picks it; the device detail shows it; the device list shows `reserved` and `static` under the address. Filtering the list by assignment comes with the address map (phase 2).

### 2. Subnet address map (phase 2)
`GET /api/v1/subnets/{id}/addresses` returns one entry per host address of the Subnet's CIDR (network and broadcast excluded for IPv4 prefixes up to `/30`):

```json
{ "address": "192.0.2.20", "state": "reserved",
  "devices": [{ "id": "…", "hostname": "nas", "ip_assignment": "reserved" }],
  "dns_names": ["nas.home.arpa"] }
```

| State | Rule |
|---|---|
| `free` | No live device holds the address |
| `in_use` | One live device holds it with assignment `unknown` or `dynamic` |
| `reserved` | One live device holds it with assignment `reserved` or `static` |
| `conflict` | Two or more live devices hold it |

- Only Subnets of at most 1 024 host addresses (`/22` for IPv4) are mapped; larger ones return `422 error_subnet_too_large_to_map`. IPv6 Subnets are not mapped.
- Membership is containment-based, like every other device-to-subnet association (ADR-015 context 2).
- The Subnet screen gets an address map: a grid of addresses coloured by state, with the holder and DNS names on selection.

### 3. DNS records (phase 3)
New table `dns_records`:

| Column | Notes |
|---|---|
| `id UUID` | |
| `name VARCHAR(253)` | Lower-cased, without the trailing dot |
| `record_type VARCHAR(8)` | `A` or `CNAME` |
| `address INET NULL` | Required for `A`, IPv4 only |
| `target VARCHAR(253) NULL` | Required for `CNAME` |
| `device_id UUID NULL` | `REFERENCES devices(id) ON DELETE SET NULL` |
| `notes TEXT NULL` | |
| timestamps | |

- Unique on `(name, record_type, address, target)`. A `CNAME` cannot share its name with any other record (RFC 1034 §3.6.2): `409 error_dns_name_conflict`.
- **Device link**: an explicit `device_id` wins; otherwise an `A` record belongs to the live device holding its `address`. Each record carries a computed `status`:
  - `ok`: the linked device holds the address;
  - `address_mismatch`: `device_id` is set but the device's address differs;
  - `orphan`: no live device holds the address;
  - `dangling`: a `CNAME` whose target is not a name in InfraMap (informational, external names are allowed).
- CRUD under `/api/v1/dns-records`; the device detail lists its records; the address map shows the names per address.
- Soft-deleting a device keeps its records (`device_id` stays set, status `orphan`), so restoring the device restores the link (ADR-014 "preserve & unlink").

### 4. Device groups (phase 4)
- Tables `device_groups (id, name UNIQUE, color, description, timestamps)` and `device_group_members (group_id, device_id, PRIMARY KEY (group_id, device_id))`, both foreign keys `ON DELETE CASCADE`.
- Flat groups, many-to-many: a device may be in several groups, groups do not nest.
- CRUD under `/api/v1/device-groups`; `PUT /api/v1/devices/{id}/groups` replaces a device's memberships; `DeviceResponse.groups` lists them; the device list filters by `?group_id=`.
- A soft-deleted device keeps its memberships and is hidden from group listings until restored.

### 5. DHCP reservation import (phase 5, later)
Reservations read from the DHCP server set `ip_assignment = reserved` and record the origin (`ip_assignment_source = 'dhcp_import'`), so a manual choice is never overwritten by an import and an import can be refreshed. The integration depends on the DHCP server and is specified separately.

### 6. Delivery
Each phase ships as its own pull request with an additive migration, its API, its screen and its tests, in the order above. No phase rewrites or drops existing data.

## Out of Scope
- Pushing records to a DNS server (Pi-hole, AdGuard Home, the router) or reservations to a DHCP server.
- IPv6 address maps, `AAAA`, `PTR`, `MX` and other record types.
- Nested groups and group-based permissions.
