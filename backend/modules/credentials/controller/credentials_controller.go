// Package controller provides HTTP REST handlers for the Credentials module.
package controller

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/credentials/dto"
	"github.com/matheussouza/inframap/modules/credentials/repository"
	"github.com/matheussouza/inframap/modules/credentials/usecase"
)

// CredentialsController manages HTTP REST requests for secret credentials.
type CredentialsController struct {
	uc usecase.UseCase
}

// NewCredentialsController constructs a new CredentialsController instance.
func NewCredentialsController(uc usecase.UseCase) *CredentialsController {
	return &CredentialsController{uc: uc}
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

// ListCredentials handles GET /api/v1/credentials
func (c *CredentialsController) ListCredentials(w http.ResponseWriter, r *http.Request) {
	page := parsePositiveInt32(r.URL.Query().Get("page"), 1)
	perPage := parsePositiveInt32(r.URL.Query().Get("per_page"), 50)

	creds, total, err := c.uc.ListCredentials(r.Context(), page, perPage)
	if err != nil {
		slog.Error("failed to list credentials", "error", err)
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to retrieve credentials list", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, map[string]any{
		"items":    creds,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}

// CreateCredential handles POST /api/v1/credentials
func (c *CredentialsController) CreateCredential(w http.ResponseWriter, r *http.Request) {
	var req dto.CreateCredentialRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_JSON", "Invalid JSON request payload", nil)
		return
	}

	res, err := c.uc.CreateCredential(r.Context(), req)
	if err != nil {
		if errors.Is(err, dto.ErrMissingCredentialName) || errors.Is(err, dto.ErrInvalidCredentialType) || errors.Is(err, dto.ErrMissingSecretData) {
			slog.Warn("credential creation validation failed", "error", err)
			httputil.WriteError(w, r, http.StatusBadRequest, "VALIDATION_FAILED", "Invalid credential input", nil)
			return
		}
		slog.Error("failed to create credential", "error", err)
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to create credential", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, res)
}

// GetCredentialByID handles GET /api/v1/credentials/{id}
func (c *CredentialsController) GetCredentialByID(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	res, err := c.uc.GetCredentialByID(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidCredentialID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid credential ID format", nil)
			return
		}
		if errors.Is(err, repository.ErrNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Credential record not found", nil)
			return
		}
		slog.Error("failed to get credential by ID", "error", err, "credential_id", idStr)
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to retrieve credential", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, res)
}

// DeleteCredential handles DELETE /api/v1/credentials/{id}
func (c *CredentialsController) DeleteCredential(w http.ResponseWriter, r *http.Request) {
	idStr := r.PathValue("id")

	err := c.uc.DeleteCredential(r.Context(), idStr)
	if err != nil {
		if errors.Is(err, usecase.ErrInvalidCredentialID) {
			httputil.WriteError(w, r, http.StatusBadRequest, "INVALID_UUID", "Invalid credential ID format", nil)
			return
		}
		if errors.Is(err, repository.ErrNotFound) {
			httputil.WriteError(w, r, http.StatusNotFound, "NOT_FOUND", "Credential record not found", nil)
			return
		}
		slog.Error("failed to delete credential", "error", err, "credential_id", idStr)
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to delete credential", nil)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
