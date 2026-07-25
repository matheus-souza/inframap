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
- `Verify Quality Gates` (GitHub Actions workflow)
- `codecov/patch` (Codecov Patch Coverage >= 85%)
- `codecov/project` (Codecov Project Coverage Non-Regression)
- `Semgrep Security Analysis`
- `CodeRabbit`

### 4.2 Local Verification Tooling
- Running `make test-coverage` executes the test suite, generates `coverage.out`, filters excluded packages per policy, and generates `coverage_formatted.md`.
