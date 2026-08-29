package mdns_test

import (
	"context"
	"errors"
	"net"
	"os"
	"testing"
	"time"

	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/collectors/mdns"
	"golang.org/x/net/dns/dnsmessage"
)

type mockMDNSClient struct {
	observations []collectors.RawObservation
	err          error
	delay        time.Duration
}

func (m *mockMDNSClient) Query(ctx context.Context, _ []string, _ time.Duration) ([]collectors.RawObservation, error) {
	if m.delay > 0 {
		select {
		case <-time.After(m.delay):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if m.err != nil {
		return nil, m.err
	}
	return m.observations, nil
}

func TestMDNSCollector_Basics(t *testing.T) {
	col := mdns.NewMDNSCollector(nil)
	if col.ID() != "mdns" {
		t.Errorf("expected ID 'mdns', got %q", col.ID())
	}
	if col.Name() != "mDNS / Bonjour Service Discovery" {
		t.Errorf("expected Name 'mDNS / Bonjour Service Discovery', got %q", col.Name())
	}
}

func TestMDNSCollector_SubnetFiltering(t *testing.T) {
	client := &mockMDNSClient{
		observations: []collectors.RawObservation{
			{IPAddress: "192.168.1.10", Hostname: "host-1", ProtocolSource: "mdns"},
			{IPAddress: "192.168.1.20", Hostname: "host-2", ProtocolSource: "mdns"},
			{IPAddress: "10.0.0.1", Hostname: "host-outside-1", ProtocolSource: "mdns"},
			{IPAddress: "192.168.2.50", Hostname: "host-outside-2", ProtocolSource: "mdns"},
			{IPAddress: "not-an-ip", Hostname: "invalid-ip", ProtocolSource: "mdns"},
		},
	}

	col := mdns.NewMDNSCollector(client)
	ctx := context.Background()

	results, err := col.Collect(ctx, collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(results) != 2 {
		t.Fatalf("expected 2 filtered observations, got %d", len(results))
	}

	foundIPs := make(map[string]bool)
	for _, r := range results {
		foundIPs[r.IPAddress] = true
		if r.ConfidenceScore != 70 {
			t.Errorf("expected ConfidenceScore 70, got %d", r.ConfidenceScore)
		}
		if r.ProtocolSource != "mdns" {
			t.Errorf("expected ProtocolSource 'mdns', got %q", r.ProtocolSource)
		}
	}

	if !foundIPs["192.168.1.10"] || !foundIPs["192.168.1.20"] {
		t.Errorf("expected IPs 192.168.1.10 and 192.168.1.20, got %v", foundIPs)
	}
}

func TestMDNSCollector_CIDRBounds(t *testing.T) {
	client := &mockMDNSClient{}
	col := mdns.NewMDNSCollector(client)
	ctx := context.Background()

	// Invalid CIDR
	_, err := col.Collect(ctx, collectors.DiscoveryTarget{CIDR: "invalid-cidr"})
	if err == nil {
		t.Error("expected error for invalid CIDR, got nil")
	}

	// Oversized CIDR /8 (Guideline #81: max /16 allowed)
	_, err = col.Collect(ctx, collectors.DiscoveryTarget{CIDR: "10.0.0.0/8"})
	if err == nil {
		t.Error("expected error for /8 CIDR, got nil")
	}
	if !errors.Is(err, collectors.ErrCIDRTooLarge) {
		t.Errorf("expected ErrCIDRTooLarge, got %v", err)
	}
}

func TestMDNSCollector_ContextCancellation(t *testing.T) {
	client := &mockMDNSClient{
		delay: 500 * time.Millisecond,
	}
	col := mdns.NewMDNSCollector(client)

	// Pre-cancelled context
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	_, err := col.Collect(ctx, collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"})
	if err == nil {
		t.Fatal("expected error on pre-cancelled context, got nil")
	}

	// Context cancelled during collection
	ctx2, cancel2 := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel2()

	_, err2 := col.Collect(ctx2, collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"})
	if err2 == nil {
		t.Fatal("expected error on context timeout during query, got nil")
	}
}

func TestMDNSCollector_TimeoutAndEmptyResponses(t *testing.T) {
	// Zero responses received (e.g. silent network)
	client := &mockMDNSClient{
		observations: []collectors.RawObservation{},
	}
	col := mdns.NewMDNSCollector(client)
	ctx := context.Background()

	results, err := col.Collect(ctx, collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"})
	if err != nil {
		t.Fatalf("expected nil error on empty response, got %v", err)
	}
	if results == nil || len(results) != 0 {
		t.Errorf("expected empty slice, got %v", results)
	}

	// Network error on client (graceful degradation)
	errClient := &mockMDNSClient{
		err: errors.New("network socket permission denied"),
	}
	colErr := mdns.NewMDNSCollector(errClient)

	resultsErr, err := colErr.Collect(ctx, collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"})
	if err != nil {
		t.Fatalf("expected nil error on non-fatal network error, got %v", err)
	}
	if len(resultsErr) != 0 {
		t.Errorf("expected 0 results on network error, got %d", len(resultsErr))
	}
}

func TestMDNSCollector_DeduplicationAndMerge(t *testing.T) {
	client := &mockMDNSClient{
		observations: []collectors.RawObservation{
			{
				IPAddress:      "192.168.1.50",
				Hostname:       "my-macbook",
				Vendor:         "Apple",
				OS:             "macOS",
				ProtocolSource: "mdns",
				RawMetadata: map[string]interface{}{
					"services": []string{"_workstation._tcp"},
					"txt":      map[string]string{"model": "MacBookPro18,1"},
				},
			},
			{
				IPAddress:      "192.168.1.50",
				Hostname:       "",
				Vendor:         "",
				ProtocolSource: "mdns",
				RawMetadata: map[string]interface{}{
					"services": []string{"_http._tcp", "_airplay._tcp"},
					"txt":      map[string]string{"osx": "14.5"},
				},
			},
		},
	}

	col := mdns.NewMDNSCollector(client)
	ctx := context.Background()

	results, err := col.Collect(ctx, collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(results) != 1 {
		t.Fatalf("expected 1 merged observation for IP 192.168.1.50, got %d", len(results))
	}

	res := results[0]
	if res.IPAddress != "192.168.1.50" {
		t.Errorf("expected IP 192.168.1.50, got %s", res.IPAddress)
	}
	if res.Hostname != "my-macbook" {
		t.Errorf("expected Hostname 'my-macbook', got %s", res.Hostname)
	}
	if res.Vendor != "Apple" {
		t.Errorf("expected Vendor 'Apple', got %s", res.Vendor)
	}
	if res.OS != "macOS" {
		t.Errorf("expected OS 'macOS', got %s", res.OS)
	}

	services, _ := res.RawMetadata["services"].([]string)
	if len(services) != 3 {
		t.Errorf("expected 3 merged services, got %v", services)
	}

	txt, _ := res.RawMetadata["txt"].(map[string]string)
	if txt["model"] != "MacBookPro18,1" || txt["osx"] != "14.5" {
		t.Errorf("expected merged txt records, got %v", txt)
	}
}

func TestParseMDNSPacket_WorkstationAndAirPlay(t *testing.T) {
	// Build mock DNS response packet for a MacBook Pro
	msg := dnsmessage.Message{
		Header: dnsmessage.Header{
			Response: true,
		},
		Answers: []dnsmessage.Resource{
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("_workstation._tcp.local."),
					Type:  dnsmessage.TypePTR,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.PTRResource{
					PTR: dnsmessage.MustNewName("MacBook-Pro._workstation._tcp.local."),
				},
			},
		},
		Additionals: []dnsmessage.Resource{
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("MacBook-Pro._workstation._tcp.local."),
					Type:  dnsmessage.TypeSRV,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.SRVResource{
					Target: dnsmessage.MustNewName("MacBook-Pro.local."),
					Port:   9,
				},
			},
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("MacBook-Pro._workstation._tcp.local."),
					Type:  dnsmessage.TypeTXT,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.TXTResource{
					TXT: []string{"model=MacBookPro18,1", "osx=14.5"},
				},
			},
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("MacBook-Pro.local."),
					Type:  dnsmessage.TypeA,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.AResource{
					A: [4]byte{192, 168, 1, 45},
				},
			},
		},
	}

	packed, err := msg.Pack()
	if err != nil {
		t.Fatalf("failed to pack mock DNS message: %v", err)
	}

	observations := mdns.ParseMDNSPacket(packed, net.ParseIP("192.168.1.45"))
	if len(observations) != 1 {
		t.Fatalf("expected 1 observation, got %d", len(observations))
	}

	obs := observations[0]
	if obs.IPAddress != "192.168.1.45" {
		t.Errorf("expected IP 192.168.1.45, got %s", obs.IPAddress)
	}
	if obs.Hostname != "MacBook-Pro" {
		t.Errorf("expected Hostname 'MacBook-Pro', got %s", obs.Hostname)
	}
	if obs.Vendor != "Apple" {
		t.Errorf("expected Vendor 'Apple', got %s", obs.Vendor)
	}
	if obs.OS != "macOS" {
		t.Errorf("expected OS 'macOS', got %s", obs.OS)
	}
	if obs.ConfidenceScore != 70 {
		t.Errorf("expected ConfidenceScore 70, got %d", obs.ConfidenceScore)
	}
	if obs.ProtocolSource != "mdns" {
		t.Errorf("expected ProtocolSource 'mdns', got %s", obs.ProtocolSource)
	}
	if port, ok := obs.RawMetadata["port"].(int); !ok || port != 9 {
		t.Errorf("expected port 9, got %v", obs.RawMetadata["port"])
	}
}

func TestParseMDNSPacket_Chromecast(t *testing.T) {
	// Build mock DNS response for Google Chromecast
	msg := dnsmessage.Message{
		Header: dnsmessage.Header{
			Response: true,
		},
		Answers: []dnsmessage.Resource{
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("_googlecast._tcp.local."),
					Type:  dnsmessage.TypePTR,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.PTRResource{
					PTR: dnsmessage.MustNewName("Living-Room-TV._googlecast._tcp.local."),
				},
			},
		},
		Additionals: []dnsmessage.Resource{
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("Living-Room-TV._googlecast._tcp.local."),
					Type:  dnsmessage.TypeSRV,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.SRVResource{
					Target: dnsmessage.MustNewName("chromecast-ultra.local."),
					Port:   8009,
				},
			},
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("Living-Room-TV._googlecast._tcp.local."),
					Type:  dnsmessage.TypeTXT,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.TXTResource{
					TXT: []string{"fn=Living Room TV", "md=Chromecast Ultra"},
				},
			},
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("chromecast-ultra.local."),
					Type:  dnsmessage.TypeA,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.AResource{
					A: [4]byte{192, 168, 1, 88},
				},
			},
		},
	}

	packed, err := msg.Pack()
	if err != nil {
		t.Fatalf("failed to pack mock message: %v", err)
	}

	observations := mdns.ParseMDNSPacket(packed, net.ParseIP("192.168.1.88"))
	if len(observations) != 1 {
		t.Fatalf("expected 1 observation, got %d", len(observations))
	}

	obs := observations[0]
	if obs.IPAddress != "192.168.1.88" {
		t.Errorf("expected IP 192.168.1.88, got %s", obs.IPAddress)
	}
	if obs.Hostname != "chromecast-ultra" {
		t.Errorf("expected Hostname 'chromecast-ultra', got %s", obs.Hostname)
	}
	if obs.Vendor != "Google" {
		t.Errorf("expected Vendor 'Google', got %s", obs.Vendor)
	}
	if port, ok := obs.RawMetadata["port"].(int); !ok || port != 8009 {
		t.Errorf("expected port 8009, got %v", obs.RawMetadata["port"])
	}
}

