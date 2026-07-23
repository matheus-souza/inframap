# Repository Scaffolding Specification

## Problem Statement

InfraMap has completed its architectural definition (RFC-001 through RFC-010). However, the repository currently contains only documentation files (`docs/`, `.github/`, `README.md`). We need to establish the physical repository structure, build scripts (`Makefile`), Go module initialization, SQL generator configuration (`sqlc.yaml`), and initial database migration script so that development of domain modules can begin.

## Goals

- [ ] Initialize Go 1.24+ module (`github.com/matheussouza/inframap`) inside `backend/`, managed via `mise` (`.mise.toml`).
- [ ] Create standardized modular monolith directory layout per RFC-010 (`backend/cmd/api/`, `backend/internal/bootstrap/`, `backend/internal/platform/{crypto,eventbus,logger,sdk}/`, `backend/internal/shared/`, `backend/modules/{audit,configuration,discovery,identity,integrations,inventory,topology}/`, `backend/migrations/`, `backend/queries/`, `frontend/`).
- [ ] Write `Makefile` with targets aligned to RFC-010: `dev`, `dev-down`, `build`, `test`, `test-coverage`, `lint`, `verify`, `generate`, `migrate-up`, `migrate-down`, `clean`.
- [ ] Create initial Goose migration `20260722000001_initial_schema.sql` implementing the **complete core schema** defined in RFC-006: `system_state`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `user_sessions`, `devices`, `network_interfaces`, `ip_addresses`, `subnets`, `topology_links`, `discovery_sources`, `device_discovery_records`, `audit_logs`.
- [ ] Create `backend/sqlc.yaml` matching RFC-010 specification including PostgreSQL type overrides (`uuid` → `uuid.UUID`, `inet` → `netip.Addr`, `cidr` → `netip.Prefix`, `macaddr` → `net.HardwareAddr`).
- [ ] Create minimal HTTP server entry point (`backend/cmd/api/main.go`) listening on port `8055` (`INFRAMAP_PORT`).
- [ ] Create `docker-compose.dev.yml` for local PostgreSQL 17 development environment.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Kotlin WASM UI source code implementation | Handled in dedicated Frontend Feature phase |
| Module business logic handlers | Handled in individual module feature implementation phases |
| Docker Hub image publishing | Handled in CI/CD deployment phase |
| Chi router integration | Can use stdlib `http.ServeMux` for scaffolding; Chi migration happens when adding real routes |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Go module location | `backend/` directory | Clean separation between Go backend and Kotlin frontend | Yes |
| Database Migration Tool | Goose SQL migrations | Embedded transaction-safe migrations on app startup | Yes |
| Default HTTP Port | `8055` | Defined in RFC-001 / RFC-010 | Yes |
| Toolchain Manager | `mise` (`.mise.toml`) | Polyglot version pinning for Go, JDK, Goose, sqlc | Yes |
| Go Version | `1.24+` (latest stable) | Updated from original 1.22+ per toolchain upgrade decision | Yes |
| JDK Version | `21 LTS` (Temurin) | Required for Gradle/Kotlin compiler; max version supported by Kotlin 2.1 | Yes |
| PostgreSQL Version | `17+` (latest stable) | RFC-006 specifies PostgreSQL as the database engine | Yes |
| Primary Key Strategy | UUIDv7 (application-side) | RFC-006 specifies UUIDv7 for temporal sortability | Yes |

---

## User Stories

### P1: Repository Physical Scaffolding ⭐ MVP

**User Story**: As a developer, I want a complete directory structure, Makefile, Go module, and initial database migration so that I can run `make dev` and begin developing modules.

**Why P1**: Critical foundation required before any Go code or database query can be executed.

**Acceptance Criteria**:

1. WHEN `make build` or `go build ./...` is executed inside `backend/` THEN system SHALL compile cleanly without errors.
2. WHEN `backend/migrations/20260722000001_initial_schema.sql` is executed by Goose THEN PostgreSQL SHALL create all 15 core tables defined in RFC-006: `system_state`, `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `user_sessions`, `devices`, `network_interfaces`, `ip_addresses`, `subnets`, `topology_links`, `discovery_sources`, `device_discovery_records`, `audit_logs`.
3. WHEN `sqlc generate` is run with `backend/sqlc.yaml` THEN Go database models and query interfaces SHALL be generated without errors, using type overrides for `uuid`, `inet`, `cidr`, and `macaddr`.
4. WHEN the entry point `backend/cmd/api/main.go` runs THEN the HTTP server SHALL bind to port `8055` and respond to health check requests on `/api/v1/health`.
5. WHEN `make verify` is executed THEN it SHALL run `generate + lint + test + build` sequentially, matching the CI Quality Gates pipeline (RFC-003).

**Independent Test**: Execute `go test ./...` and `go build ./cmd/api` inside `backend/`.

---

## Edge Cases

- WHEN database environment variables are missing THEN backend SHALL fallback gracefully to default values (`localhost:5432`, db `inframap`).
- WHEN port 8055 is specified via `INFRAMAP_PORT` THEN backend SHALL honor the environment variable over default values.

---

## Requirement Traceability

| Requirement ID | Description | Story | Phase | Status |
| --- | --- | --- | --- | --- |
| REQ-SCAF-01 | Directory structure & Makefile aligned with RFC-010 | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-02 | Goose Migration with all 15 RFC-006 tables | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-03 | sqlc.yaml with RFC-010 type overrides | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-04 | HTTP Server on port 8055 with health endpoint | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-05 | docker-compose.dev.yml for local PostgreSQL | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-06 | mise.toml with pinned toolchain versions | P1: Scaffolding | Tasks | In Tasks |

---

## RFC Cross-References

| RFC | What this spec uses from it |
| --- | --- |
| RFC-001 | Go module name, Chi router (deferred), PostgreSQL, sqlc, Goose |
| RFC-003 | Quality Gates pipeline → `make verify` target |
| RFC-005 | Modular Monolith directory layout (modules/, internal/platform/) |
| RFC-006 | Complete database schema (15 tables, UUIDv7, native PG types) |
| RFC-008 | API prefix `/api/v1/`, health endpoint |
| RFC-010 | Directory layout, Makefile targets, sqlc.yaml spec, docker-compose.dev.yml |

---

## Success Criteria

- [ ] All directories created according to RFC-010 (including module and platform subdirectories).
- [ ] Go compilation (`go test ./...` and `go build ./...`) succeeds.
- [ ] Goose migration file contains all 15 tables from RFC-006 with correct columns, types, indexes, and constraints.
- [ ] `sqlc.yaml` matches RFC-010 specification (including 4 type overrides).
- [ ] `Makefile` contains all targets defined in RFC-010 (including `verify`).
- [ ] `docker-compose.dev.yml` exists with PostgreSQL 17 configuration.
- [ ] `.mise.toml` pins Go 1.24, JDK 21 LTS, Goose, sqlc.
