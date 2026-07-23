# RFC-003 — Quality Gates & CI Policy

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the quality standards, validation policies and Continuous Integration (CI) requirements adopted by InfraMap.

The objective is to ensure that every change introduced into the project is automatically validated against a comprehensive set of quality gates before being integrated into the codebase.

Quality is considered a non-functional requirement and must be continuously verified throughout the entire software lifecycle.

---

# Guiding Principles

InfraMap adopts the following quality principles:

- Automation First
- Continuous Quality
- Functional Test Coverage
- Security by Default
- Reproducible Builds
- Shift Left Testing
- Fast Feedback
- Continuous Improvement

Quality validation should be automated whenever possible.

Every Pull Request is considered a production candidate and must satisfy all quality gates before being merged.

---

# Quality Philosophy

Quality is measured using objective metrics rather than subjective opinions.

The goal is **not** to maximize numbers, but to maximize confidence.

Examples:

- High test coverage without meaningful assertions has little value.
- Complex code with 100% coverage is still complex.
- Passing tests do not replace static analysis.
- Static analysis does not replace security validation.

Quality should be evaluated as the combination of multiple independent dimensions.

---

# Quality Gates

Every Pull Request must successfully pass every configured quality gate.

InfraMap follows a **fail-fast** strategy.

If any mandatory validation fails, the Pull Request cannot be merged.

There are no exceptions.

---

# Quality Dimensions

InfraMap continuously measures quality across the following dimensions.

## Code Quality

- Formatting
- Lint
- Static Analysis
- Code Smells
- Maintainability
- Complexity
- Duplication
- Dead Code

---

## Testing

- Unit Tests
- Integration Tests
- Functional Coverage
- Benchmark Validation

---

## Security

- Dependency Scan
- Vulnerability Scan
- Secret Detection
- Container Scan
- License Validation

---

## Documentation

- Markdown validation
- OpenAPI validation
- Documentation consistency
- README validation

---

## Build

- Compilation
- Docker Build
- Dependency Resolution

---

# Test Coverage Policy

InfraMap targets **90% minimum coverage** for both backend and frontend.

However, coverage exists to measure confidence rather than satisfy an arbitrary percentage.

The primary objective is **functional coverage**.

Coverage should focus on:

- Business Rules
- Use Cases
- Controllers
- Services
- Models
- DTOs
- Mappers
- Configuration
- Infrastructure Code

Artificially increasing coverage through meaningless tests is discouraged.

---

## Coverage Regression

Coverage must never decrease.

If a Pull Request reduces the project's overall coverage, the merge is blocked.

Every new feature should include corresponding automated tests.

---

# Static Analysis

Static analysis is mandatory.

The project should continuously analyze:

- Code Smells
- Dead Code
- Unreachable Code
- Inefficient Patterns
- Unsafe Constructs
- API Misuse
- Incorrect Error Handling

The objective is early detection rather than post-release correction.

---

# Complexity Policy

Complexity should remain as low as reasonably possible.

Industry-recognized thresholds should be adopted whenever applicable.

The project should continuously monitor:

- Cyclomatic Complexity
- Cognitive Complexity

Complex functions should be refactored before merge whenever possible.

---

# Function Size

Functions should remain focused on a single responsibility.

Although no strict line-count limit is enforced, excessively large functions should be considered candidates for refactoring.

Code readability always takes precedence over arbitrary numeric limits.

---

# File Size

No hard file-size limit is defined.

Instead, maintainability should guide decomposition.

Files should be organized around cohesive responsibilities rather than line counts.

---

# Code Duplication

The project targets a maximum duplication rate of **3%**.

Duplication should only be accepted when abstraction would significantly reduce readability.

Copy-and-paste programming should be avoided.

---

# Backend Quality Tools

The backend should adopt mature tooling with minimal overlap.

Candidate tools include:

- gofmt
- gofumpt
- golangci-lint
- staticcheck
- govulncheck
- gosec

Only tools providing complementary analysis should be enabled.

Redundant validations should be avoided.

---

# Frontend Quality Tools

Candidate tools include:

- ktlint
- detekt
- Compose Compiler Metrics
- Kover

As with the backend, overlapping validations should be avoided whenever possible.

---

# Security Policy

Security is considered a mandatory quality requirement.

Every Pull Request must execute security validation before merge.

Security validation includes:

- Dependency Analysis
- Vulnerability Detection
- Secret Detection
- Container Scanning
- License Validation

Any detected issue blocks the merge.

---

# Dependency Management

Dependencies should be continuously monitored.

The project should adopt complementary tooling without unnecessary duplication.

Candidate tools include:

- Dependabot
- Renovate

The selected combination should maximize ecosystem coverage while minimizing redundant pull requests.

