package main

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/bootstrap"
)

func TestHealthEndpoint(t *testing.T) {
	cfg := bootstrap.Config{
		DatabaseURL: "postgres://invalid:invalid@localhost:5432/invalid?sslmode=disable",
	}
	app, err := bootstrap.New(context.Background(), cfg)
	if err != nil {
		t.Fatalf("failed to create bootstrap app: %v", err)
	}
	defer app.Close()

	w := httptest.NewRecorder()
	r := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)

	app.Router.ServeHTTP(w, r)

	if w.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", w.Code)
	}

	var resp struct {
		Data struct {
			Status  string `json:"status"`
			Version string `json:"version"`
		} `json:"data"`
	}

	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}

	if resp.Data.Status != "ok" {
		t.Errorf("expected status 'ok', got %q", resp.Data.Status)
	}
}

func TestGetPort(t *testing.T) {
	if port := getPort(); port != "8055" {
		t.Errorf("expected default port 8055, got %s", port)
	}
}
