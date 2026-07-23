# Repository Scaffolding Tasks

## Execution Protocol

Implement these tasks with the `tlc-spec-driven` skill.

---

**Spec**: `.specs/features/repository-scaffolding/spec.md`
**Status**: Approved

---

## Test Coverage Matrix

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| HTTP API Entry Point | unit | Health check route `/api/v1/health` returns 200 OK | `backend/cmd/api/*_test.go` | `cd backend && go test ./...` |
| Database Migration DDL | build | Goose migration syntax valid | `backend/migrations/*.sql` | `goose -dir backend/migrations validate` (or build check) |
| SQLC Config & Schema | build | `sqlc generate` succeeds | `backend/sqlc.yaml` | `cd backend && sqlc compile` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | After unit tests | `cd backend && go test ./...` |
| Build | After file creation / config tasks | `cd backend && go build ./...` |

---

## Execution Plan

```
Phase 1: Foundation (Directories & Makefile)
T1 → T2

Phase 2: Go Backend Initialization & Health API
T3 → T4

Phase 3: Database Migration & SQLC Setup
T5 → T6
```

---

## Task Breakdown

### T1: Create Directory Layout & Root Makefile

**What**: Create directory structure (`backend/cmd/api/`, `backend/internal/`, `backend/modules/`, `backend/migrations/`, `backend/queries/`, `frontend/`) and root `Makefile`.
**Where**: `Makefile`, `backend/`, `frontend/`
**Depends on**: None
**Requirement**: REQ-SCAF-01

**Done when**:
- [ ] Root `Makefile` created with `dev`, `build`, `test`, `lint` targets
- [ ] Directory structure established
- [ ] `make help` or `make` executes without errors

**Tests**: none
**Gate**: Build (`make help` or `ls Makefile`)
**Commit**: `chore(scaffold): initialize directory structure and root Makefile`

---

### T2: Initialize Go Module in Backend

**What**: Run `go mod init github.com/matheussouza/inframap` inside `backend/` and create initial `go.mod`.
**Where**: `backend/go.mod`
**Depends on**: T1
**Requirement**: REQ-SCAF-01

**Done when**:
- [ ] `backend/go.mod` created with module `github.com/matheussouza/inframap` and Go version 1.22+
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
- [ ] `main_test.go` verifies the HTTP response

**Tests**: unit
**Gate**: Quick (`cd backend && go test ./...`)
**Commit**: `feat(api): add minimal HTTP server entry point and health check route`

---

### T4: Create Initial Goose Database Migration Schema

**What**: Create Goose migration `backend/migrations/20260722000001_initial_schema.sql` based on RFC-006 DDLs.
**Where**: `backend/migrations/20260722000001_initial_schema.sql`
**Depends on**: T1
**Requirement**: REQ-SCAF-02

**Done when**:
- [ ] Migration includes `-- +goose Up` and `-- +goose Down` sections
- [ ] Defines `organizations`, `projects`, `environments`, `nodes`, `edges`, `users`, `audit_logs` tables with soft delete and updated_at triggers

**Tests**: none
**Gate**: Build (`cd backend && go build ./...`)
**Commit**: `feat(db): create initial Goose schema migration 20260722000001_initial_schema.sql`

---

### T5: Create sqlc Configuration & Initial Queries

**What**: Create `backend/sqlc.yaml` and `backend/queries/health.sql` for sqlc code generation.
**Where**: `backend/sqlc.yaml`, `backend/queries/health.sql`
**Depends on**: T4
**Requirement**: REQ-SCAF-03

**Done when**:
- [ ] `backend/sqlc.yaml` configured for PostgreSQL 16
- [ ] `backend/queries/health.sql` includes sample query
- [ ] `cd backend && go build ./...` passes

**Tests**: none
**Gate**: Build (`cd backend && go build ./...`)
**Commit**: `feat(db): configure sqlc.yaml and initial query files`

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Directories & Makefile | Root & folders | ✅ Granular |
| T2: Go Module init | `go.mod` file | ✅ Granular |
| T3: Main HTTP Server | `cmd/api/main.go` & test | ✅ Granular |
| T4: Initial Schema Migration | 1 SQL migration file | ✅ Granular |
| T5: sqlc Config & Queries | `sqlc.yaml` & query | ✅ Granular |

---

## Diagram-Definition Cross-Check

| Task | Depends On | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | None | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T2 | T2 → T3 | ✅ Match |
| T4 | T1 | T1 → T4 | ✅ Match |
| T5 | T4 | T4 → T5 | ✅ Match |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Directory & Makefile | none | none | ✅ OK |
| T2 | Go Module | none | none | ✅ OK |
| T3 | HTTP API Entry Point | unit | unit (`main_test.go`) | ✅ OK |
| T4 | Migration SQL | build | none (build gate) | ✅ OK |
| T5 | SQLC config | build | none (build gate) | ✅ OK |
