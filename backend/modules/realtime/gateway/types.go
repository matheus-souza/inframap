// Package gateway provides real-time EventBus subscriptions and client SSE channel management.
package gateway

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/google/uuid"
)

// EventMessage represents a single Server-Sent Event message.
type EventMessage struct {
	ID        string      `json:"id"`
	Event     string      `json:"event"`
	Data      interface{} `json:"data"`
	Timestamp time.Time   `json:"timestamp"`
}

// NewEventMessage constructs a new EventMessage with a time-ordered UUIDv7 ID.
func NewEventMessage(event string, data interface{}) EventMessage {
	return EventMessage{
		ID:        uuid.Must(uuid.NewV7()).String(),
		Event:     event,
		Data:      data,
		Timestamp: time.Now().UTC(),
	}
}

// FormatSSE formats the EventMessage into standard Server-Sent Event wire protocol bytes.
func (m EventMessage) FormatSSE() string {
	jsonData, err := json.Marshal(m.Data)
	if err != nil {
		jsonData = []byte("{}")
	}
	return fmt.Sprintf("id: %s\nevent: %s\ndata: %s\n\n", m.ID, m.Event, string(jsonData))
}

// SubscriberChan represents an active SSE client event channel.
type SubscriberChan chan EventMessage