func TestParseMDNSPacket_StandaloneARecord(t *testing.T) {
	msg := dnsmessage.Message{
		Header: dnsmessage.Header{
			Response: true,
		},
		Answers: []dnsmessage.Resource{
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("synology-nas.local."),
					Type:  dnsmessage.TypeA,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.AResource{
					A: [4]byte{192, 168, 1, 120},
				},
			},
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("synology-nas.local."),
					Type:  dnsmessage.TypeTXT,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.TXTResource{
					TXT: []string{"model=Synology DS920+", "os=Linux"},
				},
			},
		},
	}

	packed, err := msg.Pack()
	if err != nil {
		t.Fatalf("failed to pack mock message: %v", err)
	}

	observations := mdns.ParseMDNSPacket(packed, net.ParseIP("192.168.1.120"))
	if len(observations) != 1 {
		t.Fatalf("expected 1 observation, got %d", len(observations))
	}

	obs := observations[0]
	if obs.IPAddress != "192.168.1.120" {
		t.Errorf("expected IP 192.168.1.120, got %s", obs.IPAddress)
	}
	if obs.Hostname != "synology-nas" {
		t.Errorf("expected Hostname 'synology-nas', got %s", obs.Hostname)
	}
	if obs.Vendor != "Synology" {
		t.Errorf("expected Vendor 'Synology', got %s", obs.Vendor)
	}
	if obs.OS != "Linux" {
		t.Errorf("expected OS 'Linux', got %s", obs.OS)
	}
}

