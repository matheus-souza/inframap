# InfraMap Makefile — RFC-010 Compliant

.PHONY: help dev dev-down build test test-coverage lint verify generate migrate-up migrate-down clean

DEFAULT_PORT ?= 8055
CGO_ENABLED ?= 0
MISE := $(shell command -v mise 2> /dev/null)
GO := $(if $(MISE),CGO_ENABLED=$(CGO_ENABLED) mise exec -- go,CGO_ENABLED=$(CGO_ENABLED) go)
GOOSE := $(if $(MISE),mise exec -- goose,goose)
SQLC := $(if $(MISE),mise exec -- sqlc,sqlc)
LINT := $(if $(MISE),mise exec -- golangci-lint,golangci-lint)
DATABASE_URL ?= postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable

help: ## Display available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

dev: ## Start single self-contained local development environment (PostgreSQL + backend)
	docker-compose -f docker-compose.dev.yml up -d postgres
	@echo "Waiting for PostgreSQL to be ready..."
	@until docker-compose -f docker-compose.dev.yml exec postgres pg_isready -U inframap; do sleep 1; done
	cd backend && INFRAMAP_PORT=$(DEFAULT_PORT) $(GO) run ./cmd/api

dev-down: ## Stop local development environment containers
	docker-compose -f docker-compose.dev.yml down -v

build: ## Build production backend binary
	@echo "Building InfraMap single binary..."
	cd backend && $(GO) build -ldflags="-s -w" -o bin/inframap ./cmd/api

test: ## Run backend unit & integration tests
	@echo "Running backend test suite..."
	cd backend && $(GO) test -v -race ./...

test-coverage: test ## Run tests and output HTML coverage report
	cd backend && go tool cover -html=coverage.out -o coverage.html
	@echo "Coverage report: backend/coverage.html"

lint: ## Run golangci-lint static code analysis
	@echo "Running golangci-lint..."
	cd backend && $(LINT) run ./...

generate: ## Run code generation (sqlc)
	@echo "Generating sqlc code..."
	cd backend && $(SQLC) generate

migrate-up: ## Apply pending Goose database migrations
	@echo "Running Goose migrations up..."
	cd backend && $(GOOSE) -dir migrations postgres "$(DATABASE_URL)" up

migrate-down: ## Rollback last Goose database migration
	@echo "Rolling back Goose migration..."
	cd backend && $(GOOSE) -dir migrations postgres "$(DATABASE_URL)" down

verify: generate lint test build ## Execute complete local validation pipeline (matches CI Quality Gates)
	@echo "=========================================="
	@echo " All Quality Gates Passed Successfully! "
	@echo "=========================================="

clean: ## Clean build artifacts and coverage files
	rm -rf backend/bin backend/coverage.out backend/coverage.html
