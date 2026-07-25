// Package controller provides HTTP handlers for device inventory, staging queue, and subnets.
package controller

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/inventory/dto"
	"github.com/matheussouza/inframap/modules/inventory/repository"
	"github.com/matheussouza/inframap/modules/inventory/usecase"
)

// InventoryController handles REST endpoints for inventory management.
type InventoryController struct {
	useCase usecase.InventoryUseCase
}

// NewInventoryController creates a new InventoryController.
func NewInventoryController(uc usecase.InventoryUseCase) *InventoryController {
	return &InventoryController{useCase: uc}
}

func parsePositiveInt32(s string, defaultVal int32) int32 {
	if s == "" {
		return defaultVal
	}
	val, err := strconv.ParseInt(s, 10, 32)
	if err != nil || val <= 0 {
		return defaultVal
	}
	return int32(val)
}

// ListDevices handles GET /api/v1/devices.
func (c *InventoryController) ListDevices(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	deviceType := r.URL.Query().Get("device_type")
	includeDeletedStr := r.URL.Query().Get("include_deleted")
	includeDeleted := includeDeletedStr == "true"

	page := parsePositiveInt32(r.URL.Query().Get("page"), 1)
	perPage := parsePositiveInt32(r.URL.Query().Get("per_page"), 50)

	devices, total, err := c.useCase.ListDevices(r.Context(), q, deviceType, page, perPage, includeDeleted)
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list devices", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]any{
		"items":    devices,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// CreateDevice handles POST /api/v1/devices.
func (c *InventoryController) CreateDevice(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateDeviceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid JSON payload", nil)
		return
	}

	req.Normalize()

	if valErrs := req.Validate(); len(valErrs) > 0 {
		fieldErrs := make([]httputil.FieldError, len(valErrs))
		for i, ve := range valErrs {
			fieldErrs[i] = httputil.FieldError{Field: ve.Field, Issue: ve.Issue}
		}
		httputil.WriteError(w, r, http.StatusBadRequest, "VALIDATION_FAILED", "Request validation failed", fieldErrs)
		return
	}

	resp, err := c.useCase.CreateDevice(r.Context(), req)
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to create device", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, resp)
}

// GetDeviceByID handles GET /api/v1/devices/{id}.
func (c *InventoryController) GetDeviceByID(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	resp, err := c.useCase.GetDeviceByID(r.Context(), idStr, false)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid device ID format", nil)
			return
		}
		if errors.Is(err, repository.ErrDeviceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Device not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to fetch device", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// UpdateDevice handles PUT /api/v1/devices/{id}.
func (c *InventoryController) UpdateDevice(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	var req dto.UpdateDeviceRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid JSON payload", nil)
		return
	}

	resp, err := c.useCase.UpdateDevice(r.Context(), idStr, req)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) || errors.Is(err, usecase.ErrInvalidInput) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", err.Error(), nil)
			return
		}
		if errors.Is(err, repository.ErrDeviceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Device not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to update device", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// DeleteDevice handles DELETE /api/v1/devices/{id}.
func (c *InventoryController) DeleteDevice(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	if err := c.useCase.SoftDeleteDevice(r.Context(), idStr); err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid device ID format", nil)
			return
		}
		if errors.Is(err, repository.ErrDeviceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Device not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to delete device", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
		"message": "device soft-deleted successfully",
	})
}

// ListStagingDevices handles GET /api/v1/devices/staging.
func (c *InventoryController) ListStagingDevices(w http.ResponseWriter, r *http.Request) {
	status := r.URL.Query().Get("status")
	page := parsePositiveInt32(r.URL.Query().Get("page"), 1)
	perPage := parsePositiveInt32(r.URL.Query().Get("per_page"), 50)

	items, total, err := c.useCase.ListStagingDevices(r.Context(), status, page, perPage)
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list staging queue", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]any{
		"items":    items,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// ApproveStagingDevice handles POST /api/v1/devices/staging/{id}/approve.
func (c *InventoryController) ApproveStagingDevice(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	resp, err := c.useCase.ApproveStagingDevice(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid staging ID format", nil)
			return
		}
		if errors.Is(err, repository.ErrStagingDeviceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Staged device not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to approve staging device", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// DismissStagingDevice handles POST /api/v1/devices/staging/{id}/dismiss.
func (c *InventoryController) DismissStagingDevice(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	if err := c.useCase.DismissStagingDevice(r.Context(), idStr); err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid staging ID format", nil)
			return
		}
		if errors.Is(err, repository.ErrStagingDeviceNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Staged device not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to dismiss staged device", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
		"message": "staged device dismissed successfully",
	})
}

// ListSubnets handles GET /api/v1/subnets.
func (c *InventoryController) ListSubnets(w http.ResponseWriter, r *http.Request) {
	subnets, err := c.useCase.ListSubnets(r.Context())
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list subnets", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, subnets)
}

// CreateSubnet handles POST /api/v1/subnets.
func (c *InventoryController) CreateSubnet(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateSubnetRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid JSON payload", nil)
		return
	}

	if valErrs := req.Validate(); len(valErrs) > 0 {
		fieldErrs := make([]httputil.FieldError, len(valErrs))
		for i, ve := range valErrs {
			fieldErrs[i] = httputil.FieldError{Field: ve.Field, Issue: ve.Issue}
		}
		httputil.WriteError(w, r, http.StatusBadRequest, "VALIDATION_FAILED", "Request validation failed", fieldErrs)
		return
	}

	resp, err := c.useCase.CreateSubnet(r.Context(), req)
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to create subnet", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, resp)
}