func TestCleanMDNSHostname(t *testing.T) {
	cases := []struct {
		input    string
		expected string
	}{
		{"my-macbook.local.", "my-macbook"},
		{"my-macbook.local", "my-macbook"},
		{"my-macbook.", "my-macbook"},
		{"My Printer._printer._tcp.local.", "My Printer"},
		{"Canon TS8300._ipp._tcp.local", "Canon TS8300"},
		{"Living Room TV._googlecast._tcp.local.", "Living Room TV"},
		{"device._workstation._tcp", "device"},
		{"  server.local.  ", "server"},
	}

	for _, c := range cases {
		got := mdns.CleanMDNSHostname(c.input)
		if got != c.expected {
			t.Errorf("CleanMDNSHostname(%q) = %q, expected %q", c.input, got, c.expected)
		}
	}
}

func TestExtractVendorAndOS(t *testing.T) {
	// Canon printer
	v, _ := mdns.ExtractVendorAndOS("_printer._tcp", map[string]string{"model": "Canon MG7500"})
	if v != "Canon" {
		t.Errorf("expected Canon, got %s", v)
	}

	// Raspberry Pi
	v, _ = mdns.ExtractVendorAndOS("_workstation._tcp", map[string]string{"model": "Raspberry Pi 4"})
	if v != "Raspberry Pi Foundation" {
		t.Errorf("expected Raspberry Pi Foundation, got %s", v)
	}

	// Service fallback for airplay
	v, _ = mdns.ExtractVendorAndOS("_airplay._tcp", nil)
	if v != "Apple" {
		t.Errorf("expected Apple for airplay service, got %s", v)
	}

	// Service fallback for sonos
	v, _ = mdns.ExtractVendorAndOS("_sonos._tcp", nil)
	if v != "Sonos" {
		t.Errorf("expected Sonos for sonos service, got %s", v)
	}

	// Windows OS detection
	_, osName := mdns.ExtractVendorAndOS("_smb._tcp", map[string]string{"os": "Windows 11"})
	if osName != "Windows" {
		t.Errorf("expected Windows, got %s", osName)
	}
}

