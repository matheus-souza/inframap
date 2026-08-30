// Package scheduler manages background cron jobs for automated discovery source execution.
package scheduler

import (
	"context"
	"log/slog"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/robfig/cron/v3"
)

const (
	defaultReconcileInterval = 5 * time.Minute
	defaultPurgeInterval     = 24 * time.Hour
	defaultStartupPurgeDelay = 1 * time.Minute
	defaultRetentionDays     = 7
)

type registeredEntry struct {
	entryID  cron.EntryID
	cronExpr string
}

// SourceLister loads discovery sources for scheduling.
type SourceLister interface {
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	TriggerRun(ctx context.Context, sourceID string) (*dto.DiscoverySourceResponse, error)
}

// RunPurger purges old collector runs based on retention policy.
type RunPurger interface {
	PurgeCollectorRuns(ctx context.Context, retentionDays int) (int64, error)
}

// StatusUpdater persists source status changes (boot cleanup, shutdown).
type StatusUpdater interface {
	UpdateSourceStatus(ctx context.Context, id uuid.UUID, status string) (*dto.DiscoverySourceResponse, error)
}

// Scheduler manages background cron jobs for discovery sources.
type Scheduler struct {
	uc      SourceLister
	updater StatusUpdater
	bus     eventbus.EventBus
	logger  *slog.Logger

	cron      *cron.Cron
	entriesMu sync.Mutex
	entries   map[uuid.UUID]registeredEntry

	runningMu sync.Mutex
	running   map[uuid.UUID]*sync.Mutex

	inFlightMu  sync.Mutex
	inFlight    map[uuid.UUID]bool
	cancelledMu sync.Mutex
	cancelled   map[uuid.UUID]bool
	wg          sync.WaitGroup

	reconcileInterval time.Duration
	purgeInterval     time.Duration
	startupPurgeDelay time.Duration
	retentionDays     int

	ctx    context.Context
	cancel context.CancelFunc
}

// New creates a Scheduler. Call Start to begin scheduling.
func New(uc SourceLister, updater StatusUpdater, bus eventbus.EventBus, logger *slog.Logger) *Scheduler {
	return &Scheduler{
		uc:                uc,
		updater:           updater,
		bus:               bus,
		logger:            logger,
		entries:           make(map[uuid.UUID]registeredEntry),
		running:           make(map[uuid.UUID]*sync.Mutex),
		inFlight:          make(map[uuid.UUID]bool),
		cancelled:         make(map[uuid.UUID]bool),
		reconcileInterval: defaultReconcileInterval,
		purgeInterval:     defaultPurgeInterval,
		startupPurgeDelay: defaultStartupPurgeDelay,
		retentionDays:     0,
	}
}

// SetReconcileInterval overrides the default 5-minute reconciliation interval. Must be called before Start.
func (s *Scheduler) SetReconcileInterval(d time.Duration) {
	s.reconcileInterval = d
}

// SetPurgeInterval overrides the default 24-hour purge interval. Must be called before Start.
func (s *Scheduler) SetPurgeInterval(d time.Duration) {
	s.purgeInterval = d
}

// SetStartupPurgeDelay overrides the default 1-minute delay before the first purge run. Must be called before Start.
func (s *Scheduler) SetStartupPurgeDelay(d time.Duration) {
	s.startupPurgeDelay = d
}

// SetRetentionDays overrides the default retention days for collector runs.
func (s *Scheduler) SetRetentionDays(days int) {
	if days > 0 {
		s.retentionDays = days
	}
}

// Start loads sources, cleans up stale statuses, registers cron jobs, and starts the cron runner.
func (s *Scheduler) Start(ctx context.Context) error {
	s.ctx, s.cancel = context.WithCancel(ctx)
	s.cron = cron.New()

	if _, ok := s.uc.(RunPurger); !ok {
		s.logger.Warn("discovery usecase does not implement RunPurger; background retention purge is disabled")
	}

	sources, err := s.uc.ListSources(s.ctx)
	if err != nil {
		return err
	}

	for _, src := range sources {
		if src.LastStatus == "running" || src.LastStatus == "cancelled" {
			s.logger.Warn("resetting stale discovery source status on boot",
				slog.String("source_id", src.ID.String()),
				slog.String("previous_status", src.LastStatus),
			)
			if _, updateErr := s.updater.UpdateSourceStatus(s.ctx, src.ID, "idle"); updateErr != nil {
				s.logger.Error("failed to reset source status", slog.String("source_id", src.ID.String()), slog.Any("error", updateErr))
			}
		}

		s.registerSourceLocked(src)
	}

	subscriptions := map[string]eventbus.EventHandler{
		"discovery_source.created": s.handleSourceCreated,
		"discovery_source.updated": s.handleSourceUpdated,
		"discovery_source.deleted": s.handleSourceDeleted,
	}
	for topic, handler := range subscriptions {
		if subErr := s.bus.Subscribe(topic, handler); subErr != nil {
			s.logger.Error("failed to subscribe scheduler handler",
				slog.String("topic", topic),
				slog.Any("error", subErr),
			)
		}
	}

	s.cron.Start()
	s.wg.Add(1)
	go s.reconcileLoop()
	s.wg.Add(1)
	go s.purgeLoop()
	return nil
}

