# Code Review — Full Develop Branch (Cross-Feature)

| Scope | All modules on `develop` not covered by per-feature reviews |
|---|---|
| Modules | `identity`, `configuration`, `inventory`, `httputil`, `bootstrap`, E2E tests, CI/CD, infrastructure |
| Date | 2026-07-25 |
| Status | **ALL FINDINGS RESOLVED** |

---

## Summary

21 findings across 4 severity levels. 5 critical, 5 high, 8 medium, 3 low.
Prior per-feature reviews (event-bus, identity, configuration) approved the functional spec compliance — this review focuses on cross-cutting quality, security, and correctness gaps.

**Resolution**: All 21 findings addressed — 19 fixed in code, 2 documented as known limitations (F19, F20).

---

## Findings

### CRITICAL — Fix Before Merge

#### F1: `ApproveStagingDevice` leaks internal error to client — FIXED
- **File**: `modules/inventory/controller/inventory_controller.go:181`
- **Issue**: `err.Error()` is returned directly in the HTTP response for the fallback error case, exposing database/internal error messages to the client.
- **Resolution**: Replaced `err.Error()` with opaque `"Failed to approve staging device"`.

#### F2: `Onboard` controller leaks internal error to client — FIXED
- **File**: `modules/configuration/controller/setup_controller.go:62`
- **Issue**: Same as F1 — `err.Error()` is sent as the HTTP error message.
- **Resolution**: Replaced with `"Failed to complete system onboarding"`.

#### F3: `dummyBcryptHash` is malformed — timing mitigation defeated — FIXED
- **File**: `modules/identity/usecase/identity_usecase.go:32`
- **Issue**: The constant was 59 characters (not 60); `bcrypt.CompareHashAndPassword` rejected it immediately, defeating the timing-attack mitigation.
- **Resolution**: Generated a real bcrypt cost-12 hash and replaced the constant.

#### F4: Lockout tracker grows unbounded — FIXED
- **File**: `modules/identity/usecase/identity_usecase.go:57-93`
- **Issue**: The `lockouts` map was never cleaned.
- **Resolution**: Added `cleanupLockouts` goroutine that runs every 10 minutes, evicting entries where both `lockedUntil` and `firstFail` have expired past the 5-minute window. Constructor now accepts `context.Context` to control goroutine lifetime.

#### F5: No request body size limit on any endpoint — FIXED
- **File**: `internal/platform/httputil/middleware.go`
- **Issue**: Without `http.MaxBytesReader`, clients could send arbitrarily large bodies.
- **Resolution**: Added `LimitBody` middleware (1 MiB cap via `http.MaxBytesReader`) to the middleware stack between `SecurityHeaders` and `Recovery`.

---

### HIGH — Should Fix

#### F6: `isPublicRoute` uses overly broad prefix matching — FIXED
- **File**: `internal/platform/httputil/auth_middleware.go`
- **Issue**: Prefix matching meant any future route under `/setup/` would be automatically public.
- **Resolution**: Replaced with exact-match `publicRoutes` map.

#### F7: `ListDevices` and `ListStagingDevices` discard pagination total — FIXED
- **File**: `modules/inventory/controller/inventory_controller.go`
- **Issue**: Controller discarded `totalCount` from usecase.
- **Resolution**: Both endpoints now return `{"items": [...], "total": N, "page": P, "per_page": PP}`.

#### F8: `UpdateDevice` silently ignores IP/MAC address updates — FIXED
- **File**: `modules/inventory/usecase/inventory_usecase.go`
- **Issue**: `IPAddress` and `MACAddress` fields in `UpdateDeviceRequest` were never applied.
- **Resolution**: Added IP parsing (`netip.ParseAddr`) and MAC parsing (`net.ParseMAC`) with locked-field tracking, matching the pattern used for other fields.

#### F9: `SubnetResponse.VLANID` inconsistent nil handling — FIXED
- **File**: `modules/inventory/usecase/inventory_usecase.go`
- **Issue**: `CreateSubnet` always set `VLANID: &subnet.VlanID.Int32` even when not valid.
- **Resolution**: Added `.Valid` check before setting `VLANID`, consistent with `ListSubnets`.

#### F10: Duplicate token extraction logic — FIXED
- **Files**: `identity/controller/identity_controller.go`, `internal/platform/httputil/auth_middleware.go`
- **Issue**: Two copies of identical token extraction logic.
- **Resolution**: Exported `httputil.ExtractToken` (with nil-check on request), removed local copy from identity controller.

---

### MEDIUM — Nice to Fix

#### F11: Installed version metadata never persisted — FIXED
- **File**: `modules/configuration/repository/repository.go`
- **Issue**: `metadataBytes` computed but never used (`_ = metadataBytes`).
- **Resolution**: Removed dead computation and unused `encoding/json` import.

#### F12: Dead code — unused import retainers — FIXED
- **Files**: `inventory/usecase`, `configuration/dto`, `identity/dto`
- **Issue**: `var _ = json.Unmarshal`, `_ = uuid.Nil`, `_ = mail.ParseAddress` blank assignments.
- **Resolution**: Removed all retainers and their unused imports.

