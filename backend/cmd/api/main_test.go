package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHealthHandler(t *testing.T) {
	router := setupRouter()

	req, err := http.NewRequest(http.MethodGet, "/api/v1/health", nil)
	if err != nil {
		t.Fatalf("failed to create request: %v", err)
	}

	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Errorf("expected status 200, got %d", rec.Code)
	}

	var resp HealthResponse
	if err := json.NewDecoder(rec.Body).Decode(&resp); err != nil {
		t.Fatalf("failed to decode response body: %v", err)
	}

	if resp.Status != "ok" {
		t.Errorf("expected status 'ok', got '%s'", resp.Status)
	}
}

func TestGetPortDefault(t *testing.T) {
	t.Setenv("INFRAMAP_PORT", "")
	port := getPort()
	if port != "8055" {
		t.Errorf("expected default port 8055, got %s", port)
	}
}

func TestGetPortCustom(t *testing.T) {
	t.Setenv("INFRAMAP_PORT", "9090")
	port := getPort()
	if port != "9090" {
		t.Errorf("expected port 9090, got %s", port)
	}
}
