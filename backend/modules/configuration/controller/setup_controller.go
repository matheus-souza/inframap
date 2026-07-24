// Package controller provides HTTP handlers for the configuration module.
package controller

import (
	"encoding/json"
	"errors"
	"net/http"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/configuration/dto"
	"github.com/matheussouza/inframap/modules/configuration/usecase"
)

// SetupController handles configuration HTTP endpoints.
type SetupController struct {
	useCase usecase.SetupUseCase
}

// NewSetupController creates a new SetupController instance.
func NewSetupController(uc usecase.SetupUseCase) *SetupController {
	return &SetupController{useCase: uc}
}

// GetStatus handles GET /api/v1/setup/status.
func (c *SetupController) GetStatus(w http.ResponseWriter, r *http.Request) {
	status, err := c.useCase.GetStatus(r.Context())
	if err != nil {
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Failed to retrieve onboarding status", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, status)
}

// Onboard handles POST /api/v1/setup/onboard.
func (c *SetupController) Onboard(w http.ResponseWriter, r *http.Request) {
	var req dto.OnboardRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		httputil.WriteError(w, r, http.StatusBadRequest, "BAD_REQUEST", "Invalid JSON payload", nil)
		return
	}

	if valErrs := req.Validate(); len(valErrs) > 0 {
		fieldErrs := make([]httputil.FieldError, len(valErrs))
		for i, ve := range valErrs {
			fieldErrs[i] = httputil.FieldError{
				Field: ve.Field,
				Issue: ve.Issue,
			}
		}
		httputil.WriteError(w, r, http.StatusBadRequest, "VALIDATION_FAILED", "Request validation failed", fieldErrs)
		return
	}

	resp, err := c.useCase.Onboard(r.Context(), req)
	if err != nil {
		if errors.Is(err, usecase.ErrAlreadyOnboarded) {
			httputil.WriteError(w, r, http.StatusConflict, "CONFLICT", "System onboarding is already completed", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", err.Error(), nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusCreated, resp)
}
