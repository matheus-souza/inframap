# RFC-002 — Development Workflow

| Status | Accepted |
|--------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official development workflow of InfraMap.

Its purpose is to establish a consistent, predictable and automated development process, ensuring high software quality from the first commit to every production release.

The workflow is designed around continuous integration, automated quality gates and controlled releases.

---

# Guiding Principles

The development workflow is based on the following principles:

- Automation First
- Continuous Integration
- Continuous Quality
- Incremental Delivery
- Reproducible Releases
- Manual Production Approval
- Infrastructure as Code

Every change merged into the repository must be traceable, validated and reproducible.

---

# Branch Strategy

InfraMap adopts a simplified Git Flow.

```text
main
│
├── develop
│   ├── feature/*
│   ├── bugfix/*
│   ├── docs/*
│   ├── ci/*
│   └── test/*
│
└── hotfix/*
```

The workflow separates production-ready code from ongoing development while keeping the release process simple.

---

# Branch Definitions

## main

Represents the production environment.

Characteristics:

- Always stable
- Represents the latest official release
- Represents the code currently considered production-ready
- Protected branch
- No direct commits
- No direct pushes
- Receives merges only from `develop` or `hotfix/*`

Every merge into `main` automatically generates a new stable release.

---

## develop

Represents the next version of InfraMap.

Characteristics:

- Always compilable
- Always deployable in a test environment
- Receives all new features
- Receives bug fixes
- Used as the validation branch before production

The `develop` branch should never intentionally remain broken.

---

## feature/*

Used for implementing new features.

Examples:

```text
feature/device-discovery

feature/dashboard

feature/home-assistant
```

Origin:

```text
develop
```

Destination:

```text
develop
```

---

## bugfix/*

Used for fixing bugs identified during development.

Examples:

```text
bugfix/device-name

bugfix/discovery-timeout
```

Origin:

```text
develop
```

Destination:

```text
develop
```

---

## docs/*

Used exclusively for documentation updates.

Examples:

```text
docs/readme

docs/api

docs/architecture
```

---

## ci/*

Used for changes related to CI/CD.

Examples:

```text
ci/github-actions

ci/release-workflow
```

---

## test/*

Used for testing experiments, quality improvements or test infrastructure.

Examples:

```text
test/integration

test/performance
```

---

## hotfix/*

Used to fix critical production issues.

Origin:

```text
main
```

Destination:

```text
main

develop
```

Every production hotfix must also be merged back into `develop`.

---

# Branch Protection

The following branches are protected:

- main
- develop

Protected branches enforce the following rules:

- Direct pushes are forbidden.
- Force pushes are forbidden.
- Pull Requests are mandatory.
- All CI pipelines must succeed.
- Branch must be up-to-date before merge.
- All review conversations must be resolved.

---

## main

Additional rules:

- Auto Merge disabled.
- Manual merge only.
- Only the project maintainer may merge.
- Every merge produces an official release.

---

## develop

Additional rules:

- Auto Merge enabled.
- Merge occurs automatically after all required validations succeed.
- Automated code review must approve the Pull Request before merge.

This allows continuous delivery into the development environment while maintaining quality standards.

---

# Branch Naming Convention

Branch names should be short, descriptive and lowercase.

Examples:

```text
feature/device-discovery

feature/proxmox-provider

bugfix/home-assistant-timeout

docs/readme

ci/docker-build
```

Avoid:

- spaces
- uppercase letters
- generic names

---

# Commit Convention

InfraMap adopts the Conventional Commits specification.

Allowed prefixes include:

```text
feat
fix
docs
perf
refactor
test
build
ci
style
chore
revert
```

Examples:

```text
feat(discovery): add ARP scanner

fix(api): validate IPv6 addresses

docs(readme): update installation guide
```

Breaking changes must follow the Conventional Commits specification.

---

# Pull Request Policy

Every Pull Request should represent a complete and functional increment.

Commits should remain small and focused.

Large Pull Requests are acceptable when they deliver a cohesive feature.

---

## Pull Request Template

Every Pull Request must include:

- Objective
- Description of implemented changes
- Testing instructions
- Checklist
- Breaking Changes (if applicable)

---

## Pull Request Validation

Every Pull Request must execute the complete validation pipeline.

Validation includes:

