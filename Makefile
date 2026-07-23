# InfraMap Makefile

.PHONY: help dev build test lint clean goose-up goose-down sqlc-generate

DEFAULT_PORT ?= 8055
MISE := $(shell command -v mise 2> /dev/null)
GO := $(if $(MISE),mise exec -- go,go)
GOOSE := $(if $(MISE),mise exec -- goose,goose)
SQLC := $(if $(MISE),mise exec -- sqlc,sqlc)

help: ## Display available commands
	@echo "InfraMap Development Commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}'

dev: ## Run local development environment (single binary)
	@echo "Starting InfraMap backend on port $(DEFAULT_PORT)..."
	cd backend && INFRAMAP_PORT=$(DEFAULT_PORT) $(GO) run ./cmd/api/main.go

build: ## Build single binary with embedded WASM frontend
	@echo "Building InfraMap single binary..."
	cd backend && $(GO) build -o bin/inframap ./cmd/api

test: ## Run backend unit & integration tests
	@echo "Running backend test suite..."
	cd backend && $(GO) test -v -race ./...

lint: ## Run golangci-lint on backend code
	@echo "Running golangci-lint..."
	cd backend && golangci-lint run ./...

clean: ## Clean built binaries and coverage reports
	rm -rf backend/bin/ backend/coverage.out

goose-up: ## Run database migrations up
	@echo "Running Goose migrations up..."
	cd backend && $(GOOSE) postgres "$${DATABASE_URL:-postgres://postgres:postgres@localhost:5432/inframap?sslmode=disable}" up

goose-down: ## Rollback last database migration
	@echo "Rolling back Goose migration..."
	cd backend && $(GOOSE) postgres "$${DATABASE_URL:-postgres://postgres:postgres@localhost:5432/inframap?sslmode=disable}" down

sqlc-generate: ## Generate Go code from SQL queries using sqlc
	@echo "Generating SQLC code..."
	cd backend && $(SQLC) generate
