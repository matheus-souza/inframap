package engine_test

import (
	"context"
	"errors"
	"net"
	"runtime"
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
