# InfraMap — Continuous Engineering & Review Knowledge Base

> **Purpose**: This document catalogues lessons learned from external code reviews (CodeRabbit, Peer Reviews, Audit Reports). Every developer and AI agent MUST consult this knowledge base before specifying, designing, or implementing new modules.

---

## 🛑 Anti-Patterns & Mandatory Guidelines Catalog

### 1. Error Propagation & Logging
- ❌ **Anti-Pattern**: Swallowing errors silently (e.g., `if fetchErr == nil { ... }` without an `else` branch, or discarding `netip.ParseAddr` errors without logging).
- ✅ **Required Pattern**: Always log errors with contextual fields via `slog.Logger` or return wrapped errors with sentinel preservation (`fmt.Errorf("failed to process: %w", err)`).
- 🔒 **Controller Privacy Rule**: Return opaque error responses to HTTP clients (`httputil.WriteError`), but log detailed tracebacks internally.

### 2. Database & Data Access
- ❌ **Anti-Pattern**: Using static query limits (e.g. `limit = 1000`) without a pagination loop when fetching active inventory for matching or processing.
- ✅ **Required Pattern**: Implement pagination loops (`for { offset += limit }`) when fetching collections that could exceed page boundaries.
- ❌ **Anti-Pattern**: Plaintext fallbacks when encryption dependencies are missing (e.g., saving unencrypted secrets when `encryptor == nil`).
- ✅ **Required Pattern**: Enforce strict erroring (`return nil, fmt.Errorf("encryptor is required")`) whenever encryption or security requirements are unfulfilled.

### 3. Type Assertions & JSON Unmarshaling
- ❌ **Anti-Pattern**: Expecting `.(string)` on unmarshaled JSON numbers inside `map[string]interface{}` (e.g., `meta["proxmox"]["vm_id"].(string)` fails when JSON contains `100`).
- ✅ **Required Pattern**: Accept interface values and convert using `fmt.Sprintf("%v", val)` or `strings.TrimSpace` for string/number flexibility.

### 4. State Reconciliations & Metadata Merging
- ❌ **Anti-Pattern**: Overwriting nested JSONB namespaces (e.g. `meta[sourceType] = incoming.RawPayload`), wiping out existing provider keys during partial scans.
- ✅ **Required Pattern**: Perform deep key-by-key merging on nested metadata maps and use `reflect.DeepEqual` before marking records as changed.
- ❌ **Anti-Pattern**: Single device-level confidence score blocking independent attribute updates.
- ✅ **Required Pattern**: Track confidence scores per field independently in `metadata.field_confidence_scores`.

### 5. Cross-Module Boundary Isolation
- **Anti-Pattern**: Importing sentinel errors (`ErrInvalidUUID`, `ErrInvalidInput`) from a sibling domain module's `usecase` package (e.g., `discovery` importing from `inventory/usecase`). This creates hidden coupling that breaks when the source module refactors.
- **Required Pattern**: Define module-local sentinel errors in each module's `usecase` package. If errors are truly shared across 3+ modules, promote them to `internal/platform/errors`.

### 6. Documentation & Implementation Alignment
- ❌ **Anti-Pattern**: Documenting future state machine transitions (e.g., 24h/30d offline sweeps) as already delivered in a feature RFC.
- ✅ **Required Pattern**: Clearly distinguish what is delivered in the current feature PR vs. what is delegated to future background lifecycle workers (Phase 4.2).

---

## 📋 Pre-Implementation Checklist (Run Before Writing Code)

Before writing code for any ticket or RFC, verify:
- [ ] Are DTO `Normalize()` (mutating) and `Validate()` (pure) separated?
- [ ] Are all error branches handled with either an explicit return or `slog.Warn/Error` logging?
- [ ] Are all database fetches paginated if total records can exceed 1000?
- [ ] Are JSON map assertions resilient to numbers vs strings?
- [ ] Are metadata merges deep-merged and guarded by `reflect.DeepEqual`?
- [ ] Is encryption strictly enforced without silent fallback to plaintext?
- [ ] Are sentinel errors defined locally (not imported from sibling modules)?
- [ ] Are unit test suites targeting >= 85% Patch Coverage?
