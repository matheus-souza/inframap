// Package dto provides request and response structures for authentication and identity operations.
package dto

import (
	"fmt"
	"strings"
	"time"
)

// LoginRequest represents credentials payload for POST /api/v1/auth/login.
type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// LoginResponse represents successful authentication response.
type LoginResponse struct {
	Token       string    `json:"token"`
	UserID      string    `json:"user_id"`
	Username    string    `json:"username"`
	Email       string    `json:"email"`
	FullName    string    `json:"full_name"`
	Permissions []string  `json:"permissions"`
	ExpiresAt   time.Time `json:"expires_at"`
}

// UserMeResponse represents profile response for GET /api/v1/auth/me.
type UserMeResponse struct {
	ID          string   `json:"id"`
	Username    string   `json:"username"`
	Email       string   `json:"email"`
	FullName    string   `json:"full_name"`
	IsActive    bool     `json:"is_active"`
	Permissions []string `json:"permissions"`
}

// ValidationError represents request validation error details.
type ValidationError struct {
	Field string
	Issue string
}

func (v ValidationError) Error() string {
	return fmt.Sprintf("%s: %s", v.Field, v.Issue)
}

// Normalize trims whitespace from string fields before validation.
func (r *LoginRequest) Normalize() {
	r.Username = strings.TrimSpace(r.Username)
}

// Validate validates LoginRequest fields.
// Call Normalize() before Validate().
func (r *LoginRequest) Validate() []ValidationError {
	var errs []ValidationError

	if len(r.Username) == 0 {
		errs = append(errs, ValidationError{
			Field: "username",
			Issue: "username or email is required",
		})
	}

	if len(r.Password) == 0 {
		errs = append(errs, ValidationError{
			Field: "password",
			Issue: "password is required",
		})
	}

	return errs
}
