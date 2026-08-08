package collectors_test

import (
	"context"
	"errors"
	"testing"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
)


// fakeSNMPClient implements collectors.SNMPClient for testing.
type fakeSNMPClient struct {
	responses map[string]map[string]interface{}
	errs      map[string]error
}

func (f *fakeSNMPClient) GetOIDs(_ context.Context, targetIP string, cred collectors.SNMPCredential, _ []string) (map[string]interface{}, error) {
	key := targetIP + ":" + cred.Community
	if cred.Version == "v3" {
		key = targetIP + ":" + cred.SecName
	}

	if err, ok := f.errs[key]; ok {
		return nil, err
	}

	if res, ok := f.responses[key]; ok {
		return res, nil
	}

	return nil, errors.New("snmp request timeout")
}

// fakeCredentialResolver implements collectors.CredentialResolver for testing.
type fakeCredentialResolver struct {
	creds map[string][]collectors.SNMPCredential
	err   error
}

func (f *fakeCredentialResolver) ResolveSNMPCredentials(_ context.Context, credentialSetID *string) ([]collectors.SNMPCredential, error) {
	if f.err != nil {
		return nil, f.err
	}
	if credentialSetID == nil {
		return []collectors.SNMPCredential{{ID: "default", Version: "v2c", Community: "public"}}, nil
	}
	if creds, ok := f.creds[*credentialSetID]; ok {
		return creds, nil
	}
	return []collectors.SNMPCredential{{ID: "default", Version: "v2c", Community: "public"}}, nil
}

func TestSNMPCollector_IDAndName(t *testing.T) {
	c := collectors.NewSNMPCollector(&fakeSNMPClient{}, &fakeCredentialResolver{})
	if c.ID() != "snmp" {
		t.Errorf("expected ID 'snmp', got %q", c.ID())
	}
	if c.Name() != "SNMP MIB Collector" {
		t.Errorf("expected Name 'SNMP MIB Collector', got %q", c.Name())
	}
}

func TestSNMPCollector_Collect(t *testing.T) {
	t.Run("Returns observations for SNMP responsive hosts", func(t *testing.T) {
		client := &fakeSNMPClient{
			responses: map[string]map[string]interface{}{
				"192.168.1.1:public": {
					"sysName":        "core-switch.local",
					"sysDescr":      "Cisco IOS Software, C3560 Software (C3560-IPBASEK9-M)",
					"sysObjectID":   "1.3.6.1.4.1.9.1.563",
					"ifPhysAddress": []byte{0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E},
				},
				"192.168.1.2:secret-v2": {
					"sysName":        "router-mikrotik",
					"sysDescr":      "RouterOS RB3011UiAS",
					"sysObjectID":   "1.3.6.1.4.1.14988.1",
					"ifPhysAddress": "00:0c:42:11:22:33",
				},
			},
		}

		credSetID := "set-1"
		resolver := &fakeCredentialResolver{
			creds: map[string][]collectors.SNMPCredential{
				"set-1": {
					{ID: "cred-1", Version: "v2c", Community: "wrong-comm"},
					{ID: "cred-2", Version: "v2c", Community: "secret-v2"},
					{ID: "cred-3", Version: "v2c", Community: "public"},
				},
			},
		}

		c := collectors.NewSNMPCollector(client, resolver)
		target := collectors.DiscoveryTarget{
			CIDR:             "192.168.1.0/30",
			SubnetID:         "sub-1",
			CredentialSetID: &credSetID,
		}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		if len(obs) != 2 {
			t.Fatalf("expected 2 observations for responsive hosts, got %d", len(obs))
		}

		found := make(map[string]collectors.RawObservation)
		for _, o := range obs {
			found[o.IPAddress] = o
			if o.ProtocolSource != "snmp" {
				t.Errorf("expected ProtocolSource 'snmp', got %q", o.ProtocolSource)
			}
			if o.ConfidenceScore != 80 {
				t.Errorf("expected ConfidenceScore 80, got %d", o.ConfidenceScore)
			}
			if o.ObservedAt.IsZero() {
				t.Error("ObservedAt should not be zero")
			}
		}

		// Verify core switch observation
		sw, ok := found["192.168.1.1"]
		if !ok {
			t.Fatal("missing observation for 192.168.1.1")
		}
		if sw.Hostname != "core-switch.local" {
			t.Errorf("expected Hostname 'core-switch.local', got %q", sw.Hostname)
		}
		if sw.Vendor != "Cisco" {
			t.Errorf("expected Vendor 'Cisco', got %q", sw.Vendor)
		}
		if sw.MACAddress != "00:1a:2b:3c:4d:5e" {
			t.Errorf("expected MACAddress '00:1a:2b:3c:4d:5e', got %q", sw.MACAddress)
		}

		// Verify Mikrotik observation
		mk, ok := found["192.168.1.2"]
		if !ok {
			t.Fatal("missing observation for 192.168.1.2")
		}
		if mk.Hostname != "router-mikrotik" {
			t.Errorf("expected Hostname 'router-mikrotik', got %q", mk.Hostname)
		}
		if mk.Vendor != "Mikrotik" {
			t.Errorf("expected Vendor 'Mikrotik', got %q", mk.Vendor)
		}
	})

	t.Run("Default credential resolver falls back to public when credentialSetID is nil", func(t *testing.T) {
		client := &fakeSNMPClient{
			responses: map[string]map[string]interface{}{
				"192.168.1.1:public": {
					"sysName":  "homelab-nas",
					"sysDescr": "Synology DiskStation DSM 7.2",
				},
			},
		}

		c := collectors.NewSNMPCollector(client, nil) // nil resolver uses default
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.1/32", SubnetID: "sub-1"}

		obs, err := c.Collect(context.Background(), target)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(obs) != 1 {
			t.Fatalf("expected 1 observation, got %d", len(obs))
		}
		if obs[0].Vendor != "Synology" {
			t.Errorf("expected Vendor 'Synology', got %q", obs[0].Vendor)
		}
	})

	t.Run("Rejects oversized CIDR larger than /16", func(t *testing.T) {
		c := collectors.NewSNMPCollector(&fakeSNMPClient{}, nil)
		target := collectors.DiscoveryTarget{CIDR: "10.0.0.0/8", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for oversized CIDR /8, got nil")
		}
		if !errors.Is(err, collectors.ErrCIDRTooLarge) {
			t.Errorf("expected ErrCIDRTooLarge, got %v", err)
		}
	})

	t.Run("Handles invalid CIDR gracefully", func(t *testing.T) {
		c := collectors.NewSNMPCollector(&fakeSNMPClient{}, nil)
		target := collectors.DiscoveryTarget{CIDR: "invalid-cidr", SubnetID: "sub-1"}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected error for invalid CIDR, got nil")
		}
	})

	t.Run("Respects context cancellation during iteration", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // cancel immediately

		c := collectors.NewSNMPCollector(&fakeSNMPClient{}, nil)
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}

		_, err := c.Collect(ctx, target)
		if err == nil {
			t.Fatal("expected context cancellation error, got nil")
		}
	})

	t.Run("Propagates resolver error", func(t *testing.T) {
		resolver := &fakeCredentialResolver{err: errors.New("vault locked")}
		c := collectors.NewSNMPCollector(&fakeSNMPClient{}, resolver)
		setID := "set-1"
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1", CredentialSetID: &setID}

		_, err := c.Collect(context.Background(), target)
		if err == nil {
			t.Fatal("expected resolver error, got nil")
		}
	})
}

