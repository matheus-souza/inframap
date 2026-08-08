package engine

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"runtime"
	"strconv"
	"sync"
	"time"

	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// ScanResult summarizes the outcome of a discovery engine pipeline execution.
type ScanResult struct {
	Target          collectors.DiscoveryTarget
	TotalCollected  int
	TotalValid      int
	TotalDiscovered int
	TotalUpdated    int
	Duration        time.Duration
}

// Orchestrator manages worker pool concurrency and executes the 7-stage discovery pipeline.
type Orchestrator interface {
	RegisterCollector(c collectors.Collector)
	RunScan(ctx context.Context, target collectors.DiscoveryTarget, activeDevices []db.Device) (*ScanResult, error)
}

// DefaultWorkerPoolSize calculates the worker pool concurrency limit.
// Defaults to min(runtime.NumCPU(), 4).
// Override via INFRAMAP_SCAN_CONCURRENCY environment variable.
func DefaultWorkerPoolSize() int {
	if envVal := os.Getenv("INFRAMAP_SCAN_CONCURRENCY"); envVal != "" {
		if parsed, err := strconv.Atoi(envVal); err == nil && parsed > 0 {
			return parsed
		}
	}

	cpus := runtime.NumCPU()
	if cpus > 4 {
		return 4
	}
	if cpus < 1 {
		return 1
	}
	return cpus
}

// DefaultOrchestrator implements Orchestrator.
type DefaultOrchestrator struct {
	mu         sync.RWMutex
	collectors []collectors.Collector
	matcher    IdentityMatcher
	reconciler FieldReconciler
	bus        eventbus.EventBus
}

// NewDefaultOrchestrator constructs a DefaultOrchestrator with default matcher and reconciler.
func NewDefaultOrchestrator(bus eventbus.EventBus) *DefaultOrchestrator {
	return &DefaultOrchestrator{
		collectors: make([]collectors.Collector, 0),
		matcher:    NewDefaultIdentityMatcher(),
		reconciler: NewDefaultFieldReconciler(),
		bus:        bus,
	}
}

// RegisterCollector appends a collector worker to the orchestrator.
func (o *DefaultOrchestrator) RegisterCollector(c collectors.Collector) {
	if c == nil {
		return
	}
	o.mu.Lock()
	defer o.mu.Unlock()
	o.collectors = append(o.collectors, c)
}

type collectorResult struct {
	collectorID  string
	observations []collectors.RawObservation
	err          error
}

// RunScan executes all registered collectors concurrently using a bounded worker pool,
// then processes observations through normalization, validation, matching, reconciliation, and event publishing.
func (o *DefaultOrchestrator) RunScan(ctx context.Context, target collectors.DiscoveryTarget, activeDevices []db.Device) (*ScanResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	start := time.Now()

	o.mu.RLock()
	cols := make([]collectors.Collector, len(o.collectors))
	copy(cols, o.collectors)
	o.mu.RUnlock()

	workerLimit := DefaultWorkerPoolSize()
	sem := make(chan struct{}, workerLimit)
	resultsChan := make(chan collectorResult, len(cols))

	var wg sync.WaitGroup

	for _, col := range cols {
		wg.Add(1)
		go func(c collectors.Collector) {
			defer wg.Done()

			select {
			case sem <- struct{}{}:
				defer func() { <-sem }()
			case <-ctx.Done():
				resultsChan <- collectorResult{collectorID: c.ID(), err: ctx.Err()}
				return
			}

			obs, err := c.Collect(ctx, target)
			resultsChan <- collectorResult{collectorID: c.ID(), observations: obs, err: err}
		}(col)
	}

	wg.Wait()
	close(resultsChan)

	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	var allObservations []collectors.RawObservation
	for res := range resultsChan {
		if res.err != nil {
			slog.Warn("collector failed during discovery run", "collector_id", res.collectorID, "error", res.err)
			continue
		}
		allObservations = append(allObservations, res.observations...)
	}

	totalCollected := len(allObservations)
	totalValid := 0
	totalDiscovered := 0
	totalUpdated := 0

	// Track processed IPs in this scan cycle to avoid redundant updates
	processedIPs := make(map[string]bool)

	for _, raw := range allObservations {
		if err := ctx.Err(); err != nil {
			return nil, fmt.Errorf("orchestrator pipeline aborted: %w", err)
		}

		normalized := NormalizeObservation(raw)
		if err := ValidateObservation(normalized); err != nil {
			continue // skip invalid observations
		}

		totalValid++

		normDTO := &dto.NormalizedDeviceDTO{
			IPAddress:       normalized.IPAddress,
			MACAddress:      normalized.MACAddress,
			Hostname:        normalized.Hostname,
			Manufacturer:    normalized.Vendor,
			ConfidenceScore: normalized.ConfidenceScore,
			RawPayload:      normalized.RawMetadata,
		}


		matchRes := o.matcher.MatchDevice(normDTO, activeDevices)

		if matchRes.DeviceID != nil {
			// Find existing active device
			var existingDev *db.Device
			for i := range activeDevices {
				if activeDevices[i].ID == *matchRes.DeviceID {
					existingDev = &activeDevices[i]
					break
				}
			}

			if existingDev != nil {
				_, changed := o.reconciler.Reconcile(existingDev, normDTO, normalized.ProtocolSource)
				if changed && !processedIPs[normalized.IPAddress] {
					totalUpdated++
					processedIPs[normalized.IPAddress] = true

					if o.bus != nil {
						if pubErr := o.bus.Publish(ctx, eventbus.NewBaseEvent("device.updated", map[string]interface{}{
							"device_id":       existingDev.ID.String(),
							"ip_address":      normalized.IPAddress,
							"protocol_source": normalized.ProtocolSource,
						})); pubErr != nil {
							slog.Warn("failed to publish device.updated event", "device_id", existingDev.ID, "error", pubErr)
						}
					}
				}
			}
		} else {
			if !processedIPs[normalized.IPAddress] {
				totalDiscovered++
				processedIPs[normalized.IPAddress] = true

				if o.bus != nil {
					if pubErr := o.bus.Publish(ctx, eventbus.NewBaseEvent("device.discovered", map[string]interface{}{
						"ip_address":      normalized.IPAddress,
						"mac_address":     normalized.MACAddress,
						"hostname":        normalized.Hostname,
						"vendor":          normalized.Vendor,
						"protocol_source": normalized.ProtocolSource,
					})); pubErr != nil {
						slog.Warn("failed to publish device.discovered event", "ip_address", normalized.IPAddress, "error", pubErr)
					}
				}
			}
		}
	}

	return &ScanResult{
		Target:          target,
		TotalCollected:  totalCollected,
		TotalValid:      totalValid,
		TotalDiscovered: totalDiscovered,
		TotalUpdated:    totalUpdated,
		Duration:        time.Since(start),
	}, nil
}
