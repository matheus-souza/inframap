# RFC-006 — Data Model & Database Schema

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official relational data model, database schema guidelines, and persistence policies for InfraMap.

Its goal is to establish a robust, performant, and extensible database schema in **PostgreSQL** that serves as the **Single Source of Truth** for infrastructure assets, network interfaces, subnets, topology relationships, discovery sources, identity, and audit records.

The schema design directly reflects the architectural principles outlined in [RFC-001](./RFC-001-technology-stack.md) (No ORM, type-safe queries via `sqlc`, explicit SQL) and [RFC-005](./RFC-005-architecture.md) (Modular Monolith with Clean/Hexagonal boundaries).

---

# Guiding Principles

The database schema is designed according to the following principles:

1. **Pragmatic Hybrid Model (Explicit Core + Extensible JSONB)**
   - Universal infrastructure fields (e.g., `hostname`, `ip_address`, `mac_address`, `status`, `device_type`) are stored as strongly-typed, indexed PostgreSQL columns.
   - Provider-specific or integration-dependent metadata (e.g., Proxmox VM IDs, Docker container ports, UniFi switch ports) are stored inside a namespaced `metadata JSONB` column.
   - Domain logic evolves iteratively: attributes remain in `metadata JSONB` until an entity develops distinct domain behavior (e.g., operational actions, backups, metrics), at which point it is extracted into a dedicated extension table.

2. **Time-Ordered Unique Identifiers (UUIDv7)**
   - All primary keys adopt **UUIDv7**.
   - UUIDv7 provides temporal sortability, zero collision risk across distributed collectors, and optimal B-tree index performance compared to random UUIDv4.

3. **Native PostgreSQL Types**
   - Network properties utilize PostgreSQL native network types (`inet`, `cidr`, `macaddr`) for strict protocol validation and specialized indexing performance.

4. **Strict Relational Integrity**
   - Foreign key constraints enforce relationships between Core entities (`devices`, `network_interfaces`, `ip_addresses`, `topology_links`).
   - Shared repositories between modules are strictly forbidden; each Capability owns its persistence tables.

5. **Explicit Migrations (Goose)**
   - Schema changes are managed using **Goose** migration files (`.sql`) containing explicit `-- +goose Up` and `-- +goose Down` blocks.

6. **Zero Data Loss & Soft Deletion Policy**
   - No core entity (`devices`, `network_interfaces`, `subnets`, `topology_links`) is physically removed via `DELETE` SQL queries during normal operation.
   - All mutations preserve historical record integrity through **Soft Delete (`deleted_at TIMESTAMPTZ`)**, **Status State Transitions (`archived`)**, and immutable audit log snapshots.

---

# Data Protection, Integrity & Zero Data Loss Policy

InfraMap enforces strict data safety standards to guarantee that system updates, discovery scans, schema migrations, or user actions **never result in unexpected data loss**.

### 1. Soft Delete & Immutability Standard
- **No Physical Deletions**: When a device or interface is removed or disappears from discovery scans, it is marked with a timestamp in `deleted_at` or transitioned to `status = 'archived'`.
- **Historical Discovery Preservation**: Raw payloads from scanners are stored immutably in `device_discovery_records`. Even if normalization logic or discovery algorithms change in future releases, historical raw payloads are preserved for recalculation.
- **User Override Immunity**: User-curated fields (e.g., custom names, manual tags, notes) take absolute priority over automatic discovery scans and can never be blindly overwritten by automated updates.

### 2. Non-Destructive Schema Migration Guarantee
- **Expand / Contract (Parallel Change) Pattern**: Database migrations must never drop or rename existing columns in a single release.
  - *Phase 1 (Expand)*: Add the new column/table without breaking existing code.
  - *Phase 2 (Migrate)*: Copy/transform data asynchronously or dual-write.
  - *Phase 3 (Contract)*: Mark old column as deprecated before removal in a subsequent major release.
