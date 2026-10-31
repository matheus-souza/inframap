// Package controller provides HTTP handlers for DNS records.
package controller

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/dns/dto"
	"github.com/matheussouza/inframap/modules/dns/usecase"
)

// DNSController handles REST endpoints for DNS records.
type DNSController struct {
	useCase usecase.DNSRecordUseCase
}

// NewDNSController creates a new DNSController.
func NewDNSController(uc usecase.DNSRecordUseCase) *DNSController {
	return &DNSController{useCase: uc}
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

// CreateDNSRecord handles POST /api/v1/dns-records.
func (c *DNSController) CreateDNSRecord(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateDNSRecordRequest
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

	resp, err := c.useCase.CreateDNSRecord(r.Context(), req)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) || errors.Is(err, usecase.ErrDNSInvalidAddressType) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", err.Error(), nil)
			return
		}
		if errors.Is(err, usecase.ErrDNSRecordNameConflict) {
			httputil.WriteError(w, r, http.StatusConflict, "CONFLICT", err.Error(), nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to create DNS record", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, resp)
}

// GetDNSRecordByID handles GET /api/v1/dns-records/{id}.
func (c *DNSController) GetDNSRecordByID(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	resp, err := c.useCase.GetDNSRecordByID(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid DNS record ID format", nil)
			return
		}
		if errors.Is(err, usecase.ErrDNSRecordNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "DNS record not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to get DNS record", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// ListDNSRecords handles GET /api/v1/dns-records.
func (c *DNSController) ListDNSRecords(w http.ResponseWriter, r *http.Request) {
	page := parsePositiveInt32(r.URL.Query().Get("page"), 1)
	perPage := parsePositiveInt32(r.URL.Query().Get("per_page"), 50)

	records, total, err := c.useCase.ListDNSRecords(r.Context(), page, perPage)
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list DNS records", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]any{
		"items":  records,
		"total":  total,
		"page":   page,
		"per_page": perPage,
	})
}

// UpdateDNSRecord handles PUT /api/v1/dns-records/{id}.
func (c *DNSController) UpdateDNSRecord(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	var req dto.UpdateDNSRecordRequest
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

	resp, err := c.useCase.UpdateDNSRecord(r.Context(), idStr, req)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) || errors.Is(err, usecase.ErrDNSInvalidAddressType) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", err.Error(), nil)
			return
		}
		if errors.Is(err, usecase.ErrDNSRecordNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "DNS record not found", nil)
			return
		}
		if errors.Is(err, usecase.ErrDNSRecordNameConflict) {
			httputil.WriteError(w, r, http.StatusConflict, "CONFLICT", err.Error(), nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to update DNS record", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// DeleteDNSRecord handles DELETE /api/v1/dns-records/{id}.
func (c *DNSController) DeleteDNSRecord(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	if err := c.useCase.DeleteDNSRecord(r.Context(), idStr); err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid DNS record ID format", nil)
			return
		}
		if errors.Is(err, usecase.ErrDNSRecordNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "DNS record not found", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to delete DNS record", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
		"message": "dns record deleted successfully",
	})
}

// ListDNSRecordsByDevice handles GET /api/v1/devices/{id}/dns-records.
func (c *DNSController) ListDNSRecordsByDevice(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	records, err := c.useCase.ListDNSRecordsByDevice(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidUUID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid device ID format", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to list DNS records", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]any{
		"items": records,
		"total": len(records),
	})
}
