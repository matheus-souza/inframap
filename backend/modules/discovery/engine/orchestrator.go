package engine

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/netip"
	"os"
	"runtime"
	"sort"
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
	// TargetCIDR is the target a per_target collector ran against in RunTargets. It is
	// empty for union and none scopes, and for RunScan.
	TargetCIDR   string
	Status       string
	DevicesFound int
	DurationMs   int64
	ErrorMessage string

	// Scopes records what an authoritative provider reported, per scope. It is what lets
	// the lifecycle engine tell "this workload is gone" apart from "this workload was
	// never in this run's scope". Network sweeps leave it empty: they observe packets, not
	// a closed set of entities, so absence from a sweep proves nothing.
	Scopes []ProviderScopeObservation
}

// ProviderScopeObservation is the closed set of workloads an authoritative provider
// reported for one of its scopes during a single run.
type ProviderScopeObservation struct {
	Scope        string
	ObservedRefs []string
}

// ScanResult summarizes the outcome of a discovery engine pipeline execution.
type ScanResult struct {
	// Target is the scanned target for RunScan, and the run-wide fields for RunTargets.
	Target collectors.DiscoveryTarget
	// Targets lists every target of a RunTargets run, in order; empty for RunScan.
	Targets         []collectors.DiscoveryTarget
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
	RunTargets(ctx context.Context, run collectors.DiscoveryTarget, targets []collectors.DiscoveryTarget, activeDevices []db.Device, requestedCollectors []string) (*ScanResult, error)
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

// collectorJob is one Collect invocation: a collector paired with the target it runs against.
type collectorJob struct {
	collector collectors.Collector
	target    collectors.DiscoveryTarget
	// targetCIDR is recorded on the run detail; it is empty for union and none scopes.
	targetCIDR string
}

type collectorResult struct {
	collectorID  string
	targetCIDR   string
	observations []collectors.RawObservation
	err          error
	durationMs   int64
	// skipped marks a job that never started because the run was cancelled first.
	skipped bool
}

// RunScan executes requested collectors (or all registered collectors if none specified) concurrently
// using a bounded worker pool, isolates individual worker errors, tracks per-collector metrics,
// and processes observations through normalization, validation, matching, reconciliation, and event publishing.
func (o *DefaultOrchestrator) RunScan(ctx context.Context, target collectors.DiscoveryTarget, activeDevices []db.Device, requestedCollectors []string) (*ScanResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	start := time.Now()

	colsToRun, missingDetails := o.selectCollectors(requestedCollectors)

	jobs := make([]collectorJob, 0, len(colsToRun))
	for _, col := range colsToRun {
		jobs = append(jobs, collectorJob{collector: col, target: target})
	}
	results := runCollectorJobs(ctx, jobs)

	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	collectorDetails := make([]CollectorRunDetail, 0, len(results)+len(missingDetails))
	collectorDetails = append(collectorDetails, missingDetails...)
	resultDetails, allObservations := foldCollectorResults(results)
	collectorDetails = append(collectorDetails, resultDetails...)

	result, err := o.processObservations(ctx, allObservations, collectorDetails, activeDevices)
	if err != nil {
		return nil, err
	}
	result.Target = target
	result.Duration = time.Since(start)
	return result, nil
}

// RunTargets executes one run over several targets (RFC-026 §3). Every collector is invoked
// according to its collectors.TargetScope:
//   - per_target collectors run once per target, with run's fields and that target's CIDR and
//     SubnetID;
//   - union collectors run once, with Prefixes set to every target's CIDR;
//   - none-scope collectors run once, with run's fields and no CIDR.
//
// run carries the fields shared by the whole run (SourceID, CredentialSetID); its CIDR,
// SubnetID and Prefixes are ignored. Targets are processed sequentially, in order, and the
// collectors of one target run in parallel under the bounded worker pool; union and none
// collectors join the first target. With no targets, only union and none collectors run,
// and per_target collectors are reported as errors.
//
// All observations of the run are then processed once, so a device reported by several
// targets is discovered or updated once. When ctx is cancelled the current target is
// interrupted and every (collector, target) pair that did not start is recorded as
// "skipped"; RunTargets then returns both the partial result, carrying those run details,
// and the cancellation error, without processing observations.
func (o *DefaultOrchestrator) RunTargets(ctx context.Context, run collectors.DiscoveryTarget, targets []collectors.DiscoveryTarget, activeDevices []db.Device, requestedCollectors []string) (*ScanResult, error) {
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("orchestrator scan aborted: %w", err)
	}

	start := time.Now()

	colsToRun, missingDetails := o.selectCollectors(requestedCollectors)
	batches, unrunnable := planTargetBatches(run, targets, colsToRun)

	var results []collectorResult
	for i, batch := range batches {
		if ctx.Err() != nil {
			for _, rest := range batches[i:] {
				results = append(results, skippedResults(rest)...)
			}
			break
		}
		results = append(results, runCollectorJobs(ctx, batch)...)
	}

	collectorDetails := make([]CollectorRunDetail, 0, len(results)+len(missingDetails)+len(unrunnable))
	collectorDetails = append(collectorDetails, missingDetails...)
	collectorDetails = append(collectorDetails, unrunnable...)
	resultDetails, allObservations := foldCollectorResults(results)
	collectorDetails = append(collectorDetails, resultDetails...)

	partial := func(err error) (*ScanResult, error) {
		return &ScanResult{
			Target:         run,
			Targets:        targets,
			TotalCollected: len(allObservations),
			Duration:       time.Since(start),
			Collectors:     collectorDetails,
		}, err
	}

	if err := ctx.Err(); err != nil {
		return partial(fmt.Errorf("orchestrator scan aborted: %w", err))
	}

	result, err := o.processObservations(ctx, allObservations, collectorDetails, activeDevices)
	if err != nil {
		return partial(err)
	}
	result.Target = run
	result.Targets = targets
	result.Duration = time.Since(start)
	return result, nil
}

