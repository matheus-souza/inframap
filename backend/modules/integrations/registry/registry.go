// Package registry provides thread-safe registration and lookup for integration providers.
package registry

import (
	"fmt"
	"sync"

	"github.com/matheussouza/inframap/internal/platform/sdk"
)

// Registry manages available integration provider implementations.
type Registry struct {
	mu        sync.RWMutex
	providers map[string]sdk.Provider
}

// NewRegistry constructs a new thread-safe Registry instance.
func NewRegistry() *Registry {
	return &Registry{
		providers: make(map[string]sdk.Provider),
	}
}

// Register registers a provider implementation with the registry.
func (r *Registry) Register(provider sdk.Provider) error {
	if provider == nil {
		return fmt.Errorf("cannot register nil provider")
	}
	r.mu.Lock()
	defer r.mu.Unlock()

	id := provider.ID()
	if id == "" {
		return fmt.Errorf("provider ID cannot be empty")
	}
	r.providers[id] = provider
	return nil
}

// Get retrieves a registered provider by ID.
func (r *Registry) Get(id string) (sdk.Provider, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	p, exists := r.providers[id]
	return p, exists
}

// List returns metadata for all registered providers.
func (r *Registry) List() []sdk.ProviderMetadata {
	r.mu.RLock()
	defer r.mu.RUnlock()

	list := make([]sdk.ProviderMetadata, 0, len(r.providers))
	for _, p := range r.providers {
		list = append(list, p.Metadata())
	}
	return list
}
