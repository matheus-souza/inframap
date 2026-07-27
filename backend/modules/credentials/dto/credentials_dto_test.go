package dto_test

import (
	"testing"

	"github.com/matheussouza/inframap/modules/credentials/dto"
)

func TestCreateCredentialRequest_NormalizeAndValidate(t *testing.T) {
	t.Run("Valid Request Normalization and Validation", func(t *testing.T) {
		req := &dto.CreateCredentialRequest{
			Name:        "  PVE API Token  ",
			Type:        "  API_TOKEN  ",
			SecretData:  "  secret-12345  ",
			Description: "  Token for PVE  ",
		}

		req.Normalize()
		if req.Name != "PVE API Token" {
			t.Errorf("expected normalized name PVE API Token, got %s", req.Name)
		}
		if req.Type != "api_token" {
			t.Errorf("expected normalized type api_token, got %s", req.Type)
		}
		if req.SecretData != "secret-12345" {
			t.Errorf("expected normalized secret_data secret-12345, got %s", req.SecretData)
		}

		if err := req.Validate(); err != nil {
			t.Errorf("expected nil error on Validate, got %v", err)
		}
	})

	t.Run("Missing Name Failure", func(t *testing.T) {
		req := &dto.CreateCredentialRequest{
			Name:       "",
			Type:       "ssh_key",
			SecretData: "some-key",
		}
		req.Normalize()
		if err := req.Validate(); err != dto.ErrMissingCredentialName {
			t.Errorf("expected ErrMissingCredentialName, got %v", err)
		}
	})

	t.Run("Invalid Type Failure", func(t *testing.T) {
		req := &dto.CreateCredentialRequest{
			Name:       "Test",
			Type:       "invalid_type",
			SecretData: "some-secret",
		}
		req.Normalize()
		if err := req.Validate(); err != dto.ErrInvalidCredentialType {
			t.Errorf("expected ErrInvalidCredentialType, got %v", err)
		}
	})

	t.Run("Missing Secret Data Failure", func(t *testing.T) {
		req := &dto.CreateCredentialRequest{
			Name:       "Test",
			Type:       "snmp_v2c",
			SecretData: "",
		}
		req.Normalize()
		if err := req.Validate(); err != dto.ErrMissingSecretData {
			t.Errorf("expected ErrMissingSecretData, got %v", err)
		}
	})
}
