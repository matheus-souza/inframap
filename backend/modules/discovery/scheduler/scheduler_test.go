package scheduler_test

import (
	"context"
	"log/slog"
	"sync"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/matheussouza/inframap/modules/discovery/scheduler"
)

type fakeUseCase struct {
	mu             sync.Mutex
	sources        []*dto.DiscoverySourceResponse
	triggerCalls   []string
	triggerDelay   time.Duration
	triggerErr     error
	onTriggerStart func()
}

func (f *fakeUseCase) ListSources(_ context.Context) ([]*dto.DiscoverySourceResponse, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	cp := make([]*dto.DiscoverySourceResponse, len(f.sources))
	copy(cp, f.sources)
	return cp, nil
}

func (f *fakeUseCase) TriggerRun(ctx context.Context, sourceID string) (*dto.DiscoverySourceResponse, error) {
	if f.onTriggerStart != nil {
		f.onTriggerStart()
	}
	if f.triggerDelay > 0 {
		select {
		case <-time.After(f.triggerDelay):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	f.mu.Lock()
	f.triggerCalls = append(f.triggerCalls, sourceID)
	f.mu.Unlock()
	if f.triggerErr != nil {
		return nil, f.triggerErr
	}
	return &dto.DiscoverySourceResponse{}, nil
}

func (f *fakeUseCase) getTriggerCalls() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	cp := make([]string, len(f.triggerCalls))
	copy(cp, f.triggerCalls)
	return cp
}

type fakeStatusUpdater struct {
	mu      sync.Mutex
	updates map[uuid.UUID]string
}

func (f *fakeStatusUpdater) UpdateSourceStatus(_ context.Context, id uuid.UUID, status string) (*dto.DiscoverySourceResponse, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.updates == nil {
		f.updates = make(map[uuid.UUID]string)
	}
	f.updates[id] = status
	return &dto.DiscoverySourceResponse{}, nil
}

func (f *fakeStatusUpdater) getStatus(id uuid.UUID) (string, bool) {
	f.mu.Lock()
	defer f.mu.Unlock()
	s, ok := f.updates[id]
	return s, ok
}

func cron1s() *string { s := "@every 1s"; return &s }

func TestScheduler_RegistersAndFiresEligibleSources(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()
	logger := slog.Default()

	s := scheduler.New(uc, updater, bus, logger)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	deadline := time.After(3 * time.Second)
	for {
		calls := uc.getTriggerCalls()
		if len(calls) > 0 {
			if calls[0] != srcID.String() {
				t.Errorf("expected source %s, got %s", srcID, calls[0])
			}
			return
		}
		select {
		case <-deadline:
			t.Fatal("TriggerRun was not called within 3s")
		case <-time.After(100 * time.Millisecond):
		}
	}
}

func TestScheduler_SkipsDisabledSources(t *testing.T) {
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: uuid.New(), Enabled: false, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	time.Sleep(2 * time.Second)
	if calls := uc.getTriggerCalls(); len(calls) != 0 {
		t.Errorf("expected 0 calls for disabled source, got %d", len(calls))
	}
}

func TestScheduler_SkipsSourcesWithoutCron(t *testing.T) {
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: uuid.New(), Enabled: true, ScheduleCron: nil, LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	time.Sleep(2 * time.Second)
	if calls := uc.getTriggerCalls(); len(calls) != 0 {
		t.Errorf("expected 0 calls for source without cron, got %d", len(calls))
	}
}

func TestScheduler_SkipsConcurrentScanOfSameSource(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
		triggerDelay: 3 * time.Second,
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	time.Sleep(2500 * time.Millisecond)

	calls := uc.getTriggerCalls()
	if len(calls) > 1 {
		t.Errorf("expected at most 1 concurrent call, got %d", len(calls))
	}
}

func TestScheduler_BootCleanupResetsStaleStatuses(t *testing.T) {
	runningID := uuid.New()
	cancelledID := uuid.New()
	idleID := uuid.New()

	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: runningID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "running"},
			{ID: cancelledID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "cancelled"},
			{ID: idleID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	if status, ok := updater.getStatus(runningID); !ok || status != "idle" {
		t.Errorf("expected running source reset to idle, got %q (found=%v)", status, ok)
	}
	if status, ok := updater.getStatus(cancelledID); !ok || status != "idle" {
		t.Errorf("expected cancelled source reset to idle, got %q (found=%v)", status, ok)
	}
	if _, ok := updater.getStatus(idleID); ok {
		t.Error("idle source should not have been updated")
	}
}

func TestScheduler_ShutdownCancelsInFlightScan(t *testing.T) {
	srcID := uuid.New()
	scanStarted := make(chan struct{})
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
		triggerDelay: 30 * time.Second,
		onTriggerStart: func() {
			select {
			case scanStarted <- struct{}{}:
			default:
			}
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}

	select {
	case <-scanStarted:
	case <-time.After(3 * time.Second):
		t.Fatal("scan did not start within 3s")
	}

	done := make(chan struct{})
	go func() {
		s.Stop()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(5 * time.Second):
		t.Fatal("Stop() did not return within 5s")
	}
}

func TestScheduler_ShutdownSetsCancelledStatus(t *testing.T) {
	srcID := uuid.New()
	scanStarted := make(chan struct{})
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
		triggerDelay: 30 * time.Second,
		onTriggerStart: func() {
			select {
			case scanStarted <- struct{}{}:
			default:
			}
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}

	select {
	case <-scanStarted:
	case <-time.After(3 * time.Second):
		t.Fatal("scan did not start within 3s")
	}

	s.Stop()

	status, ok := updater.getStatus(srcID)
	if !ok {
		t.Fatal("expected status update for in-flight source after shutdown")
	}
	if status != "cancelled" {
		t.Errorf("expected status 'cancelled', got %q", status)
	}
}

func TestScheduler_ShutdownCompletesWhenNoScansRunning(t *testing.T) {
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}

	done := make(chan struct{})
	go func() {
		s.Stop()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("Stop() should return immediately when no scans are running")
	}
}

func TestScheduler_TriggerRunErrorDoesNotCrashScheduler(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
		triggerErr: context.DeadlineExceeded,
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	deadline := time.After(4 * time.Second)
	for {
		calls := uc.getTriggerCalls()
		if len(calls) >= 2 {
			return
		}
		select {
		case <-deadline:
			t.Fatalf("expected at least 2 calls despite errors, got %d", len(uc.getTriggerCalls()))
		case <-time.After(100 * time.Millisecond):
		}
	}
}
