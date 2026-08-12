// Package scheduler manages background cron jobs for automated discovery source execution.
package scheduler

import (
	"context"
	"log/slog"
	"sync"

	"github.com/google/uuid"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
	"github.com/matheussouza/inframap/modules/discovery/dto"
	"github.com/robfig/cron/v3"
)

// SourceLister loads discovery sources for scheduling.
type SourceLister interface {
	ListSources(ctx context.Context) ([]*dto.DiscoverySourceResponse, error)
	TriggerRun(ctx context.Context, sourceID string) (*dto.DiscoverySourceResponse, error)
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

	cron    *cron.Cron
	entries map[uuid.UUID]cron.EntryID

	runningMu sync.Mutex
	running   map[uuid.UUID]*sync.Mutex

	inFlightMu sync.Mutex
	inFlight   map[uuid.UUID]bool
	wg         sync.WaitGroup

	ctx    context.Context
	cancel context.CancelFunc
}

// New creates a Scheduler. Call Start to begin scheduling.
func New(uc SourceLister, updater StatusUpdater, bus eventbus.EventBus, logger *slog.Logger) *Scheduler {
	return &Scheduler{
		uc:       uc,
		updater:  updater,
		bus:      bus,
		logger:   logger,
		entries:  make(map[uuid.UUID]cron.EntryID),
		running:  make(map[uuid.UUID]*sync.Mutex),
		inFlight: make(map[uuid.UUID]bool),
	}
}

// Start loads sources, cleans up stale statuses, registers cron jobs, and starts the cron runner.
func (s *Scheduler) Start(ctx context.Context) error {
	s.ctx, s.cancel = context.WithCancel(ctx)
	s.cron = cron.New()

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

		s.registerSource(src)
	}

	_ = s.bus.Subscribe("discovery_source.created", s.handleSourceCreated)
	_ = s.bus.Subscribe("discovery_source.updated", s.handleSourceUpdated)
	_ = s.bus.Subscribe("discovery_source.deleted", s.handleSourceDeleted)

	s.cron.Start()
	return nil
}

// Stop halts the cron runner, cancels in-flight scans, waits for them
// to return, and sets their persisted status to "cancelled".
func (s *Scheduler) Stop() {
	if s.cron != nil {
		s.cron.Stop()
	}

	s.inFlightMu.Lock()
	sources := make([]uuid.UUID, 0, len(s.inFlight))
	for id := range s.inFlight {
		sources = append(sources, id)
	}
	s.inFlightMu.Unlock()

	if s.cancel != nil {
		s.cancel()
	}

	s.wg.Wait()

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
	s.unregisterSource(src.ID)
	s.registerSource(src)
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
	if entryID, ok := s.entries[id]; ok {
		s.cron.Remove(entryID)
		delete(s.entries, id)
	}
}

func (s *Scheduler) registerSource(src *dto.DiscoverySourceResponse) {
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

	s.entries[sourceID] = entryID
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
		s.inFlightMu.Lock()
		delete(s.inFlight, sourceID)
		s.inFlightMu.Unlock()
		mu.Unlock()
		s.wg.Done()
	}()

	if _, err := s.uc.TriggerRun(s.ctx, sourceID.String()); err != nil {
		if s.ctx.Err() != nil {
			return
		}
		s.logger.Error("scheduled scan failed",
			slog.String("source_id", sourceID.String()),
			slog.Any("error", err),
		)
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
