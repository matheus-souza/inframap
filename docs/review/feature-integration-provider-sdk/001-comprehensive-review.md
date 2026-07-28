# Review: Integration Provider SDK & Built-in Integrations Engine (PR #27)

- **Reviewer**: Internal Code Review
- **Date**: 2026-07-27
- **Branch**: `feature/integration-provider-sdk`
- **Scope**: 18 files, ~1273 insertions
- **Verdict**: APPROVED WITH CORRECTIONS

---

## 1. Executive Summary

The Integration Provider SDK implements Phase 6: a plugin-based provider framework with `sdk.Provider` interface, registry, built-in Proxmox VE and Docker Engine providers, health check endpoint, and config schema API. The architecture cleanly separates the SDK contract (`internal/platform/sdk`) from provider implementations (`modules/integrations/providers/`) with a central registry.

Seven actionable findings require correction, primarily around security (error leakage, missing timeouts) and operational visibility (silent error swallowing in provider fetch loops).

---

## 2. Findings

### F-001: Health Check Error Details Leaked to Client (HIGH — Security / Guideline #1)

**Location**: `integrations/controller/integrations_controller.go:65`

**Problem**: `Message: err.Error()` returns the raw provider error to the API client. Provider errors like `"failed to reach Proxmox API: dial tcp 10.0.0.5:8006: connect: connection refused"` leak internal hostnames, IPs, and port numbers.

**Fix**: Return static `"Health check failed"` and log the real error via `slog.Error`.

---

### F-002: http.Client Without Timeout (HIGH — Security / Reliability)

**Location**: `providers/docker/docker_provider.go:219`, `providers/proxmox/proxmox_provider.go:241`

**Problem**: Both providers create `http.Client{Transport: tr}` without a `Timeout` field. If the target accepts TCP but never responds, the goroutine hangs indefinitely.

**Fix**: Add `Timeout: 30 * time.Second` to both clients.

---

### F-003: Docker /info Errors Silently Swallowed (MEDIUM — Correctness / Guideline #5)

**Location**: `providers/docker/docker_provider.go:106-128`

**Problem**: The entire `/info` fetch block uses cascading `if err == nil` checks. Every failure path (request creation, HTTP call, JSON decode) is silently discarded. A broken `/info` endpoint produces results missing the host device with no indication why.

**Fix**: Log each error via `slog.Warn` before continuing.

---

### F-004: Proxmox QEMU VM Errors Silently Swallowed (MEDIUM — Correctness / Guideline #5)

**Location**: `providers/proxmox/proxmox_provider.go:167-197`

**Problem**: Same pattern as F-003: QEMU VM fetch errors per node are silently swallowed. If the QEMU endpoint fails for one node, VMs are missing with no logging.

**Fix**: Log each error via `slog.Warn` with node context.

---

### F-005: Container ID[:12] May Panic (MEDIUM — Correctness)

**Location**: `providers/docker/docker_provider.go:164`

**Problem**: `name := c.ID[:12]` will panic with index-out-of-range if the Docker API returns a container ID shorter than 12 characters.

**Fix**: Guard with `if len(c.ID) >= 12 { name = c.ID[:12] } else { name = c.ID }`.

---

### F-006: Validate() err.Error() Leaked (MEDIUM — Security / Guideline #1)

**Location**: `integrations/controller/integrations_controller.go:57`

**Problem**: `httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", err.Error(), nil)` leaks validation error details.

**Fix**: Replace with static `"Invalid health check request"` and log via `slog.Warn`.

---

### F-007: Missing LXC Container Discovery (LOW — RFC Alignment)

**Location**: `providers/proxmox/proxmox_provider.go` (Discover method)

**Problem**: RFC-009 states the Proxmox provider "Discovers PVE hypervisor nodes, LXC containers, and QEMU virtual machines." The implementation only fetches QEMU VMs — LXC containers (`/api2/json/nodes/{node}/lxc`) are not implemented.

**Status**: Documented for Phase 2 (requires additional HTTP fetch block analogous to QEMU).

---

## 3. Corrections Applied

| Finding | File(s) Changed | Status |
|---|---|---|
| F-001 | `integrations/controller/integrations_controller.go` | FIXED |
| F-002 | `providers/docker/docker_provider.go`, `providers/proxmox/proxmox_provider.go` | FIXED |
| F-003 | `providers/docker/docker_provider.go` | FIXED |
| F-004 | `providers/proxmox/proxmox_provider.go` | FIXED |
| F-005 | `providers/docker/docker_provider.go` | FIXED |
| F-006 | `integrations/controller/integrations_controller.go` | FIXED |
| F-007 | — | DOCUMENTED (Phase 2) |

## 4. Living Document Updates

### CONTEXT.md
- Added guideline #18: Explicit HTTP Client Timeout for External Requests
- Added guideline #19: Error Logging in Non-Critical Provider Fetch Paths

### LESSONS_LEARNED.md
- Added lesson #9: HTTP Client Timeout for External Integrations
- Added lesson #10: Silent Error Swallowing in Provider Fetch Loops
- Added lesson #11: Internal Error Details Leaked to API Clients