func TestExtractVendorFromSysDescr(t *testing.T) {
	tests := []struct {
		descr    string
		objectID string
		expected string
	}{
		{"Cisco IOS Software, C3560", "", "Cisco"},
		{"RouterOS RB3011UiAS", "", "Mikrotik"},
		{"UniFi Switch 24-PoE", "", "Ubiquiti"},
		{"Synology DiskStation DSM 7.2", "", "Synology"},
		{"Linux debian-server 5.10.0", "", "Linux"},
		{"Hardware: Intel64 Family 6 Model 158 Stepping 10 AT/AT COMPATIBLE - Software: Windows Version 10.0", "", "Microsoft"},
		{"Unknown device descr", "1.3.6.1.4.1.9.1.1", "Cisco"},     // OID Enterprise 9 = Cisco
		{"Unknown device descr", "1.3.6.1.4.1.14988.1", "Mikrotik"}, // OID Enterprise 14988 = Mikrotik
		{"Generic Device", "1.3.6.1.4.1.99999.1", "Generic"},
	}

	for _, tt := range tests {
		v := collectors.ExtractVendor(tt.descr, tt.objectID)
		if v != tt.expected {
			t.Errorf("ExtractVendor(%q, %q): expected %q, got %q", tt.descr, tt.objectID, tt.expected, v)
		}
	}
}


func TestParseSNMPMAC(t *testing.T) {
	tests := []struct {
		input    interface{}
		expected string
	}{
		{[]byte{0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E}, "00:1a:2b:3c:4d:5e"},
		{"00:1a:2b:3c:4d:5e", "00:1a:2b:3c:4d:5e"},
		{"00-1A-2B-3C-4D-5E", "00:1a:2b:3c:4d:5e"},
		{nil, ""},
		{12345, ""},
	}

	for _, tt := range tests {
		got := collectors.ParseSNMPMAC(tt.input)
		if got != tt.expected {
			t.Errorf("ParseSNMPMAC(%v): expected %q, got %q", tt.input, tt.expected, got)
		}
	}
}
