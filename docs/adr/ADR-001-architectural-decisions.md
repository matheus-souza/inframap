# ADR-001: Architectural Decisions Log

This document tracks active architectural decisions (ADRs) for the InfraMap project.

| ID | Date | Context / Decision | Rationale | Impact | Status |
| --- | --- | --- | --- | --- | --- |
| AD-001 | 2026-07-23 | Shared Database Modular Monolith Architecture (RFC-005) | Simplifies deployment and guarantees referential integrity via FKs between modules. | Single Go process + Shared PostgreSQL DB | Active |
| AD-002 | 2026-07-23 | Zero Data Loss Auto-Update Goose Migration Policy (RFC-006) | Automatic `goose.Up()` on container startup inside DB transactions. | Additive DDLs only, strict backward compatibility | Active |
| AD-003 | 2026-07-23 | Portainer-Style Single Binary Distribution (RFC-001) | Static assets embedded into Go binary via `embed.FS`. | Single executable serving API on `/api/v1` and UI on `/` | Active |
| AD-004 | 2026-07-23 | Default Application Port 8055 | Standardized port `8055` (`INFRAMAP_PORT`). | Avoids collisions with default 8080/3000 ports | Active |
| AD-005 | 2026-07-23 | Prefix Opaque Auth Tokens `ims_` (RFC-008) | Stateful opaque token format `ims_<crypto_random>`. | Easy secret scanning, high entropy, fast lookup | Active |
| AD-006 | 2026-07-23 | In-Memory By-Value Event Bus Payloads (RFC-009) | Go struct values passed without internal JSON serialization. | Reduces serialization overhead, strong typing across modules | Active |
| AD-007 | 2026-07-23 | GitHub Default Branch `main` & Pre-Release `develop` | `main` is production default, `develop` generates `-rc` pre-releases. | Smooth Semantic Release pipeline without doc release spam | Active |
| AD-008 | 2026-07-23 | PR Target Branch Guard | `main` only accepts PRs from `develop` or `hotfix/*`. Features target `develop`. | Prevents unreviewed feature merges directly to production | Active |
| AD-009 | 2026-07-28 | Codecov `after_n_builds` Must Match Upload Jobs | Codecov evaluates after receiving partial data (1/N uploads) and may report SUCCESS prematurely via CheckRun API, while the final Status API reports FAILURE. Auto-merge triggers on the premature CheckRun. | `after_n_builds: N` in `codecov.yml` (N = coverage upload jobs). Must be incremented when adding jobs. | Active |
| AD-010 | 2026-07-28 | All CI Gate Jobs Must Be Required Status Checks | A CI job that is not in the required status checks list runs but does not block merge on failure. This caused PR #39 to merge with a failing frontend quality gate. | Every new CI gate job must be immediately added to branch protection required checks. | Active |
| AD-011 | 2026-07-28 | GITHUB_TOKEN Limitation in Auto-Merge | Pushes from `GITHUB_TOKEN` do not trigger subsequent workflows. Auto-merge to `develop` never triggers CI, leaving external tool baselines stale. | CI includes `workflow_dispatch` for manual refresh. Future: migrate to GitHub App token for auto-merge. | Active |
