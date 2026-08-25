# RFC-021: Browser E2E Quality Gate & Automated WASM Smoke Test Suite

> **Status**: Approved  
> **Authors**: InfraMap Core Team  
> **Date**: 2026-08-25  
> **Target Branch**: `develop`  
> **Reference Decisions**: ADR-009, Guideline #133  

---

## Problem Statement

When deploying InfraMap as a single binary with embedded WebAssembly, all Go backend HTTP tests pass with 200 OK, but client-side runtime errors (such as unhandled JavaScript exceptions, infinite shimmer skeletons, blocked pointer input events on the Compose canvas, and broken keyboard shortcuts) can break the user experience without triggering existing CI quality gates. A dedicated browser-level testing gate is required to ensure that every build is verified inside a real headless browser environment before merging or publishing release images.

---

## Solution

Build a headless browser E2E test suite using **Playwright (TypeScript)** in `tests/e2e-browser/` and integrate it into GitHub Actions CI as a required, blocking quality gate. The suite runs against a live InfraMap single binary connected to an ephemeral PostgreSQL instance, validating DOM loading dismissal, absence of unhandled browser console/page errors, dashboard skeleton resolution, and global keyboard shortcuts.

---

## User Stories

1. As a developer submitting a PR, I want the CI pipeline to run automated browser smoke tests against the compiled WebAssembly frontend, so that I can catch client-side runtime errors before merging into `develop`.
2. As a homelab operator loading InfraMap, I want the `#loading-screen` to disappear within 5 seconds of opening the web page, so that I am never stuck waiting on a dead screen.
3. As a homelab operator opening the Dashboard for the first time, I want the initial shimmer loading skeletons to transition into the real dashboard content once metrics are fetched, so that I can immediately interact with the system.
4. As a homelab operator pressing `Cmd+K` (macOS) or `Ctrl+K` (Linux/Windows), I want the Command Palette modal to open immediately from anywhere in the window, so that I can quickly search devices and navigate.
5. As a release engineer running automated builds, I want CI to block PR merges and release tags if any unhandled JavaScript exception (`pageerror` or `console.error`) occurs during browser startup.
6. As a developer debugging a failed browser test in CI, I want Playwright trace files, failure screenshots, and container logs to be automatically archived as workflow artifacts, so that I can diagnose regressions quickly.
7. As a developer running tests locally, I want a single npm/make command (`npm run test:e2e` or `make e2e-browser`) that builds and runs the Playwright suite, so that I can verify fixes before pushing to GitHub.

---

## Implementation Decisions

- **Test Framework**: Playwright with TypeScript and `@playwright/test` test runner located in `tests/e2e-browser/`.
- **Browser Target**: Headless Chromium (desktop viewport 1440x900).
- **Environment Orchestration**:
  - Full-stack execution against the single binary: spins up PostgreSQL, compiles the backend binary with embedded WASM (`./bin/inframap`), launches the HTTP server on port 8055, and connects Playwright.
  - Automatic port and health check readiness (`GET /api/v1/health`) before starting test specs.
- **Assertion Strategy**:
  - **DOM Assertion**: Asserts `#loading-screen` has class `hidden` and style `display: none` within 5000ms.
  - **Console Listener**: Attaches `page.on('pageerror')` and `page.on('console')` listeners that fail the test if uncaught exceptions or error logs are emitted.
  - **Canvas Presence**: Verifies `<canvas id="inframap-canvas">` is attached, visible, and sized to fill the viewport.
  - **Interaction & Shortcut**: Dispatches `Meta+K` / `Control+K` keyboard events and asserts DOM/canvas state updates.
- **CI Quality Gate**:
  - Added as `verify-browser-e2e` job in `.github/workflows/ci.yml`.
  - Runs in parallel or right after the binary build step.
  - Configured as a required status check for branch protection rules on `develop` and `main`.

---

## Testing Decisions

- **Black-Box Testing**: Tests interact strictly through the browser DOM and viewport without mocking internal Kotlin classes.
- **Flakiness Controls**:
  - Explicit wait strategies (`waitForSelector('#loading-screen.hidden')`, network idle, and bounded retries).
  - WebServer health-check polling before suite execution.
- **Artifacts on Failure**:
  - `playwright-report/` and video/screenshots captured on failure and uploaded to GitHub Actions.

---

## Out of Scope

- Visual regression pixel diffing across different GPU architectures (skiko/skia WebGL differences).
- Mobile browser viewports (mobile web is not currently supported; desktop canvas is the primary target).
- Multi-browser matrix (WebKit/Firefox) in the initial iteration — Chromium headless covers 100% of the WASM+Skiko target.

---

## Further Notes

- Node.js version in CI: Node 20 LTS.
- Playwright dependencies cached via GitHub Actions `actions/setup-node` cache.