// planTargetBatches splits a multi-target run into one batch of jobs per target, in target
// order. Union and none-scope collectors run once and join the first batch. With no
// targets, per_target collectors cannot run and are returned as error details instead.
func planTargetBatches(run collectors.DiscoveryTarget, targets []collectors.DiscoveryTarget, cols []collectors.Collector) ([][]collectorJob, []CollectorRunDetail) {
	base := run
	base.CIDR = ""
	base.SubnetID = ""
	base.Prefixes = nil

	var perTarget []collectors.Collector
	var shared []collectorJob
	for _, col := range cols {
		switch collectors.TargetScopeOf(col.ID()) {
		case collectors.TargetScopeUnion:
			union := base
			union.Prefixes = unionPrefixes(targets)
			shared = append(shared, collectorJob{collector: col, target: union})
		case collectors.TargetScopeNone:
			shared = append(shared, collectorJob{collector: col, target: base})
		default:
			perTarget = append(perTarget, col)
		}
	}

	if len(targets) == 0 {
		unrunnable := make([]CollectorRunDetail, 0, len(perTarget))
		for _, col := range perTarget {
			unrunnable = append(unrunnable, CollectorRunDetail{
				CollectorType: col.ID(),
				Status:        "error",
				ErrorMessage:  fmt.Sprintf("collector %q requires a target CIDR", col.ID()),
			})
		}
		if len(shared) == 0 {
			return nil, unrunnable
		}
		return [][]collectorJob{shared}, unrunnable
	}

	batches := make([][]collectorJob, 0, len(targets))
	for i, tgt := range targets {
		batch := make([]collectorJob, 0, len(perTarget)+len(shared))
		for _, col := range perTarget {
			target := base
			target.CIDR = tgt.CIDR
			target.SubnetID = tgt.SubnetID
			batch = append(batch, collectorJob{collector: col, target: target, targetCIDR: tgt.CIDR})
		}
		if i == 0 {
			batch = append(batch, shared...)
		}
		batches = append(batches, batch)
	}
	return batches, nil
}

// unionPrefixes parses every target's CIDR for the union scope. A target whose CIDR does
// not parse is left out of the union; the per_target collectors report it on their own row.
func unionPrefixes(targets []collectors.DiscoveryTarget) []netip.Prefix {
	prefixes := make([]netip.Prefix, 0, len(targets))
	for _, tgt := range targets {
		prefix, err := collectors.ParseTargetPrefix(tgt.CIDR)
		if err != nil {
			slog.Warn("target left out of the union scope", "cidr", tgt.CIDR, "error", err)
			continue
		}
		prefixes = append(prefixes, prefix)
	}
	return prefixes
}

