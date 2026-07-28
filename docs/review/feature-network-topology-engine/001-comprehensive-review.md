# Review: Network Topology & Mapping Engine (RFC-017 / PR #26)

- **Reviewer**: Internal Code Review
- **Date**: 2026-07-27
- **Branch**: `feature/network-topology-engine`
- **Scope**: 15 files, ~2063 insertions
- **Verdict**: APPROVED WITH CORRECTIONS

---

## 1. Executive Summary

The Topology Engine module implements RFC-017: topology link CRUD, network graph representation, and automatic virtual link inference from EventBus device events. The architecture follows Clean Architecture (controller -> usecase -> repository) with sqlc-generated data access, EventBus integration for auto-inference, and paginated graph queries.

Six actionable findings require correction, including a repeated cross-module coupling violation and several error-handling deficiencies.

---

## 2. Findings

### F-001: Cross-Module Sentinel Error Coupling (HIGH — Modularity / Guideline #9)

**Location**: `topology/usecase/topology_usecase.go:15-16`, `topology/controller/topology_controller.go:11`

**Problem**: The topology module imports `inventoryUC.ErrInvalidUUID` and `inventoryUC.ErrInvalidInput` from the inventory module's usecase package. This is the same violation found in the discovery module (PR #25, F-001) and violates Guideline #9.

**Fix**: Define module-local sentinel errors in `topology/usecase` and update controller + tests.

---

### F-002: Internal Error Details Leaked to Client (HIGH — Security / Guideline #1)

**Location**: `topology/controller/topology_controller.go:38`

**Problem**: `httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", err.Error(), nil)` passes `err.Error()` directly to the API client. Wrapped error chains from the usecase (e.g., `invalid input: source and target must differ`) leak internal validation logic.

**Fix**: Replace with static opaque message `"Invalid topology link input"` and log the real error via `slog.Warn`.

---

### F-003: GetLinkByID Maps All DB Errors to ErrLinkNotFound (HIGH — Correctness / Guideline #5)

**Location**: `topology/repository/topology_repository.go:87-89`

**Problem**: `if err != nil { return nil, ErrLinkNotFound }` converts all database errors (connection timeouts, context cancellation, constraint violations) to "not found". This hides real failures and can mislead callers into treating infrastructure problems as missing resources.

**Fix**: Check `pgx.ErrNoRows` specifically; wrap and return all other errors.

---

### F-004: json.Unmarshal Errors Silently Discarded (MEDIUM — Correctness / Guideline #5)

**Location**: `topology/repository/topology_repository.go:176, 269`

**Problem**: `_ = json.Unmarshal(d.Metadata, &nodeMeta)` silently discards unmarshal errors in both `GetGraphData` and `mapRowToLinkResponse`. Corrupted JSONB metadata in the database would produce silently empty metadata maps.

**Fix**: Log the error at warning level via `slog.Warn`.

---

### F-005: inferVirtualLink Silently Discards CreateLink Errors (LOW — Correctness / Guideline #5)

**Location**: `topology/usecase/topology_usecase.go:210`

**Problem**: `if _, err := u.repo.CreateLink(ctx, req); err == nil { ... }` silently discards the error path. If link inference fails (e.g., duplicate constraint violation), there is no logging.

**Fix**: Add `else` branch with `u.logger.Warn(...)`.

---

### F-006: DeleteLink Hardcodes link_type "manual" in Event (LOW — Correctness)

**Location**: `topology/usecase/topology_usecase.go:116`

**Problem**: `u.publishTopologyUpdated(ctx, id, "deleted", "manual")` hardcodes `"manual"` as the link type. If a non-manual link is deleted (e.g., `virtual_hypervisor`), the event carries the wrong type.

**Impact**: Downstream event consumers (SSE gateway, audit log) receive incorrect metadata.

**Status**: Documented for Phase 2 fix (requires pre-fetch before delete or returning link type from repository).

---

## 3. Corrections Applied

| Finding | File(s) Changed | Status |
|---|---|---|
| F-001 | `topology/usecase/*.go`, `topology/controller/*.go`, both test files | FIXED |
| F-002 | `topology/controller/topology_controller.go` | FIXED |
| F-003 | `topology/repository/topology_repository.go` | FIXED |
| F-004 | `topology/repository/topology_repository.go` | FIXED |
| F-005 | `topology/usecase/topology_usecase.go` | FIXED |
| F-006 | — | DOCUMENTED (Phase 2) |

## 4. Living Document Updates

### CONTEXT.md
- Added RFC-017 to ADR list

### LESSONS_LEARNED.md
- Added lesson #8: Repository Error Discrimination
- Added lesson #11: Internal Error Details Leaked to API Clients
