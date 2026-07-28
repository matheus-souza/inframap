# RFC-015: Code Coverage & Quality Gate Policy

- **Author**: InfraMap Core Engineering
- **Status**: APPROVED & ENFORCED
- **Created**: 2026-07-25
- **Applies to**: `main`, `develop`

---

## 1. Executive Summary

This document specifies the official Code Coverage Policy for the InfraMap engine. Every Pull Request must satisfy all quality gates prior to merging into protected branches (`main`, `develop`).

---

## 2. Core Policy Directives

### 2.1 Minimum Patch Coverage (85%)
- Every new or modified line of code in a Pull Request **MUST** achieve a minimum coverage of **85%** (`Patch Coverage >= 85%`).
- If Patch Coverage is below 85%, CI pipelines will fail and GitHub Branch Protection will block the PR merge.

### 2.2 Global Coverage Non-Regression
- Global code coverage **MUST NOT decrease** relative to the target base branch (`develop` / `main`).
- Coverage is expected to monotonically increase toward long-term project targets (>= 85%).

### 2.3 New Modules Requirement
- No new module, package, or capability may be introduced without automated unit/integration tests.
- Code introduced without tests will be rejected by review and CI gates.

### 2.4 Test Quality Standards
- Coverage percentage is a metric, not a surrogate for quality.
- Tests must validate real behavior across:
  1. Happy path (Success flows)
  2. Error cases & failure paths
  3. Edge cases & boundary limits
  4. Input validations
  5. Business rules & domain logic
  6. Exception handling
- Tests written solely to inflate coverage numbers without validating behavior will be rejected during code review.

---

## 3. Justified Exclusions

The following categories are explicitly excluded from coverage enforcement:

1. **Auto-Generated Code**: `*.sql.go`, `internal/platform/db/*`
2. **Database Migrations**: `backend/migrations/*`
3. **Configuration & Build Scripts**: `backend/scripts/*`, `*.yml`, `Makefile`
4. **Application Entrypoints**: `backend/cmd/*`
5. **Bootstrap Code**: `backend/internal/bootstrap/*`
6. **Mocks & Test Stubs**: `*_mock.go`, `mock_*.go`

---

## 4. CI/CD & Branch Protection Enforcement

### 4.1 Required Status Checks
The following status checks are enforced on `main` and `develop` branches:
- `Verify Quality Gates` (GitHub Actions — backend)
- `Verify Frontend Quality Gates` (GitHub Actions — frontend)
- `codecov/patch` (Codecov Patch Coverage >= 85%)
- `codecov/project` (Codecov Project Coverage Non-Regression)
- `Semgrep Analysis`
- `CodeRabbit`

**Rule**: When adding a new CI quality gate job, it MUST be immediately added to the required status checks list in branch protection. A job that runs and fails but is not required will NOT block the merge (see Incident Log §5.2).

### 4.2 Codecov Configuration Requirements

Codecov evaluates coverage by receiving upload reports from multiple CI jobs. The following configuration in `codecov.yml` is mandatory:

```yaml
codecov:
  notify:
    after_n_builds: 2    # Must equal number of coverage upload jobs
    wait_for_ci: true    # Defer evaluation until CI completes
```

**`after_n_builds`** must always match the number of CI jobs that upload coverage reports (currently 2: backend via `Verify Quality Gates`, frontend via `Verify Frontend Quality Gates`). When adding a new coverage upload job, this value MUST be incremented.

**Rationale**: Without `after_n_builds`, Codecov evaluates after the first upload and may report SUCCESS (via GitHub CheckRun API) with incomplete data. The final result (via commit Status API) may be FAILURE — but auto-merge already triggered on the premature CheckRun. See Incident Log §5.1.

### 4.3 Baseline Staleness

The auto-merge workflow uses `GITHUB_TOKEN`, which does not trigger CI on the target branch after merge. This means Codecov never receives a coverage report for merge commits on `develop`, causing the `target: auto` baseline to become stale.

**Mitigations**:
- CI workflow includes `workflow_dispatch` trigger for manual baseline refresh
- `codecov.yml` flags use `carryforward: true` to preserve per-flag data across commits
- For automatic baseline updates, the auto-merge workflow should use a GitHub App token or PAT instead of `GITHUB_TOKEN`

### 4.4 Local Verification Tooling
- Running `make test-coverage` executes the test suite, generates `coverage.out`, filters excluded packages per policy, and generates `coverage_formatted.md`.

---

## 5. Incident Log

### 5.1 PRs #40, #41 — Merged with codecov/project FAILURE (2026-07-28)

**What happened**: Both PRs merged to `develop` via auto-merge while `codecov/project` commit Status showed FAILURE.

**Root cause**: Codecov posted results via two GitHub mechanisms — a CheckRun (evaluated after 1/2 coverage uploads → SUCCESS) and a commit Status (evaluated after 2/2 uploads → FAILURE). GitHub's required check matched the CheckRun (SUCCESS) and auto-merge proceeded before the Status (FAILURE) arrived.

**Timeline (PR #40)**:
- 13:21:45 — CheckRun `codecov/project` → SUCCESS (after frontend upload only)
- 13:22:31 — Auto-merge triggered
- 13:22:51 — Status `codecov/project` → FAILURE (after both uploads)

**Fix**: Added `after_n_builds: 2` and `wait_for_ci: true` to `codecov.yml`.

### 5.2 PR #39 — Merged with Verify Frontend Quality Gates FAILURE (2026-07-28)

**What happened**: PR merged to `develop` while the `Verify Frontend Quality Gates` CI job had failed.

**Root cause**: The job was not listed in the branch protection required status checks. Only `Verify Quality Gates` (backend) was required.

**Fix**: Added `Verify Frontend Quality Gates` to required status checks via GitHub API.
