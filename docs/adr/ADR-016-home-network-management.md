# ADR-016 — Home Network Management Data Model

| Status | Accepted |
|---|---|
| Created | 2026-10-30 |

---

## Context

RFC-027 adds three operator-owned layers to the inventory: the assignment of each device's address, the home's DNS records, and device groups. Three properties of the current model shape where they live:

1. **A device has one address.** `devices.ip_address` is the address every screen, the matcher (RFC-016) and topology use. The `network_interfaces` and `ip_addresses` tables from the initial schema exist but no query reads or writes them.
2. **Discovery reconciles fields, operators lock them.** `UpdateDevice` from the reconciliation path rewrites the observed columns; fields an operator edits are protected by `user_locked_fields` (RFC-025).
3. **Device-to-subnet membership is containment-based.** No foreign key links a device to a Subnet (ADR-015 context 2).

## Decisions

### 1. The assignment is a column of the device, not a row of `ip_addresses`
`devices.ip_assignment` describes the address in `devices.ip_address`, the only one the product shows. Bringing `ip_addresses` to life would mean keeping two copies of the address in sync for a model with one address per device. When devices gain several addresses, the column moves to `ip_addresses` together with the address it describes.

### 2. Operator-owned columns are never reconciled
`ip_assignment`, DNS records and group memberships are intent, not observation. The reconciliation path does not write them at all (rather than relying on `user_locked_fields`), so a sweep can never undo them. Disagreement between intent and observation is surfaced (address map states, DNS record status) instead of being resolved silently.

### 3. DNS records are their own entity, linked to devices by reference or by address
A record outlives the device it names (a replaced NAS keeps `nas.home.arpa`), and some records name nothing InfraMap discovers. An explicit `device_id` pins a record to a device and reports `address_mismatch` when the device moves; without it, an `A` record follows whichever live device holds its address. Status is computed on read, never stored, so it cannot go stale.

### 4. Groups are flat and many-to-many
Home networks overlap categories (a Proxmox VM is both "lab" and "media"), and nesting adds ordering and inheritance questions that filtering does not need.

### 5. Every migration is additive
New columns have defaults, new tables start empty, and no phase rewrites existing rows. An instance is upgraded in place, release after release, without losing its inventory.

## Consequences

- The address map, DNS status and group filters are computed from the live inventory on each request; for the mapped Subnet sizes (at most 1 024 addresses) this is a bounded scan of devices by containment.
- Moving to several addresses per device is a later migration of `ip_assignment` into `ip_addresses`.
- A DHCP import (RFC-027 phase 5) needs the origin of an assignment, added then as `ip_assignment_source`.
