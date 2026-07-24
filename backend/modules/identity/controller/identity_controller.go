// Package controller provides HTTP handlers for identity and authentication endpoints.
package controller

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/matheussouza/inframap/internal/platform/httputil"
	"github.com/matheussouza/inframap/modules/identity/dto"
	"github.com/matheussouza/inframap/modules/identity/usecase"
)

// SessionCookieName is the official cookie name for browser WASM clients per RFC-008.
const SessionCookieName = "inframap_session"

// IdentityController handles authentication endpoints.
type IdentityController struct {
	useCase usecase.IdentityUseCase
}

// NewIdentityController creates a new IdentityController.
func NewIdentityController(uc usecase.IdentityUseCase) *IdentityController {
	return &IdentityController{useCase: uc}
}

// Login handles POST /api/v1/auth/login.
func (c *IdentityController) Login(w http.ResponseWriter, r *http.Request) {
	var req dto.LoginRequest
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

	userAgent := r.UserAgent()
	ipAddress := r.RemoteAddr

	resp, err := c.useCase.Login(r.Context(), req, userAgent, ipAddress)
	if err != nil {
		if errors.Is(err, usecase.ErrAccountLocked) {
			httputil.WriteError(w, r, http.StatusTooManyRequests, "RATE_LIMITED", err.Error(), nil)
			return
		}
		if errors.Is(err, usecase.ErrInvalidCredentials) {
			httputil.WriteError(w, r, http.StatusUnauthorized, "UNAUTHENTICATED", "Invalid username or password", nil)
			return
		}
		httputil.WriteError(w, r, http.StatusInternalServerError, "INTERNAL_ERROR", "Authentication error", nil)
		return
	}

	// Set HttpOnly SameSite=Lax cookie for browser WASM clients
	http.SetCookie(w, &http.Cookie{
		Name:     SessionCookieName,
		Value:    resp.Token,
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   604800, // 7 days
	})

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// Logout handles POST /api/v1/auth/logout.
func (c *IdentityController) Logout(w http.ResponseWriter, r *http.Request) {
	token := ExtractToken(r)

	_ = c.useCase.Logout(r.Context(), token)

	// Clear cookie
	http.SetCookie(w, &http.Cookie{
		Name:     SessionCookieName,
		Value:    "",
		Path:     "/",
		HttpOnly: true,
		SameSite: http.SameSiteLaxMode,
		MaxAge:   -1,
	})

	httputil.WriteJSON(w, r, http.StatusOK, map[string]string{
		"message": "successfully logged out",
	})
}

// GetMe handles GET /api/v1/auth/me.
func (c *IdentityController) GetMe(w http.ResponseWriter, r *http.Request) {
	token := ExtractToken(r)
	if token == "" {
		httputil.WriteError(w, r, http.StatusUnauthorized, "UNAUTHENTICATED", "Missing or invalid session token", nil)
		return
	}

	resp, err := c.useCase.GetMe(r.Context(), token)
	if err != nil {
		httputil.WriteError(w, r, http.StatusUnauthorized, "UNAUTHENTICATED", "Session expired or invalid", nil)
		return
	}

	httputil.WriteJSON(w, r, http.StatusOK, resp)
}

// ExtractToken inspects inframap_session cookie first, falling back to Authorization: Bearer header.
func ExtractToken(r *http.Request) string {
	if r == nil {
		return ""
	}

	// 1. Inspect cookie
	if cookie, err := r.Cookie(SessionCookieName); err == nil && cookie.Value != "" {
		return cookie.Value
	}

	// 2. Inspect Authorization: Bearer header
	authHeader := r.Header.Get("Authorization")
	if strings.HasPrefix(authHeader, "Bearer ") {
		return strings.TrimPrefix(authHeader, "Bearer ")
	}

	return ""
}
