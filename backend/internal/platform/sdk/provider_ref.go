package sdk

import (
	"errors"
	"fmt"
	"strings"
	"unicode"
)

// ErrMalformedProviderRef indicates that a provider reference key does not follow
// the canonical "provider:scope:kind:native_id" form.
var ErrMalformedProviderRef = errors.New("malformed provider reference")

// providerRefSegments is the number of segments in a canonical provider reference key.
const providerRefSegments = 4

// ProviderRef represents the unique external identity of an entity managed by a provider.
//
// Workloads such as stopped containers and offline VMs without a guest agent have neither
// a MAC nor an IP address, so this reference is the only stable identity the discovery
// engine can match them by (see ADR-013 and CONTEXT.md guideline #169).
type ProviderRef struct {
	Provider string `json:"provider"`  // "proxmox" | "docker" | "unifi"
	Scope    string `json:"scope"`     // "pve-cluster1" | "engine-node1"
	Kind     string `json:"kind"`      // "node" | "qemu" | "lxc" | "engine" | "container"
	NativeID string `json:"native_id"` // "101" | "sha256:..."
}

// IsZero reports whether the reference lacks the segments required to identify a workload.
// Scope and Kind alone carry no identity, so a reference is only usable once both the
// originating provider and its native identifier are known.
func (r ProviderRef) IsZero() bool {
	return r.Provider == "" || r.NativeID == ""
}

// Key returns the canonical 4-segment string representation of the provider identity.
//
// Provider, Scope and Kind are sanitized so they can never introduce extra separators;
// NativeID is the trailing segment and keeps its colons intact, which preserves values
// such as MAC addresses and image digests. Zero references yield an empty key.
func (r ProviderRef) Key() string {
	if r.IsZero() {
		return ""
	}
	return fmt.Sprintf(
		"%s:%s:%s:%s",
		SanitizeRefSegment(r.Provider),
		SanitizeRefSegment(r.Scope),
		SanitizeRefSegment(r.Kind),
		r.NativeID,
	)
}

// ParseProviderRef reconstructs a ProviderRef from its canonical key representation.
// The key is split into at most four segments so that colons inside the native identifier
// are preserved.
func ParseProviderRef(key string) (ProviderRef, error) {
	parts := strings.SplitN(key, ":", providerRefSegments)
	if len(parts) != providerRefSegments {
		return ProviderRef{}, fmt.Errorf("%w: %q expects %d segments", ErrMalformedProviderRef, key, providerRefSegments)
	}

	ref := ProviderRef{
		Provider: parts[0],
		Scope:    parts[1],
		Kind:     parts[2],
		NativeID: parts[3],
	}
	if ref.IsZero() {
		return ProviderRef{}, fmt.Errorf("%w: %q misses provider or native id", ErrMalformedProviderRef, key)
	}
	return ref, nil
}

// SanitizeRefSegment collapses separators and whitespace into single hyphens so a segment
// can never split a canonical provider reference key into more parts than it should have.
func SanitizeRefSegment(s string) string {
	var b strings.Builder
	b.Grow(len(s))

	pendingHyphen := false
	for _, r := range s {
		if r == ':' || r == '/' || r == '-' || unicode.IsSpace(r) {
			pendingHyphen = b.Len() > 0
			continue
		}
		if pendingHyphen {
			b.WriteRune('-')
			pendingHyphen = false
		}
		b.WriteRune(r)
	}
	return b.String()
}
