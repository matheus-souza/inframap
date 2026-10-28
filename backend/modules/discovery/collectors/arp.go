package collectors

import (
	"context"
	"fmt"
	"net/netip"
	"time"
)

// ARPEntry represents a single entry from the OS ARP table.
type ARPEntry struct {
	IPAddress  string
	MACAddress string
	Interface  string
	Flags      string
}

// ARPTableReader abstracts OS-level ARP table access for testability.
type ARPTableReader interface {
	ReadEntries(ctx context.Context) ([]ARPEntry, error)
}

// ARPCollector reads the OS ARP table and returns observations for IPs within the target
// blocks: the union of target.Prefixes when set, otherwise target.CIDR.
type ARPCollector struct {
	reader ARPTableReader
}

// NewARPCollector creates a new ARPCollector with the given ARPTableReader.
func NewARPCollector(reader ARPTableReader) *ARPCollector {
	return &ARPCollector{reader: reader}
}

// ID returns the unique collector identifier.
func (c *ARPCollector) ID() string { return "arp" }

// Name returns the human-readable collector name.
func (c *ARPCollector) Name() string { return "ARP Table Reader" }

// Collect reads the ARP table once and returns observations for entries within the target
// blocks (see TargetPrefixes).
func (c *ARPCollector) Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error) {
	if c.reader == nil {
		return nil, nil
	}

	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("arp collector: %w", err)
	}

	prefixes, err := TargetPrefixes(target)
	if err != nil {
		return nil, fmt.Errorf("arp collector: %w", err)
	}

	entries, err := c.reader.ReadEntries(ctx)
	if err != nil {
		return nil, fmt.Errorf("arp collector: failed to read ARP table: %w", err)
	}

	now := time.Now()
	var observations []RawObservation

	for _, entry := range entries {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("arp collector: %w", err)
		}

		addr, parseErr := netip.ParseAddr(entry.IPAddress)
		if parseErr != nil {
			continue // skip unparseable IPs
		}

		if !PrefixesContain(prefixes, addr) {
			continue // skip entries outside every target block
		}

		observations = append(observations, RawObservation{
			IPAddress:       entry.IPAddress,
			MACAddress:      entry.MACAddress,
			ProtocolSource:  "arp",
			ConfidenceScore: 40,
			ObservedAt:      now,
		})
	}

	return observations, nil
}