---

# Vulnerability Analysis

The project should combine complementary tools.

Candidates include:

- govulncheck
- Trivy
- CodeQL
- Semgrep

Additional tools may be adopted if they provide unique analysis capabilities.

---

# Secret Detection

Secrets must never be committed.

Candidate tools include:

- Gitleaks
- TruffleHog

Any detected secret immediately blocks the pipeline.

---

# Container Security

Every Docker image must be scanned before publication.

Validation includes:

- Base Image Vulnerabilities
- Installed Packages
- Operating System Packages
- Known CVEs

Container publication is blocked if vulnerabilities exceed the project's accepted policy.

---

# License Compliance

Every dependency license must be validated.

Dependencies using incompatible licenses must be rejected.

The project should maintain compatibility with its own licensing model.

---

# Performance Validation

Performance regression should be continuously monitored.

Every Pull Request executes automated benchmark validation.

The objective is identifying regressions before they reach production.

Benchmarks should validate critical execution paths whenever practical.

---

# Documentation Validation

Documentation is considered part of the product.

A Pull Request fails when:

- Required documentation is missing.
- API changes are not reflected in the OpenAPI specification.
- README becomes inconsistent.
- Markdown validation fails.

---

# Code Quality Metrics

InfraMap continuously measures:

- Lines of Code
- Maintainability Index
- Cyclomatic Complexity
- Cognitive Complexity
- Coupling
- Fan In
- Fan Out
- Instability
- Technical Debt
- Code Smells
- Duplication

Metrics should guide improvements rather than become isolated goals.

---

# Continuous Integration Pipeline

Every Pull Request executes the complete validation pipeline.

Pipeline execution is organized into progressive quality levels.

---

# Level 1 — Fast Validation

Objective:

Provide immediate feedback.

Validation:

- Source Checkout
- Dependency Resolution
- Formatting
- Lint
- Static Analysis
- Build

Expected execution time should remain as short as possible.

---

# Level 2 — Functional Validation

Objective:

Validate application correctness.

Validation:

- Unit Tests
- Integration Tests
- Coverage
- Benchmark

---

# Level 3 — Security Validation

Objective:

Guarantee software integrity.

Validation:

- Dependency Scan
- Vulnerability Scan
- Secret Detection
- Container Scan
- License Validation

---

# Level 4 — Release Validation

Objective:

Validate production readiness.

Validation:

- Docker Build
- OpenAPI Validation
- Documentation Validation
- Release Validation
- Artifact Verification

---

# Pull Request Pipeline

Every Pull Request executes:

- Build
- Unit Tests
- Integration Tests
- Lint
- Coverage
- Docker Build
- Security Validation
- Benchmark
- Dependency Scan
- License Validation
- OpenAPI Validation
- Markdown Validation
- Spell Check
- Code Smell Analysis

Every validation is mandatory.

---

# Release Pipeline

Every release executes the complete validation pipeline again.

Release validation never reuses previous CI artifacts.

Each release must prove that it is reproducible from source.

---

# Severity Policy

InfraMap adopts a strict fail-fast policy.

| Severity | Merge |
|----------|-------|
| Warning | Allowed |
| Error | Blocked |
| Critical | Blocked |

Warnings should still be monitored and resolved whenever possible.

---

# Local Development

Developers should be able to execute the same validation pipeline locally.

The repository should provide a unified command.

Example:

```bash
make verify
```

The local pipeline should reproduce, as closely as possible, the same validations executed by GitHub Actions.

Differences between local and CI environments should be minimized.

---

# Project Metrics

The project continuously tracks:

- Test Coverage
- Number of Tests
- Build Duration
- Test Duration
- Merge Duration
- Security Vulnerabilities
- Technical Debt
- Code Duplication
- Average Complexity
- Pull Request Size

These metrics should be monitored over time to identify trends rather than isolated events.

---

# Dashboards

Whenever possible, the project should adopt free tooling compatible with public repositories.

Dashboards should provide visibility into:

- Code Quality
- Security
- Coverage
- Build Health
- Technical Debt
- Complexity Trends

The specific tooling may evolve over time without requiring changes to this policy.

---

# Continuous Improvement

Quality standards are expected to evolve.

New validation tools may be adopted whenever they provide measurable value.

Existing tools should be periodically reviewed to eliminate unnecessary overlap.

The objective is maximizing confidence while minimizing maintenance overhead.

---

# Final Principle

Quality is never considered complete.

Every successful pipeline increases confidence, but never guarantees correctness.

Automation exists to reduce risk—not to replace engineering judgment.

The purpose of this policy is to ensure that InfraMap remains maintainable, secure, reliable and production-ready throughout its entire lifecycle.