// selectCollectors resolves the requested collector types (or every registered collector
// when none is requested) to registered collectors, reporting unimplemented and
// unregistered types as error details.
func (o *DefaultOrchestrator) selectCollectors(requestedCollectors []string) ([]collectors.Collector, []CollectorRunDetail) {
	o.mu.RLock()
	allCols := make([]collectors.Collector, len(o.collectors))
	copy(allCols, o.collectors)
	o.mu.RUnlock()

	if len(requestedCollectors) == 0 {
		return allCols, nil
	}

	var colsToRun []collectors.Collector
	var missingDetails []CollectorRunDetail

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

	return colsToRun, missingDetails
}

// runCollectorJobs runs the jobs concurrently under the bounded worker pool and returns
// their results in job order. A job that has not started when ctx is cancelled is reported
// as skipped instead of being invoked.
func runCollectorJobs(ctx context.Context, jobs []collectorJob) []collectorResult {
	sem := make(chan struct{}, DefaultWorkerPoolSize())
	results := make([]collectorResult, len(jobs))

	var wg sync.WaitGroup
	for i, job := range jobs {
		wg.Add(1)
		go func(i int, job collectorJob) {
			defer wg.Done()

			res := collectorResult{collectorID: job.collector.ID(), targetCIDR: job.targetCIDR}

			select {
			case sem <- struct{}{}:
				defer func() { <-sem }()
			case <-ctx.Done():
				res.err = ctx.Err()
				res.skipped = true
				results[i] = res
				return
			}

			// The slot and the cancellation can be ready together; never start a job late.
			if err := ctx.Err(); err != nil {
				res.err = err
				res.skipped = true
				results[i] = res
				return
			}

			colStart := time.Now()
			res.observations, res.err = job.collector.Collect(ctx, job.target)
			res.durationMs = time.Since(colStart).Milliseconds()
			results[i] = res
		}(i, job)
	}
	wg.Wait()

	return results
}

// skippedResults records jobs a cancelled run never reached.
func skippedResults(jobs []collectorJob) []collectorResult {
	results := make([]collectorResult, 0, len(jobs))
	for _, job := range jobs {
		results = append(results, collectorResult{
			collectorID: job.collector.ID(),
			targetCIDR:  job.targetCIDR,
			err:         context.Canceled,
			skipped:     true,
		})
	}
	return results
}

// foldCollectorResults turns collector results into run details and gathers the
// observations of the collectors that succeeded. A collector that reports
// collectors.ErrCollectorUnavailable is recorded as "skipped" with that error as the reason.
func foldCollectorResults(results []collectorResult) ([]CollectorRunDetail, []collectors.RawObservation) {
	details := make([]CollectorRunDetail, 0, len(results))
	var observations []collectors.RawObservation

	for _, res := range results {
		detail := CollectorRunDetail{
			CollectorType: res.collectorID,
			TargetCIDR:    res.targetCIDR,
			DurationMs:    res.durationMs,
		}
		switch {
		case res.skipped:
			detail.Status = "skipped"
			detail.ErrorMessage = "run cancelled before the collector started"
		case errors.Is(res.err, collectors.ErrCollectorUnavailable):
			// The environment let the collector scan nothing: record that it did not run,
			// so the source is not reported healthy on a sweep that never happened.
			slog.Warn("collector unavailable during discovery run", "collector_id", res.collectorID, "target_cidr", res.targetCIDR, "error", res.err)
			detail.Status = "skipped"
			detail.ErrorMessage = res.err.Error()
		case res.err != nil:
			slog.Warn("collector failed during discovery run", "collector_id", res.collectorID, "target_cidr", res.targetCIDR, "error", res.err)
			detail.Status = "error"
			detail.ErrorMessage = res.err.Error()
			detail.DevicesFound = 0
		default:
			detail.Status = "success"
			detail.DevicesFound = len(res.observations)
			observations = append(observations, res.observations...)
		}
		details = append(details, detail)
	}

	return details, observations
}

