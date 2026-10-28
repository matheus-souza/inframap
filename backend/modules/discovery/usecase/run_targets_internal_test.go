package usecase

import (
	"context"
	"errors"
	"fmt"
	"net/netip"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
	"github.com/matheussouza/inframap/modules/discovery/repository"
	inventoryRepo "github.com/matheussouza/inframap/modules/inventory/repository"
)

// targetsCall is one RunTargets invocation seen by recordingOrchestrator.
type targetsCall struct {
	run        collectors.DiscoveryTarget
	targets    []collectors.DiscoveryTarget
	collectors []string
}

// recordingOrchestrator records every RunTargets call. By default it reports each requested
// collector as a success the way the engine lays rows out: one row per target for a
// per_target collector, carrying that target's CIDR, and one row without a target otherwise.
type recordingOrchestrator struct {
	calls   []targetsCall
	respond func(targets []collectors.DiscoveryTarget, requested []string) (*engine.ScanResult, error)
}

func (o *recordingOrchestrator) RegisterCollector(collectors.Collector) {}

// RunScan serves the ad-hoc scan only, which TriggerRun must not use.
func (o *recordingOrchestrator) RunScan(context.Context, collectors.DiscoveryTarget, []db.Device, []string) (*engine.ScanResult, error) {
	return nil, errors.New("RunScan is not expected from TriggerRun")
}

func (o *recordingOrchestrator) RunTargets(_ context.Context, run collectors.DiscoveryTarget, targets []collectors.DiscoveryTarget, _ []db.Device, requested []string) (*engine.ScanResult, error) {
	o.calls = append(o.calls, targetsCall{
		run:        run,
		targets:    append([]collectors.DiscoveryTarget(nil), targets...),
		collectors: append([]string(nil), requested...),
	})
	if o.respond != nil {
		return o.respond(targets, requested)
	}
	return successResult(targets, requested, nil), nil
}

// successResult reports every requested collector as a success, one row per target for
// per_target collectors. A CIDR listed in failing makes its per_target rows fail instead.
func successResult(targets []collectors.DiscoveryTarget, requested []string, failing map[string]bool) *engine.ScanResult {
	var details []engine.CollectorRunDetail
	for _, collectorType := range requested {
		if collectors.TargetScopeOf(collectorType) != collectors.TargetScopePerTarget {
			details = append(details, engine.CollectorRunDetail{CollectorType: collectorType, Status: "success", DevicesFound: 1})
			continue
		}
		for _, target := range targets {
			detail := engine.CollectorRunDetail{CollectorType: collectorType, TargetCIDR: target.CIDR, Status: "success", DevicesFound: 1}
			if failing[target.CIDR] {
				detail.Status = "error"
				detail.DevicesFound = 0
				detail.ErrorMessage = "network unreachable"
			}
			details = append(details, detail)
		}
	}
	return &engine.ScanResult{TotalCollected: len(details), Duration: time.Millisecond, Collectors: details}
}

// stubRunRepo serves one source and records what TriggerRun persists. Methods TriggerRun
// does not use are left to the embedded interface and panic if reached.
type stubRunRepo struct {
	repository.DiscoveryRepository
	source   *dto.DiscoverySourceResponse
	runs     []*db.CreateCollectorRunParams
	statuses []string
}

func (s *stubRunRepo) GetSourceByID(_ context.Context, _ uuid.UUID) (*dto.DiscoverySourceResponse, error) {
	return s.source, nil
}

func (s *stubRunRepo) UpdateSourceStatus(_ context.Context, _ uuid.UUID, status string) (*dto.DiscoverySourceResponse, error) {
	s.statuses = append(s.statuses, status)
	s.source.LastStatus = status
	return s.source, nil
}

func (s *stubRunRepo) CreateCollectorRun(_ context.Context, run *db.CreateCollectorRunParams) error {
	s.runs = append(s.runs, run)
	return nil
}

// subnetInvRepo serves Subnets by ID on top of stubInvRepo.
type subnetInvRepo struct {
	*stubInvRepo
	subnets   map[uuid.UUID]*db.Subnet
	lookupErr error
}