- **Transactional Migrations**: Every Goose migration runs inside an explicit database transaction (`BEGIN ... COMMIT`). If any migration step fails, the database automatically rolls back to its exact pre-migration state.

### 3. Pre-Migration Automatic Safeguards
- The system automatically triggers a lightweight snapshot / database backup before executing any schema migration (`goose up`).
- If a migration failure is detected, the upgrade process halts immediately and emits a critical system alert.

### 4. Audit & Change Tracking
- Every mutation (INSERT, UPDATE, SOFT_DELETE) logs a `before` and `after` JSONB snapshot in `audit_logs`, allowing complete historical reconstruction and rollback of accidental user changes.

---

# Primary Key & Timestamp Policy

Every table defined in the InfraMap database must adhere to the following column standards:

| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | UUIDv7 generated application-side or via database functions |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record creation timestamp |
| `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Record last modification timestamp |

---

# Core Domain Schemas

## 0. System Initialization & Onboarding (`configuration`)

### `system_state`
Controls application initialization status and initial onboarding state (Portainer-style first launch setup).

```sql
CREATE TABLE system_state (
    id UUID PRIMARY KEY,
    onboarding_completed BOOLEAN NOT NULL DEFAULT false,
    onboarding_completed_at TIMESTAMPTZ,
    system_instance_id UUID NOT NULL, -- Permanent unique installation ID
    telemetry_enabled BOOLEAN NOT NULL DEFAULT false,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

## 1. Identity & Access Management (`identity`)

### `users`
Stores system accounts for local authentication.

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL, -- Argon2id hash
    full_name VARCHAR(128) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
```

### `roles`
Defines system and custom roles for Role-Based Access Control (RBAC).

```sql
CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(64) UNIQUE NOT NULL, -- administrator, operator, viewer, read_only, custom_name
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT false, -- True for protected system roles
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_roles_name ON roles(name);
```

### `permissions`
Defines fine-grained operational permissions across capabilities.

```sql
CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    name VARCHAR(64) UNIQUE NOT NULL, -- e.g., devices:read, devices:write, discovery:execute, users:manage
    resource VARCHAR(64) NOT NULL,    -- devices, subnets, users, discovery, audit
    action VARCHAR(64) NOT NULL,      -- read, write, delete, execute, admin
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_permissions_resource ON permissions(resource);
```

### `role_permissions`
Maps permissions to roles (N:M).

```sql
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);
```

### `user_roles`
Assigns roles to users (N:M).

```sql
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
```

### `user_sessions`
Stores active session tokens or refresh tokens.

```sql
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) UNIQUE NOT NULL,
    user_agent TEXT,
    ip_address INET,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_token_hash ON user_sessions(token_hash);
```


---

## 2. Infrastructure Inventory (`inventory`)

### `devices`
The authoritative registry of discovered and managed infrastructure assets.

```sql
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip_address INET,
    mac_address MACADDR,
    manufacturer VARCHAR(128),
    model VARCHAR(128),
    serial_number VARCHAR(128),
    device_type VARCHAR(64) NOT NULL DEFAULT 'unknown', -- server, switch, router, vm, container, iot, storage, workstation, unknown
    status VARCHAR(32) NOT NULL DEFAULT 'active', -- active, degraded, offline, archived
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted_at TIMESTAMPTZ, -- Soft delete support
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_hostname ON devices(hostname);
CREATE INDEX idx_devices_ip_address ON devices(ip_address);
CREATE INDEX idx_devices_mac_address ON devices(mac_address);
CREATE INDEX idx_devices_device_type ON devices(device_type);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_deleted_at ON devices(deleted_at);
CREATE INDEX idx_devices_metadata ON devices USING gin (metadata);
```

### `network_interfaces`
Physical or virtual network interfaces attached to a device.

```sql
CREATE TABLE network_interfaces (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name VARCHAR(64) NOT NULL, -- e.g., eth0, wlan0, br0
    mac_address MACADDR,
    vlan_id INT,
    speed_mbps INT,
    is_virtual BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(32) NOT NULL DEFAULT 'up', -- up, down, unknown
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_interface UNIQUE (device_id, name)
);