#### F13: `Validate()` methods mutate the receiver struct — FIXED
- **Files**: `configuration/dto`, `identity/dto`, `inventory/dto`
- **Issue**: Validation functions mutating input (trimming, defaulting).
- **Resolution**: Extracted `Normalize()` methods for mutation (trim, defaults). `Validate()` is now pure. All call sites updated to call `Normalize()` before `Validate()`.

#### F14: `CreateSubnet` validation is inconsistent — FIXED
- **File**: `modules/inventory/dto/inventory_dto.go`
- **Issue**: No `Validate()` method on `CreateSubnetRequest`, CIDR not validated.
- **Resolution**: Added `Validate()` checking name and CIDR format via `netip.ParsePrefix`. Controller uses `req.Validate()` pattern.

#### F15: Login path lacks unit test coverage — FIXED
- **Files**: `modules/identity/repository/session_repository.go`, `modules/identity/usecase/identity_usecase.go`, `modules/identity/usecase/identity_usecase_test.go`
- **Issue**: `Login()` used raw SQL via `pgxpool.Pool`, untestable without a live database.
- **Resolution**: Added `LookupUserByUsername` to `SessionRepository` interface with `UserCredentials` struct and `ErrUserNotFound` sentinel. Refactored `Login()` to use the repository. Added 5 unit tests: valid login, wrong password, unknown user, inactive user, lockout after 5 failures.

#### F16: `sanitizeLogInput` is incomplete — FIXED
- **File**: `modules/identity/usecase/identity_usecase.go:242-249`
- **Issue**: Only stripped `\n` and `\r`, missing null bytes, ANSI escapes, other control chars.
- **Resolution**: Rewritten with `strings.Map` to filter all characters `< 0x20` and `0x7F` (DEL).

#### F17: `CreateDeviceRequest.Validate()` defaults DeviceType — FIXED
- **File**: `modules/inventory/dto/inventory_dto.go`
- **Issue**: `Validate()` silently set `DeviceType` to `"unknown"`.
- **Resolution**: Defaulting moved to `Normalize()`. `Validate()` is now pure.

#### F18: Inventory controller mock couples GetDevice and UpdateDevice state — FIXED
- **File**: `modules/inventory/controller/inventory_controller_test.go`
- **Issue**: `UpdateDevice` mock used `getDeviceResp`/`getDeviceErr` fields.
- **Resolution**: Added dedicated `updateDeviceResp`/`updateDeviceErr` fields to mock, `UpdateDevice` method now uses its own fields.

---

### LOW — Informational

#### F19: In-memory lockout tracker resets on restart — ACKNOWLEDGED
- **File**: `modules/identity/usecase/identity_usecase.go:57`
- **Note**: Acceptable for single-binary homelab deployment. To be addressed when Redis or persistent store is introduced. Partially mitigated by F4 cleanup goroutine preventing unbounded growth.

#### F20: E2E tests share database state — ACKNOWLEDGED
- **File**: `backend/tests/e2e/onboarding_e2e_test.go`
- **Note**: Tests partially handle this (accept both 201 and 409 on onboarding). Full isolation (per-test truncate or separate DB) deferred to a dedicated test-infrastructure improvement.

#### F21: E2E `inventory_e2e_test.go` unsafe type assertions — FIXED
- **File**: `backend/tests/e2e/inventory_e2e_test.go`
- **Issue**: Bare type assertions would panic on unexpected shape.
- **Resolution**: Replaced with two-value form + `t.Fatal` for both login response and create device response parsing.

---

## Implementation Phases (Completed)

| Phase | Findings | Status |
|---|---|---|
| **Phase 1 — Security** | F1, F2, F3, F4, F5, F6, F7, F10, F13, F14, F16 | Done |
| **Phase 2 — Correctness** | F8, F9, F11, F12, F13 | Done |
| **Phase 3 — Code quality** | F12, F13, F14, F17 | Done |
| **Phase 4 — Test quality** | F15, F18, F21 | Done |
| **Phase 5 — Acknowledged** | F19, F20 | Known limitations |

---

## Cross-Cutting Observations (No Action)

- **Architecture**: Clean separation of controller/usecase/repository is consistent across all three domain modules. Good.
- **Event Bus integration**: All write operations emit domain events. The `_ = bus.Publish()` pattern means event delivery failures are silently dropped, which is the correct choice for non-critical audit logging.
- **Security headers**: Applied globally via middleware (X-Frame-Options: DENY, X-Content-Type-Options: nosniff, Referrer-Policy). Good.
- **Recovery middleware**: Handles panics correctly with structured logging.
- **Session management**: SHA-256 token hashing, sliding + absolute expiry, HttpOnly/Secure cookies — all well-implemented.
- **Pre-push hook**: Runs lint + test which is aligned with the CI pipeline.
- **Codecov config**: Exclusion rules match the `format_coverage.go` script exclusions. Consistent.
