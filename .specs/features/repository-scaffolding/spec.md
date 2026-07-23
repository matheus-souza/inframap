# Repository Scaffolding Specification

## Problem Statement

InfraMap has completed its architectural definition (RFC-001 through RFC-010). However, the repository currently contains only documentation files (`docs/`, `.github/`, `README.md`). We need to establish the physical repository structure, build scripts (`Makefile`), Go module initialization, SQL generator configuration (`sqlc.yaml`), and initial database migration script so that development of domain modules can begin.

## Goals

- [ ] Initialize Go 1.22+ module (`github.com/matheussouza/inframap`) inside `backend/`.
- [ ] Create standardized modular monolith directory layout (`backend/cmd/api/`, `backend/internal/`, `backend/modules/`, `backend/migrations/`, `backend/queries/`, `frontend/`).
- [ ] Write `Makefile` with single-command `make dev` setup, build, test, and migration targets.
- [ ] Create initial Goose migration `20260722000001_initial_schema.sql` implementing the core schema defined in RFC-006.
- [ ] Create `backend/sqlc.yaml` matching PostgreSQL 16 schema and Go types.
- [ ] Create minimal HTTP server entry point (`backend/cmd/api/main.go`) listening on port `8055` (`INFRAMAP_PORT`).

## Out of Scope

| Feature | Reason |
| --- | --- |
| Kotlin WASM UI source code implementation | Handled in dedicated Frontend Feature phase |
| Module business logic handlers | Handled in individual module feature implementation phases |
| Docker Hub image publishing | Handled in CI/CD deployment phase |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Go module location | `backend/` directory | Clean separation between Go backend and Kotlin frontend | Yes |
| Database Migration Tool | Goose SQL migrations | Embedded transaction-safe migrations on app startup | Yes |
| Default HTTP Port | `8055` | Defined in RFC-001 / RFC-010 | Yes |

---

## User Stories

### P1: Repository Physical Scaffolding ⭐ MVP

**User Story**: As a developer, I want a complete directory structure, Makefile, Go module, and initial database migration so that I can run `make dev` and begin developing modules.

**Why P1**: Critical foundation required before any Go code or database query can be executed.

**Acceptance Criteria**:

1. WHEN `make dev` or `go build ./...` is executed inside `backend/` THEN system SHALL compile cleanly without errors.
2. WHEN `backend/migrations/20260722000001_initial_schema.sql` is executed by Goose THEN PostgreSQL SHALL create all core tables (`organizations`, `projects`, `environments`, `nodes`, `edges`, `users`, `audit_logs`).
3. WHEN `sqlc generate` is run with `backend/sqlc.yaml` THEN Go database models and query interfaces SHALL be generated without errors.
4. WHEN the entry point `backend/cmd/api/main.go` runs THEN the HTTP server SHALL bind to port `8055` and respond to health check requests on `/api/v1/health`.

**Independent Test**: Execute `go test ./...` and `go build ./cmd/api` inside `backend/`.

---

## Edge Cases

- WHEN database environment variables are missing THEN backend SHALL fallback gracefully to default values (`localhost:5432`, db `inframap`).
- WHEN port 8055 is specified via `INFRAMAP_PORT` THEN backend SHALL honor the environment variable over default values.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| REQ-SCAF-01 | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-02 | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-03 | P1: Scaffolding | Tasks | In Tasks |
| REQ-SCAF-04 | P1: Scaffolding | Tasks | In Tasks |

---

## Success Criteria

- [ ] All directories created according to RFC-010.
- [ ] Go compilation (`go test ./...` and `go build ./...`) succeeds.
- [ ] Goose migration file syntax is valid SQL.
- [ ] `sqlc.yaml` is valid.
