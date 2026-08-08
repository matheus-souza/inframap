package collectors

import (
	"context"
	"fmt"
	"time"
)

// DNSResolver abstracts DNS PTR lookups for testability.
type DNSResolver interface {
	LookupAddr(ctx context.Context, addr string) ([]string, error)
}

// ReverseDNSCollector performs PTR lookups to obtain canonical hostnames for discovered IPs.
type ReverseDNSCollector struct {
	resolver DNSResolver
}

// NewReverseDNSCollector creates a new ReverseDNSCollector with the given DNSResolver.
func NewReverseDNSCollector(resolver DNSResolver) *ReverseDNSCollector {
	return &ReverseDNSCollector{resolver: resolver}
}

// ID returns the unique collector identifier.
func (c *ReverseDNSCollector) ID() string { return "reverse-dns" }

// Name returns the human-readable collector name.
func (c *ReverseDNSCollector) Name() string { return "Reverse DNS Resolver" }

// Collect expands the target CIDR and performs PTR lookups for each IP.
// Only IPs with valid PTR records produce observations.
func (c *ReverseDNSCollector) Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error) {
	if c.resolver == nil {
		return nil, nil
	}

	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("reverse-dns collector: %w", err)
	}


	addrs, err := ExpandCIDR(target.CIDR)
	if err != nil {
		return nil, fmt.Errorf("reverse-dns collector: %w", err)
	}

	now := time.Now()
	var observations []RawObservation

	for _, addr := range addrs {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("reverse-dns collector: %w", err)
		}

		names, lookupErr := c.resolver.LookupAddr(ctx, addr.String())
		if lookupErr != nil || len(names) == 0 {
			continue // no PTR record for this IP
		}

		observations = append(observations, RawObservation{
			IPAddress:       addr.String(),
			Hostname:        names[0],
			ProtocolSource:  "reverse-dns",
			ConfidenceScore: 30,
			ObservedAt:      now,
		})
	}

	return observations, nil
}
