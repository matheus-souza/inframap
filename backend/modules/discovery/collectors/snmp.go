package collectors

import (
	"context"
	"fmt"
	"net"
	"strings"
	"sync"
	"time"
)

// SNMPCredential defines parameters for authenticating against SNMP agents (v2c or v3).
type SNMPCredential struct {
	ID           string
	Version      string // "v2c" or "v3"
	Community    string // for v2c
	SecName      string // for v3
	SecLevel     string // "noAuthNoPriv", "authNoPriv", "authPriv"
	AuthProtocol string // "MD5", "SHA"
	AuthPassword string
	PrivProtocol string // "DES", "AES"
	PrivPassword string
}

// CredentialResolver resolves SNMP credentials for a given CredentialSet ID.
type CredentialResolver interface {
	ResolveSNMPCredentials(ctx context.Context, credentialSetID *string) ([]SNMPCredential, error)
}

// DefaultCredentialResolver returns standard default fallback credentials (public community v2c).
type DefaultCredentialResolver struct{}

// ResolveSNMPCredentials implements CredentialResolver for default public fallback.
func (d *DefaultCredentialResolver) ResolveSNMPCredentials(_ context.Context, _ *string) ([]SNMPCredential, error) {
	return []SNMPCredential{
		{ID: "default-public", Version: "v2c", Community: "public"},
	}, nil
}

// SNMPClient abstracts SNMP MIB queries for testability and protocol decoupling.
type SNMPClient interface {
	GetOIDs(ctx context.Context, targetIP string, cred SNMPCredential, oids []string) (map[string]interface{}, error)
}

// SNMPCollector queries standard SNMP MIB OIDs to discover hardware vendor, OS, and network interfaces.
type SNMPCollector struct {
	client   SNMPClient
	resolver CredentialResolver
}

// NewSNMPCollector constructs an SNMPCollector with the given SNMPClient and CredentialResolver.
// If resolver is nil, DefaultCredentialResolver is used.
func NewSNMPCollector(client SNMPClient, resolver CredentialResolver) *SNMPCollector {
	if resolver == nil {
		resolver = &DefaultCredentialResolver{}
	}
	return &SNMPCollector{
		client:   client,
		resolver: resolver,
	}
}

// ID returns the unique collector identifier.
func (c *SNMPCollector) ID() string { return "snmp" }

// Name returns the human-readable collector name.
func (c *SNMPCollector) Name() string { return "SNMP MIB Collector" }

// Standard OIDs queried by SNMPCollector
var snmpTargetOIDs = []string{
	"sysName",        // 1.3.6.1.2.1.1.5.0
	"sysDescr",       // 1.3.6.1.2.1.1.1.0
	"sysObjectID",    // 1.3.6.1.2.1.1.2.0
	"ifPhysAddress",  // 1.3.6.1.2.1.2.2.1.6
}

// Collect queries all host IPs within target CIDR using resolved credential sets.
func (c *SNMPCollector) Collect(ctx context.Context, target DiscoveryTarget) ([]RawObservation, error) {
	if c.client == nil {
		return nil, nil
	}

	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("snmp collector: %w", err)
	}


	addrs, err := ExpandCIDR(target.CIDR)
	if err != nil {
		return nil, fmt.Errorf("snmp collector: %w", err)
	}

	creds, err := c.resolver.ResolveSNMPCredentials(ctx, target.CredentialSetID)
	if err != nil {
		return nil, fmt.Errorf("snmp collector: failed to resolve credentials: %w", err)
	}

	now := time.Now()
	var observations []RawObservation
	var cacheMu sync.RWMutex
	cachedCreds := make(map[string]SNMPCredential)

	for _, addr := range addrs {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("snmp collector: %w", err)
		}

		ipStr := addr.String()

		// Check cached working credential first
		cacheMu.RLock()
		cached, hasCached := cachedCreds[ipStr]
		cacheMu.RUnlock()

		tryCreds := creds
		if hasCached {
			tryCreds = append([]SNMPCredential{cached}, creds...)
		}

		for _, cred := range tryCreds {
			data, getErr := c.client.GetOIDs(ctx, ipStr, cred, snmpTargetOIDs)
			if getErr != nil || len(data) == 0 {
				continue // try next credential
			}

			// Cache successful working credential
			cacheMu.Lock()
			cachedCreds[ipStr] = cred
			cacheMu.Unlock()

			sysName, _ := data["sysName"].(string)
			sysDescr, _ := data["sysDescr"].(string)
			sysObjectID, _ := data["sysObjectID"].(string)

			vendor := ExtractVendor(sysDescr, sysObjectID)
			mac := ParseSNMPMAC(data["ifPhysAddress"])

			rawMeta := map[string]interface{}{
				"sysName":       sysName,
				"sysDescr":      sysDescr,
				"sysObjectID":   sysObjectID,
				"credential_id": cred.ID,
			}

			observations = append(observations, RawObservation{
				IPAddress:       ipStr,
				MACAddress:      mac,
				Hostname:        sysName,
				Vendor:          vendor,
				OS:              sysDescr,
				ProtocolSource:  "snmp",
				ConfidenceScore: 80,
				RawMetadata:     rawMeta,
				ObservedAt:      now,
			})
			break // host responded successfully, stop trying remaining credentials
		}
	}

	return observations, nil
}

// ExtractVendor parses vendor identity from sysDescr string or sysObjectID enterprise OID.
func ExtractVendor(sysDescr, sysObjectID string) string {
	lower := strings.ToLower(sysDescr)

	switch {
	case strings.Contains(lower, "cisco"):
		return "Cisco"
	case strings.Contains(lower, "routeros") || strings.Contains(lower, "mikrotik"):
		return "Mikrotik"
	case strings.Contains(lower, "unifi") || strings.Contains(lower, "ubiquiti") || strings.Contains(lower, "edgeos"):
		return "Ubiquiti"
	case strings.Contains(lower, "synology") || strings.Contains(lower, "diskstation"):
		return "Synology"
	case strings.Contains(lower, "linux"):
		return "Linux"
	case strings.Contains(lower, "windows") || strings.Contains(lower, "microsoft"):
		return "Microsoft"
	}

	// Enterprise OID lookup fallback (1.3.6.1.4.1.<ENTERPRISE_ID>...)
	if strings.HasPrefix(sysObjectID, "1.3.6.1.4.1.") {
		parts := strings.Split(sysObjectID, ".")
		if len(parts) >= 7 {
			switch parts[6] {
			case "9":
				return "Cisco"
			case "14988":
				return "Mikrotik"
			case "41112":
				return "Ubiquiti"
			case "24681":
				return "Synology"
			case "311":
				return "Microsoft"
			case "8072":
				return "Linux"
			}
		}
	}

	return "Generic"
}

// ParseSNMPMAC standardizes various SNMP MAC address representations ([]byte or string) into canonical aa:bb:cc:dd:ee:ff format.
func ParseSNMPMAC(val interface{}) string {
	if val == nil {
		return ""
	}

	switch v := val.(type) {
	case []byte:
		if len(v) == 6 {
			return fmt.Sprintf("%02x:%02x:%02x:%02x:%02x:%02x", v[0], v[1], v[2], v[3], v[4], v[5])
		}
		return strings.ToLower(string(v))
	case string:
		if parsed, err := net.ParseMAC(v); err == nil {
			return strings.ToLower(parsed.String())
		}
		return strings.ToLower(v)
	}

	return ""
}
