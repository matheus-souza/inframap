// Package audit implements the Domain Event subscriber that persists audit records into PostgreSQL.
package audit

import (
	"context"
	"encoding/json"
	"fmt"
	"log"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/matheussouza/inframap/internal/platform/db"
	"github.com/matheussouza/inframap/internal/platform/eventbus"
)

// Subscriber listens to DomainEvents and persists corresponding audit log entries.
type Subscriber struct {
	db db.DBTX
}

// NewSubscriber creates a new audit Subscriber instance with the given database connection.
func NewSubscriber(database db.DBTX) *Subscriber {
	return &Subscriber{db: database}
}

// Register subscribes the Subscriber instance to wildcard * events on the provided EventBus.
func (s *Subscriber) Register(bus eventbus.EventBus) error {
	return bus.Subscribe("*", s.HandleEvent)
}

// HandleEvent processes a DomainEvent and inserts an audit log record into PostgreSQL.
func (s *Subscriber) HandleEvent(ctx context.Context, event eventbus.DomainEvent) error {
	eventID, err := uuid.Parse(event.EventID())
	if err != nil {
		eventID = uuid.New()
	}

	payloadBytes, err := json.Marshal(event.Payload())
	if err != nil {
		log.Printf("[audit] failed to marshal payload for event %s: %v", event.EventType(), err)
		payloadBytes = []byte("{}")
	}

	queries := db.New(s.db)
	_, err = queries.CreateAuditLog(ctx, db.CreateAuditLogParams{
		ID:           eventID,
		ActorID:      pgtype.UUID{Valid: false},
		ActorName:    "system",
		Action:       event.EventType(),
		ResourceType: "system_event",
		ResourceID:   pgtype.UUID{Valid: false},
		Changes:      payloadBytes,
		IpAddress:    nil,
		CreatedAt:    pgtype.Timestamptz{Time: event.OccurredAt(), Valid: true},
	})
	if err != nil {
		return fmt.Errorf("failed to insert audit log: %w", err)
	}

	return nil
}
