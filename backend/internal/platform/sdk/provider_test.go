package sdk_test

import (
	"errors"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

func TestProviderConfig_GetString(t *testing.T) {
	cfg := sdk.ProviderConfig{
		"api_url": "https://proxmox.local:8006",
		"port":    8006,
	}

	t.Run("Existing String Value", func(t *testing.T) {
		val, err := cfg.GetString("api_url")
		if err != nil {
			t.Fatalf("expected nil error, got %v", err)
		}
		if val != "https://proxmox.local:8006" {
			t.Errorf("expected https://proxmox.local:8006, got %s", val)
		}
	})

	t.Run("Missing Key Returns ErrMissingConfigField", func(t *testing.T) {
		_, err := cfg.GetString("missing_key")
		if !errors.Is(err, sdk.ErrMissingConfigField) {
			t.Errorf("expected ErrMissingConfigField, got %v", err)
		}
	})

	t.Run("Non-String Value Returns ErrInvalidConfigType", func(t *testing.T) {
		_, err := cfg.GetString("port")
		if !errors.Is(err, sdk.ErrInvalidConfigType) {
			t.Errorf("expected ErrInvalidConfigType, got %v", err)
		}
	})

	t.Run("GetStringOrDefault Fallback", func(t *testing.T) {
		val := cfg.GetStringOrDefault("missing_key", "default_val")
		if val != "default_val" {
			t.Errorf("expected default_val, got %s", val)
		}
	})
}
