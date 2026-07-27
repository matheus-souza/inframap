package dto_test

import (
	"errors"
	"testing"

	"github.com/matheussouza/inframap/modules/integrations/dto"
)

func TestHealthCheckRequest_NormalizeAndValidate(t *testing.T) {
	t.Run("Valid Payload", func(t *testing.T) {
		req := &dto.HealthCheckRequest{
			Config: map[string]interface{}{"api_url": "http://localhost:8006"},
		}
		req.Normalize()
		if err := req.Validate(); err != nil {
			t.Errorf("expected nil error, got %v", err)
		}
	})

	t.Run("Empty Payload Returns Error", func(t *testing.T) {
		req := &dto.HealthCheckRequest{}
		req.Normalize()
		if err := req.Validate(); !errors.Is(err, dto.ErrInvalidConfig) {
			t.Errorf("expected ErrInvalidConfig, got %v", err)
		}
	})
}
