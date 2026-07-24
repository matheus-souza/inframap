# Repository Scaffolding Tasks

## Execution Protocol

Implement these tasks with the `tlc-spec-driven` skill.

---

**Spec**: `.specs/features/repository-scaffolding/spec.md`
**Status**: Approved (Revised — aligned with RFC-006 and RFC-010)

---

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| HTTP API Entry Point | unit | Health check route `/api/v1/health` returns 200 OK | `backend/cmd/api/*_test.go` | `cd backend && go test ./...` |
| Database Migration DDL | build | Goose migration syntax valid, 15 tables from RFC-006 | `backend/migrations/*.sql` | `goose -dir backend/migrations validate` (or build check) |
| SQLC Config & Schema | build | `sqlc generate` succeeds with type overrides | `backend/sqlc.yaml` | `cd backend && sqlc compile` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | After unit tests | `cd backend && go test ./...` |
| Build | After file creation / config tasks | `cd backend && go build ./...` |
| Verify | Before PR / merge | `make verify` |

---

## Execution Plan

```text
Phase 1: Foundation (Directories & Makefile)
T1 → T2

Phase 2: Go Backend Initialization & Health API
T3

Phase 3: Database Migration & SQLC Setup
T4 → T5

Phase 4: Local Development Environment
T6
```

---

## Task Breakdown

### T1: Create Directory Layout & Root Makefile

**What**: Create full directory structure per RFC-010 and root `Makefile` with all targets defined in RFC-010.
**Where**: `Makefile`, `backend/`, `frontend/`
**Depends on**: None
**Requirement**: REQ-SCAF-01

**Directories to create** (per RFC-010):
- `backend/cmd/api/`
- `backend/internal/bootstrap/`
- `backend/internal/platform/crypto/`
- `backend/internal/platform/eventbus/`
- `backend/internal/platform/logger/`
- `backend/internal/platform/sdk/`
- `backend/internal/shared/`
- `backend/modules/audit/`
- `backend/modules/configuration/`
- `backend/modules/discovery/`
- `backend/modules/identity/`
- `backend/modules/integrations/`
- `backend/modules/inventory/`
- `backend/modules/topology/`
- `backend/migrations/`
- `backend/queries/`
- `frontend/`

**Makefile targets** (per RFC-010):
- `help`, `dev`, `dev-down`, `build` (backend only initially), `test` (with `-coverprofile`), `test-coverage`, `lint`, `verify` (generate + lint + test + build), `generate` (sqlc), `migrate-up`, `migrate-down`, `clean`

**Done when**:
- [ ] Root `Makefile` created with all RFC-010 targets
- [ ] All directories from RFC-010 established (with `.gitkeep` for empty dirs)
- [ ] `make help` executes without errors

**Tests**: none
**Gate**: Build (`make help`)
**Commit**: `chore(scaffold): initialize directory structure and root Makefile`

---

### T2: Initialize Go Module & Toolchain Configuration

**What**: Run `go mod init github.com/matheussouza/inframap` inside `backend/` and create `.mise.toml` with pinned toolchain versions.
**Where**: `backend/go.mod`, `.mise.toml`
**Depends on**: T1
**Requirement**: REQ-SCAF-01, REQ-SCAF-06

**Done when**:
- [ ] `backend/go.mod` created with module `github.com/matheussouza/inframap` and Go version 1.24+
- [ ] `.mise.toml` created with Go 1.24, JDK 21 LTS (Temurin), Goose, sqlc pinned
- [ ] `cd backend && go mod tidy` passes cleanly

**Tests**: none
**Gate**: Build (`cd backend && go mod verify`)
**Commit**: `chore(backend): initialize Go module github.com/matheussouza/inframap`

---

### T3: Implement Minimal HTTP Server & Health Endpoint

**What**: Create entry point `backend/cmd/api/main.go` listening on port `8055` (`INFRAMAP_PORT`) with route `/api/v1/health`.
**Where**: `backend/cmd/api/main.go`, `backend/cmd/api/main_test.go`
**Depends on**: T2
**Requirement**: REQ-SCAF-04

**Done when**:
- [ ] `main.go` starts `net/http` server on port 8055
- [ ] `/api/v1/health` returns `{"status":"ok"}` with HTTP 200
- [ ] `main_test.go` verifies the HTTP response and port configuration

**Tests**: unit
**Gate**: Quick (`cd backend && go test ./...`)
**Commit**: `feat(api): add minimal HTTP server entry point and health check route`

---

### T4: Create Initial Goose Database Migration Schema (RFC-006)

**What**: Create Goose migration `backend/migrations/20260722000001_initial_schema.sql` implementing the **complete** RFC-006 schema.
**Where**: `backend/migrations/20260722000001_initial_schema.sql`
**Depends on**: T1
**Requirement**: REQ-SCAF-02

**Tables to create** (15 tables from RFC-006):

