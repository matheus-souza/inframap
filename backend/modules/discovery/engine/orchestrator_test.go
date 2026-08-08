package engine_test

import (
	"context"
	"errors"
	"net"
	"runtime"
	"testing"


	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/engine"
)

type fakeCollector struct {
	id           string
	name         string
	observations []collectors.RawObservation
	err          error
}

func (f *fakeCollector) ID() string   { return f.id }
func (f *fakeCollector) Name() string { return f.name }
func (f *fakeCollector) Collect(_ context.Context, _ collectors.DiscoveryTarget) ([]collectors.RawObservation, error) {
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
			t.Errorf("expected 8, got %d", 8)
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
		res, err := orch.RunScan(context.Background(), target, activeDevices)
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

	})

	t.Run("Resilient to failing collector in worker pool", func(t *testing.T) {
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
		res, err := orch.RunScan(context.Background(), target, nil)
		if err != nil {
			t.Fatalf("unexpected scan failure: %v", err)
		}
		if res.TotalCollected != 1 {
			t.Errorf("expected 1 collected observation from good worker, got %d", res.TotalCollected)
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
		_, err := orch.RunScan(ctx, target, nil)
		if err == nil {
			t.Fatal("expected context cancellation error, got nil")
		}
	})
}
