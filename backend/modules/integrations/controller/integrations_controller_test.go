package controller_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/integrations/controller"
	"github.com/matheussouza/inframap/modules/integrations/registry"
)

type dummyTestProvider struct {
	failHealth bool
}

func (d *dummyTestProvider) ID() string { return "test-provider" }
func (d *dummyTestProvider) Metadata() sdk.ProviderMetadata {
	return sdk.ProviderMetadata{ID: "test-provider", Name: "Test Provider"}
}
func (d *dummyTestProvider) ConfigSchema() sdk.ConfigSchema {
	return sdk.ConfigSchema{Fields: []sdk.ConfigField{{Key: "url"}}}
}
func (d *dummyTestProvider) HealthCheck(_ context.Context, _ sdk.ProviderConfig) error {
	if d.failHealth {
		return errors.New("connection refused")
	}
	return nil
}
func (d *dummyTestProvider) Discover(_ context.Context, _ sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	return []sdk.NormalizedDevice{}, nil
}

func TestIntegrationsController(t *testing.T) {
	reg := registry.NewRegistry()
	p := &dummyTestProvider{}
	_ = reg.Register(p)

	ctrl := controller.NewIntegrationsController(reg)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /api/v1/integrations/providers", ctrl.ListProviders)
	mux.HandleFunc("POST /api/v1/integrations/providers/{id}/health", ctrl.TestProviderHealth)

	t.Run("ListProviders", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/integrations/providers", nil)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("TestProviderHealth Success", func(t *testing.T) {
		body, _ := json.Marshal(map[string]interface{}{
			"config": map[string]interface{}{"url": "http://localhost"},
		})
		req := httptest.NewRequest(http.MethodPost, "/api/v1/integrations/providers/test-provider/health", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})

	t.Run("TestProviderHealth NotFound & Invalid JSON", func(t *testing.T) {
		reqNotFound := httptest.NewRequest(http.MethodPost, "/api/v1/integrations/providers/unknown/health", nil)
		recNotFound := httptest.NewRecorder()
		mux.ServeHTTP(recNotFound, reqNotFound)

		if recNotFound.Code != http.StatusNotFound {
			t.Errorf("expected 404 Not Found, got %d", recNotFound.Code)
		}

		reqBadJSON := httptest.NewRequest(http.MethodPost, "/api/v1/integrations/providers/test-provider/health", bytes.NewReader([]byte("invalid")))
		recBadJSON := httptest.NewRecorder()
		mux.ServeHTTP(recBadJSON, reqBadJSON)

		if recBadJSON.Code != http.StatusBadRequest {
			t.Errorf("expected 400 Bad Request, got %d", recBadJSON.Code)
		}
	})

	t.Run("TestProviderHealth Failing HealthCheck", func(t *testing.T) {
		p.failHealth = true
		body, _ := json.Marshal(map[string]interface{}{
			"config": map[string]interface{}{"url": "http://localhost"},
		})
		req := httptest.NewRequest(http.MethodPost, "/api/v1/integrations/providers/test-provider/health", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		if rec.Code != http.StatusOK {
			t.Errorf("expected 200 OK, got %d", rec.Code)
		}
	})
}
