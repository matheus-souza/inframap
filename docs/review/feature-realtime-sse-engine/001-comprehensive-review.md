# Review: Real-time Event Streaming Gateway & SSE Engine (PR #28)

- **Reviewer**: Internal Code Review
- **Date**: 2026-07-27
- **Branch**: `feature/realtime-sse-engine`
- **Scope**: 13 files, ~853 insertions
- **Verdict**: APPROVED WITH CORRECTIONS

---

## 1. Executive Summary

The Realtime module implements Phase 7: an SSE-based event streaming gateway with ring buffer replay, heartbeat keep-alive, and EventBus integration. The architecture is clean — `gateway.Gateway` subscribes to domain events and broadcasts to SSE channels, `controller.SSEController` handles HTTP streaming with proper Flusher detection, and `RingBuffer` provides reconnection replay via `Last-Event-ID`.

Two minor findings require correction. The implementation already incorporates guidelines #15-17 (subscription ordering, safe UUID construction, deterministic async tests) which were added proactively by the PR author.

---

## 2. Findings

### F-001: UUIDv7 Fallback Error Silently Discarded (LOW — Correctness / Guideline #5)

**Location**: `gateway/types.go:22-28`

**Problem**: When `uuid.NewV7()` fails, `NewEventMessage` falls back to UUIDv4 but silently discards the error. Per Guideline #5, all error paths must be logged or returned. Per Guideline #16, UUID construction should not panic, but errors should still be visible.

**Fix**: Add `slog.Warn("UUIDv7 generation failed, falling back to UUIDv4", "error", err)`.

---

### F-002: StreamQueryParams DTO Defined But Not Wired (LOW — Dead Code)

**Location**: `dto/realtime_dto.go`, `controller/sse_controller.go`

**Problem**: The `StreamQueryParams` struct has `Normalize()` and `Validate()` methods but is never used by the controller. The controller reads `Last-Event-ID` header and `last_event_id` query param directly. The DTO also has a `Token` field suggesting planned SSE auth that is not implemented.

**Impact**: Dead code that will need wiring when SSE auth is implemented. Not harmful but violates the project pattern of DTOs being actively consumed.

**Status**: Documented — no code change needed. Wire when SSE token auth is implemented.

---

## 3. Architecture Assessment

### Strengths

- **Subscribe-before-replay ordering** (controller:40-58) correctly prevents race windows where live events during replay query would be dropped. Codified as Guideline #15.
- **Non-blocking broadcast** with `select/default` drop and `slog.Warn` logging (gateway:86-92) prevents slow SSE clients from stalling the EventBus.
- **Safe UUID construction** without `uuid.Must` (types:22-28) avoids panics in background EventBus workers. Codified as Guideline #16.
- **Deterministic test synchronization** via `waitForSubscriber` polling (controller_test:17-26) avoids flaky `time.Sleep` patterns. Codified as Guideline #17.
- **Comprehensive error path testing**: Non-flusher, initial write failure, replay write failure, and event write failure all covered with custom `failingResponseWriter`.
- **Ring buffer circular wrapping** with proper eviction replay (returns all buffered on unknown ID) provides best-effort reconnection.

### Design Notes

- `DefaultHeartbeatInterval = 15s` is appropriate for SSE keep-alive behind reverse proxies (nginx default timeout is 60s).
- Ring buffer capacity of 100 events is a tracer-bullet value. May need tuning based on event volume in production.
- The `Token` field in `StreamQueryParams` is scaffolding for future SSE auth via query parameter (since SSE `EventSource` API doesn't support custom headers). Will need wiring when implemented.

### F-003: SSE Write Errors Silently Ignored (MINOR — Stability, CodeRabbit)

**Location**: `controller/sse_controller.go:43-45` (original)

**Problem**: `fmt.Fprint(w, ": connected\n\n")` return value was discarded with `_, _ =`. When the client disconnects mid-handshake, the subscriber stays registered in the EventBus, filling its channel buffer and potentially blocking publishers. Same pattern applied to replay, heartbeat, and live event writes.

**Fix**: Check every `fmt.Fprint` error, log with `slog.Error`, and return immediately — `defer unsub()` handles subscriber cleanup. Applied to all 4 write paths (initial ack, replay, heartbeat, live events).

**Source**: CodeRabbit automated review (PR #28, comment 2026-07-27T16:39)

---

## 4. Corrections Applied

| Finding | File(s) Changed | Status |
|---|---|---|
| F-001 | `gateway/types.go` | FIXED |
| F-002 | — | DOCUMENTED (future SSE auth) |
| F-003 | `controller/sse_controller.go` | FIXED (commit 70bb7be) |

## 5. Living Document Updates

### CONTEXT.md
- Guidelines #15-17 were added proactively by the PR author (no additional changes needed for this PR)

### LESSONS_LEARNED.md
- Added lesson #12: SSE/Streaming Write Error Handling
- Added checklist item: SSE write error checking