// Stop halts the cron runner, cancels in-flight scans, waits for them
// to return, and sets their persisted status to "cancelled".
func (s *Scheduler) Stop() {
	var cronCtx context.Context
	if s.cron != nil {
		cronCtx = s.cron.Stop()
	}

	if s.cancel != nil {
		s.cancel()
	}

	if cronCtx != nil {
		<-cronCtx.Done()
	}

	s.wg.Wait()

	s.cancelledMu.Lock()
	sources := make([]uuid.UUID, 0, len(s.cancelled))
	for id := range s.cancelled {
		sources = append(sources, id)
	}
	s.cancelledMu.Unlock()

	for _, id := range sources {
		if _, err := s.updater.UpdateSourceStatus(context.Background(), id, "cancelled"); err != nil {
			s.logger.Error("failed to set cancelled status on shutdown",
				slog.String("source_id", id.String()),
				slog.Any("error", err),
			)
		}
	}
}

func (s *Scheduler) handleSourceCreated(_ context.Context, event eventbus.DomainEvent) error {
	src, ok := s.parseSourceEvent(event)
	if !ok {
		return nil
	}
	s.registerSource(src)
	return nil
}

func (s *Scheduler) handleSourceUpdated(_ context.Context, event eventbus.DomainEvent) error {
	src, ok := s.parseSourceEvent(event)
	if !ok {
		return nil
	}
	s.entriesMu.Lock()
	defer s.entriesMu.Unlock()
	s.unregisterSourceLocked(src.ID)
	s.registerSourceLocked(src)
	return nil
}

func (s *Scheduler) handleSourceDeleted(_ context.Context, event eventbus.DomainEvent) error {
	payload, ok := event.Payload().(map[string]interface{})
	if !ok {
		return nil
	}
	idStr, _ := payload["source_id"].(string)
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil
	}
	s.unregisterSource(id)
	return nil
}

func (s *Scheduler) parseSourceEvent(event eventbus.DomainEvent) (*dto.DiscoverySourceResponse, bool) {
	payload, ok := event.Payload().(map[string]interface{})
	if !ok {
		return nil, false
	}

	idStr, _ := payload["source_id"].(string)
	id, err := uuid.Parse(idStr)
	if err != nil {
		return nil, false
	}

	enabled, _ := payload["enabled"].(bool)

	var cronPtr *string
	if cronStr, ok := payload["schedule_cron"].(string); ok && cronStr != "" {
		cronPtr = &cronStr
	}

	return &dto.DiscoverySourceResponse{
		ID:           id,
		Enabled:      enabled,
		ScheduleCron: cronPtr,
	}, true
}

func (s *Scheduler) unregisterSource(id uuid.UUID) {
	s.entriesMu.Lock()
	defer s.entriesMu.Unlock()
	s.unregisterSourceLocked(id)
}

func (s *Scheduler) unregisterSourceLocked(id uuid.UUID) {
	if entry, ok := s.entries[id]; ok {
		s.cron.Remove(entry.entryID)
		delete(s.entries, id)
	}
	s.runningMu.Lock()
	delete(s.running, id)
	s.runningMu.Unlock()
}

func (s *Scheduler) registerSource(src *dto.DiscoverySourceResponse) {
	s.entriesMu.Lock()
	defer s.entriesMu.Unlock()
	s.registerSourceLocked(src)
}

func (s *Scheduler) registerSourceLocked(src *dto.DiscoverySourceResponse) {
	if !src.Enabled || src.ScheduleCron == nil || *src.ScheduleCron == "" {
		return
	}

	sourceID := src.ID
	cronExpr := *src.ScheduleCron

	entryID, err := s.cron.AddFunc(cronExpr, func() {
		s.executeSource(sourceID)
	})
	if err != nil {
		s.logger.Error("failed to register cron job",
			slog.String("source_id", sourceID.String()),
			slog.String("cron", cronExpr),
			slog.Any("error", err),
		)
		return
	}

	s.entries[sourceID] = registeredEntry{entryID: entryID, cronExpr: cronExpr}
}

