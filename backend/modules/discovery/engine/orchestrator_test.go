package engine_test

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"runtime"
	"slices"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

type fakeCollector struct {
	id           string
	name         string
	observations []collectors.RawObservation
	err          error
	delay        time.Duration
}

func (f *fakeCollector) ID() string   { return f.id }
func (f *fakeCollector) Name() string { return f.name }
func (f *fakeCollector) Collect(ctx context.Context, _ collectors.DiscoveryTarget) ([]collectors.RawObservation, error) {
	if f.delay > 0 {
		select {
		case <-time.After(f.delay):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	if f.err != nil {
		return nil, f.err
	}
	return f.observations, nil
}

func TestDefaultWorkerPoolSize(t *testing.T) {
	t.Run("Defaults to min(CPU, 4)", func(t *testing.T) {
		t.Setenv("INFRAMAP_SCAN_CONCURRENCY", "")
		expected := runtime.NumCPU()
		if expected > 4 {
			expected = 4
		}
		if got := engine.DefaultWorkerPoolSize(); got != expected {
			t.Errorf("expected %d, got %d", expected, got)
		}
	})

	t.Run("Respects INFRAMAP_SCAN_CONCURRENCY override", func(t *testing.T) {
		t.Setenv("INFRAMAP_SCAN_CONCURRENCY", "8")
		if got := engine.DefaultWorkerPoolSize(); got != 8 {
			t.Errorf("expected 8, got %d", got)
		}
	})

	t.Run("Falls back on invalid env string", func(t *testing.T) {
		t.Setenv("INFRAMAP_SCAN_CONCURRENCY", "invalid")
		expected := runtime.NumCPU()
		if expected > 4 {
			expected = 4
		}
		if got := engine.DefaultWorkerPoolSize(); got != expected {
			t.Errorf("expected %d, got %d", expected, got)
		}
	})

	t.Run("Falls back on non-positive int", func(t *testing.T) {
		t.Setenv("INFRAMAP_SCAN_CONCURRENCY", "0")
		expected := runtime.NumCPU()
		if expected > 4 {
			expected = 4
		}
		if got := engine.DefaultWorkerPoolSize(); got != expected {
			t.Errorf("expected %d, got %d", expected, got)
		}
	})
}

func TestOrchestrator_RunScan(t *testing.T) {
	t.Run("Full pipeline executes and categorizes new and updated assets", func(t *testing.T) {
		c1 := &fakeCollector{
			id:   "arp",
			name: "ARP Reader",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", ProtocolSource: "arp", ConfidenceScore: 40},
				{IPAddress: "192.168.1.20", MACAddress: "00:00:00:00:00:00", ProtocolSource: "arp", ConfidenceScore: 40}, // zero MAC, should be rejected by validator
			},
		}

		c2 := &fakeCollector{
			id:   "snmp",
			name: "SNMP Collector",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", Hostname: "core-sw", Vendor: "Cisco", ProtocolSource: "snmp", ConfidenceScore: 80},
				{IPAddress: "192.168.1.30", MACAddress: "aa:bb:cc:dd:ee:03", Hostname: "new-nas", Vendor: "Synology", ProtocolSource: "snmp", ConfidenceScore: 80},
			},
		}

		bus := eventbus.NewInMemoryEventBus(2, 100)
		defer func() { _ = bus.Close() }()

		eventsChan := make(chan eventbus.DomainEvent, 10)
		_ = bus.Subscribe("device.discovered", func(_ context.Context, e eventbus.DomainEvent) error {
			eventsChan <- e
			return nil
		})
		_ = bus.Subscribe("device.updated", func(_ context.Context, e eventbus.DomainEvent) error {
			eventsChan <- e
			return nil
		})

		existingID := uuid.New()
		activeDevices := []db.Device{
			{
				ID:       existingID,
				Hostname: "old-sw",
			},
		}
		// Set MacAddress for active device
		activeDevices[0].MacAddress, _ = net.ParseMAC("aa:bb:cc:dd:ee:01")

		orch := engine.NewDefaultOrchestrator(bus)
		orch.RegisterCollector(c1)
		orch.RegisterCollector(c2)

		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}
		res, err := orch.RunScan(context.Background(), target, activeDevices, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		if res.TotalCollected != 4 {
			t.Errorf("expected TotalCollected 4, got %d", res.TotalCollected)
		}
		if res.TotalValid != 3 {
			t.Errorf("expected TotalValid 3 (rejecting zero MAC), got %d", res.TotalValid)
		}
		if res.TotalDiscovered != 1 {
			t.Errorf("expected TotalDiscovered 1 (new-nas), got %d", res.TotalDiscovered)
		}
		if res.TotalUpdated != 1 {
			t.Errorf("expected TotalUpdated 1 (core-sw), got %d", res.TotalUpdated)
		}

		if len(res.Collectors) != 2 {
			t.Fatalf("expected 2 collector details, got %d", len(res.Collectors))
		}
	})

	t.Run("Resilient to failing collector in worker pool with metrics tracking", func(t *testing.T) {
		goodCol := &fakeCollector{
			id:   "arp",
			name: "ARP Reader",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", ProtocolSource: "arp", ConfidenceScore: 40},
			},
		}

		badCol := &fakeCollector{
			id:  "failing",
			err: errors.New("network interface down"),
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(goodCol)
		orch.RegisterCollector(badCol)

		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}
		res, err := orch.RunScan(context.Background(), target, nil, nil)
		if err != nil {
			t.Fatalf("unexpected scan failure: %v", err)
		}
		if res.TotalCollected != 1 {
			t.Errorf("expected 1 collected observation from good worker, got %d", res.TotalCollected)
		}

		if len(res.Collectors) != 2 {
			t.Fatalf("expected 2 collector details, got %d", len(res.Collectors))
		}

		foundError := false
		foundSuccess := false
		for _, col := range res.Collectors {
			if col.CollectorType == "failing" {
				foundError = true
				if col.Status != "error" {
					t.Errorf("expected status 'error' for failing collector, got %q", col.Status)
				}
				if col.ErrorMessage != "network interface down" {
					t.Errorf("expected error message 'network interface down', got %q", col.ErrorMessage)
				}
				if col.DevicesFound != 0 {
					t.Errorf("expected 0 devices found for failing collector, got %d", col.DevicesFound)
				}
			}
			if col.CollectorType == "arp" {
				foundSuccess = true
				if col.Status != "success" {
					t.Errorf("expected status 'success' for good collector, got %q", col.Status)
				}
				if col.DevicesFound != 1 {
					t.Errorf("expected 1 device found for arp collector, got %d", col.DevicesFound)
				}
			}
		}

		if !foundError || !foundSuccess {
			t.Errorf("expected both error and success collector metrics: foundError=%v, foundSuccess=%v", foundError, foundSuccess)
		}
	})

	t.Run("Selective scan runs only requested collectors", func(t *testing.T) {
		c1 := &fakeCollector{
			id:   "icmp",
			name: "ICMP",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", LatencyMs: 5, ProtocolSource: "icmp", ConfidenceScore: 50},
			},
		}
		c2 := &fakeCollector{
			id:   "arp",
			name: "ARP",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", ProtocolSource: "arp", ConfidenceScore: 40},
			},
		}
		c3 := &fakeCollector{
			id:   "snmp",
			name: "SNMP",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", Hostname: "switch-1", ProtocolSource: "snmp", ConfidenceScore: 80},
			},
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(c1)
		orch.RegisterCollector(c2)
		orch.RegisterCollector(c3)

		// Request only "arp"
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"}
		res, err := orch.RunScan(context.Background(), target, nil, []string{"arp"})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		if res.TotalCollected != 1 {
			t.Errorf("expected 1 observation from selective arp collector, got %d", res.TotalCollected)
		}
		if len(res.Collectors) != 1 {
			t.Fatalf("expected 1 collector detail, got %d", len(res.Collectors))
		}
		if res.Collectors[0].CollectorType != "arp" {
			t.Errorf("expected collector type 'arp', got %q", res.Collectors[0].CollectorType)
		}
	})

	t.Run("Selective scan maps canonical type names to registered collectors", func(t *testing.T) {
		c1 := &fakeCollector{
			id:   "icmp",
			name: "ICMP",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", LatencyMs: 5, ProtocolSource: "icmp", ConfidenceScore: 50},
			},
		}
		c2 := &fakeCollector{
			id:   "reverse-dns",
			name: "Reverse DNS",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", Hostname: "host.local", ProtocolSource: "reverse-dns", ConfidenceScore: 30},
			},
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(c1)
		orch.RegisterCollector(c2)

		// Request with canonical type names "icmp_sweep" and "reverse_dns"
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"}
		res, err := orch.RunScan(context.Background(), target, nil, []string{"icmp_sweep", "reverse_dns"})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		if res.TotalCollected != 2 {
			t.Errorf("expected 2 observations from mapped collectors, got %d", res.TotalCollected)
		}
		if len(res.Collectors) != 2 {
			t.Fatalf("expected 2 collector details, got %d", len(res.Collectors))
		}
	})

	t.Run("Selective scan with unimplemented collector records error without failing scan", func(t *testing.T) {
		c1 := &fakeCollector{
			id:   "arp",
			name: "ARP",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", ProtocolSource: "arp", ConfidenceScore: 40},
			},
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(c1)

		// Request implemented "arp" and unimplemented "proxmox"
		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24"}
		res, err := orch.RunScan(context.Background(), target, nil, []string{"arp", "proxmox"})
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}

		if res.TotalCollected != 1 {
			t.Errorf("expected 1 observation from implemented arp collector, got %d", res.TotalCollected)
		}
		if len(res.Collectors) != 2 {
			t.Fatalf("expected 2 collector details, got %d", len(res.Collectors))
		}

		var proxmoxDetail *engine.CollectorRunDetail
		for i := range res.Collectors {
			if res.Collectors[i].CollectorType == "proxmox" {
				proxmoxDetail = &res.Collectors[i]
				break
			}
		}

		if proxmoxDetail == nil {
			t.Fatal("expected collector detail for 'proxmox'")
		}
		if proxmoxDetail.Status != "error" {
			t.Errorf("expected status 'error' for unimplemented proxmox, got %q", proxmoxDetail.Status)
		}
		if proxmoxDetail.ErrorMessage == "" {
			t.Error("expected non-empty error message for unimplemented collector")
		}
	})

	t.Run("Respects context cancellation", func(t *testing.T) {
		ctx, cancel := context.WithCancel(context.Background())
		cancel() // cancel immediately

		c1 := &fakeCollector{
			id: "arp",
			observations: []collectors.RawObservation{
				{IPAddress: "192.168.1.10", MACAddress: "aa:bb:cc:dd:ee:01", ProtocolSource: "arp"},
			},
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(c1)

		target := collectors.DiscoveryTarget{CIDR: "192.168.1.0/24", SubnetID: "sub-1"}
		_, err := orch.RunScan(ctx, target, nil, nil)
		if err == nil {
			t.Fatal("expected context cancellation error, got nil")
		}
	})

	t.Run("DeviceCallback receives network_device type for SNMP source", func(t *testing.T) {
		col := &fakeCollector{
			id:   "snmp",
			name: "SNMP",
			observations: []collectors.RawObservation{
				{IPAddress: "10.0.0.1", MACAddress: "aa:bb:cc:dd:ee:01", Hostname: "core-sw", ProtocolSource: "snmp", ConfidenceScore: 80},
			},
		}

		bus := eventbus.NewInMemoryEventBus(1, 16)
		orch := engine.NewDefaultOrchestrator(bus)
		orch.RegisterCollector(col)

		var captured []*dto.NormalizedDeviceDTO
		orch.SetDeviceCallback(func(_ context.Context, norm *dto.NormalizedDeviceDTO, _ string, _ bool) {
			captured = append(captured, norm)
		})

		target := collectors.DiscoveryTarget{CIDR: "10.0.0.0/24"}
		_, err := orch.RunScan(context.Background(), target, nil, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(captured) != 1 {
			t.Fatalf("expected 1 callback, got %d", len(captured))
		}
		if captured[0].DeviceType != "network_device" {
			t.Errorf("expected DeviceType 'network_device' for snmp, got %q", captured[0].DeviceType)
		}
	})

	t.Run("DeviceCallback for matched device receives matched=true", func(t *testing.T) {
		col := &fakeCollector{
			id:   "arp",
			name: "ARP",
			observations: []collectors.RawObservation{
				{IPAddress: "10.0.0.10", MACAddress: "aa:bb:cc:dd:ee:01", Hostname: "known-host", ProtocolSource: "arp", ConfidenceScore: 50},
			},
		}

		existingID := uuid.New()
		activeDevices := []db.Device{{ID: existingID, Hostname: "known-host"}}
		activeDevices[0].MacAddress, _ = net.ParseMAC("aa:bb:cc:dd:ee:01")

		bus := eventbus.NewInMemoryEventBus(1, 16)
		orch := engine.NewDefaultOrchestrator(bus)
		orch.RegisterCollector(col)

		var matchedFlags []bool
		orch.SetDeviceCallback(func(_ context.Context, _ *dto.NormalizedDeviceDTO, _ string, matched bool) {
			matchedFlags = append(matchedFlags, matched)
		})

		target := collectors.DiscoveryTarget{CIDR: "10.0.0.0/24"}
		_, err := orch.RunScan(context.Background(), target, activeDevices, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(matchedFlags) != 1 {
			t.Fatalf("expected 1 callback, got %d", len(matchedFlags))
		}
		if !matchedFlags[0] {
			t.Error("expected matched=true for known device")
		}
	})

	t.Run("DeviceCallback receives every observation of a known device and the device it matched", func(t *testing.T) {
		// An ICMP sweep reports only the address, and the user renamed the router: nothing
		// about the device changes, but the observation still has to reach persistence.
		col := &fakeCollector{
			id:   "icmp",
			name: "ICMP",
			observations: []collectors.RawObservation{
				{IPAddress: "10.0.0.1", ProtocolSource: "icmp", ConfidenceScore: 20},
			},
		}

		routerID := uuid.New()
		addr := netip.MustParseAddr("10.0.0.1")
		activeDevices := []db.Device{{ID: routerID, Hostname: "home-router", IpAddress: &addr}}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(col)

		var matchedIDs []*uuid.UUID
		orch.SetDeviceCallback(func(_ context.Context, norm *dto.NormalizedDeviceDTO, _ string, matched bool) {
			if !matched {
				t.Errorf("expected the router to be matched, got a new device for %s", norm.IPAddress)
			}
			matchedIDs = append(matchedIDs, norm.MatchedDeviceID)
		})

		if _, err := orch.RunScan(context.Background(), collectors.DiscoveryTarget{CIDR: "10.0.0.0/24"}, activeDevices, nil); err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(matchedIDs) != 1 || matchedIDs[0] == nil || *matchedIDs[0] != routerID {
			t.Fatalf("callback matched IDs = %v, want [%s]", matchedIDs, routerID)
		}
	})

	t.Run("DeviceCallback receives populated DeviceType", func(t *testing.T) {
		col := &fakeCollector{
			id:   "arp",
			name: "ARP",
			observations: []collectors.RawObservation{
				{IPAddress: "10.0.0.1", MACAddress: "aa:bb:cc:dd:ee:01", Hostname: "host1", ProtocolSource: "arp", ConfidenceScore: 50},
			},
		}

		bus := eventbus.NewInMemoryEventBus(1, 16)
		orch := engine.NewDefaultOrchestrator(bus)
		orch.RegisterCollector(col)

		var captured []*dto.NormalizedDeviceDTO
		orch.SetDeviceCallback(func(_ context.Context, norm *dto.NormalizedDeviceDTO, _ string, _ bool) {
			captured = append(captured, norm)
		})

		target := collectors.DiscoveryTarget{CIDR: "10.0.0.0/24"}
		_, err := orch.RunScan(context.Background(), target, nil, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if len(captured) != 1 {
			t.Fatalf("expected 1 callback invocation, got %d", len(captured))
		}
		if captured[0].DeviceType != "host" {
			t.Errorf("expected DeviceType 'host' for arp source, got %q", captured[0].DeviceType)
		}
	})
}

func TestOrchestrator_RunScan_DeduplicatesByProviderRef(t *testing.T) {
	t.Run("counts every address-less workload in a cycle", func(t *testing.T) {
		// Keying the cycle on the IP alone collapsed all of these onto the empty string, so
		// only the first stopped container was ever discovered and the rest vanished.
		col := &fakeCollector{
			id:   "docker",
			name: "Docker Engine",
			observations: []collectors.RawObservation{
				{
					Hostname:        "redis",
					ProtocolSource:  "docker",
					ConfidenceScore: 85,
					ProviderRef:     &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "aaa"},
				},
				{
					Hostname:        "nginx",
					ProtocolSource:  "docker",
					ConfidenceScore: 85,
					ProviderRef:     &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "bbb"},
				},
				{
					Hostname:        "postgres",
					ProtocolSource:  "docker",
					ConfidenceScore: 85,
					ProviderRef:     &collectors.ProviderRef{Provider: "docker", Scope: "lab", Kind: "container", NativeID: "ccc"},
				},
			},
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(col)

		res, err := orch.RunScan(context.Background(), collectors.DiscoveryTarget{}, nil, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if res.TotalDiscovered != 3 {
			t.Errorf("TotalDiscovered = %d, want 3", res.TotalDiscovered)
		}
	})

	t.Run("collapses the same workload reported twice", func(t *testing.T) {
		ref := &collectors.ProviderRef{Provider: "proxmox", Scope: "pve", Kind: "qemu", NativeID: "101"}
		col := &fakeCollector{
			id:   "proxmox",
			name: "Proxmox VE",
			observations: []collectors.RawObservation{
				{Hostname: "web-01", IPAddress: "192.168.1.50", ProtocolSource: "proxmox", ConfidenceScore: 85, ProviderRef: ref},
				// Same workload, different address: without Tier 0 keying this would be
				// counted as a second discovery.
				{Hostname: "web-01", IPAddress: "192.168.1.51", ProtocolSource: "proxmox", ConfidenceScore: 85, ProviderRef: ref},
			},
		}

		orch := engine.NewDefaultOrchestrator(nil)
		orch.RegisterCollector(col)

		res, err := orch.RunScan(context.Background(), collectors.DiscoveryTarget{}, nil, nil)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if res.TotalDiscovered != 1 {
			t.Errorf("TotalDiscovered = %d, want 1", res.TotalDiscovered)
		}
	})
}

// recordingCollector records every target it is invoked with. onCollect, when set, runs
// before the observations are returned and may replace them or fail the call.
type recordingCollector struct {
	id           string
	observations []collectors.RawObservation
	onCollect    func(ctx context.Context, target collectors.DiscoveryTarget) error

	mu    sync.Mutex
	calls []collectors.DiscoveryTarget
}

func (r *recordingCollector) ID() string   { return r.id }
func (r *recordingCollector) Name() string { return r.id }
func (r *recordingCollector) Collect(ctx context.Context, target collectors.DiscoveryTarget) ([]collectors.RawObservation, error) {
	r.mu.Lock()
	r.calls = append(r.calls, target)
	r.mu.Unlock()
	if r.onCollect != nil {
		if err := r.onCollect(ctx, target); err != nil {
			return nil, err
		}
	}
	return r.observations, nil
}

func (r *recordingCollector) recordedCalls() []collectors.DiscoveryTarget {
	r.mu.Lock()
	defer r.mu.Unlock()
	return append([]collectors.DiscoveryTarget(nil), r.calls...)
}

func threeTargets() []collectors.DiscoveryTarget {
	return []collectors.DiscoveryTarget{
		{CIDR: "10.0.10.0/24", SubnetID: "sub-10"},
		{CIDR: "10.0.20.0/24"},
		{CIDR: "10.0.30.0/24"},
	}
}

func detailsFor(res *engine.ScanResult, collectorType string) []engine.CollectorRunDetail {
	var out []engine.CollectorRunDetail
	for _, d := range res.Collectors {
		if d.CollectorType == collectorType {
			out = append(out, d)
		}
	}
	return out
}

func TestOrchestrator_RunTargets_Scopes(t *testing.T) {
	sourceID := uuid.New()
	credentialID := "cred-1"
	run := collectors.DiscoveryTarget{SourceID: &sourceID, CredentialSetID: &credentialID, CIDR: "192.0.2.0/24"}
	targets := threeTargets()

	icmp := &recordingCollector{id: "icmp"}
	arp := &recordingCollector{id: "arp"}
	docker := &recordingCollector{id: "docker"}

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(icmp)
	orch.RegisterCollector(arp)
	orch.RegisterCollector(docker)

	res, err := orch.RunTargets(context.Background(), run, targets, nil, []string{"icmp_sweep", "arp_sweep", "docker"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	t.Run("per_target collector runs once per target with that target's CIDR", func(t *testing.T) {
		calls := icmp.recordedCalls()
		if len(calls) != len(targets) {
			t.Fatalf("icmp called %d times, want %d", len(calls), len(targets))
		}
		// Targets run sequentially, so the calls arrive in target order.
		for i, call := range calls {
			if call.CIDR != targets[i].CIDR || call.SubnetID != targets[i].SubnetID {
				t.Errorf("call %d got CIDR %q SubnetID %q, want %q %q", i, call.CIDR, call.SubnetID, targets[i].CIDR, targets[i].SubnetID)
			}
			if call.SourceID != &sourceID || call.CredentialSetID != &credentialID {
				t.Errorf("call %d did not carry the run-wide SourceID and CredentialSetID", i)
			}
			if len(call.Prefixes) != 0 {
				t.Errorf("call %d got Prefixes %v, want none", i, call.Prefixes)
			}
		}

		rows := detailsFor(res, "icmp")
		if len(rows) != len(targets) {
			t.Fatalf("got %d icmp rows, want %d", len(rows), len(targets))
		}
		for i, row := range rows {
			if row.TargetCIDR != targets[i].CIDR || row.Status != "success" {
				t.Errorf("row %d = %+v, want success for %q", i, row, targets[i].CIDR)
			}
		}
	})

	t.Run("union collector runs once with every target prefix", func(t *testing.T) {
		calls := arp.recordedCalls()
		if len(calls) != 1 {
			t.Fatalf("arp called %d times, want 1", len(calls))
		}
		want := []netip.Prefix{
			netip.MustParsePrefix("10.0.10.0/24"),
			netip.MustParsePrefix("10.0.20.0/24"),
			netip.MustParsePrefix("10.0.30.0/24"),
		}
		if !slices.Equal(calls[0].Prefixes, want) {
			t.Errorf("arp Prefixes = %v, want %v", calls[0].Prefixes, want)
		}
		if calls[0].CIDR != "" || calls[0].SourceID != &sourceID {
			t.Errorf("arp target = %+v, want no CIDR and the run's SourceID", calls[0])
		}
		rows := detailsFor(res, "arp")
		if len(rows) != 1 || rows[0].TargetCIDR != "" {
			t.Errorf("arp rows = %+v, want one row with no TargetCIDR", rows)
		}
	})

	t.Run("none-scope collector runs once with no target", func(t *testing.T) {
		calls := docker.recordedCalls()
		if len(calls) != 1 {
			t.Fatalf("docker called %d times, want 1", len(calls))
		}
		if calls[0].CIDR != "" || len(calls[0].Prefixes) != 0 || calls[0].SourceID != &sourceID {
			t.Errorf("docker target = %+v, want no CIDR, no Prefixes and the run's SourceID", calls[0])
		}
		rows := detailsFor(res, "docker")
		if len(rows) != 1 || rows[0].TargetCIDR != "" {
			t.Errorf("docker rows = %+v, want one row with no TargetCIDR", rows)
		}
	})

	t.Run("result carries the run and its targets", func(t *testing.T) {
		if len(res.Targets) != len(targets) || res.Targets[0].CIDR != targets[0].CIDR || res.Targets[2].CIDR != targets[2].CIDR {
			t.Errorf("Targets = %+v, want %+v", res.Targets, targets)
		}
		if res.Target.SourceID != &sourceID {
			t.Errorf("Target.SourceID = %v, want the run's", res.Target.SourceID)
		}
		if len(res.Collectors) != 5 {
			t.Errorf("got %d run details, want 5 (3 icmp + arp + docker)", len(res.Collectors))
		}
	})
}

func TestOrchestrator_RunTargets_UnionCollectorsRunAfterEveryTarget(t *testing.T) {
	var mu sync.Mutex
	var order []string
	logCall := func(id string) func(context.Context, collectors.DiscoveryTarget) error {
		return func(_ context.Context, target collectors.DiscoveryTarget) error {
			mu.Lock()
			defer mu.Unlock()
			order = append(order, id+" "+target.CIDR)
			return nil
		}
	}
	icmp := &recordingCollector{id: "icmp"}
	icmp.onCollect = logCall("icmp")
	arp := &recordingCollector{id: "arp"}
	arp.onCollect = logCall("arp")
	docker := &recordingCollector{id: "docker"}
	docker.onCollect = logCall("docker")

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(icmp)
	orch.RegisterCollector(arp)
	orch.RegisterCollector(docker)

	targets := threeTargets()
	if _, err := orch.RunTargets(context.Background(), collectors.DiscoveryTarget{}, targets, nil, []string{"icmp_sweep", "arp_sweep", "docker"}); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// The ARP collector reads the neighbour table the sweeps fill, so it must come after the
	// last target's sweep; providers need no target and keep running with the first one.
	if len(order) != 5 || order[len(order)-1] != "arp " {
		t.Fatalf("call order = %v, want arp last", order)
	}
	if !slices.Contains(order[:2], "docker ") {
		t.Errorf("call order = %v, want docker with the first target", order)
	}
}

func TestOrchestrator_RunTargets_NoTargets(t *testing.T) {
	sourceID := uuid.New()
	icmp := &recordingCollector{id: "icmp"}
	docker := &recordingCollector{id: "docker"}

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(icmp)
	orch.RegisterCollector(docker)

	res, err := orch.RunTargets(context.Background(), collectors.DiscoveryTarget{SourceID: &sourceID}, nil, nil, []string{"icmp_sweep", "docker"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(docker.recordedCalls()) != 1 {
		t.Errorf("docker called %d times, want 1", len(docker.recordedCalls()))
	}
	if len(icmp.recordedCalls()) != 0 {
		t.Errorf("icmp called %d times without a target, want 0", len(icmp.recordedCalls()))
	}
	rows := detailsFor(res, "icmp")
	if len(rows) != 1 || rows[0].Status != "error" || rows[0].ErrorMessage == "" {
		t.Errorf("icmp rows = %+v, want one error row", rows)
	}
}

func TestOrchestrator_RunTargets_Cancellation(t *testing.T) {
	// snmp blocks on the second target until icmp cancels the run, so both need a slot.
	t.Setenv("INFRAMAP_SCAN_CONCURRENCY", "2")
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	targets := threeTargets()
	// The run is cancelled while the second target is being scanned.
	icmp := &recordingCollector{
		id: "icmp",
		onCollect: func(ctx context.Context, target collectors.DiscoveryTarget) error {
			if target.CIDR == targets[1].CIDR {
				cancel()
				return ctx.Err()
			}
			return nil
		},
		observations: []collectors.RawObservation{{IPAddress: "10.0.10.5", ProtocolSource: "icmp", ConfidenceScore: 50}},
	}
	snmp := &recordingCollector{
		id: "snmp",
		onCollect: func(ctx context.Context, target collectors.DiscoveryTarget) error {
			if target.CIDR == targets[1].CIDR {
				<-ctx.Done()
				return ctx.Err()
			}
			return nil
		},
	}

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(icmp)
	orch.RegisterCollector(snmp)

	res, err := orch.RunTargets(ctx, collectors.DiscoveryTarget{}, targets, nil, []string{"icmp_sweep", "snmp"})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("expected a cancellation error, got %v", err)
	}
	if res == nil {
		t.Fatal("expected a partial result alongside the cancellation error")
	}

	for _, col := range []*recordingCollector{icmp, snmp} {
		for _, call := range col.recordedCalls() {
			if call.CIDR == targets[2].CIDR {
				t.Errorf("%s ran against %q after the run was cancelled", col.id, call.CIDR)
			}
		}

		rows := detailsFor(res, col.id)
		if len(rows) != len(targets) {
			t.Fatalf("got %d %s rows, want one per target", len(rows), col.id)
		}
		if rows[0].TargetCIDR != targets[0].CIDR || rows[0].Status != "success" {
			t.Errorf("%s first row = %+v, want success on %q", col.id, rows[0], targets[0].CIDR)
		}
		if rows[1].TargetCIDR != targets[1].CIDR || rows[1].Status == "success" {
			t.Errorf("%s second row = %+v, want an interrupted run on %q", col.id, rows[1], targets[1].CIDR)
		}
		if rows[2].TargetCIDR != targets[2].CIDR || rows[2].Status != "skipped" {
			t.Errorf("%s third row = %+v, want skipped on %q", col.id, rows[2], targets[2].CIDR)
		}
	}

	if res.TotalDiscovered != 0 || res.TotalUpdated != 0 {
		t.Errorf("a cancelled run must not process observations, got %+v", res)
	}
}

func TestOrchestrator_RunTargets_CancelledBeforeStart(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(&recordingCollector{id: "icmp"})

	if _, err := orch.RunTargets(ctx, collectors.DiscoveryTarget{}, threeTargets(), nil, nil); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected a cancellation error, got %v", err)
	}
}

func TestOrchestrator_RunTargets_DeduplicatesAcrossTargets(t *testing.T) {
	// A device reachable from two targets (a multi-homed host answering on both, or the
	// same MAC learnt on two interfaces) is reported by every per-target call.
	icmp := &recordingCollector{
		id: "icmp",
		observations: []collectors.RawObservation{
			{IPAddress: "10.0.10.5", MACAddress: "aa:bb:cc:dd:ee:01", ProtocolSource: "icmp", ConfidenceScore: 50},
		},
	}

	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()
	orch := engine.NewDefaultOrchestrator(bus)
	orch.RegisterCollector(icmp)

	var callbacks int
	orch.SetDeviceCallback(func(_ context.Context, _ *dto.NormalizedDeviceDTO, _ string, _ bool) {
		callbacks++
	})

	targets := threeTargets()[:2]
	res, err := orch.RunTargets(context.Background(), collectors.DiscoveryTarget{}, targets, nil, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if res.TotalCollected != 2 {
		t.Errorf("TotalCollected = %d, want 2", res.TotalCollected)
	}
	if res.TotalDiscovered != 1 {
		t.Errorf("TotalDiscovered = %d, want 1", res.TotalDiscovered)
	}
	if callbacks != 1 {
		t.Errorf("device callback invoked %d times, want 1", callbacks)
	}
}

func TestOrchestrator_RunTargets_UnionLeavesOutInvalidTargets(t *testing.T) {
	mdns := &recordingCollector{id: "mdns"}
	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(mdns)

	targets := []collectors.DiscoveryTarget{{CIDR: "10.0.10.0/24"}, {CIDR: "not-a-cidr"}}
	if _, err := orch.RunTargets(context.Background(), collectors.DiscoveryTarget{}, targets, nil, nil); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	calls := mdns.recordedCalls()
	if len(calls) != 1 || !slices.Equal(calls[0].Prefixes, []netip.Prefix{netip.MustParsePrefix("10.0.10.0/24")}) {
		t.Errorf("mdns calls = %+v, want one call with the valid prefix only", calls)
	}
}

// unavailablePinger stands for an environment that allows no ICMP at all, counting the
// addresses it was asked to probe.
type unavailablePinger struct {
	mu    sync.Mutex
	calls int
}

func (p *unavailablePinger) Ping(_ context.Context, _ netip.Addr) (time.Duration, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.calls++
	return 0, collectors.ErrICMPUnavailable
}

func TestOrchestrator_RunTargets_UnavailableICMPIsSkipped(t *testing.T) {
	pinger := &unavailablePinger{}
	docker := &recordingCollector{
		id: "docker",
		observations: []collectors.RawObservation{
			{IPAddress: "10.0.10.9", ProtocolSource: "docker", ConfidenceScore: 90},
		},
	}

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(collectors.NewICMPCollector(pinger))
	orch.RegisterCollector(docker)

	targets := threeTargets()
	res, err := orch.RunTargets(context.Background(), collectors.DiscoveryTarget{}, targets, nil, []string{"icmp_sweep", "docker"})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	rows := detailsFor(res, "icmp")
	if len(rows) != len(targets) {
		t.Fatalf("got %d icmp rows, want one per target (%d)", len(rows), len(targets))
	}
	for i, row := range rows {
		if row.Status != "skipped" || row.TargetCIDR != targets[i].CIDR {
			t.Errorf("row %d = %+v, want skipped for %q", i, row, targets[i].CIDR)
		}
		if row.ErrorMessage != collectors.ErrICMPUnavailable.Error() {
			t.Errorf("row %d ErrorMessage = %q, want %q", i, row.ErrorMessage, collectors.ErrICMPUnavailable.Error())
		}
		if row.DevicesFound != 0 {
			t.Errorf("row %d DevicesFound = %d, want 0", i, row.DevicesFound)
		}
	}
	// Unavailability is known from the first address: no target probes the rest of its /24.
	if pinger.calls != len(targets) {
		t.Errorf("pinger called %d times, want once per target (%d)", pinger.calls, len(targets))
	}

	if providerRows := detailsFor(res, "docker"); len(providerRows) != 1 || providerRows[0].Status != "success" {
		t.Errorf("docker rows = %+v, want one success row beside the skipped sweep", providerRows)
	}
	if res.TotalCollected != 1 {
		t.Errorf("TotalCollected = %d, want 1 (the provider's observation only)", res.TotalCollected)
	}
}

func TestOrchestrator_RunScan_UnavailableICMPIsSkipped(t *testing.T) {
	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(collectors.NewICMPCollector(&unavailablePinger{}))

	res, err := orch.RunScan(context.Background(), collectors.DiscoveryTarget{CIDR: "192.0.2.0/24"}, nil, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	rows := detailsFor(res, "icmp")
	if len(rows) != 1 || rows[0].Status != "skipped" || rows[0].ErrorMessage != collectors.ErrICMPUnavailable.Error() {
		t.Errorf("icmp rows = %+v, want one skipped row carrying the unavailability reason", rows)
	}
}

func TestOrchestrator_RunScan_CollectorUnavailableIsSkippedNotError(t *testing.T) {
	// Any collector can report that its environment lets it scan nothing; a plain failure
	// stays an error.
	unavailable := &fakeCollector{id: "snmp", err: fmt.Errorf("snmp: %w", collectors.ErrCollectorUnavailable)}
	failing := &fakeCollector{id: "arp", err: errors.New("read /proc/net/arp: permission denied")}

	orch := engine.NewDefaultOrchestrator(nil)
	orch.RegisterCollector(unavailable)
	orch.RegisterCollector(failing)

	res, err := orch.RunScan(context.Background(), collectors.DiscoveryTarget{CIDR: "192.0.2.0/24"}, nil, nil)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if rows := detailsFor(res, "snmp"); len(rows) != 1 || rows[0].Status != "skipped" {
		t.Errorf("snmp rows = %+v, want one skipped row", rows)
	}
	if rows := detailsFor(res, "arp"); len(rows) != 1 || rows[0].Status != "error" {
		t.Errorf("arp rows = %+v, want one error row", rows)
	}
}