func (s *subnetInvRepo) GetSubnetByID(_ context.Context, id uuid.UUID) (*db.Subnet, error) {
	if s.lookupErr != nil {
		return nil, s.lookupErr
	}
	if subnet, ok := s.subnets[id]; ok {
		return subnet, nil
	}
	return nil, inventoryRepo.ErrSubnetNotFound
}

func (s *subnetInvRepo) addSubnet(cidr string, deleted bool) uuid.UUID {
	id := uuid.New()
	subnet := &db.Subnet{ID: id, Name: "lab-" + cidr, Cidr: netip.MustParsePrefix(cidr)}
	if deleted {
		subnet.DeletedAt = pgtype.Timestamptz{Time: time.Now(), Valid: true}
	}
	s.subnets[id] = subnet
	return id
}

func newRunTargetsUseCase(source *dto.DiscoverySourceResponse) (*DefaultDiscoveryUseCase, *recordingOrchestrator, *stubRunRepo, *subnetInvRepo) {
	orch := &recordingOrchestrator{}
	repo := &stubRunRepo{source: source}
	inv := &subnetInvRepo{stubInvRepo: &stubInvRepo{}, subnets: make(map[uuid.UUID]*db.Subnet)}
	return &DefaultDiscoveryUseCase{discRepo: repo, invRepo: inv, orchestrator: orch}, orch, repo, inv
}

func sourceWithTargets(collectorTypes []string, cidrs ...string) *dto.DiscoverySourceResponse {
	source := &dto.DiscoverySourceResponse{ID: uuid.New(), Name: "Lab", Enabled: true}
	for _, collectorType := range collectorTypes {
		source.Collectors = append(source.Collectors, dto.CollectorResponse{ID: uuid.New(), CollectorType: collectorType, Enabled: true})
	}
	for _, cidr := range cidrs {
		source.Targets = append(source.Targets, dto.SourceTargetResponse{CIDR: cidr})
	}
	if len(cidrs) > 0 {
		source.ConfigCIDR = cidrs[0]
	}
	return source
}

func targetCIDRs(targets []collectors.DiscoveryTarget) []string {
	cidrs := make([]string, len(targets))
	for i, target := range targets {
		cidrs[i] = target.CIDR
	}
	return cidrs
}

// runKey identifies a recorded row by collector and target, for order-free assertions.
func runKey(run *db.CreateCollectorRunParams) string {
	target := "-"
	if run.TargetCidr != nil {
		target = run.TargetCidr.String()
	}
	return run.CollectorType + "@" + target
}

func runsByKey(runs []*db.CreateCollectorRunParams) map[string]*db.CreateCollectorRunParams {
	byKey := make(map[string]*db.CreateCollectorRunParams, len(runs))
	for _, run := range runs {
		byKey[runKey(run)] = run
	}
	return byKey
}

