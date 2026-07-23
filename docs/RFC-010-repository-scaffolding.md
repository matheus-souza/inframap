# RFC-010 — Repository Scaffolding & Developer Environment

| Status | Accepted |
|----------|----------|
| Owner | InfraMap Team |
| Created | 2026 |
| Last Updated | 2026 |

---

# Overview

This document defines the official **Repository Directory Layout**, **Local Task Automation (`Makefile`)**, **Code Generation Configuration (`sqlc`)**, and **Local Developer Environment (`docker-compose`)** for InfraMap.

Establishing a clean, standardized workspace structure ensures that developers can set up, build, test, and validate the entire platform locally with zero friction, adhering to the Quality Gates defined in [RFC-003](./RFC-003-quality-gates.md) and the Docker-First philosophy established in [project-foundation.md](./project-foundation.md).

---

# Guiding Principles

1. **Clean Monorepo Layout**
   - The repository isolates `backend` (Go), `frontend` (Compose Multiplatform WASM), `docker` configurations, and `docs` into well-defined top-level directories while maintaining unified repository governance.

2. **Symmetric Local & CI Validation (`make verify`)**
   - Executing `make verify` locally runs the exact same formatting, linting, security scanning, static analysis, unit testing, and build validation steps executed by GitHub Actions CI.

3. **Zero-Config Developer Environment**
   - A single command (`make dev`) spins up PostgreSQL, applies database migrations, and launches the backend server in development mode.

4. **Type-Safe Code Generation (`sqlc`)**
   - SQL queries are version-controlled in `.sql` files and compiled to type-safe Go code using `sqlc`. Hand-written SQL string concatenation or ORMs are strictly prohibited ([RFC-001](./RFC-001-technology-stack.md)).

---

# Repository Directory Layout

```text
inframap/
├── .github/                      # GitHub Actions workflows and PR templates
│   └── workflows/
│       ├── ci.yml                # Main CI Quality Gates pipeline
│       └── release.yml           # Automated Semantic Versioning release pipeline
├── docs/                         # Foundation guidelines and RFC documents
│   ├── project-foundation.md
│   ├── RFC-001-technology-stack.md
│   ├── RFC-002-development-workflow.md
│   ├── RFC-003-quality-gates.md
│   ├── RFC-004-security-policy.md
│   ├── RFC-005-architecture.md
│   ├── RFC-006-data-model.md
│   ├── RFC-007-discovery-engine.md
│   ├── RFC-008-api-specification.md
│   ├── RFC-009-integration-sdk-event-bus.md
│   └── RFC-010-repository-scaffolding.md
├── backend/                      # Go Backend Monolith
│   ├── cmd/
│   │   └── api/                  # Application entry point (main.go)
│   │       └── main.go
│   ├── internal/
│   │   ├── bootstrap/            # Application startup, DI & wire-up
│   │   ├── platform/             # Core platform utilities (SDK, Event Bus, Logger, Crypto)
│   │   │   ├── crypto/           # AES-256-GCM secret encryption
│   │   │   ├── eventbus/         # In-memory Go channels Event Bus
│   │   │   ├── logger/           # Structured JSON logger
│   │   │   └── sdk/              # Integration Provider interface & contracts
│   │   └── shared/               # Shared domain primitives & AppError
│   ├── modules/                  # Modular Monolith Capabilities
│   │   ├── audit/
│   │   ├── configuration/
│   │   ├── discovery/
│   │   ├── identity/
│   │   ├── integrations/
│   │   ├── inventory/
│   │   └── topology/
│   ├── migrations/               # Goose SQL migrations (.sql)
│   ├── queries/                  # sqlc query definition files (.sql)
│   ├── go.mod
│   ├── go.sum
│   └── sqlc.yaml                 # sqlc configuration file
├── frontend/                     # Kotlin Compose Multiplatform Frontend (WASM)
│   ├── src/
│   │   ├── commonMain/           # Common Kotlin UI code & MVI StateFlow
│   │   └── wasmJsMain/           # WebAssembly targets
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── docker/                       # Multi-stage Dockerfiles
│   ├── backend.Dockerfile
│   └── frontend.Dockerfile
├── docker-compose.dev.yml        # Development environment (Postgres)
├── Makefile                      # Developer task automation
├── .golangci.yml                 # Linter configuration
├── .gitignore
├── LICENSE                       # Apache License 2.0
└── README.md
```

---

# Code Generation Specification (`sqlc.yaml`)

The file `backend/sqlc.yaml` configures type-safe Go code generation from SQL queries:

