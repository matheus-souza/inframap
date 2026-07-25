package dto_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/identity/dto"
)

func TestLoginRequest_Normalize(t *testing.T) {
	req := dto.LoginRequest{
		Username: "  Admin  ",
		Password: "secret",
	}
	req.Normalize()

	if req.Username != "Admin" {
		t.Errorf("expected trimmed username 'Admin', got %q", req.Username)
	}
	if req.Password != "secret" {
		t.Error("password should not be modified by Normalize")
	}
}

func TestLoginRequest_Validate(t *testing.T) {
	t.Run("valid request", func(t *testing.T) {
		req := dto.LoginRequest{Username: "admin", Password: "pass"}
		if errs := req.Validate(); len(errs) != 0 {
			t.Errorf("expected no errors, got %v", errs)
		}
	})

	t.Run("empty username", func(t *testing.T) {
		req := dto.LoginRequest{Username: "", Password: "pass"}
		errs := req.Validate()
		if len(errs) != 1 || errs[0].Field != "username" {
			t.Errorf("expected username error, got %v", errs)
		}
	})

	t.Run("empty password", func(t *testing.T) {
		req := dto.LoginRequest{Username: "admin", Password: ""}
		errs := req.Validate()
		if len(errs) != 1 || errs[0].Field != "password" {
			t.Errorf("expected password error, got %v", errs)
		}
	})

	t.Run("both empty", func(t *testing.T) {
		req := dto.LoginRequest{Username: "", Password: ""}
		errs := req.Validate()
		if len(errs) != 2 {
			t.Errorf("expected 2 errors, got %d", len(errs))
		}
	})
}

func TestValidationError_Error(t *testing.T) {
	ve := dto.ValidationError{Field: "email", Issue: "required"}
	if ve.Error() != "email: required" {
		t.Errorf("unexpected error string: %s", ve.Error())
	}
}