func TestTriggerRun_Targets(t *testing.T) {
	ctx := context.Background()

	t.Run("two free targets run in one engine call and each row carries its target", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep", "arp_sweep"}, "10.0.10.0/24", "10.0.20.0/24")
		uc, orch, repo, _ := newRunTargetsUseCase(source)

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}

		if len(orch.calls) != 1 {
			t.Fatalf("expected 1 RunTargets call, got %d", len(orch.calls))
		}
		call := orch.calls[0]
		if got := targetCIDRs(call.targets); fmt.Sprint(got) != "[10.0.10.0/24 10.0.20.0/24]" {
			t.Errorf("expected both targets in order, got %v", got)
		}
		if call.run.SourceID == nil || *call.run.SourceID != source.ID {
			t.Errorf("expected the run to carry the source ID, got %+v", call.run)
		}
		if call.run.CIDR != "" {
			t.Errorf("expected the run-wide target to carry no CIDR, got %q", call.run.CIDR)
		}

		byKey := runsByKey(repo.runs)
		if len(repo.runs) != 3 {
			t.Fatalf("expected 3 rows (icmp per target + arp once), got %d: %v", len(repo.runs), byKey)
		}
		for _, key := range []string{"icmp_sweep@10.0.10.0/24", "icmp_sweep@10.0.20.0/24", "arp_sweep@-"} {
			if _, ok := byKey[key]; !ok {
				t.Errorf("expected row %s, got %v", key, byKey)
			}
		}
		if res.LastStatus != "idle" {
			t.Errorf("expected status idle, got %q", res.LastStatus)
		}
	})

	t.Run("a linked target runs against the Subnet's current CIDR", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"})
		uc, orch, repo, inv := newRunTargetsUseCase(source)
		// The Subnet was edited after the source was saved: the stored cidr is a stale snapshot.
		subnetID := inv.addSubnet("10.0.31.0/24", false)
		source.Targets = []dto.SourceTargetResponse{{CIDR: "10.0.30.0/24", SubnetID: &subnetID}}

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}
		if len(orch.calls) != 1 || len(orch.calls[0].targets) != 1 {
			t.Fatalf("expected one call with one target, got %+v", orch.calls)
		}
		target := orch.calls[0].targets[0]
		if target.CIDR != "10.0.31.0/24" || target.SubnetID != subnetID.String() {
			t.Errorf("expected the Subnet's current CIDR and ID, got %+v", target)
		}
		if len(repo.runs) != 1 || runKey(repo.runs[0]) != "icmp_sweep@10.0.31.0/24" {
			t.Errorf("expected the row to carry the resolved CIDR, got %v", runsByKey(repo.runs))
		}
	})

	t.Run("a target linked to a deleted Subnet is skipped without scanning it", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep", "reverse_dns", "arp_sweep"}, "10.0.10.0/24")
		uc, orch, repo, inv := newRunTargetsUseCase(source)
		deletedID := inv.addSubnet("10.0.40.0/24", true)
		missingID := uuid.New()
		source.Targets = append(source.Targets,
			dto.SourceTargetResponse{CIDR: "10.0.40.0/24", SubnetID: &deletedID},
			dto.SourceTargetResponse{CIDR: "10.0.50.0/24", SubnetID: &missingID},
		)

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}
		if len(orch.calls) != 1 {
			t.Fatalf("expected 1 RunTargets call, got %d", len(orch.calls))
		}
		if got := targetCIDRs(orch.calls[0].targets); fmt.Sprint(got) != "[10.0.10.0/24]" {
			t.Errorf("expected only the free target to reach the engine, got %v", got)
		}

		byKey := runsByKey(repo.runs)
		for _, cidr := range []string{"10.0.40.0/24", "10.0.50.0/24"} {
			for _, collectorType := range []string{"icmp_sweep", "reverse_dns"} {
				run, ok := byKey[collectorType+"@"+cidr]
				if !ok {
					t.Errorf("expected a skipped %s row for %s, got %v", collectorType, cidr, byKey)
					continue
				}
				if run.Status != "skipped" || run.ErrorMessage.String != "target subnet deleted" {
					t.Errorf("expected skipped with the deleted subnet message, got %s / %q", run.Status, run.ErrorMessage.String)
				}
			}
			if _, ok := byKey["arp_sweep@"+cidr]; ok {
				t.Errorf("a union collector must not get a per-target row for %s", cidr)
			}
		}
		if len(repo.runs) != 3+4 {
			t.Errorf("expected 3 scanned rows and 4 skipped rows, got %d", len(repo.runs))
		}
		if res.LastStatus != "partial" {
			t.Errorf("expected skipped rows to make the run partial, got %q", res.LastStatus)
		}
	})

	t.Run("every target linked to a deleted Subnet ends in error without scanning", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep", "proxmox"})
		uc, orch, repo, inv := newRunTargetsUseCase(source)
		deletedID := inv.addSubnet("10.0.40.0/24", true)
		source.Targets = []dto.SourceTargetResponse{{CIDR: "10.0.40.0/24", SubnetID: &deletedID}}

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if err == nil {
			t.Fatal("expected an error when no target is left to scan")
		}
		if len(orch.calls) != 0 {
			t.Errorf("expected no engine call, got %+v", orch.calls)
		}
		if len(repo.runs) != 1 || repo.runs[0].Status != "skipped" || runKey(repo.runs[0]) != "icmp_sweep@10.0.40.0/24" {
			t.Errorf("expected one skipped row for the deleted target, got %v", runsByKey(repo.runs))
		}
		if res == nil || res.LastStatus != "error" {
			t.Errorf("expected status error, got %+v", res)
		}
	})

	t.Run("a union-only plan whose every target was deleted still ends in error", func(t *testing.T) {
		source := sourceWithTargets([]string{"arp_sweep"})
		uc, orch, repo, inv := newRunTargetsUseCase(source)
		deletedID := inv.addSubnet("10.0.40.0/24", true)
		source.Targets = []dto.SourceTargetResponse{{CIDR: "10.0.40.0/24", SubnetID: &deletedID}}

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err == nil {
			t.Fatal("expected an error when no target is left to scan")
		}
		if len(orch.calls) != 0 || len(repo.runs) != 0 {
			t.Errorf("expected no scan and no rows, got %+v / %d rows", orch.calls, len(repo.runs))
		}
		if source.LastStatus != "error" {
			t.Errorf("expected status error, got %q", source.LastStatus)
		}
	})

	t.Run("one failing target and one succeeding target yield partial", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"}, "10.0.10.0/24", "10.0.20.0/24")
		uc, orch, repo, _ := newRunTargetsUseCase(source)
		orch.respond = func(targets []collectors.DiscoveryTarget, requested []string) (*engine.ScanResult, error) {
			return successResult(targets, requested, map[string]bool{"10.0.20.0/24": true}), nil
		}

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if err != nil {
			t.Fatalf("expected a partial run to succeed, got %v", err)
		}
		if res.LastStatus != "partial" {
			t.Errorf("expected status partial, got %q", res.LastStatus)
		}
		byKey := runsByKey(repo.runs)
		if run := byKey["icmp_sweep@10.0.10.0/24"]; run == nil || run.Status != "success" {
			t.Errorf("expected the first target to succeed, got %v", byKey)
		}
		if run := byKey["icmp_sweep@10.0.20.0/24"]; run == nil || run.Status != "error" {
			t.Errorf("expected the second target to fail, got %v", byKey)
		}
	})

	t.Run("cancellation records the partial rows and keeps skipped rows skipped", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep", "docker"}, "10.0.10.0/24", "10.0.20.0/24", "10.0.30.0/24")
		uc, orch, repo, inv := newRunTargetsUseCase(source)
		const scope = "lab-cluster"
		gone := deviceWithRef(t, "docker:lab-cluster:container:gone")
		inv.devicesByProviderScope = map[string][]db.Device{scope: {gone}}
		orch.respond = func([]collectors.DiscoveryTarget, []string) (*engine.ScanResult, error) {
			return &engine.ScanResult{Collectors: []engine.CollectorRunDetail{
				{CollectorType: "icmp", TargetCIDR: "10.0.10.0/24", Status: "success", DevicesFound: 2},
				{
					CollectorType: "docker", Status: "success", DevicesFound: 1,
					Scopes: []engine.ProviderScopeObservation{{Scope: scope, ObservedRefs: []string{"docker:lab-cluster:container:present"}}},
				},
				{CollectorType: "icmp", TargetCIDR: "10.0.20.0/24", Status: "error", ErrorMessage: "icmp sweep: context canceled"},
				{CollectorType: "icmp", TargetCIDR: "10.0.30.0/24", Status: "skipped", ErrorMessage: "run cancelled before the collector started"},
			}}, fmt.Errorf("orchestrator scan aborted: %w", context.Canceled)
		}

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if !errors.Is(err, context.Canceled) {
			t.Fatalf("expected the cancellation to be returned, got %v", err)
		}
		if res.LastStatus != "cancelled" {
			t.Errorf("expected status cancelled, got %q", res.LastStatus)
		}

		byKey := runsByKey(repo.runs)
		want := map[string]string{
			"icmp_sweep@10.0.10.0/24": "success",
			"docker@-":                "success",
			"icmp_sweep@10.0.20.0/24": "timeout",
			"icmp_sweep@10.0.30.0/24": "skipped",
		}
		if len(repo.runs) != len(want) {
			t.Errorf("expected %d rows, got %v", len(want), byKey)
		}
		for key, status := range want {
			if run := byKey[key]; run == nil || run.Status != status {
				t.Errorf("row %s: expected %s, got %v", key, status, run)
			}
		}
		if len(inv.absenceCalls) != 0 {
			t.Errorf("a cancelled run must not apply the lifecycle hysteresis, got %v", inv.absenceCalls)
		}
	})

	t.Run("provider-only plan with no targets runs with an empty target list", func(t *testing.T) {
		source := sourceWithTargets([]string{"proxmox", "docker"})
		uc, orch, repo, _ := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}
		if len(orch.calls) != 1 || len(orch.calls[0].targets) != 0 {
			t.Fatalf("expected one call without targets, got %+v", orch.calls)
		}
		if fmt.Sprint(orch.calls[0].collectors) != "[proxmox docker]" {
			t.Errorf("expected the whole plan once, got %v", orch.calls[0].collectors)
		}
		for _, run := range repo.runs {
			if run.TargetCidr != nil {
				t.Errorf("expected provider rows without a target, got %s", runKey(run))
			}
		}
	})

	t.Run("sweep plan with no targets fails without scanning", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"})
		uc, orch, repo, _ := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err == nil {
			t.Fatal("expected an error for a sweep without targets")
		}
		if len(orch.calls) != 0 {
			t.Errorf("expected no scan, got %+v", orch.calls)
		}
		if source.LastStatus != "error" {
			t.Errorf("expected status error, got %q (history %v)", source.LastStatus, repo.statuses)
		}
	})

	t.Run("an invalid stored target fails before any scan", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"}, "10.0.10.0/24", "not-a-cidr")
		uc, orch, _, _ := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err == nil {
			t.Fatal("expected an error for an invalid stored target")
		}
		if len(orch.calls) != 0 {
			t.Errorf("expected no scan, got %+v", orch.calls)
		}
	})

	t.Run("a linked Subnet grown past the target limit fails before any scan", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"})
		uc, orch, _, inv := newRunTargetsUseCase(source)
		subnetID := inv.addSubnet("10.0.0.0/8", false)
		source.Targets = []dto.SourceTargetResponse{{CIDR: "10.0.10.0/24", SubnetID: &subnetID}}

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err == nil {
			t.Fatal("expected the resolved CIDR to be checked")
		}
		if len(orch.calls) != 0 {
			t.Errorf("expected no scan, got %+v", orch.calls)
		}
		if source.LastStatus != "error" {
			t.Errorf("expected status error, got %q", source.LastStatus)
		}
	})

	t.Run("a failed Subnet lookup fails the run without scanning", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"})
		uc, orch, repo, inv := newRunTargetsUseCase(source)
		inv.lookupErr = errors.New("connection reset")
		subnetID := uuid.New()
		source.Targets = []dto.SourceTargetResponse{{CIDR: "10.0.10.0/24", SubnetID: &subnetID}}

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err == nil {
			t.Fatal("expected the lookup failure to be returned")
		}
		if len(orch.calls) != 0 || len(repo.runs) != 0 {
			t.Errorf("expected no scan and no rows, got %+v / %d rows", orch.calls, len(repo.runs))
		}
		if source.LastStatus != "error" {
			t.Errorf("expected status error, got %q", source.LastStatus)
		}
	})
}

