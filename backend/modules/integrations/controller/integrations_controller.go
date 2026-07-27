// Package controller provides REST HTTP handlers for the Integrations module.
package controller

import (
	"encoding/json"
	"log/slog"
	"net/http"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/integrations/dto"
	"github.com/matheussouza/inframap/modules/integrations/registry"
)

// IntegrationsController handles integration provider discovery endpoints.
type IntegrationsController struct {
	reg *registry.Registry
}

// NewIntegrationsController constructs an IntegrationsController.
func NewIntegrationsController(reg *registry.Registry) *IntegrationsController {
	return &IntegrationsController{reg: reg}
}

// ListProviders handles GET /api/v1/integrations/providers
func (c *IntegrationsController) ListProviders(w http.ResponseWriter, r *http.Request) {
	metaList := c.reg.List()

	response := make([]dto.ProviderSchemaResponse, 0, len(metaList))
	for _, meta := range metaList {
		if p, found := c.reg.Get(meta.ID); found {
			response = append(response, dto.ProviderSchemaResponse{
				Metadata: meta,
				Schema:   p.ConfigSchema(),
			})
		}
	}

	httputil.WriteJSON(w, r, http.StatusOK, response)
}

// TestProviderHealth handles POST /api/v1/integrations/providers/{id}/health
func (c *IntegrationsController) TestProviderHealth(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	provider, found := c.reg.Get(id)
	if !found {
		httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Integration provider not found", nil)
		return
	}

	var req dto.HealthCheckRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON request body", nil)
		return
	}
	req.Normalize()
	if err := req.Validate(); err != nil {
		slog.Warn("integration health check validation failed", "provider", id, "error", err)
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", "Invalid health check request", nil)
		return
	}

	if err := provider.HealthCheck(r.Context(), sdk.ProviderConfig(req.Config)); err != nil {
		slog.Error("integration provider health check failed", "provider", id, "error", err)
		httputil.WriteJSON(w, r, http.StatusOK, dto.HealthCheckResponse{
			ProviderID: id,
			Status:     "error",
			Message:    "Health check failed",
		})
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, dto.HealthCheckResponse{
		ProviderID: id,
		Status:     "ok",
	})
}
