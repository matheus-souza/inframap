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

	ctx    context.Context
	cancel context.CancelFunc
}

// New creates a Scheduler. Call Start to begin scheduling.
func New(uc SourceLister, updater StatusUpdater, bus eventbus.EventBus, logger *slog.Logger) *Scheduler {
	return &Scheduler{
		uc:      uc,
		updater: updater,
		bus:     bus,
		logger:  logger,
		entries: make(map[uuid.UUID]cron.EntryID),
		running: make(map[uuid.UUID]*sync.Mutex),
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

	s.cron.Start()
	return nil
}

// Stop halts the cron runner and cancels the scheduler context.
func (s *Scheduler) Stop() {
	if s.cron != nil {
		s.cron.Stop()
	}
	if s.cancel != nil {
		s.cancel()
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
	defer mu.Unlock()

	if _, err := s.uc.TriggerRun(s.ctx, sourceID.String()); err != nil {
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