- Build
- Unit Tests
- Integration Tests
- Lint
- Static Analysis
- Security Analysis
- Dependency Scan
- License Validation
- Docker Image Build
- Test Coverage Verification
- Code Complexity Analysis

A Pull Request cannot be merged if any validation fails.

---

# Code Review Policy

Automated review is mandatory.

Human review depends on the target branch.

## develop

Requirements:

- Automated review approval
- Successful CI
- Auto Merge

## main

Requirements:

- Successful CI
- Manual merge by the project maintainer

Although an automated review is expected to run, the final production decision always belongs to the maintainer.

---

# Automated Review

The project prioritizes free tools suitable for open-source projects.

The selected toolset should maximize coverage while minimizing duplicated analysis.

Candidate tools include:

- CodeRabbit
- CodeQL
- Trivy
- Semgrep
- Gitleaks
- Dependabot
- Renovate

The toolset may evolve over time.

---

# Merge Strategy

InfraMap adopts **Merge Commit** as the official merge strategy.

Reasons:

- Preserves the complete development history.
- Maintains traceability between Pull Requests and merges.
- Simplifies auditing.
- Makes feature integration easier to understand.

Merge commits generated by GitHub should remain unchanged.

Example:

```text
Merge pull request #42 from feature/device-discovery
```

---

# Release Workflow

The release process is fully automated.

Development flow:

```text
feature/*
        │
        ▼
develop
        │
        ▼
Automatic Release Candidate
        │
        ▼
Validation in Homelab
        │
        ▼
Manual Merge
        │
        ▼
main
        │
        ▼
Stable Release
```

---

# Release Candidates

The `develop` branch is continuously validated.

Release Candidates are intended for deployment into the maintainer's Homelab environment before reaching production.

There is no release freeze.

Development continues normally on `develop` while Release Candidates are validated.

---

# Automatic Versioning

InfraMap adopts Semantic Versioning.

```text
MAJOR.MINOR.PATCH
```

Version numbers are generated automatically.

The versioning process should analyze Conventional Commits and determine whether the next release is:

- Major
- Minor
- Patch

No manual version editing should be necessary.

---

# Stable Releases

Every merge into `main` automatically generates:

- Semantic Version
- Git Tag
- GitHub Release
- Release Notes
- CHANGELOG
- Docker Images
- Container Registry Publication

The release process must be fully automated and reproducible.

---

# Continuous Integration

Every Pull Request executes the complete CI pipeline.

The pipeline includes:

- Source Checkout
- Dependency Installation
- Formatting Validation
- Static Analysis
- Security Analysis
- Unit Tests
- Integration Tests
- Coverage Analysis
- Complexity Analysis
- Dependency Scan
- License Validation
- Docker Build
- Automated Review

Any failure blocks the merge.

---

# Protected Files

The following resources require manual review regardless of automated approvals.

Examples include:

```text
.github/workflows/

.github/actions/

docs/architecture/

docs/rfc/

LICENSE

SECURITY.md

CODEOWNERS
```

Changes affecting these files must always receive explicit maintainer approval.

---

# Breaking Changes

Breaking changes must be clearly documented.

Every breaking change must appear in:

- Release Notes
- CHANGELOG

Whenever possible, deprecated functionality should be marked before removal.

Deprecation policy:

- Features already released to `main` should first be marked as deprecated.
- Removal should occur in a subsequent release.
- Features existing only on `develop` may be changed or removed without deprecation.

---

# Repository Governance

InfraMap is currently maintained by a single maintainer.

Current governance rules:

| Action | Policy |
|----------|--------|
| Merge into `main` | Maintainer only |
| Merge into `develop` | Automated after successful validation |
| Create Releases | Automated |
| Modify GitHub Actions | Maintainer only |
| Modify Architecture Documentation | Maintainer only |
| Modify RFC Documents | Maintainer only |

These rules may evolve if additional maintainers join the project.

---

# Support Policy

InfraMap supports only the latest stable release.

Older versions receive:

- No bug fixes
- No security patches
- No maintenance

Users are expected to remain on the latest stable version.

---

# Workflow Philosophy

The development workflow is designed around the following principles:

- Automation should replace repetitive manual work.
- Production releases require explicit maintainer approval.
- Quality gates are mandatory.
- Every release must be reproducible.
- Every change must be traceable.
- The repository history should remain complete and understandable.
- Continuous integration is mandatory from the first commit.