```yaml
version: "2"
sql:
  - schema: "migrations"
    queries: "queries"
    engine: "postgresql"
    gen:
      go:
        package: "db"
        out: "internal/platform/db"
        sql_package: "pgx/v5"
        emit_json_tags: true
        emit_prepared_queries: false
        emit_exact_table_names: false
        emit_empty_slices: true
        overrides:
          - db_type: "uuid"
            go_type: "github.com/google/uuid.UUID"
          - db_type: "inet"
            go_type: "net/netip.Addr"
          - db_type: "cidr"
            go_type: "net/netip.Prefix"
          - db_type: "macaddr"
            go_type: "net.HardwareAddr"
```

---

# Local Task Automation (`Makefile`)

The `Makefile` located in the root of the repository provides standardized targets for all local engineering tasks.

```makefile
# InfraMap Makefile

.PHONY: help dev dev-down build verify test lint migrate-up migrate-down generate clean

DEFAULT_GOAL := help

help: ## Display available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

dev: ## Start single self-contained local development environment (PostgreSQL + embedded backend & frontend)
	docker-compose -f docker-compose.dev.yml up -d postgres
	@echo "Waiting for PostgreSQL to be ready..."
	@until docker-compose -f docker-compose.dev.yml exec postgres pg_isready -U inframap; do sleep 1; done
	cd backend && go run ./cmd/api

dev-down: ## Stop local development environment containers
	docker-compose -f docker-compose.dev.yml down -v

generate: ## Run code generation (sqlc & mock generation)
	cd backend && sqlc generate

migrate-up: ## Apply pending Goose database migrations
	cd backend && goose postgres "postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable" -dir migrations up

migrate-down: ## Rollback last Goose database migration
	cd backend && goose postgres "postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable" -dir migrations down

test: ## Run backend unit and integration tests
	cd backend && go test -v -race -coverprofile=coverage.out ./...

test-coverage: test ## Run tests and output HTML coverage report
	cd backend && go tool cover -html=coverage.out -o coverage.html

lint: ## Run golangci-lint static code analysis
	cd backend && golangci-lint run ./...

build-backend: ## Build production backend binary
	cd backend && CGO_ENABLED=0 go build -ldflags="-s -w" -o bin/api ./cmd/api

build-frontend: ## Build production WASM frontend
	cd frontend && ./gradlew wasmJsBrowserDistribution

build: build-backend build-frontend ## Build both backend and frontend

verify: generate lint test build-backend ## Execute complete local validation pipeline (matches CI Quality Gates)
	@echo "=========================================="
	@echo " All Quality Gates Passed Successfully! "
	@echo "=========================================="

clean: ## Clean build artifacts and coverage files
	rm -rf backend/bin backend/coverage.out backend/coverage.html
	cd frontend && ./gradlew clean
```

---

# Local Developer Environment (`docker-compose.dev.yml`)

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    container_name: inframap-dev-postgres
    restart: always
    environment:
      POSTGRES_DB: inframap
      POSTGRES_USER: inframap
      POSTGRES_PASSWORD: inframap_dev_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_dev_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U inframap"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_dev_data:
```

---

# Local Verification vs CI Pipeline Symmetry

By running `make verify`, developers execute the exact validation steps required by [RFC-003](./RFC-003-quality-gates.md) before pushing code to GitHub:

```text
[ Developer Runs: make verify ]
                │
                ├── 1. Code Generation (sqlc generate)
                ├── 2. Static Analysis & Linting (golangci-lint run)
                ├── 3. Unit & Integration Tests (go test -race -cover)
                └── 4. Production Compilation (go build)
                │
                ▼
[ Clean Pass -> Safe to Push / Create Pull Request ]
```

---

# Toolchain & Linter Specification

### Toolchain Versions & Management (`.mise.toml`)
- **Toolchain Manager:** `mise` (polyglot manager for Go, JDK, Goose, sqlc).
- **Go Version:** **Go 1.24+** (latest stable release for enhanced performance, WASM improvements, and modern concurrency features).
- **Java / JDK:** **JDK 21 LTS** (Temurin 21 LTS for Gradle & Kotlin Compiler build environment).
- **Kotlin:** **Kotlin 2.1+** (Compose Multiplatform WASM compiler).
- **Database:** **PostgreSQL 17+**.

### Static Analysis (`.golangci.yml`)
The local linter configuration enforces the following static analysis suite:
- `errcheck`: Verifies that returned errors are explicitly handled.
- `gofumpt`: Enforces strict, idiomatic Go formatting.
- `gosec`: Inspects code for AST-level security vulnerabilities.
- `govulncheck`: Scans Go dependencies against known CVE databases.
- `ineffassign`: Detects unused assignments.
- `staticcheck`: Comprehensive Go static code analysis.

```
