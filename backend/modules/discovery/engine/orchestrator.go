package engine

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
)

// CollectorRunDetail represents the execution metrics and outcome of an individual collector in a scan.
type CollectorRunDetail struct {
	CollectorType string
	Status        string
	DevicesFound  int
	DurationMs    int64
	ErrorMessage  string
}

// ScanResult summarizes the outcome of a discovery engine pipeline execution.
type ScanResult struct {
	Target          collectors.DiscoveryTarget
	TotalCollected  int
	TotalValid      int
	TotalDiscovered int
	TotalUpdated    int
	Duration        time.Duration
	Collectors      []CollectorRunDetail
}

// Orchestrator manages worker pool concurrency and executes the 7-stage discovery pipeline.
type Orchestrator interface {
	RegisterCollector(c collectors.Collector)
	RunScan(ctx context.Context, target collectors.DiscoveryTarget, activeDevices []db.Device, requestedCollectors []string) (*ScanResult, error)
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

// DeviceCallback is invoked for each valid, normalized observation after matching.
// sourceType is the protocol source (e.g. "icmp", "arp"). norm is the normalized DTO.
// matched indicates whether the device was found in active inventory.
type DeviceCallback func(ctx context.Context, norm *dto.NormalizedDeviceDTO, sourceType string, matched bool)

// DefaultOrchestrator implements Orchestrator.
type DefaultOrchestrator struct {
	mu               sync.RWMutex
	collectors       []collectors.Collector
	matcher          IdentityMatcher
	reconciler       FieldReconciler
	bus              eventbus.EventBus
	onDeviceCallback DeviceCallback
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

// SetDeviceCallback registers a callback invoked for each processed device observation.
func (o *DefaultOrchestrator) SetDeviceCallback(cb DeviceCallback) {
	o.mu.Lock()
	defer o.mu.Unlock()
	o.onDeviceCallback = cb
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
	durationMs   int64
}

// RunScan executes requested collectors (or all registered collectors if none specified) concurrently
// using a bounded worker pool, isolates individual worker errors, tracks per-collector metrics,
// and processes observations through normalization, validation, matching, reconciliation, and event publishing.
func (o *DefaultOrchestrator) RunScan(ctx context.Context, target collectors.DiscoveryTarget, activeDevices []db.Device, requestedCollectors []string) (*ScanResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	start := time.Now()

	o.mu.RLock()
	allCols := make([]collectors.Collector, len(o.collectors))
	copy(allCols, o.collectors)
	o.mu.RUnlock()

	var colsToRun []collectors.Collector
	var missingDetails []CollectorRunDetail

	if len(requestedCollectors) == 0 {
		colsToRun = allCols
	} else {
		// Index registered collectors by raw ID and canonical alias
		registeredMap := make(map[string]collectors.Collector)
		for _, c := range allCols {
			cID := strings.ToLower(strings.TrimSpace(c.ID()))
			registeredMap[cID] = c
			canonical := collectors.CanonicalType(cID)
			if canonical != "" {
				registeredMap[canonical] = c
			}
		}

		seen := make(map[collectors.Collector]bool)
		for _, req := range requestedCollectors {
			reqNorm := strings.ToLower(strings.TrimSpace(req))
			if reqNorm == "" {
				continue
			}

			// Check if collector is explicitly marked as not implemented in this wave
			if !collectors.IsImplemented(reqNorm) {
				missingDetails = append(missingDetails, CollectorRunDetail{
					CollectorType: reqNorm,
					Status:        "error",
					ErrorMessage:  fmt.Sprintf("collector %q is not implemented in this wave", reqNorm),
					DevicesFound:  0,
					DurationMs:    0,
				})
				continue
			}

			// Lookup in registered collectors
			col, found := registeredMap[reqNorm]
			if !found {
				canonical := collectors.CanonicalType(reqNorm)
				col, found = registeredMap[canonical]
			}

			if found {
				if !seen[col] {
					seen[col] = true
					colsToRun = append(colsToRun, col)
				}
			} else {
				missingDetails = append(missingDetails, CollectorRunDetail{
					CollectorType: reqNorm,
					Status:        "error",
					ErrorMessage:  fmt.Sprintf("collector %q is not registered", reqNorm),
					DevicesFound:  0,
					DurationMs:    0,
				})
			}
		}
	}

	workerLimit := DefaultWorkerPoolSize()
	sem := make(chan struct{}, workerLimit)
	resultsChan := make(chan collectorResult, len(colsToRun))

	var wg sync.WaitGroup

	for _, col := range colsToRun {
		wg.Add(1)
		go func(c collectors.Collector) {
			defer wg.Done()

			select {
			case sem <- struct{}{}:
				defer func() { <-sem }()
			case <-ctx.Done():
				resultsChan <- collectorResult{
					collectorID: c.ID(),
					err:         ctx.Err(),
				}
				return
			}

			colStart := time.Now()
			obs, err := c.Collect(ctx, target)
			colDuration := time.Since(colStart).Milliseconds()

			resultsChan <- collectorResult{
				collectorID:  c.ID(),
				observations: obs,
				err:          err,
				durationMs:   colDuration,
			}
		}(col)
	}

	wg.Wait()
	close(resultsChan)

	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	var allObservations []collectors.RawObservation
	collectorDetails := make([]CollectorRunDetail, 0, len(colsToRun)+len(missingDetails))
	collectorDetails = append(collectorDetails, missingDetails...)

	for res := range resultsChan {
		detail := CollectorRunDetail{
			CollectorType: res.collectorID,
			DurationMs:    res.durationMs,
		}
		if res.err != nil {
			slog.Warn("collector failed during discovery run", "collector_id", res.collectorID, "error", res.err)
			detail.Status = "error"
			detail.ErrorMessage = res.err.Error()
			detail.DevicesFound = 0
		} else {
			detail.Status = "success"
			detail.DevicesFound = len(res.observations)
			allObservations = append(allObservations, res.observations...)
		}
		collectorDetails = append(collectorDetails, detail)
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
			IPAddress:         normalized.IPAddress,
			MACAddress:        normalized.MACAddress,
			Hostname:          normalized.Hostname,
			Manufacturer:      normalized.Vendor,
			DeviceType:        classifyDeviceType(normalized),
			ConfidenceScore:   normalized.ConfidenceScore,
			ProtocolSource:    normalized.ProtocolSource,
			RawPayload:        normalized.RawMetadata,
			ProviderRef:       normalized.ProviderRef,
			ParentProviderRef: normalized.ParentProviderRef,
		}

		matchRes := o.matcher.MatchDevice(normDTO, activeDevices)

		if matchRes.DeviceID != nil {
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

					o.mu.RLock()
					cb := o.onDeviceCallback
					o.mu.RUnlock()
					if cb != nil {
						cb(ctx, normDTO, normalized.ProtocolSource, true)
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

				o.mu.RLock()
				cb := o.onDeviceCallback
				o.mu.RUnlock()
				if cb != nil {
					cb(ctx, normDTO, normalized.ProtocolSource, false)
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
		Collectors:      collectorDetails,
	}, nil
}

func classifyDeviceType(obs collectors.RawObservation) string {
	if obs.RawMetadata != nil {
		if dt, ok := obs.RawMetadata["device_type"].(string); ok && dt != "" {
			return dt
		}
	}
	switch obs.ProtocolSource {
	case "snmp":
		return "network_device"
	case "icmp", "arp", "mdns":
		return "host"
	case "docker":
		return "container"
	case "proxmox":
		return "virtual_machine"
	default:
		return "unknown"
	}
}