CREATE INDEX idx_network_interfaces_device_id ON network_interfaces(device_id);
CREATE INDEX idx_network_interfaces_mac ON network_interfaces(mac_address);
```

### `ip_addresses`
IPv4 and IPv6 network addresses assigned to interfaces.

```sql
CREATE TABLE ip_addresses (
    id UUID PRIMARY KEY,
    interface_id UUID REFERENCES network_interfaces(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    address INET NOT NULL,
    family VARCHAR(8) NOT NULL DEFAULT 'v4', -- v4, v6
    assignment_type VARCHAR(32) NOT NULL DEFAULT 'dhcp', -- static, dhcp, reserved
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_ip UNIQUE (device_id, address)
);

CREATE INDEX idx_ip_addresses_address ON ip_addresses(address);
CREATE INDEX idx_ip_addresses_device_id ON ip_addresses(device_id);
```

### `subnets`
Managed network segments and VLAN definitions.

```sql
CREATE TABLE subnets (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    cidr CIDR NOT NULL UNIQUE,
    vlan_id INT,
    gateway_ip INET,
    description TEXT,
    discovery_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_subnets_cidr ON subnets USING gist (cidr inet_ops);
```

---

## 3. Network Topology & Relationships (`topology`)

### `topology_links`
Represents physical, logical, or virtual connections between devices.

```sql
CREATE TABLE topology_links (
    id UUID PRIMARY KEY,
    source_device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    target_device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    source_interface_id UUID REFERENCES network_interfaces(id) ON DELETE SET NULL,
    target_interface_id UUID REFERENCES network_interfaces(id) ON DELETE SET NULL,
    link_type VARCHAR(64) NOT NULL, -- physical_cable, vlan_peer, hypervisor_guest, container_host, wifi_client, dependency
    confidence_score NUMERIC(3, 2) NOT NULL DEFAULT 1.00, -- 0.00 to 1.00
    discovered_by VARCHAR(64) NOT NULL, -- source plugin or manual
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_confidence_range CHECK (confidence_score >= 0.00 AND confidence_score <= 1.00)
);

CREATE INDEX idx_topology_links_source ON topology_links(source_device_id);
CREATE INDEX idx_topology_links_target ON topology_links(target_device_id);
CREATE INDEX idx_topology_links_type ON topology_links(link_type);
```

---

## 4. Discovery Engine & Provenance (`discovery`)

### `discovery_sources`
Configured discovery plugins and scanners (e.g., mDNS, ARP, Proxmox, UniFi, Docker).

```sql
CREATE TABLE discovery_sources (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL, -- arp, ping, mdns, proxmox, homeassistant, docker, unifi, snmp
    enabled BOOLEAN NOT NULL DEFAULT true,
    schedule_cron VARCHAR(64), -- e.g., "*/15 * * * *"
    config_encrypted TEXT, -- Encrypted JSON credentials
    last_run_at TIMESTAMPTZ,
    last_status VARCHAR(32) NOT NULL DEFAULT 'idle', -- idle, running, success, failed
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_discovery_sources_type ON discovery_sources(type);
```

### `device_discovery_records`
Stores raw scan payloads for auditability, reconciliation, and conflict resolution.

```sql
CREATE TABLE device_discovery_records (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    discovery_source_id UUID NOT NULL REFERENCES discovery_sources(id) ON DELETE CASCADE,
    matched_by VARCHAR(32) NOT NULL, -- mac, ip, hostname, provider_uuid
    raw_payload JSONB NOT NULL,
    last_scanned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_device_source UNIQUE (device_id, discovery_source_id)
);

CREATE INDEX idx_discovery_records_device ON device_discovery_records(device_id);
CREATE INDEX idx_discovery_records_payload ON device_discovery_records USING gin (raw_payload);
```

---

## 5. Audit & Platform Logs (`audit`)

### `audit_logs`
Immutable operational event log for system actions and user activities.

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    actor_name VARCHAR(128) NOT NULL, -- username or 'system'
    action VARCHAR(64) NOT NULL, -- device.create, device.update, user.login, config.change
    resource_type VARCHAR(64) NOT NULL, -- device, subnet, user, discovery_source
    resource_id UUID,
    changes JSONB NOT NULL DEFAULT '{}'::jsonb, -- { "before": {...}, "after": {...} }
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
```

---

# JSONB Namespace Convention

To avoid key collisions and maintain structure across multiple discovery providers, the `metadata JSONB` column in the `devices` table follows a strict top-level namespace rule:

```json
{
  "proxmox": {
    "node": "pve-01",
    "vm_id": 102,
    "type": "qemu",
    "cores": 4,
    "memory_mb": 8192,
    "status": "running"
  },
  "docker": {
    "container_id": "8f3b2a1c9e4d",
    "image": "nginx:alpine",
    "ports": ["80:80", "443:443"],
    "created_at": "2026-01-15T10:00:00Z"
  },
  "unifi": {
    "model": "U6-Lite",
    "site": "default",
    "switch_mac": "74:83:c2:11:22:33",
    "switch_port": 14
  },
  "custom": {
    "tags": ["production", "nas"],
    "location": "Rack A - Unit 4"
  }
}
```

### Extraction Guideline
If a specific integration domain (such as Proxmox or Docker) requires complex operations (e.g., executing snapshots, tracking metrics, managing container lifecycles), its data should be extracted from `metadata JSONB` into a dedicated table (e.g., `proxmox_instances`) via a database migration.

---

# Migration Workflow (Goose)

1. Migration scripts must be placed in `backend/migrations/`.
2. File naming format: `YYYYMMDDHHMMSS_short_description.sql`.
3. Every migration file must contain `-- +goose Up` and `-- +goose Down` annotations.

Example migration (`20260722000001_create_devices.sql`):

```sql
-- +goose Up
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip_address INET,
    mac_address MACADDR,
    manufacturer VARCHAR(128),
    model VARCHAR(128),
    serial_number VARCHAR(128),
    device_type VARCHAR(64) NOT NULL DEFAULT 'unknown',
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_hostname ON devices(hostname);
CREATE INDEX idx_devices_ip_address ON devices(ip_address);
CREATE INDEX idx_devices_mac_address ON devices(mac_address);
CREATE INDEX idx_devices_metadata ON devices USING gin (metadata);

-- +goose Down
DROP TABLE IF EXISTS devices CASCADE;
```

---

# Auto-Update & Zero Data Loss Migration Policy

To support homelab environments where container images are automatically updated (e.g. via Watchtower or `docker compose pull`), InfraMap enforces a strict update and migration policy:

1. **Automatic Startup Execution:**
   - On application startup, before initializing HTTP listeners, the Go binary automatically executes pending Goose migrations (`goose.Up()`).
   - Upgrades require no manual CLI steps or external migration containers.

2. **Transaction Isolation & Atomicity:**
   - Every migration script is executed inside a PostgreSQL transaction (`BEGIN ... COMMIT`).
   - If a migration encounters an error during container update, the entire transaction is rolled back automatically. The application exits with a clear error log, leaving the existing database in a completely valid, uncorrupted state.

3. **Backward Compatibility Rule (Expand-Contract):**
   - Schema modifications must follow backward-compatible DDL patterns:
     - New columns must be `NULLable` or have explicit `DEFAULT` values.
     - Destructive DDL actions (e.g., `DROP TABLE`, `DROP COLUMN`) are strictly forbidden in standard release migrations.
     - Data loss or breaking schema changes during a stable version upgrade is strictly prohibited.
