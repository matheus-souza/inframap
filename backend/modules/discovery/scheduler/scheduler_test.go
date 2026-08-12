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

// fakeUseCase implements scheduler.SourceLister for tests.
// triggerDelay, triggerErr, and onTriggerStart are configuration fields
// that must be set before Start and remain immutable after construction.
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

func waitForCalls(t *testing.T, uc *fakeUseCase, n int, timeout time.Duration) {
	t.Helper()
	deadline := time.After(timeout)
	for {
		if len(uc.getTriggerCalls()) >= n {
			return
		}
		select {
		case <-deadline:
			t.Fatalf("expected at least %d TriggerRun calls within %s, got %d", n, timeout, len(uc.getTriggerCalls()))
		case <-time.After(100 * time.Millisecond):
		}
	}
}

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

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	waitForCalls(t, uc, 1, 3*time.Second)
	calls := uc.getTriggerCalls()
	if calls[0] != srcID.String() {
		t.Errorf("expected source %s, got %s", srcID, calls[0])
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

	time.Sleep(1200 * time.Millisecond)
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

	time.Sleep(1200 * time.Millisecond)
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

func TestScheduler_EventCreatedRegistersNewCronJob(t *testing.T) {
	srcID := uuid.New()
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
	defer s.Stop()

	cronExpr := "@every 1s"
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.created", map[string]interface{}{
		"source_id":     srcID.String(),
		"schedule_cron": cronExpr,
		"enabled":       true,
	}))

	waitForCalls(t, uc, 1, 3*time.Second)
	calls := uc.getTriggerCalls()
	if calls[0] != srcID.String() {
		t.Errorf("expected source %s, got %s", srcID, calls[0])
	}
}

func TestScheduler_EventUpdatedReRegistersCronJob(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
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

	waitForCalls(t, uc, 1, 3*time.Second)

	// Publish update event — disable the source
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.updated", map[string]interface{}{
		"source_id":     srcID.String(),
		"schedule_cron": "@every 1s",
		"enabled":       false,
	}))

	time.Sleep(500 * time.Millisecond)
	countAfterDisable := len(uc.getTriggerCalls())
	time.Sleep(1500 * time.Millisecond)
	countFinal := len(uc.getTriggerCalls())

	if countFinal > countAfterDisable {
		t.Errorf("source continued to fire after update event disabled it: %d → %d", countAfterDisable, countFinal)
	}
}

func TestScheduler_EventWithInvalidPayloadIsIgnored(t *testing.T) {
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
	defer s.Stop()

	// Non-map payload
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.created", "invalid"))
	// Invalid UUID
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.created", map[string]interface{}{
		"source_id": "not-a-uuid",
	}))
	// Delete with invalid payload
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.deleted", "invalid"))
	// Update with invalid payload
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.updated", "invalid"))

	time.Sleep(500 * time.Millisecond)
	if calls := uc.getTriggerCalls(); len(calls) != 0 {
		t.Errorf("expected 0 calls for invalid payloads, got %d", len(calls))
	}
}

func TestScheduler_InvalidCronExpressionLogsError(t *testing.T) {
	srcID := uuid.New()
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
	defer s.Stop()

	// Publish event with invalid cron expression
	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.created", map[string]interface{}{
		"source_id":     srcID.String(),
		"schedule_cron": "not-a-cron",
		"enabled":       true,
	}))

	time.Sleep(1200 * time.Millisecond)
	if calls := uc.getTriggerCalls(); len(calls) != 0 {
		t.Errorf("expected 0 calls for invalid cron expression, got %d", len(calls))
	}
}

func TestScheduler_EventCreatedDisabledDoesNotRegister(t *testing.T) {
	srcID := uuid.New()
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
	defer s.Stop()

	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.created", map[string]interface{}{
		"source_id":     srcID.String(),
		"schedule_cron": "@every 1s",
		"enabled":       false,
	}))

	time.Sleep(1200 * time.Millisecond)
	if calls := uc.getTriggerCalls(); len(calls) != 0 {
		t.Errorf("expected 0 calls for disabled source event, got %d", len(calls))
	}
}

func TestScheduler_EventDeletedRemovesCronJob(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
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

	waitForCalls(t, uc, 1, 3*time.Second)

	_ = bus.Publish(ctx, eventbus.NewBaseEvent("discovery_source.deleted", map[string]interface{}{
		"source_id": srcID.String(),
	}))

	time.Sleep(500 * time.Millisecond)

	countAfterDelete := len(uc.getTriggerCalls())
	time.Sleep(1500 * time.Millisecond)
	countFinal := len(uc.getTriggerCalls())

	if countFinal > countAfterDelete {
		t.Errorf("source continued to fire after delete event: %d → %d calls", countAfterDelete, countFinal)
	}
}

