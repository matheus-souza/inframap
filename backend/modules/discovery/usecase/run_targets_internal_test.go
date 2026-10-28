package usecase

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/modules/discovery/collectors"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/engine"
	"github.com/matheussouza/inframap/modules/discovery/repository"
)

// scanCall is one RunScan invocation seen by recordingOrchestrator.
type scanCall struct {
	cidr       string
	collectors []string
}

// recordingOrchestrator records every RunScan call and reports each requested collector as
// a success, with one observation per collector.
type recordingOrchestrator struct {
	calls     []scanCall
	failOnRun int // 1-based call that returns a cancellation error; 0 never fails
}

func (o *recordingOrchestrator) RegisterCollector(collectors.Collector) {}

func (o *recordingOrchestrator) RunScan(_ context.Context, target collectors.DiscoveryTarget, _ []db.Device, requested []string) (*engine.ScanResult, error) {
	o.calls = append(o.calls, scanCall{cidr: target.CIDR, collectors: append([]string(nil), requested...)})
	if o.failOnRun == len(o.calls) {
		return nil, context.Canceled
	}
	details := make([]engine.CollectorRunDetail, len(requested))
	for i, collectorType := range requested {
		details[i] = engine.CollectorRunDetail{CollectorType: collectorType, Status: "success", DevicesFound: 1}
	}
	return &engine.ScanResult{
		Target:         target,
		TotalCollected: len(requested),
		Duration:       time.Millisecond,
		Collectors:     details,
	}, nil
}

// RunTargets is not used by the interim per-target execution path under test.
func (o *recordingOrchestrator) RunTargets(context.Context, collectors.DiscoveryTarget, []collectors.DiscoveryTarget, []db.Device, []string) (*engine.ScanResult, error) {
	return nil, errors.New("RunTargets is not expected in this test")
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

func newRunTargetsUseCase(source *dto.DiscoverySourceResponse) (*DefaultDiscoveryUseCase, *recordingOrchestrator, *stubRunRepo) {
	orch := &recordingOrchestrator{}
	repo := &stubRunRepo{source: source}
	return &DefaultDiscoveryUseCase{discRepo: repo, orchestrator: orch}, orch, repo
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

func TestTriggerRun_Targets(t *testing.T) {
	ctx := context.Background()

	t.Run("two targets run the plan once per target, in order", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep", "arp_sweep"}, "10.0.10.0/24", "10.0.20.0/24")
		uc, orch, repo := newRunTargetsUseCase(source)

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}

		if len(orch.calls) != 2 {
			t.Fatalf("expected 2 RunScan calls, got %d", len(orch.calls))
		}
		if orch.calls[0].cidr != "10.0.10.0/24" || orch.calls[1].cidr != "10.0.20.0/24" {
			t.Errorf("expected one call per target CIDR, got %+v", orch.calls)
		}
		for _, call := range orch.calls {
			if len(call.collectors) != 2 {
				t.Errorf("expected every sweep to run for each target, got %v", call.collectors)
			}
		}
		// Folded results: every collector of every target is recorded and aggregated.
		if len(repo.runs) != 4 {
			t.Errorf("expected 4 collector runs recorded, got %d", len(repo.runs))
		}
		if res.LastStatus != "idle" {
			t.Errorf("expected status idle, got %q", res.LastStatus)
		}
	})

	t.Run("a linked target runs against its stored CIDR", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"})
		subnetID := uuid.New()
		source.Targets = []dto.SourceTargetResponse{{CIDR: "10.0.30.0/24", SubnetID: &subnetID}}
		uc, orch, _ := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}
		if len(orch.calls) != 1 || orch.calls[0].cidr != "10.0.30.0/24" {
			t.Fatalf("expected one call with the stored CIDR, got %+v", orch.calls)
		}
	})

	t.Run("providers run with the first target only", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep", "proxmox"}, "10.0.10.0/24", "10.0.20.0/24", "10.0.30.0/24")
		uc, orch, repo := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}
		if len(orch.calls) != 3 {
			t.Fatalf("expected 3 RunScan calls, got %d", len(orch.calls))
		}
		if got := orch.calls[0].collectors; len(got) != 2 {
			t.Errorf("expected the first target to run the whole plan, got %v", got)
		}
		for _, call := range orch.calls[1:] {
			if len(call.collectors) != 1 || call.collectors[0] != "icmp_sweep" {
				t.Errorf("expected later targets to run only the sweep, got %v", call.collectors)
			}
		}
		if len(repo.runs) != 4 {
			t.Errorf("expected 4 collector runs recorded (3 sweeps + 1 provider), got %d", len(repo.runs))
		}
	})

	t.Run("provider-only plan with no targets runs once with an empty target", func(t *testing.T) {
		source := sourceWithTargets([]string{"proxmox", "docker"})
		uc, orch, _ := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err != nil {
			t.Fatalf("TriggerRun failed: %v", err)
		}
		if len(orch.calls) != 1 || orch.calls[0].cidr != "" {
			t.Fatalf("expected one call without a target, got %+v", orch.calls)
		}
	})

	t.Run("sweep plan with no targets fails without scanning", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"})
		uc, orch, repo := newRunTargetsUseCase(source)

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
		uc, orch, _ := newRunTargetsUseCase(source)

		if _, err := uc.TriggerRun(ctx, source.ID.String()); err == nil {
			t.Fatal("expected an error for an invalid stored target")
		}
		if len(orch.calls) != 0 {
			t.Errorf("expected no scan, got %+v", orch.calls)
		}
	})

	t.Run("cancellation skips the remaining targets and keeps earlier results", func(t *testing.T) {
		source := sourceWithTargets([]string{"icmp_sweep"}, "10.0.10.0/24", "10.0.20.0/24", "10.0.30.0/24")
		uc, orch, repo := newRunTargetsUseCase(source)
		orch.failOnRun = 2

		res, err := uc.TriggerRun(ctx, source.ID.String())
		if err == nil {
			t.Fatal("expected the cancellation to be returned")
		}
		if len(orch.calls) != 2 {
			t.Errorf("expected the third target to be skipped, got %d calls", len(orch.calls))
		}
		if res.LastStatus != "cancelled" {
			t.Errorf("expected status cancelled, got %q", res.LastStatus)
		}
		if len(repo.runs) != 1 {
			t.Errorf("expected the first target's run to be recorded, got %d", len(repo.runs))
		}
	})
}

