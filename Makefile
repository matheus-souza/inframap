# InfraMap Makefile — RFC-010 Compliant

.PHONY: help dev dev-down dev-clean build test test-e2e test-coverage lint verify generate migrate-up migrate-down setup-hooks clean

DEFAULT_PORT ?= 8055
MISE := $(shell command -v mise 2> /dev/null)
GO := $(if $(MISE),mise exec -- go,go)
GOOSE := $(if $(MISE),mise exec -- goose,goose)
SQLC := $(if $(MISE),mise exec -- sqlc,sqlc)
LINT := $(GO) run github.com/golangci/golangci-lint/cmd/golangci-lint@v1.64.8
DATABASE_URL ?= postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable

help: ## Display available commands
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

dev: ## Start single self-contained local development environment (PostgreSQL + backend)
	docker-compose -f docker-compose.dev.yml up -d postgres
	@echo "Waiting for PostgreSQL to be ready..."
	@until docker-compose -f docker-compose.dev.yml exec postgres pg_isready -U inframap; do sleep 1; done
	cd backend && INFRAMAP_PORT=$(DEFAULT_PORT) $(GO) run ./cmd/api

dev-down: ## Stop local development environment containers (preserves database volume)
	docker-compose -f docker-compose.dev.yml down

dev-clean: ## Stop containers and remove database volume
	docker-compose -f docker-compose.dev.yml down -v

build: ## Build production backend binary
	@echo "Building InfraMap single binary..."
	cd backend && CGO_ENABLED=0 $(GO) build -ldflags="-s -w" -o bin/inframap ./cmd/api

test: ## Run backend unit & integration tests
	@echo "Running backend test suite..."
	cd backend && $(GO) test -v -race ./...

test-e2e: ## Run end-to-end functional integration tests
	@echo "Running E2E integration test suite..."
	cd backend && $(GO) test -v -race ./tests/e2e/...

test-coverage: ## Run tests and output HTML coverage report
	@echo "Running test coverage report..."
	cd backend && $(GO) test -v -coverprofile=coverage.out ./... && $(GO) tool cover -html=coverage.out -o coverage.html
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

setup-hooks: ## Configure local git hooks path (.githooks)
	@git config core.hooksPath .githooks
	@chmod +x .githooks/*
	@echo "Git pre-push hook configured successfully (.githooks)!"

clean: ## Clean build artifacts and coverage files
	rm -rf backend/bin backend/coverage.out backend/coverage.html