| # | Table | Module | Key Features |
|---|---|---|---|
| 1 | `system_state` | configuration | Onboarding state, system instance ID |
| 2 | `users` | identity | Local auth, `username`, `email`, `password_hash` (Argon2id), `is_active` |
| 3 | `roles` | identity | RBAC roles, `is_system` flag |
| 4 | `permissions` | identity | Fine-grained `resource:action` permissions |
| 5 | `role_permissions` | identity | N:M mapping roles ↔ permissions |
| 6 | `user_roles` | identity | N:M mapping users ↔ roles |
| 7 | `user_sessions` | identity | Session tokens, `token_hash`, `expires_at`, `ip_address` (INET) |
| 8 | `devices` | inventory | Core asset registry: `hostname`, `ip_address` (INET), `mac_address` (MACADDR), `device_type`, `status`, `metadata` (JSONB), `first_seen_at`, `last_seen_at` |
| 9 | `network_interfaces` | inventory | Device NICs: `mac_address` (MACADDR), `vlan_id`, `speed_mbps`, `is_virtual` |
| 10 | `ip_addresses` | inventory | IPv4/IPv6 per interface: `address` (INET), `family`, `assignment_type` |
| 11 | `subnets` | inventory | Network segments: `cidr` (CIDR), `vlan_id`, `gateway_ip` (INET) |
| 12 | `topology_links` | topology | Device connections: `link_type`, `confidence_score`, `discovered_by` |
| 13 | `discovery_sources` | discovery | Scanner plugins: `type`, `schedule_cron`, `config_encrypted` |
| 14 | `device_discovery_records` | discovery | Raw scan payloads: `raw_payload` (JSONB), `matched_by` |
| 15 | `audit_logs` | audit | Immutable log: `actor_id`, `actor_name`, `action`, `resource_type`, `resource_id`, `changes` (JSONB), `ip_address` (INET) |

**Schema requirements**:
- All PKs are UUID (application-side UUIDv7 per RFC-006)
- All tables with mutations have `created_at` / `updated_at` TIMESTAMPTZ
- Soft delete via `deleted_at TIMESTAMPTZ` where applicable
- PostgreSQL native types: `INET`, `CIDR`, `MACADDR`
- `-- +goose Up` and `-- +goose Down` annotations
- Indexes as specified in RFC-006

**Done when**:
- [ ] Migration includes `-- +goose Up` and `-- +goose Down` sections
- [ ] All 15 RFC-006 tables created with correct columns, types, constraints, and indexes
- [ ] `update_updated_at_column()` trigger function created and applied

**Tests**: none
**Gate**: Build (`cd backend && go build ./...`) + Goose syntax validation (`goose -dir migrations validate`)
**Commit**: `feat(db): create initial Goose schema migration aligned with RFC-006`

---

### T5: Create sqlc Configuration & Initial Queries (RFC-010)

**What**: Create `backend/sqlc.yaml` matching RFC-010 specification and `backend/queries/health.sql`.
**Where**: `backend/sqlc.yaml`, `backend/queries/health.sql`
**Depends on**: T4
**Requirement**: REQ-SCAF-03

**sqlc.yaml requirements** (per RFC-010):
- `emit_prepared_queries: false`
- `emit_json_tags: true`
- `emit_exact_table_names: false`
- `emit_empty_slices: true`
- Type overrides:
  - `uuid` → `github.com/google/uuid.UUID`
  - `inet` → `net/netip.Addr`
  - `cidr` → `net/netip.Prefix`
  - `macaddr` → `net.HardwareAddr`

**Done when**:
- [ ] `backend/sqlc.yaml` configured with all RFC-010 fields including 4 type overrides
- [ ] `backend/queries/health.sql` includes sample query
- [ ] `cd backend && go build ./...` passes

**Tests**: none
**Gate**: Build (`cd backend && go build ./...`) + sqlc compile (`cd backend && sqlc compile`)
**Commit**: `feat(db): configure sqlc.yaml with RFC-010 type overrides and initial queries`

---

### T6: Create Local Development Environment (docker-compose)

**What**: Create `docker-compose.dev.yml` for local PostgreSQL 17 development.
**Where**: `docker-compose.dev.yml`
**Depends on**: T1
**Requirement**: REQ-SCAF-05

**Done when**:
- [ ] `docker-compose.dev.yml` created with PostgreSQL 17 service
- [ ] `make dev` uses docker-compose to start PostgreSQL
- [ ] `make dev-down` stops and removes containers

**Tests**: none
**Gate**: Build (file exists, YAML valid)
**Commit**: `feat(infra): add docker-compose.dev.yml for local PostgreSQL 17 environment`

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Directories & Makefile | Root & folders + all RFC-010 targets | ✅ Granular |
| T2: Go Module & mise.toml | `go.mod` + `.mise.toml` | ✅ Granular |
| T3: Main HTTP Server | `cmd/api/main.go` & test | ✅ Granular |
| T4: Initial Schema Migration | 1 SQL migration file (15 RFC-006 tables) | ✅ Granular |
| T5: sqlc Config & Queries | `sqlc.yaml` with overrides & query | ✅ Granular |
| T6: docker-compose.dev.yml | 1 YAML file | ✅ Granular |

---

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T2 | T2 → T3 | ✅ Match |
| T4 | T1 | T1 → T4 | ✅ Match |
| T5 | T4 | T4 → T5 | ✅ Match |
| T6 | T1 | T1 → T6 | ✅ Match |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Directory & Makefile | none | none | ✅ OK |
| T2 | Go Module & mise.toml | none | none | ✅ OK |
| T3 | HTTP API Entry Point | unit | unit (`main_test.go`) | ✅ OK |
| T4 | Migration SQL (15 tables) | build | none (build gate) | ✅ OK |
| T5 | SQLC config with overrides | build | none (build gate) | ✅ OK |
| T6 | docker-compose.dev.yml | none | none (file check) | ✅ OK |
