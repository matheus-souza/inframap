// Package controller provides HTTP REST handlers for the Discovery Engine.
package controller

import (
	"encoding/json"
	"errors"
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

// DeleteSource handles DELETE /api/v1/discovery/sources/{id}
func (c *DiscoveryController) DeleteSource(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")
	err := c.uc.DeleteSource(r.Context(), idStr)
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

	httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
		"message": "discovery source deleted successfully",
	})
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


