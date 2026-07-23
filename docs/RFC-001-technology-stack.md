# RFC-001 — Technology Stack

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official technology stack adopted by InfraMap.

Its purpose is to document not only the technologies chosen, but also the reasons behind each decision and the architectural principles that guide future changes.

Technology choices should prioritize long-term maintainability over short-term convenience.

Version-specific information is intentionally omitted from this document. The project should always target the latest stable versions officially validated by the project.

---

# Technology Philosophy

InfraMap follows a pragmatic philosophy regarding technology adoption.

The project values simplicity, maintainability and performance, while avoiding unnecessary complexity.

Every dependency must justify its existence by providing measurable value.

## Priorities

Technology decisions should follow the following order of importance:

1. Performance
2. Low Memory Consumption
3. Ease of Maintenance
4. Long-Term Sustainability
5. Ecosystem Maturity
6. Ease of Deployment
7. Developer Productivity
8. Community Adoption

---

# Dependency Philosophy

InfraMap does not aim to minimize dependencies at all costs.

Instead, the project adopts mature, well-maintained libraries whenever they significantly improve productivity, reliability or maintainability.

Dependencies should satisfy the following criteria:

- Active community
- Frequent maintenance
- Stable API
- Wide industry adoption
- Clear documentation
- Compatible license
- Low risk of abandonment

Whenever a dependency becomes difficult to maintain or no longer provides sufficient value, it should be reevaluated.

---

# Upgrade Policy

The project should always target the latest stable releases officially supported by each technology.

However, upgrades must never be automatic.

Every dependency update must be validated before adoption.

Major version upgrades should be documented through an Architecture Decision Record (ADR).

---

# Backend

## Language

Go

### Decision

The backend will be implemented using Go.

The canonical Go module name for the project is `github.com/matheussouza/inframap`. This follows the standard Go convention of using the Version Control System (VCS) URL as the module namespace.

### Why

Go provides an excellent balance between:

- Performance
- Low memory usage
- Concurrency
- Networking
- Deployment simplicity
- Small container images
- Fast compilation

It is also widely adopted across modern infrastructure software.

Examples include:

- Docker
- Kubernetes
- Terraform
- Prometheus
- Traefik
- Caddy
- Grafana Agent

---

## HTTP Router

### Decision

Chi

### Why

Chi provides:

- Minimal abstraction over net/http
- Excellent routing capabilities
- Lightweight middleware
- Small dependency footprint
- Excellent maintainability
- Strong community adoption

The project intentionally avoids large HTTP frameworks to reduce coupling and simplify long-term maintenance.

### Alternatives

Rejected:

- Gin
- Echo

Reason:

Although mature, they introduce additional abstractions that provide little value for the needs of InfraMap.

---

## Configuration

Configuration should support multiple sources.

Priority order:

1. Environment Variables
2. .env
3. Configuration File (optional)
4. Default Values

Production deployments should rely primarily on environment variables.

The initial setup should require as little configuration as possible.

Whenever possible, sensible defaults should be provided.

### Default Server Port

To avoid common homelab port collisions (such as `8080`, `8000`, `3000`, or `9000` used by Traefik, UniFi, or Portainer), InfraMap officially defaults to port **`8055`**.

The port can be easily overridden via the `INFRAMAP_PORT` environment variable.

---

## Logging

Structured logging will be adopted from the beginning.

Development

- Human-readable logs

Production

- Structured JSON logs

The logging implementation should remain compatible with modern observability platforms.

Examples:

- OpenTelemetry
- Grafana Loki
- Elastic
- OpenSearch

---

## Scheduler

The project should initially rely on Go's native concurrency primitives.

The adoption of third-party schedulers should only occur if clear benefits are identified.

Goals:

- Low overhead
- Easy maintenance
- High reliability

---

# Database

## Database Engine

PostgreSQL

### Why

InfraMap stores highly relational information.

Examples:

- Assets
- Interfaces
- Networks
- Services
- Groups
- Relationships

PostgreSQL offers:

- ACID compliance
- Strong consistency
- Advanced indexing
- JSON support
- Excellent tooling
- Long-term stability

---

## Database Driver

pgx

Reason:

Official PostgreSQL driver with excellent performance and feature support.

---

## ORM

### Decision

No ORM.

Business queries should remain explicit.

### Why

Infrastructure management often requires:

- Complex joins
- Recursive queries
- Optimized indexes
- Fine-grained SQL tuning

Explicit SQL provides greater control, predictability and performance.

---

## Query Generation

sqlc

### Why

sqlc combines the performance of handwritten SQL with compile-time type safety.

Benefits include:

- Type-safe code
- No runtime reflection
- Explicit SQL
- Better performance
- Easier optimization

---

## Database Migrations

Migration tooling should satisfy:

- Strong community adoption
- Active maintenance
- Simple workflow
- CI/CD compatibility

### Decision

Goose

### Why

