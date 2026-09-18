// Package controller provides HTTP REST handlers for the Discovery Engine.
package controller

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strconv"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	"github.com/matheussouza/inframap/modules/discovery/usecase"
)

// DiscoveryController handles HTTP requests for discovery management.
type DiscoveryController struct {
	uc usecase.DiscoveryUseCase
}

// NewDiscoveryController constructs a DiscoveryController instance.
func NewDiscoveryController(uc usecase.DiscoveryUseCase) *DiscoveryController {
	return &DiscoveryController{uc: uc}
}

// CreateSource handles POST /api/v1/discovery/sources
func (c *DiscoveryController) CreateSource(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateDiscoverySourceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON payload format", nil)
		return
	}

	resp, err := c.uc.CreateSource(r.Context(), &req)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidInput) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", err.Error(), nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to create discovery source", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, resp)
}

// UpdateSource handles PUT /api/v1/discovery/sources/{id}
func (c *DiscoveryController) UpdateSource(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	var req dto.UpdateDiscoverySourceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON payload format", nil)
		return
	}

	resp, err := c.uc.UpdateSource(r.Context(), idStr, &req)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid discovery source UUID format", nil)
			return
		}
		if errors.Is(err, usecase.ErrInvalidInput) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", err.Error(), nil)
			return
		}
		if errors.Is(err, repository.ErrSourceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
			return
		}
		var errSSRF *dto.ErrSecretRequiredOnTargetChange
		if errors.As(err, &errSSRF) {
			fieldErrors := make([]httputil.FieldError, len(errSSRF.Fields))
			for i, f := range errSSRF.Fields {
				fieldErrors[i] = httputil.FieldError{
					Field: f,
					Issue: "target host/URL changed without supplying credentials",
				}
			}
			httputil.WriteError(w, r, http.StatusUnprocessableEntity, "secret_required_on_target_change", "Alterar o host/URL de destino exige reentrada da credencial.", fieldErrors)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to update discovery source", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// GetSourceByID handles GET /api/v1/discovery/sources/{id}
func (c *DiscoveryController) GetSourceByID(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	resp, err := c.uc.GetSourceByID(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid discovery source UUID format", nil)
			return
		}
		if errors.Is(err, repository.ErrSourceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to retrieve discovery source", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// ListSources handles GET /api/v1/discovery/sources
func (c *DiscoveryController) ListSources(w http.ResponseWriter, r *http.Request) {
	items, err := c.uc.ListSources(r.Context())
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list discovery sources", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"items": items,
		"total": len(items),
	})
}

// TriggerRun handles POST /api/v1/discovery/sources/{id}/run
func (c *DiscoveryController) TriggerRun(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	resp, err := c.uc.TriggerRun(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid discovery source UUID format", nil)
			return
		}
		if errors.Is(err, repository.ErrSourceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to trigger discovery run", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// GetDeletionImpact handles GET /api/v1/discovery/sources/{id}/deletion-impact.
func (c *DiscoveryController) GetDeletionImpact(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	resp, err := c.uc.GetDeletionImpact(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid discovery source UUID format", nil)
			return
		}
		if errors.Is(err, repository.ErrSourceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to get discovery source deletion impact", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// DeleteSource handles DELETE /api/v1/discovery/sources/{id}
func (c *DiscoveryController) DeleteSource(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	resp, err := c.uc.SoftDeleteSource(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid discovery source UUID format", nil)
			return
		}
		if errors.Is(err, repository.ErrSourceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to delete discovery source", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// ListRecordsByDevice handles GET /api/v1/discovery/devices/{id}/records
func (c *DiscoveryController) ListRecordsByDevice(w http.ResponseWriter, r *http.Request) {
	deviceIDStr := r.PathValue("id")
	records, err := c.uc.ListRecordsByDevice(r.Context(), deviceIDStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid device UUID format", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list discovery records", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"items": records,
		"total": len(records),
	})
}

// TriggerScan handles POST /api/v1/discovery/scan
func (c *DiscoveryController) TriggerScan(w http.ResponseWriter, r *http.Request) {
	var req dto.TriggerScanRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON payload format", nil)
		return
	}

	resp, err := c.uc.TriggerScan(r.Context(), &req)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidInput) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_INPUT", err.Error(), nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to execute active discovery scan", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// ListRunsBySource handles GET /api/v1/discovery/sources/{id}/runs
func (c *DiscoveryController) ListRunsBySource(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	limit := 20
	offset := 0

	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 && parsed <= 1000 {
			limit = parsed
		}
	}
	if o := r.URL.Query().Get("offset"); o != "" {
		if parsed, err := strconv.Atoi(o); err == nil && parsed >= 0 && parsed <= 1000000 {
			offset = parsed
		}
	}

	runs, total, err := c.uc.ListRunsBySource(r.Context(), idStr, limit, offset)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid discovery source UUID format", nil)
			return
		}
		if errors.Is(err, repository.ErrSourceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list collector runs", nil)
		return
	}

	if runs == nil {
		runs = make([]*dto.CollectorRunResponse, 0)
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]interface{}{
		"items": runs,
		"total": total,
	})
}

// TestHealth handles POST /api/v1/discovery/sources/{id}/health
func (c *DiscoveryController) TestHealth(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if id == "" {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_ID", "Discovery source ID is required", nil)
		return
	}

	// Guard against SSRF: reject any payload containing target host/URL or credentials
	if r.Body != nil {
		bodyBytes, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 1024*1024))
		if err != nil {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "Failed to read request body", nil)
			return
		}
		trimmed := bytes.TrimSpace(bodyBytes)
		if len(trimmed) > 0 {
			var payload map[string]interface{}
			if err := json.Unmarshal(trimmed, &payload); err != nil {
				httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON request body", nil)
				return
			}
			for k := range payload {
				if dto.IsTargetKey("", k) || dto.IsSecretKey("", k) {
					httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "Health check requests must not contain destination or credential parameters", nil)
					return
				}
			}
			if len(payload) > 0 {
				httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_REQUEST", "Health check requests must not contain payload body", nil)
				return
			}
		}
	}

	providerID := r.URL.Query().Get("provider")
	resp, err := c.uc.TestHealth(r.Context(), id, providerID)
	if err != nil {
		switch {
		case errors.Is(err, usecase.ErrInvalidUUID):
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_ID", "Invalid discovery source UUID", nil)
		case errors.Is(err, repository.ErrSourceNotFound):
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Discovery source not found", nil)
		case errors.Is(err, usecase.ErrNoProviderForSource):
			httputil.WriteError(w, r, http.StatusBadRequest, "NO_PROVIDER", "Source does not have an integration provider configured", nil)
		case errors.Is(err, usecase.ErrProviderNotFound):
			httputil.WriteError(w, r, http.StatusBadRequest, "PROVIDER_NOT_FOUND", "Requested provider not found", nil)
		default:
			httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to test source health", nil)
		}
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}


