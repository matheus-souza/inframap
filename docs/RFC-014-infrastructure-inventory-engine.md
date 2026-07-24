# RFC-014 — Infrastructure Inventory & Asset Engine Specification

| Status | Draft / Proposed |
|--------|------------------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

## 1. Problem Statement

InfraMap requires a high-performance, robust **Infrastructure Inventory Engine** to store, manage, and query network devices, subnets, and unverified staging queues. The engine must support offset pagination, full-text/prefix search, user-locking of manual edits, soft-deletions, and approval workflows for newly discovered devices.

---

## 2. User Stories & Capabilities

1. **As an Operator/Admin**, I want to list registered devices (`GET /api/v1/devices`) with search filters (`q`, `device_type`, `subnet`) and offset pagination (`page`, `per_page`).
2. **As an Operator/Admin**, I want to register a new device manually (`POST /api/v1/devices`) with hostname, IP, MAC address, manufacturer, model, and device type.
3. **As an Operator/Admin**, I want to retrieve details for a specific device (`GET /api/v1/devices/:id`).
4. **As an Operator/Admin**, I want to update device attributes (`PUT /api/v1/devices/:id`), automatically marking modified fields in `metadata->'user_locked_fields'` to prevent discovery scanners from overwriting manual edits.
5. **As an Admin**, I want to soft-delete a device (`DELETE /api/v1/devices/:id`) by setting `deleted_at`.
6. **As an Operator/Admin**, I want to view unverified discovered devices in the staging queue (`GET /api/v1/devices/staging`).
7. **As an Operator/Admin**, I want to approve a staged device (`POST /api/v1/devices/staging/:id/approve`), promoting it to active inventory (or merging if MAC/IP collides).
8. **As an Operator/Admin**, I want to dismiss a staged device (`POST /api/v1/devices/staging/:id/dismiss`), rejecting it from promotion.
9. **As an Operator/Admin**, I want to list and add network subnets (`GET /api/v1/subnets`, `POST /api/v1/subnets`).

---

## 3. Data Model & Migrations

### 3.1 Migration: `20260724000001_create_device_staging.sql`
```sql
CREATE TABLE device_staging (
    id UUID PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip_address INET,
    mac_address MACADDR,
    manufacturer VARCHAR(128),
    model VARCHAR(128),
    device_type VARCHAR(64) NOT NULL DEFAULT 'unknown',
    discovery_source_id UUID REFERENCES discovery_sources(id) ON DELETE SET NULL,
    raw_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_staging_status ON device_staging(status);
CREATE INDEX idx_device_staging_ip ON device_staging(ip_address);
CREATE INDEX idx_device_staging_mac ON device_staging(mac_address);
```

---

## 4. RBAC & Access Control

| Endpoint | Method | Permission Required | Roles Allowed |
|---|:---:|:---:|:---:|
| `/api/v1/devices` | GET | `inventory:read` | `admin`, `operator`, `viewer` |
| `/api/v1/devices` | POST | `inventory:write` | `admin`, `operator` |
| `/api/v1/devices/:id` | GET | `inventory:read` | `admin`, `operator`, `viewer` |
| `/api/v1/devices/:id` | PUT | `inventory:write` | `admin`, `operator` |
| `/api/v1/devices/:id` | DELETE | `inventory:delete` | `admin` |
| `/api/v1/devices/staging` | GET | `inventory:read` | `admin`, `operator`, `viewer` |
| `/api/v1/devices/staging/:id/approve` | POST | `inventory:write` | `admin`, `operator` |
| `/api/v1/devices/staging/:id/dismiss` | POST | `inventory:delete` | `admin`, `operator` |
| `/api/v1/subnets` | GET | `inventory:read` | `admin`, `operator`, `viewer` |
| `/api/v1/subnets` | POST | `inventory:write` | `admin`, `operator` |

---

## 5. Domain Event Publishing

Whenever inventory changes occur, audit events and domain events are emitted to the internal event bus:
- `device.created`: Emitted when a new device is registered or approved.
- `device.updated`: Emitted when device attributes are modified.
- `device.deleted`: Emitted when a device is soft-deleted.
- `device.approved`: Emitted when a staged device is promoted to active inventory.
- `device.dismissed`: Emitted when a staged device is rejected.
