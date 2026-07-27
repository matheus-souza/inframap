package registry_test

import (
	"context"
	"testing"

	"github.com/matheussouza/inframap/internal/platform/sdk"
	"github.com/matheussouza/inframap/modules/integrations/registry"
)

type dummyProvider struct {
	id string
}

func (d *dummyProvider) ID() string { return d.id }
func (d *dummyProvider) Metadata() sdk.ProviderMetadata {
	return sdk.ProviderMetadata{ID: d.id, Name: "Dummy"}
}
func (d *dummyProvider) ConfigSchema() sdk.ConfigSchema { return sdk.ConfigSchema{} }
func (d *dummyProvider) HealthCheck(_ context.Context, _ sdk.ProviderConfig) error { return nil }
func (d *dummyProvider) Discover(_ context.Context, _ sdk.ProviderConfig) ([]sdk.NormalizedDevice, error) {
	return []sdk.NormalizedDevice{}, nil
}

func TestRegistry(t *testing.T) {
	reg := registry.NewRegistry()

	t.Run("Register and Get Provider", func(t *testing.T) {
		p := &dummyProvider{id: "test-provider"}
		if err := reg.Register(p); err != nil {
			t.Fatalf("expected nil error on Register, got %v", err)
		}

		retrieved, found := reg.Get("test-provider")
		if !found {
			t.Fatal("expected provider to be found")
		}
		if retrieved.ID() != "test-provider" {
			t.Errorf("expected test-provider ID, got %s", retrieved.ID())
		}
	})

	t.Run("Register Nil or Empty ID Errors", func(t *testing.T) {
		if err := reg.Register(nil); err == nil {
			t.Error("expected error when registering nil provider")
		}

		emptyP := &dummyProvider{id: ""}
		if err := reg.Register(emptyP); err == nil {
			t.Error("expected error when registering provider with empty ID")
		}
	})

	t.Run("List Registered Providers", func(t *testing.T) {
		list := reg.List()
		if len(list) == 0 {
			t.Error("expected non-empty list of provider metadata")
		}
	})
}
