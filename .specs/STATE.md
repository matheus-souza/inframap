# Project State & Memory

## Decisions Log (AD-NNN)

| ID | Date | Context / Decision | Rationale | Impact | Status |
| --- | --- | --- | --- | --- | --- |
| AD-001 | 2026-07-23 | Shared Database Modular Monolith Architecture (RFC-005) | Simplifies deployment and guarantees referential integrity via FKs between modules. | Single Go process + Shared PostgreSQL DB | Active |
| AD-002 | 2026-07-23 | Zero Data Loss Auto-Update Goose Migration Policy (RFC-006) | Automatic `goose.Up()` on container startup inside DB transactions. | Additive DDLs only, strict backward compatibility | Active |
| AD-003 | 2026-07-23 | Portainer-Style Single Binary Distribution (RFC-001) | Kotlin WASM UI static assets embedded into Go binary via `embed.FS`. | Single executable serving API on `/api/v1` and UI on `/` | Active |
| AD-004 | 2026-07-23 | Default Application Port 8055 | Standardized port `8055` (`INFRAMAP_PORT`). | Avoids collisions with default 8080/3000 ports | Active |
| AD-005 | 2026-07-23 | Prefix Opaque Auth Tokens `ims_` (RFC-008) | Stateful opaque token format `ims_<crypto_random>`. | Easy secret scanning, high entropy, fast lookup | Active |
| AD-006 | 2026-07-23 | In-Memory By-Value Event Bus Payloads (RFC-009) | Go struct values passed without internal JSON serialization. | Zero allocation overhead, strong typing across modules | Active |
| AD-007 | 2026-07-23 | GitHub Default Branch `main` & Pre-Release `develop` | `main` is production default, `develop` generates `-rc` pre-releases. | Smooth Semantic Release pipeline without doc release spam | Active |
| AD-008 | 2026-07-23 | PR Target Branch Guard | `main` only accepts PRs from `develop` or `hotfix/*`. Features target `develop`. | Prevents unreviewed feature merges directly to production | Active |

---

## Handoff Snapshot

- **Current Active Feature:** `repository-scaffolding`
- **Active Branch:** `feature/repository-scaffolding`
- **Current Phase:** Specify & Tasks Planning
- **Next Action:** Execute tasks in `.specs/features/repository-scaffolding/tasks.md` sequentially.