func TestFoldScanResult(t *testing.T) {
	first := &engine.ScanResult{
		Target:         collectors.DiscoveryTarget{CIDR: "10.0.10.0/24"},
		TotalCollected: 2, TotalValid: 2, TotalDiscovered: 1, TotalUpdated: 1,
		Duration:   time.Second,
		Collectors: []engine.CollectorRunDetail{{CollectorType: "icmp", Status: "success"}},
	}
	second := &engine.ScanResult{
		Target:         collectors.DiscoveryTarget{CIDR: "10.0.20.0/24"},
		TotalCollected: 3, TotalValid: 1, TotalDiscovered: 1, TotalUpdated: 0,
		Duration:   2 * time.Second,
		Collectors: []engine.CollectorRunDetail{{CollectorType: "icmp", Status: "error"}},
	}

	folded := foldScanResult(nil, first)
	folded = foldScanResult(folded, nil)
	folded = foldScanResult(folded, second)

	if folded.TotalCollected != 5 || folded.TotalValid != 3 || folded.TotalDiscovered != 2 || folded.TotalUpdated != 1 {
		t.Errorf("expected totals to be summed, got %+v", folded)
	}
	if folded.Duration != 3*time.Second {
		t.Errorf("expected durations to be summed, got %v", folded.Duration)
	}
	if len(folded.Collectors) != 2 || folded.Collectors[1].Status != "error" {
		t.Errorf("expected collector details to be concatenated, got %+v", folded.Collectors)
	}
	if folded.Target.CIDR != "10.0.10.0/24" {
		t.Errorf("expected the folded result to keep the first target, got %q", folded.Target.CIDR)
	}
	if len(first.Collectors) != 1 {
		t.Error("folding must not modify the first result")
	}
}