Goose provides an excellent workflow by allowing both `Up` and `Down` migrations within a single `.sql` file using `-- +goose Up` and `-- +goose Down` annotations.

To support seamless container updates (e.g., via Watchtower or pulling `:latest`), **Goose migrations are executed automatically during application startup** (`goose.Up()` via Go library or entrypoint). Migrations execute within strict database transactions to guarantee zero data loss and safe rollbacks if a migration fails.

---

## Seed Strategy

Static reference data

- SQL

Dynamic initialization

- Go

This approach keeps installations reproducible while allowing more complex initialization logic when necessary.

---

# API

## Style

REST

REST will be the official API style for the first releases.

The internal architecture should remain flexible enough to support future protocols such as:

- WebSocket
- Server-Sent Events
- gRPC

without requiring major architectural changes.

---

## Documentation

The API specification should be generated using OpenAPI.

The generated specification should become the official API contract.

---

## Versioning

The official API versioning strategy is **Path-based Versioning** (e.g., `/api/v1/`).

As a homelab-first project where the API is primarily consumed by the internal frontend, this approach prioritizes:

- Simplicity of routing
- Explicit backward compatibility
- Easy client maintainability

---

## Frontend

## Framework

Compose Multiplatform

Initial target:

- WebAssembly

### Distribution & Single-Binary Architecture

To deliver an all-in-one, self-contained homelab experience (similar to Portainer), the compiled WASM static assets (`index.html`, `.wasm`, `.js`) are **embedded directly into the Go backend binary using Go's `embed.FS`**.

When running InfraMap:
- A single Go process serves both the REST/SSE APIs and static WASM frontend files.
- No separate Nginx or Caddy web server container is required for production deployments.

---

## UI

Material Design 3

Whenever possible, official Material components should be preferred.

Custom components should only be created when existing Material components cannot satisfy project requirements.

---

## State Management

The frontend adopts a combination of:

- MVI
- ViewModel
- StateFlow
- Compose State

Responsibilities:

MVI

Business flow.

ViewModel

Application logic.

StateFlow

State management.

Compose State

UI rendering.

This architecture aims to maintain a clear separation between presentation and business logic.

---

## Navigation

The project should prioritize the official navigation solution recommended by the Compose ecosystem.

---

# Containerization

InfraMap is Docker First.

Every service must execute inside containers.

Development, testing and production should remain as similar as possible.

---

## Docker Images

Each service should provide its own image.

The project should adopt:

- Multi-stage builds
- Minimal production images
- Secure runtime images

The final base image should prioritize:

- Security
- Reduced attack surface
- Small image size

without sacrificing maintainability.

---

# CI/CD

GitHub Actions will be the official CI/CD platform.

Every Pull Request should execute:

- Formatting
- Static Analysis
- Unit Tests
- Integration Tests
- Docker Build
- Security Checks

No Pull Request should be merged without passing all required checks.

---

## Pull Request Review

Automated code review should be adopted.

Candidates include:

- CodeRabbit
- GitHub CodeQL
- Semgrep

Automated review should complement—not replace—human review.

---

## Release Pipeline

Every release should automatically generate:

- Docker Images
- Git Tags
- GitHub Release
- Release Notes
- Changelog

The release process should be fully reproducible.

---

# Code Quality

Code quality should be continuously measured.

Metrics include:

- Test Coverage
- Code Duplication
- Cyclomatic Complexity
- Cognitive Complexity
- Maintainability Index
- Security Vulnerabilities
- Dependency Health
- Static Analysis
- Dead Code
- Code Smells

Recommended tools include:

Backend

- golangci-lint
- gofumpt
- staticcheck
- govulncheck
- gosec
- Trivy
- CodeQL
- Semgrep
- Gitleaks

Frontend

- ktlint
- detekt
- Kover
- Qodana
- CodeQL
- Trivy

Quality gates should be enforced through CI.

---

# Observability

Observability should be considered from the first release.

The project should remain vendor-neutral.

Generated telemetry should be compatible with OpenTelemetry standards.

The architecture should support:

- Structured Logs
- Metrics
- Distributed Tracing
- Health Checks

without coupling the project to a specific observability platform.

---

# Security

Security is a core architectural principle.

The project should adopt secure defaults.

Examples include:

- Configurable CORS
- CSP
- Rate Limiting
- Secure HTTP Headers
- Environment-based Secrets
- Strong Password Hashing
- Dependency Scanning
- Secret Detection
- Container Image Scanning

Security checks should be integrated into CI/CD.

---

# Architectural Principles

Every technology adopted by InfraMap should satisfy at least one of the following goals:

- Simplicity
- Performance
- Maintainability
- Reliability
- Security
- Developer Experience

Technologies that do not provide measurable value should not be introduced.

---

# Future Revisions

Technology choices are expected to evolve.

Any significant change to the stack must be documented through an Architecture Decision Record (ADR).

This document should describe principles rather than implementation details, ensuring its longevity throughout the lifetime of the project.