func TestBuildMDNSQuery(t *testing.T) {
	packet, err := mdns.BuildMDNSQuery("_workstation._tcp.local.")
	if err != nil {
		t.Fatalf("unexpected error building query: %v", err)
	}
	if len(packet) == 0 {
		t.Fatal("expected non-empty query packet")
	}

	var msg dnsmessage.Message
	if err := msg.Unpack(packet); err != nil {
		t.Fatalf("failed to unpack crafted query: %v", err)
	}
	if len(msg.Questions) != 1 {
		t.Fatalf("expected 1 question, got %d", len(msg.Questions))
	}
	if msg.Questions[0].Name.String() != "_workstation._tcp.local." {
		t.Errorf("expected question name '_workstation._tcp.local.', got %s", msg.Questions[0].Name.String())
	}
}

type mockPacketConn struct {
	packets [][]byte
	index   int
}

func (m *mockPacketConn) SetReadDeadline(_ time.Time) error  { return nil }
func (m *mockPacketConn) SetWriteDeadline(_ time.Time) error { return nil }
func (m *mockPacketConn) WriteTo(b []byte, _ net.Addr) (int, error) {
	return len(b), nil
}
func (m *mockPacketConn) ReadFrom(b []byte) (int, net.Addr, error) {
	if m.index >= len(m.packets) {
		return 0, nil, os.ErrDeadlineExceeded
	}
	data := m.packets[m.index]
	m.index++
	n := copy(b, data)
	return n, &net.UDPAddr{IP: net.ParseIP("192.168.1.99"), Port: 5353}, nil
}
func (m *mockPacketConn) Close() error { return nil }

func TestDefaultMDNSClient_Query(t *testing.T) {
	// Craft a simple response packet
	msg := dnsmessage.Message{
		Header: dnsmessage.Header{Response: true},
		Answers: []dnsmessage.Resource{
			{
				Header: dnsmessage.ResourceHeader{
					Name:  dnsmessage.MustNewName("test-host.local."),
					Type:  dnsmessage.TypeA,
					Class: dnsmessage.ClassINET,
				},
				Body: &dnsmessage.AResource{
					A: [4]byte{192, 168, 1, 99},
				},
			},
		},
	}
	packed, _ := msg.Pack()

	mockConn := &mockPacketConn{
		packets: [][]byte{packed},
	}

	client := mdns.NewDefaultMDNSClientWithConn(func(_ string, laddr *net.UDPAddr) (mdns.PacketConn, error) {
		return mockConn, nil
	})

	ctx := context.Background()
	obs, err := client.Query(ctx, []string{"_workstation._tcp.local."}, 100*time.Millisecond)
	if err != nil {
		t.Fatalf("unexpected query error: %v", err)
	}
	if len(obs) != 1 {
		t.Fatalf("expected 1 observation, got %d", len(obs))
	}
	if obs[0].IPAddress != "192.168.1.99" {
		t.Errorf("expected IP 192.168.1.99, got %s", obs[0].IPAddress)
	}
	if obs[0].Hostname != "test-host" {
		t.Errorf("expected Hostname 'test-host', got %s", obs[0].Hostname)
	}
}
