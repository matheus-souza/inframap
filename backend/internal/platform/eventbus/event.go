// Package eventbus provides an in-memory asynchronous Domain Event publisher and subscriber engine.
package eventbus

import (
	"time"

	"github.com/google/uuid"
)

// DomainEvent represents an immutable domain state change payload.
type DomainEvent interface {
	EventID() string
	EventType() string
	OccurredAt() time.Time
	Payload() any
}

// BaseEvent is a convenience struct for constructing DomainEvents.
type BaseEvent struct {
	id         string
	eventType  string
	occurredAt time.Time
	payload    any
}

// NewBaseEvent constructs a new BaseEvent with a generated UUIDv7 identifier and current UTC timestamp.
func NewBaseEvent(eventType string, payload any) BaseEvent {
	return BaseEvent{
		id:         uuid.Must(uuid.NewV7()).String(),
		eventType:  eventType,
		occurredAt: time.Now().UTC(),
		payload:    payload,
	}
}

// EventID returns the unique identifier for the event.
func (e BaseEvent) EventID() string { return e.id }

// EventType returns the type string for the event.
func (e BaseEvent) EventType() string { return e.eventType }

// OccurredAt returns the timestamp when the event occurred.
func (e BaseEvent) OccurredAt() time.Time { return e.occurredAt }

// Payload returns the typed payload associated with the event.
func (e BaseEvent) Payload() any { return e.payload }