func (s *Scheduler) executeSource(sourceID uuid.UUID) {
	mu := s.getSourceLock(sourceID)

	if !mu.TryLock() {
		s.logger.Warn("skipping scheduled scan: previous scan still running",
			slog.String("source_id", sourceID.String()),
		)
		return
	}

	s.wg.Add(1)
	s.inFlightMu.Lock()
	s.inFlight[sourceID] = true
	s.inFlightMu.Unlock()

	defer func() {
		if s.ctx.Err() != nil {
			s.cancelledMu.Lock()
			s.cancelled[sourceID] = true
			s.cancelledMu.Unlock()
		}
		s.inFlightMu.Lock()
		delete(s.inFlight, sourceID)
		s.inFlightMu.Unlock()
		mu.Unlock()
		s.wg.Done()
	}()

	_ = s.bus.Publish(s.ctx, eventbus.NewBaseEvent("discovery_source.scan.started", map[string]interface{}{
		"source_id": sourceID.String(),
		"trigger":   "scheduled",
	}))

	start := time.Now()
	res, err := s.uc.TriggerRun(s.ctx, sourceID.String())
	durationMs := time.Since(start).Milliseconds()

	if err != nil {
		if s.ctx.Err() != nil {
			return
		}
		s.logger.Error("scheduled scan failed",
			slog.String("source_id", sourceID.String()),
			slog.Any("error", err),
		)
		_ = s.bus.Publish(s.ctx, eventbus.NewBaseEvent("discovery_source.scan.completed", map[string]interface{}{
			"source_id":   sourceID.String(),
			"trigger":     "scheduled",
			"duration_ms": durationMs,
			"success":     false,
			"error":       err.Error(),
		}))
		return
	}

	payload := map[string]interface{}{
		"source_id":   sourceID.String(),
		"trigger":     "scheduled",
		"duration_ms": durationMs,
		"success":     true,
	}
	if res != nil && res.LastStatus != "" {
		payload["status"] = res.LastStatus
	}
	_ = s.bus.Publish(s.ctx, eventbus.NewBaseEvent("discovery_source.scan.completed", payload))
}

func (s *Scheduler) reconcileLoop() {
	defer s.wg.Done()
	ticker := time.NewTicker(s.reconcileInterval)
	defer ticker.Stop()

	for {
		select {
		case <-s.ctx.Done():
			return
		case <-ticker.C:
			s.reconcile()
		}
	}
}

func (s *Scheduler) reconcile() {
	sources, err := s.uc.ListSources(s.ctx)
	if err != nil {
		if s.ctx.Err() != nil {
			return
		}
		s.logger.Error("reconciliation failed to list sources", slog.Any("error", err))
		return
	}

	desired := make(map[uuid.UUID]string)
	desiredSources := make(map[uuid.UUID]*dto.DiscoverySourceResponse)
	for _, src := range sources {
		if src.Enabled && src.ScheduleCron != nil && *src.ScheduleCron != "" {
			desired[src.ID] = *src.ScheduleCron
			desiredSources[src.ID] = src
		}
	}

	s.entriesMu.Lock()
	defer s.entriesMu.Unlock()

	for id, entry := range s.entries {
		cronExpr, exists := desired[id]
		if !exists || cronExpr != entry.cronExpr {
			s.unregisterSourceLocked(id)
		}
	}

	for id, src := range desiredSources {
		if _, exists := s.entries[id]; !exists {
			s.registerSourceLocked(src)
		}
	}
}

func (s *Scheduler) getSourceLock(id uuid.UUID) *sync.Mutex {
	s.runningMu.Lock()
	defer s.runningMu.Unlock()
	mu, ok := s.running[id]
	if !ok {
		mu = &sync.Mutex{}
		s.running[id] = mu
	}
	return mu
}

func (s *Scheduler) purgeLoop() {
	defer s.wg.Done()

	// Initial delay before the first purge run
	select {
	case <-s.ctx.Done():
		return
	case <-time.After(s.startupPurgeDelay):
		s.executePurge()
	}

	ticker := time.NewTicker(s.purgeInterval)
	defer ticker.Stop()

	for {
		select {
		case <-s.ctx.Done():
			return
		case <-ticker.C:
			s.executePurge()
		}
	}
}

func (s *Scheduler) executePurge() {
	purger, ok := s.uc.(RunPurger)
	if !ok {
		return
	}

	s.logger.Debug("starting background retention purge for discovery collector runs",
		slog.Int("retention_days", s.retentionDays),
	)

	deleted, err := purger.PurgeCollectorRuns(s.ctx, s.retentionDays)
	if err != nil {
		if s.ctx.Err() != nil {
			return
		}
		s.logger.Error("background retention purge failed",
			slog.Int("retention_days", s.retentionDays),
			slog.Any("error", err),
		)
		return
	}

	if deleted > 0 {
		s.logger.Info("background retention purge completed",
			slog.Int64("purged_runs", deleted),
			slog.Int("retention_days", s.retentionDays),
		)
	}
}