// processObservations runs the observations of a whole run through normalization,
// validation, matching, reconciliation and event publishing, then folds the provider
// scopes onto details. Each identity is handled once per run, however many collectors or
// targets reported it. The caller fills Target, Targets and Duration.
func (o *DefaultOrchestrator) processObservations(ctx context.Context, observations []collectors.RawObservation, details []CollectorRunDetail, activeDevices []db.Device) (*ScanResult, error) {
	totalValid := 0
	totalDiscovered := 0
	totalUpdated := 0

	// Accumulate what each authoritative provider saw, per scope, so the lifecycle engine
	// can reason about absence once the run is known to be complete.
	observedByCollector := make(map[string]map[string]map[string]bool)

	// Track the identities already handled in this scan cycle to avoid redundant updates.
	// Keying on the IP alone collapsed every address-less workload onto the empty string,
	// so a single stopped container silently swallowed all the others in the same run.
	processedKeys := make(map[string]bool)

	for _, raw := range observations {
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

		if ref := normalized.ProviderRef; ref != nil && !ref.IsZero() {
			byScope, ok := observedByCollector[normalized.ProtocolSource]
			if !ok {
				byScope = make(map[string]map[string]bool)
				observedByCollector[normalized.ProtocolSource] = byScope
			}
			if byScope[ref.Scope] == nil {
				byScope[ref.Scope] = make(map[string]bool)
			}
			byScope[ref.Scope][ref.Key()] = true
		}

		dedupeKey := observationDedupeKey(normalized)
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
				if existingDev.DeletedAt.Valid {
					normDTO.MatchedDeviceID = &existingDev.ID
					normDTO.PreviouslyDeleted = true
					if !processedKeys[dedupeKey] {
						totalDiscovered++
						processedKeys[dedupeKey] = true

						if o.bus != nil {
							if pubErr := o.bus.Publish(ctx, eventbus.NewBaseEvent("device.discovered", map[string]interface{}{
								"ip_address":         normalized.IPAddress,
								"mac_address":        normalized.MACAddress,
								"hostname":           normalized.Hostname,
								"vendor":             normalized.Vendor,
								"protocol_source":    normalized.ProtocolSource,
								"previously_deleted": true,
								"matched_device_id":  existingDev.ID.String(),
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
				} else {
					_, changed := o.reconciler.Reconcile(existingDev, normDTO, normalized.ProtocolSource)
					if changed && !processedKeys[dedupeKey] {
						totalUpdated++
						processedKeys[dedupeKey] = true

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
			}
		} else {
			if !processedKeys[dedupeKey] {
				totalDiscovered++
				processedKeys[dedupeKey] = true

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

	attachScopeObservations(details, observedByCollector)

	return &ScanResult{
		TotalCollected:  len(observations),
		TotalValid:      totalValid,
		TotalDiscovered: totalDiscovered,
		TotalUpdated:    totalUpdated,
		Collectors:      details,
	}, nil
}

// attachScopeObservations folds what each collector observed back onto its run detail,
// with the references sorted so a run summary is reproducible.
func attachScopeObservations(details []CollectorRunDetail, observed map[string]map[string]map[string]bool) {
	for i := range details {
		byScope, ok := observed[details[i].CollectorType]
		if !ok {
			continue
		}

		scopes := make([]string, 0, len(byScope))
		for scope := range byScope {
			scopes = append(scopes, scope)
		}
		sort.Strings(scopes)

		for _, scope := range scopes {
			refs := make([]string, 0, len(byScope[scope]))
			for ref := range byScope[scope] {
				refs = append(refs, ref)
			}
			sort.Strings(refs)
			details[i].Scopes = append(details[i].Scopes, ProviderScopeObservation{Scope: scope, ObservedRefs: refs})
		}
	}
}

// observationDedupeKey returns the strongest identity an observation carries, so a single
// scan cycle handles each real entity once even when several collectors report it.
// Precedence follows the matcher: ProviderRef, then MAC, then IP, then hostname.
func observationDedupeKey(obs collectors.RawObservation) string {
	if obs.ProviderRef != nil && !obs.ProviderRef.IsZero() {
		return obs.ProviderRef.Key()
	}
	if obs.MACAddress != "" {
		return "mac:" + obs.MACAddress
	}
	if obs.IPAddress != "" {
		return "ip:" + obs.IPAddress
	}
	return "host:" + obs.Hostname
}

func classifyDeviceType(obs collectors.RawObservation) string {
	// Providers report the canonical device type themselves; only fall back to inference
	// for network sweeps, which observe packets rather than declared entities.
	if obs.DeviceType != "" {
		return obs.DeviceType
	}
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
