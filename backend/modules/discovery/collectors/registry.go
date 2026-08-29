// Package collectors provides plug-and-play worker contracts, registry, and DTOs for discovery.
package collectors

import (
	"errors"
	"strings"
	"sync"
)

// Supported discovery collector type constants matching dto.ValidDiscoveryTypes.
const (
	TypeICMPSweep  = "icmp_sweep"
	TypeARPSweep   = "arp_sweep"
	TypeReverseDNS = "reverse_dns"
	TypeSNMP       = "snmp"
	TypeMDNS       = "mdns"
	TypeProxmox    = "proxmox"
	TypeDocker     = "docker"
	TypeUniFi      = "unifi"
)

// Canonical collector identifiers for implemented workers.
const (
	CollectorIDICMP       = "icmp"
	CollectorIDARP        = "arp"
	CollectorIDReverseDNS = "reversedns"
	CollectorIDSNMP       = "snmp"
	CollectorIDMDNS       = "mdns"
)

// ErrCollectorNotImplemented is returned when a collector type is not implemented in this wave.
var ErrCollectorNotImplemented = errors.New("collector is not implemented in this wave")

// ErrCollectorNotFound is returned when a collector cannot be resolved in the registry.
var ErrCollectorNotFound = errors.New("collector not found in registry")

// ErrNilCollector is returned when attempting to register a nil collector.
var ErrNilCollector = errors.New("cannot register nil collector")

// ErrEmptyCollectorID is returned when attempting to register a collector with an empty ID.
var ErrEmptyCollectorID = errors.New("collector ID cannot be empty")

// CanonicalMapping maps all discovery type keys and aliases to their canonical collector identifier.
var CanonicalMapping = map[string]string{
	TypeICMPSweep:         CollectorIDICMP,
	"icmp":                CollectorIDICMP,
	TypeARPSweep:          CollectorIDARP,
	"arp":                 CollectorIDARP,
	TypeReverseDNS:        CollectorIDReverseDNS,
	"reversedns":          CollectorIDReverseDNS,
	"reverse-dns":         CollectorIDReverseDNS,
	TypeSNMP:              CollectorIDSNMP,
	TypeMDNS:              CollectorIDMDNS,
	TypeProxmox:           TypeProxmox,
	TypeDocker:            TypeDocker,
	TypeUniFi:             TypeUniFi,
}

// ImplementedStatus maps canonical collector types and aliases to whether they are implemented in this wave.
var ImplementedStatus = map[string]bool{
	CollectorIDICMP:       true,
	CollectorIDARP:        true,
	CollectorIDReverseDNS: true,
	CollectorIDSNMP:       true,
	CollectorIDMDNS:       true,
	TypeICMPSweep:         true,
	TypeARPSweep:          true,
	TypeReverseDNS:        true,
	"reverse-dns":         true,
	TypeMDNS:              true,
	TypeProxmox:           false, // Not implemented in this wave
	TypeDocker:            false, // Not implemented in this wave
	TypeUniFi:             false, // Not implemented in this wave
}

// CanonicalType resolves a raw collector name/type string to its canonical representation.
func CanonicalType(raw string) string {
	normalized := strings.ToLower(strings.TrimSpace(raw))
	if canonical, ok := CanonicalMapping[normalized]; ok {
		return canonical
	}
	return normalized
}

// IsImplemented returns whether the collector type is implemented in this wave.
func IsImplemented(raw string) bool {
	normalized := strings.ToLower(strings.TrimSpace(raw))
	if implemented, ok := ImplementedStatus[normalized]; ok {
		return implemented
	}
	canonical := CanonicalType(normalized)
	return ImplementedStatus[canonical]
}

// Registry provides thread-safe collector registration and resolution.
type Registry struct {
	mu         sync.RWMutex
	collectors map[string]Collector
}

// NewRegistry creates a new empty Registry instance.
func NewRegistry() *Registry {
	return &Registry{
		collectors: make(map[string]Collector),
	}
}

// DefaultRegistry creates and returns an initialized Registry instance.
func DefaultRegistry() *Registry {
	return NewRegistry()
}

// Register registers a concrete Collector under its ID and canonical aliases.
func (r *Registry) Register(c Collector) error {
	if c == nil {
		return ErrNilCollector
	}
	r.mu.Lock()
	defer r.mu.Unlock()

	id := strings.ToLower(strings.TrimSpace(c.ID()))
	if id == "" {
		return ErrEmptyCollectorID
	}

	r.collectors[id] = c
	canonical := CanonicalType(id)
	if canonical != "" {
		r.collectors[canonical] = c
	}
	return nil
}

// Get retrieves a registered Collector by ID or canonical type.
func (r *Registry) Get(idOrType string) (Collector, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	normalized := strings.ToLower(strings.TrimSpace(idOrType))
	if c, ok := r.collectors[normalized]; ok {
		return c, true
	}
	canonical := CanonicalType(normalized)
	if c, ok := r.collectors[canonical]; ok {
		return c, true
	}
	return nil, false
}

// List returns a deduplicated list of all registered collectors.
func (r *Registry) List() []Collector {
	r.mu.RLock()
	defer r.mu.RUnlock()

	seen := make(map[Collector]bool)
	var list []Collector
	for _, c := range r.collectors {
		if !seen[c] {
			seen[c] = true
			list = append(list, c)
		}
	}
	return list
}
