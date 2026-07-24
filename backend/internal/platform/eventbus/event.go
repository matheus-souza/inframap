package eventbus

import (
	"time"

	"github.com/google/uuid"
)

// DomainEvent represents an immutable domain state change payload
type DomainEvent interface {
	EventID() string
	EventType() string
	OccurredAt() time.Time
	Payload() any
}

// BaseEvent is a convenience struct for constructing DomainEvents
type BaseEvent struct {
	id         string
	eventType  string
	occurredAt time.Time
	payload    any
}

func NewBaseEvent(eventType string, payload any) BaseEvent {
	return BaseEvent{
		id:         uuid.Must(uuid.NewV7()).String(),
		eventType:  eventType,
		occurredAt: time.Now().UTC(),
		payload:    payload,
	}
}

func (e BaseEvent) EventID() string      { return e.id }
func (e BaseEvent) EventType() string    { return e.eventType }
func (e BaseEvent) OccurredAt() time.Time { return e.occurredAt }
func (e BaseEvent) Payload() any         { return e.payload }
