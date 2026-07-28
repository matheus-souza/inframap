# Review: Automated Discovery & Reconciliation Engine (RFC-016 / PR #24)

- **Reviewer**: Internal Code Review
- **Date**: 2026-07-26
- **Branch**: `feature/automated-discovery-reconciliation-engine`
- **Scope**: 19 files, ~2789 insertions
- **Verdict**: APPROVED WITH CORRECTIONS

---

## 1. Executive Summary

The Discovery Engine module implements the tracer-bullet delivery of RFC-016: identity matching (5-tier precedence), field reconciliation with per-field confidence scoring, user-lock immunity, discovery source CRUD, and scan record persistence. The architecture follows the established Clean Architecture pattern (controller -> usecase -> repository) with proper interface boundaries, EventBus integration, and AES-256-GCM encryption for source configs.

The implementation is solid overall, with strong test coverage across all layers. Four actionable findings require correction before the module can be considered production-ready.

---

## 2. Findings

### F-001: Cross-Module Sentinel Error Coupling (MEDIUM — Correctness / Modularity)

**Location**: `backend/modules/discovery/usecase/discovery_usecase.go:22-23`, `backend/modules/discovery/controller/discovery_controller.go:13`

**Problem**: The discovery module imports `inventoryUC.ErrInvalidUUID` and `inventoryUC.ErrInvalidInput` from the inventory module's usecase package. This creates tight coupling between two domain modules that should be independently deployable. If the inventory module renames or moves these errors, discovery breaks.

**Impact**: Violates module boundary isolation principle. Makes future refactoring fragile.

**Fix**: Define discovery-local sentinel errors (`ErrInvalidUUID`, `ErrInvalidInput`) in `discovery/usecase` and use them in both the usecase and controller layers. Update corresponding tests.

**New CONTEXT.md Guideline**: "Modules MUST NOT import sentinel errors from sibling modules. Shared error types belong in `internal/platform/` or are re-declared locally."

---

### F-002: Nil Encryptor in Bootstrap Prevents Config Storage (MEDIUM — Functional Gap)

**Location**: `backend/internal/bootstrap/app.go:114`

**Problem**: `discrepo.NewPgDiscoveryRepository(pool, nil)` passes a nil encryptor. The repository correctly enforces encryption (`return nil, fmt.Errorf("encryptor is required")`), which means any discovery source with API credentials in `config` will fail at runtime. Discovery sources for Proxmox VE, Docker Engine, and UniFi Controller all require configuration with API endpoints and credentials.

**Impact**: Config-bearing discovery sources cannot be created until the encryptor is wired.

**Fix**: Read `INFRAMAP_MASTER_KEY` from environment and validate the encryptor **before** allocating any resources (database pool, event bus). When the key is present, create `crypto.AESGCMEncryptor` and pass it to `NewPgDiscoveryRepository`. When the key is absent, log a warning — the repository layer enforces encryption at runtime (`return nil, fmt.Errorf("encryptor is required")`) so config-bearing sources cannot be created without it. Production deployments MUST set `INFRAMAP_MASTER_KEY`.

---

### F-003: Dead Code — sanitizeLogInput Return Value Discarded (LOW — Code Quality)

**Location**: `backend/modules/discovery/usecase/discovery_usecase.go:103`

**Problem**: `_ = sanitizeLogInput(source.Name)` — the sanitized value is computed but discarded. Per Guideline #1 (No Error Swallowing), all function calls should either use their return value or be removed.

**Impact**: Dead code that misleads readers into thinking log sanitization is applied.

**Fix**: Remove the dead call and the now-unused `sanitizeLogInput` function. Re-add when actual scan logging is implemented in Phase 2.

---

### F-004: Reconciler Test Missing Changed Flag Assertion (LOW — Test Quality)

**Location**: `backend/modules/discovery/engine/reconciler_test.go:89`

**Problem**: The "Invalid IP and MAC format ignored gracefully" test case discards the `changed` flag with `updated, _ := reconciler.Reconcile(...)`. For a test verifying that invalid inputs don't modify the device, asserting `changed == false` is important to confirm the reconciler's no-op path.

**Fix**: Capture and assert `changed == false`.

---

## 3. Architecture Assessment

### Strengths

- **5-tier identity matching** follows RFC-016 precedence strictly with proper MAC parsing, JSON provider UUID extraction (handles both string and numeric values), and hostname+IP compound matching.
- **Per-field confidence scoring** with user-lock immunity is well-implemented. The `canUpdate` closure cleanly encapsulates the precedence+lock logic.
- **Deep additive metadata merge** with `reflect.DeepEqual` guard prevents false-positive change detection on identical payloads.
- **Pagination loop** for active device fetching (usecase:128-140) follows Guideline #6.
- **Strict encryption enforcement** in the repository (no plaintext fallback) follows Guideline #8.
- **Comprehensive test coverage**: controller (18 sub-tests), usecase (12 sub-tests), matcher (8 sub-tests), reconciler (6 sub-tests), DTO (3 sub-tests), E2E (4-step flow).

### Design Notes

- `TriggerRun` is a synchronous tracer-bullet placeholder (sets status running → idle with no actual scan work). Phase 2 will add async scan worker integration.
- `IngestNormalizedDevice` is correctly exposed only via the usecase interface (not via HTTP controller), as it will be called internally by scan workers — not by external clients.
- The `extractProviderUUID` function currently supports Proxmox and Docker namespaces. UniFi namespace extraction should be added when the UniFi collector is implemented.

---

## 4. Living Document Updates Applied

### CONTEXT.md

Added guideline: "Modules MUST NOT import sentinel errors from sibling modules. Shared error types belong in `internal/platform/` or are re-declared locally."

### LESSONS_LEARNED.md

Added lesson: Cross-module sentinel error coupling anti-pattern.

---

## 5. Corrections Applied in This PR

| Finding | File(s) Changed | Status |
|---|---|---|
| F-001 | `discovery/usecase/discovery_usecase.go`, `discovery/controller/discovery_controller.go`, both test files | FIXED |
| F-002 | `bootstrap/app.go` | FIXED |
| F-003 | `discovery/usecase/discovery_usecase.go` | FIXED |
| F-004 | `discovery/engine/reconciler_test.go` | FIXED |
