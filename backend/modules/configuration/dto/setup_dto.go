// Package dto provides request and response data transfer objects for the configuration module.
package dto

import (
	"fmt"
	"net/mail"
	"strings"

	"github.com/google/uuid"
	"github.com/trustelem/zxcvbn"
)

// StatusResponse represents the response for GET /api/v1/setup/status.
type StatusResponse struct {
	OnboardingCompleted bool   `json:"onboarding_completed"`
	SystemInstanceID    string `json:"system_instance_id"`
}

// OnboardRequest represents the payload for POST /api/v1/setup/onboard.
type OnboardRequest struct {
	AdminUsername    string `json:"admin_username"`
	AdminEmail       string `json:"admin_email"`
	AdminPassword    string `json:"admin_password"`
	AdminFullName    string `json:"admin_full_name"`
	TelemetryEnabled bool   `json:"telemetry_enabled"`
}

// OnboardResponse represents the response for POST /api/v1/setup/onboard.
type OnboardResponse struct {
	OnboardingCompleted bool   `json:"onboarding_completed"`
	SystemInstanceID    string `json:"system_instance_id"`
	AdminUserID         string `json:"admin_user_id"`
}

// ValidationError represents a field-level request validation failure.
type ValidationError struct {
	Field string
	Issue string
}

func (v ValidationError) Error() string {
	return fmt.Sprintf("%s: %s", v.Field, v.Issue)
}

// Validate validates OnboardRequest fields according to RFC-012 guidelines.
func (r *OnboardRequest) Validate() []ValidationError {
	var errs []ValidationError

	r.AdminUsername = strings.TrimSpace(r.AdminUsername)
	if len(r.AdminUsername) < 3 || len(r.AdminUsername) > 64 {
		errs = append(errs, ValidationError{
			Field: "admin_username",
			Issue: "username must be between 3 and 64 characters",
		})
	}

	r.AdminEmail = strings.TrimSpace(r.AdminEmail)
	if _, err := mail.ParseAddress(r.AdminEmail); err != nil {
		errs = append(errs, ValidationError{
			Field: "admin_email",
			Issue: "must be a valid email address",
		})
	}

	r.AdminFullName = strings.TrimSpace(r.AdminFullName)
	if len(r.AdminFullName) == 0 || len(r.AdminFullName) > 128 {
		errs = append(errs, ValidationError{
			Field: "admin_full_name",
			Issue: "full name is required and cannot exceed 128 characters",
		})
	}

	// Password validation: min 12 chars, zxcvbn score >= 3
	if len(r.AdminPassword) < 12 {
		errs = append(errs, ValidationError{
			Field: "admin_password",
			Issue: "password must be at least 12 characters long",
		})
	} else {
		res := zxcvbn.PasswordStrength(r.AdminPassword, []string{r.AdminUsername, r.AdminEmail, "inframap"})
		if res.Score < 3 {
			errs = append(errs, ValidationError{
				Field: "admin_password",
				Issue: "password is too weak (zxcvbn score must be at least 3); try a longer passphrase",
			})
		}
	}

	_ = uuid.Nil

	return errs
}