func TestInterruptedByCancellation(t *testing.T) {
	cases := []struct {
		detail engine.CollectorRunDetail
		want   bool
	}{
		{engine.CollectorRunDetail{Status: "error", ErrorMessage: "ping: context canceled"}, true},
		{engine.CollectorRunDetail{Status: "error", ErrorMessage: "context deadline exceeded"}, true},
		{engine.CollectorRunDetail{Status: "error", ErrorMessage: "collector \"unifi\" is not implemented in this wave"}, false},
		{engine.CollectorRunDetail{Status: "skipped", ErrorMessage: "context canceled"}, false},
		{engine.CollectorRunDetail{Status: "success"}, false},
	}
	for _, tc := range cases {
		if got := interruptedByCancellation(tc.detail); got != tc.want {
			t.Errorf("interruptedByCancellation(%+v) = %v, want %v", tc.detail, got, tc.want)
		}
	}
}

func TestTargetCIDRColumn(t *testing.T) {
	if targetCIDRColumn("") != nil {
		t.Error("expected an empty target to map to NULL")
	}
	if targetCIDRColumn("not-a-cidr") != nil {
		t.Error("expected an unparsable target to map to NULL")
	}
	if got := targetCIDRColumn("10.0.10.7/24"); got == nil || got.String() != "10.0.10.0/24" {
		t.Errorf("expected the masked prefix, got %v", got)
	}
}
