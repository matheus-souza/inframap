# ADR-009: Browser E2E Quality Gate with Playwright for WASM Frontend Stability

Date: 2026-08-25

## Context

InfraMap distributes its web interface as a WebAssembly (WASM) SPA embedded directly into a single Go binary. While Go backend endpoints had full test coverage via `tests/e2e/`, critical runtime bugs occurred exclusively in the browser layer (e.g. unhandled JavaScript `JsException` errors, infinite shimmer loading states due to unhandled coroutine cancellations, and unresponsive keyboard shortcuts/clicks). Because backend HTTP tests always returned 200 OK, existing CI pipelines failed to detect frontend crashes and UI freezes before merging and releasing container images.

## Decision

We will implement a dedicated Browser E2E Test Suite using **Playwright (Node.js/TypeScript)** located in `tests/e2e-browser/` and integrate it as a mandatory, blocking Quality Gate in the GitHub Actions CI workflow (`.github/workflows/ci.yml`).

### Key Decisions:
1. **Framework & Engine**: Playwright with headless Chromium.
2. **Environment Orchestration**: Full-stack real testing. CI spins up a PostgreSQL container, builds the single Go binary with embedded WASM, starts the server on port 8055, and runs Playwright against `http://localhost:8055`.
3. **Multi-Layer Assertions**:
   - **DOM Layer**: `#loading-screen` must hide and reach `display: none` within 5 seconds.
   - **Runtime Error Detection**: Zero uncaught JavaScript errors or unhandled coroutine rejections in `pageerror` and `console.error`.
   - **Interaction & Shortcut Testing**: Validates global `Cmd+K` / `Ctrl+K` keydown dispatches, route transitions, and responsive canvas clicks.
   - **Shimmer / Infinite Loading Guard**: Asserts that loading skeletons disappear and dashboard interactive components (Hero Card / Welcome Banner / KPI Cards) are rendered.

## Consequences

### Positive
- Prevents infinite loading, UI freezes, and JS/WASM interop regressions from merging into `develop` or reaching release Docker images.
- Tests the exact single binary artifact distributed to end users.
- Validates real browser interaction with WebAssembly and Skiko WebGL canvas rendering.

### Trade-offs / Neutral
- Adds a Node.js/Playwright installation step and ~1-2 minutes to the CI workflow.
- Requires maintaining Playwright test fixtures alongside Kotlin and Go codebases.
