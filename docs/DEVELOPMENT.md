# InfraMap Developer Guide

This document provides complete instructions for setting up your local environment, building, running, and testing the InfraMap project.

---

## 1. Prerequisites

Before getting started, ensure you have installed:

- **mise** (Environment manager): Automatically manages tool versions via `.mise.toml`
  - **Go**: 1.25.x
  - **JDK**: 21 (Eclipse Temurin)
  - **Node.js**: 22.x
- **Docker & Docker Compose**: For local PostgreSQL database and container testing
- **PostgreSQL**: 17.x (via Docker Compose)

---

## 2. Quick Start

Clone the repository and run the local development setup:

```bash
# Set up git pre-push hooks
make setup-hooks

# Start PostgreSQL database and run Goose migrations
make dev
```

The database container will start on port `5432` and migrations will automatically execute.

---

## 3. Local Development Mode

InfraMap consists of a Go backend and a Kotlin WASM Compose Multiplatform frontend. During development, you can run them independently with live reloads.

### Frontend Development Server (Hot-Reload)

```bash
cd frontend
./gradlew wasmJsBrowserDevelopmentRun
```

- Accessible at: `http://localhost:8080`
- Features hot-reloading on Compose UI code changes.

### Backend API Server

In a separate terminal:

```bash
cd backend
go run ./cmd/api
```

- Accessible at: `http://localhost:8055`
- Cross-Origin Resource Sharing (CORS) is pre-configured to accept requests from `http://localhost:8080`.

---

## 4. Quality Gates & Testing Suite

InfraMap enforces strict quality gates prior to code commits and pushes.

```bash
# Run full local quality gate suite (Go + Kotlin linting, unit tests, coverage, security)
make verify

# Backend tests with race detector and coverage
make test

# Frontend unit tests with Kover coverage verification
make test-frontend

# Playwright Visual Regression E2E suite
make test-e2e-frontend
```

---

## 5. Database & Code Generation

### Database Migrations (Goose)

```bash
# Apply pending migrations
make migrate-up

# Rollback last migration
make migrate-down
```

### Code Generation (sqlc)

When updating SQL queries in `backend/internal/platform/db/sqlc/queries/`:

```bash
make generate
```

---

## 6. Build Artifacts

### Single Binary Build

To compile the Kotlin WASM frontend assets and embed them into a single Go binary:

```bash
make build
```

The resulting standalone binary will be generated at `backend/bin/inframap`.

### Production Docker Container Image

To build the multi-stage distroless Docker image locally:

```bash
make docker-build
```

To run the full production environment with Docker Compose:

```bash
make docker-run
```