func TestScheduler_ReconcilePicksUpNewSource(t *testing.T) {
	newSrcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	s.SetReconcileInterval(500 * time.Millisecond)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	uc.mu.Lock()
	uc.sources = append(uc.sources, &dto.DiscoverySourceResponse{
		ID: newSrcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle",
	})
	uc.mu.Unlock()

	deadline := time.After(5 * time.Second)
	for {
		calls := uc.getTriggerCalls()
		for _, c := range calls {
			if c == newSrcID.String() {
				return
			}
		}
		select {
		case <-deadline:
			t.Fatal("reconciliation did not pick up new source within 5s")
		case <-time.After(100 * time.Millisecond):
		}
	}
}

func TestScheduler_ReconcileRemovesDeletedSource(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	s.SetReconcileInterval(500 * time.Millisecond)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	waitForCalls(t, uc, 1, 3*time.Second)

	uc.mu.Lock()
	uc.sources = nil
	uc.mu.Unlock()

	time.Sleep(1200 * time.Millisecond)

	countAfterReconcile := len(uc.getTriggerCalls())
	time.Sleep(1500 * time.Millisecond)
	countFinal := len(uc.getTriggerCalls())

	if countFinal > countAfterReconcile {
		t.Errorf("source continued to fire after reconciliation removed it: %d → %d", countAfterReconcile, countFinal)
	}
}

func TestScheduler_ReconcileUpdatesChangedCron(t *testing.T) {
	srcID := uuid.New()
	slowCron := func() *string { s := "@every 30s"; return &s }
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: slowCron(), LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	s.SetReconcileInterval(500 * time.Millisecond)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	time.Sleep(500 * time.Millisecond)
	if len(uc.getTriggerCalls()) > 0 {
		t.Fatal("30s cron should not have fired yet")
	}

	uc.mu.Lock()
	uc.sources[0] = &dto.DiscoverySourceResponse{
		ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle",
	}
	uc.mu.Unlock()

	deadline := time.After(5 * time.Second)
	for {
		if len(uc.getTriggerCalls()) > 0 {
			return
		}
		select {
		case <-deadline:
			t.Fatal("reconciliation did not update cron expression — source never fired")
		case <-time.After(100 * time.Millisecond):
		}
	}
}

func TestScheduler_ReconcileStopsOnContextCancel(t *testing.T) {
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	s := scheduler.New(uc, updater, bus, slog.Default())
	s.SetReconcileInterval(100 * time.Millisecond)
	ctx, cancel := context.WithCancel(context.Background())

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}

	cancel()

	done := make(chan struct{})
	go func() {
		s.Stop()
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(3 * time.Second):
		t.Fatal("Stop() did not return within 3s after context cancel")
	}
}

func TestScheduler_ScanEventsPublished(t *testing.T) {
	srcID := uuid.New()
	uc := &fakeUseCase{
		sources: []*dto.DiscoverySourceResponse{
			{ID: srcID, Enabled: true, ScheduleCron: cron1s(), LastStatus: "idle"},
		},
	}
	updater := &fakeStatusUpdater{}
	bus := eventbus.NewInMemoryEventBus(1, 16)
	defer func() { _ = bus.Close() }()

	startedEvents := make(chan eventbus.DomainEvent, 10)
	completedEvents := make(chan eventbus.DomainEvent, 10)
	_ = bus.Subscribe("discovery_source.scan.started", func(_ context.Context, e eventbus.DomainEvent) error {
		startedEvents <- e
		return nil
	})
	_ = bus.Subscribe("discovery_source.scan.completed", func(_ context.Context, e eventbus.DomainEvent) error {
		completedEvents <- e
		return nil
	})

	s := scheduler.New(uc, updater, bus, slog.Default())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	if err := s.Start(ctx); err != nil {
		t.Fatalf("Start() error: %v", err)
	}
	defer s.Stop()

	select {
	case evt := <-startedEvents:
		payload, ok := evt.Payload().(map[string]interface{})
		if !ok {
			t.Fatal("started event payload is not map[string]interface{}")
		}
		if payload["source_id"] != srcID.String() {
			t.Errorf("expected source_id %s, got %v", srcID, payload["source_id"])
		}
		if payload["trigger"] != "scheduled" {
			t.Errorf("expected trigger 'scheduled', got %v", payload["trigger"])
		}
	case <-time.After(3 * time.Second):
		t.Fatal("discovery_source.scan.started event not received within 3s")
	}

	select {
	case evt := <-completedEvents:
		payload, ok := evt.Payload().(map[string]interface{})
		if !ok {
			t.Fatal("completed event payload is not map[string]interface{}")
		}
		if payload["source_id"] != srcID.String() {
			t.Errorf("expected source_id %s, got %v", srcID, payload["source_id"])
		}
		if payload["success"] != true {
			t.Errorf("expected success true, got %v", payload["success"])
		}
		if _, ok := payload["duration_ms"]; !ok {
			t.Error("expected duration_ms in completed event payload")
		}
	case <-time.After(3 * time.Second):
		t.Fatal("discovery_source.scan.completed event not received within 3s")
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

	waitForCalls(t, uc, 2, 4*time.Second)
}
