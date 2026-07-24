// Package e2e provides end-to-end functional integration tests for InfraMap API endpoints.
package e2e

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/matheussouza/inframap/internal/bootstrap"
	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/configuration/dto"
)

func getTestDatabaseURL() string {
	url := os.Getenv("DATABASE_URL")
	if url == "" {
		url = "postgres://inframap:inframap_dev_pass@localhost:5432/inframap?sslmode=disable"
	}
	return url
}

func setupTestApp(t *testing.T) (*bootstrap.App, *httptest.Server) {
	t.Helper()
	ctx := context.Background()
	cfg := bootstrap.Config{DatabaseURL: getTestDatabaseURL()}

	// Test if DB is reachable
	pool, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil || pool.Ping(ctx) != nil {
		if pool != nil {
			pool.Close()
		}
		t.Skip("skipping E2E test: PostgreSQL database not available on localhost:5432")
	}
	pool.Close()

	app, err := bootstrap.New(ctx, cfg)
	if err != nil {
		t.Fatalf("failed to bootstrap app for E2E test: %v", err)
	}

	ts := httptest.NewServer(app.Router)
	return app, ts
}

func TestE2E_OnboardingFlow(t *testing.T) {
	app, ts := setupTestApp(t)
	defer app.Close()
	defer ts.Close()

	client := ts.Client()

	// 1. Health Check Endpoint
	t.Run("GET /api/v1/health", func(t *testing.T) {
		resp, err := client.Get(ts.URL + "/api/v1/health")
		if err != nil {
			t.Fatalf("health check request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", resp.StatusCode)
		}

		var env httputil.SuccessEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode health response: %v", err)
		}
	})

	// 2. Initial Setup Status Endpoint
	t.Run("GET /api/v1/setup/status", func(t *testing.T) {
		resp, err := client.Get(ts.URL + "/api/v1/setup/status")
		if err != nil {
			t.Fatalf("status request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", resp.StatusCode)
		}

		var env httputil.SuccessEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&env); err != nil {
			t.Fatalf("failed to decode status response: %v", err)
		}
	})

	// 3. Validation Failure on Weak Password
	t.Run("POST /api/v1/setup/onboard - Weak Password", func(t *testing.T) {
		payload := dto.OnboardRequest{
			AdminUsername: "admin",
			AdminEmail:    "admin@example.com",
			AdminPassword: "weak",
			AdminFullName: "Admin User",
		}
		body, _ := json.Marshal(payload)

		resp, err := client.Post(ts.URL+"/api/v1/setup/onboard", "application/json", bytes.NewReader(body))
		if err != nil {
			t.Fatalf("onboard request failed: %v", err)
		}
		defer func() { _ = resp.Body.Close() }()

		if resp.StatusCode != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request for weak password, got %d", resp.StatusCode)
		}

		var errEnv httputil.ErrorEnvelope
		if err := json.NewDecoder(resp.Body).Decode(&errEnv); err != nil {
			t.Fatalf("failed to decode error response: %v", err)
		}

		if errEnv.Error.Code != "VALIDATION_FAILED" {
			t.Errorf("expected error code VALIDATION_FAILED, got %s", errEnv.Error.Code)
		}
	})
}
