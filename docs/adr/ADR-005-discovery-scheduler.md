# ADR-005: Discovery Background Scheduler

| ID | Date | Context / Decision | Rationale | Impact | Status |
| --- | --- | --- | --- | --- | --- |
| AD-032 | 2026-08-11 | Use `robfig/cron/v3` for cron expression parsing and job scheduling | Standard Go cron library (~2k LoC, no transitive deps). Parsing cron expressions correctly (day-of-week, ranges, steps) is complexity we don't want to maintain. | First external utility dependency beyond core (pgx, uuid, goose) | Active |
| AD-033 | 2026-08-11 | Scheduler lives in separate package `modules/discovery/scheduler/` | Scheduler decides **when** to execute; UseCase decides **how**. Separate responsibilities, independently testable without cron or database. | Clean dependency: scheduler depends on `DiscoveryUseCase` interface | Active |
| AD-034 | 2026-08-11 | One `cron.Entry` per discovery source, tracked via `map[uuid.UUID]cron.EntryID` | Natural use of `robfig/cron`, avoids polling the database, respects individual source cadence. | Per-source job management with entry tracking | Active |
| AD-035 | 2026-08-11 | Event bus for immediate reload + reconciliation loop every 5 min | Event bus provides speed (immediate reaction to source CRUD). Reconciliation provides consistency (diff DB vs. registered jobs, self-corrects drift). Decouples scheduler correctness from event bus reliability. | Dual mechanism: `discovery_source.created/updated/deleted` events + periodic DB reconciliation | Active |
| AD-036 | 2026-08-11 | In-memory lock per source to prevent concurrent scans of the same source | Skip policy: if source is already running, log warn and skip. In-memory `sync.Mutex` per source avoids extra DB queries. Single-instance only; evolve to advisory lock if multi-instance is needed. | No concurrent scans per source; lock is local, not distributed | Active |
| AD-037 | 2026-08-11 | Graceful shutdown: cancel in-flight scans, set status to `"cancelled"` (not `"error"`) | Shutdown is controlled interruption, not failure. New `last_status` value `"cancelled"` distinguishes shutdown from scan errors. Requires CHECK constraint migration. | New status value; scheduler cleanup runs before EventBus/DB close | Active |
| AD-038 | 2026-08-11 | On boot, reset `running`/`cancelled` sources to `idle` with structured warning log | Stale states from previous lifecycle (crash or graceful shutdown) are reset. No immediate re-execution — source follows normal cron schedule. Operator visibility via `slog.Warn`. | Predictable boot behavior; no thundering herd after restart | Active |
| AD-039 | 2026-08-11 | Incremental `App.Start(ctx)` lifecycle — scheduler only | New `Start(ctx)` method on `App` for explicit post-wiring startup. Only scheduler uses it initially; existing components (EventBus, Realtime Gateway) migrate in a separate change. | `main.go` calls `New() → Start(ctx) → Close()`; minimal blast radius | Active |
| AD-040 | 2026-08-11 | Publish `discovery_source.scan.started` and `discovery_source.scan.completed` events | Enables audit trail to distinguish scheduled vs. manual scans. Frontend can react to scan lifecycle. Scheduler-internal lifecycle events (started/stopped) stay in logs only. | Two new domain events with scan metadata (source_id, trigger, duration, result) | Active |
| AD-041 | 2026-08-11 | No dedicated scheduler HTTP endpoint | Existing `GET /api/v1/discovery/sources` returns `schedule_cron`, `last_status`, `last_run_at` — sufficient for operator visibility. Dedicated endpoint deferred until proven necessary. | Zero API surface change | Active |

## Context

Discovery sources support `schedule_cron` expressions for automated scanning, but no background scheduler consumed them. Scans could only be triggered manually via `POST /api/v1/discovery/sources/{id}/run`. T31 introduces a background scheduler that executes `TriggerRun` on cron schedules.

## Key Design Principles

1. **Speed + Consistency**: Event bus reacts immediately to source changes; reconciliation loop guarantees eventual correctness independent of event bus reliability.
2. **Shutdown ≠ Error**: Controlled shutdown sets `"cancelled"`, not `"error"`. The operator sees the distinction; monitoring does not alert on shutdowns.
3. **Single-instance first**: In-memory locks and local state. Advisory locks deferred until multi-instance deployment exists.
4. **Incremental lifecycle**: `Start(ctx)` introduced for scheduler without refactoring existing component startup.
