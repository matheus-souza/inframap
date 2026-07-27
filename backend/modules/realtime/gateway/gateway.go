package gateway

import (
	"context"
	"sync"

	"github.com/matheussouza/inframap/internal/platform/eventbus"
)

// Gateway manages real-time EventBus subscriptions and client SSE connections.
type Gateway struct {
	bus         eventbus.EventBus
	buffer      *RingBuffer
	mu          sync.RWMutex
	subscribers map[chan EventMessage]bool
}

// NewGateway constructs a new Realtime Gateway instance.
func NewGateway(bus eventbus.EventBus) *Gateway {
	return &Gateway{
		bus:         bus,
		buffer:      NewRingBuffer(DefaultRingBufferCapacity),
		subscribers: make(map[chan EventMessage]bool),
	}
}

// Start subscribes to internal domain events and forwards them to SSE subscribers.
func (g *Gateway) Start(_ context.Context) error {
	if g.bus == nil {
		return nil
	}

	topics := []string{
		"discovery.progress",
		"topology.updated",
		"system.notification",
		"device.created",
		"device.updated",
	}

	for _, topic := range topics {
		t := topic
		err := g.bus.Subscribe(t, func(_ context.Context, evt eventbus.DomainEvent) error {
			msg := NewEventMessage(evt.EventType(), evt.Payload())
			g.Broadcast(msg)
			return nil
		})
		if err != nil {
			return err
		}
	}
	return nil
}

// Subscribe registers a new client subscriber channel.
func (g *Gateway) Subscribe() (chan EventMessage, func()) {
	ch := make(chan EventMessage, 50)

	g.mu.Lock()
	g.subscribers[ch] = true
	g.mu.Unlock()

	unsubscribe := func() {
		g.mu.Lock()
		if _, exists := g.subscribers[ch]; exists {
			delete(g.subscribers, ch)
			close(ch)
		}
		g.mu.Unlock()
	}

	return ch, unsubscribe
}

// Broadcast distributes an EventMessage to the ring buffer and all active subscribers.
func (g *Gateway) Broadcast(msg EventMessage) {
	g.buffer.Add(msg)

	g.mu.RLock()
	defer g.mu.RUnlock()

	for ch := range g.subscribers {
		select {
		case ch <- msg:
		default:
			// Non-blocking drop if subscriber channel is full
		}
	}
}

// GetEventsAfter delegates to the ring buffer to replay missed events.
func (g *Gateway) GetEventsAfter(lastEventID string) []EventMessage {
	return g.buffer.GetEventsAfter(lastEventID)
}

// ActiveSubscribersCount returns the number of currently connected SSE clients.
func (g *Gateway) ActiveSubscribersCount() int {
	g.mu.RLock()
	defer g.mu.RUnlock()
	return len(g.subscribers)
}
